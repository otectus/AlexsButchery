package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.advancement.ButcheringTrigger;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.SkeletonBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.RegistryObject;

/**
 * Butchery's acid, for our carcasses ({@code <Mob>acidProcedure}): a bottle of sulfuric acid poured on a whole
 * carcass (fresh or drained, lying or hanging) fizzes for eleven seconds and leaves the mob's skeleton in the same
 * place and position. Unlike Butchery's work queue, the countdown lives in the block entity and survives a reload.
 */
public final class Acid {
    /** Butchery's delay between pouring and the skeleton: 220 ticks. */
    public static final int DISSOLVE_TICKS = 220;

    public static boolean canDissolve(AbstractCarcassBlock block, BlockState state) {
        return block.def().hasSkeleton() && !(block instanceof SkeletonBlock) && block.stage(state) <= 1;
    }

    public static InteractionResult pour(Level level, BlockPos pos, BlockState state, AbstractCarcassBlock block, Player player, InteractionHand hand) {
        if (!canDissolve(block, state)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof CarcassBlockEntity carcass) || carcass.isBleeding() || carcass.isDissolving()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack bottle = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild) {
            bottle.shrink(1);
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(Items.GLASS_BOTTLE));
        }
        player.swing(hand, true);
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        SoundEvent acid = ButcheryHooks.acidSound();
        if (acid != null) level.playSound(null, pos, acid, SoundSource.BLOCKS, 1.0F, 1.0F);
        carcass.startDissolving(DISSOLVE_TICKS);
        fizz((ServerLevel) level, pos, 0);
        if (player instanceof ServerPlayer sp) ButcheringTrigger.INSTANCE.trigger(sp, block.def(), ButcheringTrigger.Action.ACID);
        return InteractionResult.CONSUME;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CarcassBlockEntity carcass) {
        if (level instanceof ServerLevel server && carcass.acidTicks() % 20 == 0) fizz(server, pos, carcass.acidTicks() / 20);
        if (carcass.tickDissolve()) dissolve(level, pos, state, carcass);
    }

    /** Butchery's bubbles, alternating between two spots on the carcass. */
    private static void fizz(ServerLevel level, BlockPos pos, int step) {
        double dx = step % 2 == 0 ? 0.5 : 0.25;
        double dy = step % 3 == 0 ? 0.55 : 0.0;
        level.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + dx, pos.getY() + dy, pos.getZ() + dx, 20, 0.1, 0.1, 0.1, 0.1);
    }

    private static void dissolve(Level level, BlockPos pos, BlockState state, CarcassBlockEntity carcass) {
        if (!(state.getBlock() instanceof AbstractCarcassBlock block)) return;
        MobDef def = block.def();
        RegistryObject<SkeletonBlock> skeleton = ModBlocks.of(def).skeleton();
        if (skeleton == null) return;
        CompoundTag mobData = carcass.mobData().copy();
        BlockState next = skeleton.get().defaultBlockState()
                .setValue(AbstractCarcassBlock.FACING, state.getValue(AbstractCarcassBlock.FACING))
                .setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, block.hanging(state) ? 1 : 0);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof CarcassBlockEntity bones) bones.setMobData(mobData);
        level.playSound(null, pos, SoundEvents.BONE_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    private Acid() {}
}
