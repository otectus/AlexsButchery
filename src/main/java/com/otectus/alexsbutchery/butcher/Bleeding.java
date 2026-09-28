package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.advancement.ButcheringTrigger;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBlock;
import com.otectus.alexsbutchery.block.DrainedCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.BloodClass;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

/**
 * Draining a fresh carcass, the way Butchery does it: a cleaver starts it, blood drips and fills the first blood
 * grate below every 45 ticks (50 mB a time), and after the mob's fill count the block becomes the drained carcass.
 * Butchery's "Instant Bleeding" option skips the wait. Blood puddles follow Butchery's own rule on every click.
 */
public final class Bleeding {

    public static InteractionResult onCleaver(Level level, BlockPos pos, BlockState state, CarcassBlockEntity carcass, Player player, InteractionHand hand) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        MobDef def = carcass.def();
        if (!carcass.isBleeding()) {
            carcass.startBleeding();
            player.swing(hand, true);
            if (player instanceof ServerPlayer sp) ButcheringTrigger.INSTANCE.trigger(sp, def, ButcheringTrigger.Action.BLEED);
            level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (ButcheryHooks.instantBleed()) {
                ButcheryHooks.instantFill(level, pos, def.small());
                ButcheryHooks.drip(level, pos, def.small());
                convert(level, pos, state, carcass);
                return InteractionResult.CONSUME;
            }
        }
        ButcheryHooks.puddle(level, pos);
        return InteractionResult.CONSUME;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CarcassBlockEntity carcass) {
        MobDef def = carcass.def();
        BloodClass blood = def.blood();
        if (blood == BloodClass.NONE) return;
        if (!carcass.tickBleed(blood.intervalTicks)) return;
        ButcheryHooks.drip(level, pos, def.small());
        ButcheryHooks.fillGrate(level, pos, def.small());
        carcass.countFill();
        if (carcass.fills() >= blood.fills) convert(level, pos, state, carcass);
    }

    /** Replaces the fresh carcass with the drained one in the matching position (hanging stays hanging). */
    public static void convert(Level level, BlockPos pos, BlockState state, CarcassBlockEntity carcass) {
        MobDef def = carcass.def();
        RegistryObject<DrainedCarcassBlock> drained = ModBlocks.of(def).drained();
        if (drained == null || !(state.getBlock() instanceof CarcassBlock fresh)) return;
        boolean hanging = fresh.hanging(state);
        CompoundTag mobData = carcass.mobData().copy();
        BlockState next = drained.get().defaultBlockState()
                .setValue(AbstractCarcassBlock.FACING, state.getValue(AbstractCarcassBlock.FACING))
                .setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, hanging ? 1 : 0);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof CarcassBlockEntity drainedEntity) {
            drainedEntity.setMobData(mobData);
            drainedEntity.getPersistentData().putBoolean("isDrained", true);
        }
        level.playSound(null, pos, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 0.9F);
    }

    private Bleeding() {}
}
