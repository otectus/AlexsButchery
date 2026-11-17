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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

/**
 * Draining a fresh carcass, the way Butchery does it: a cleaver starts it, blood drips and fills the first blood
 * grate below every 45 ticks (50 mB a time), and after the mob's fill count the block becomes the drained carcass.
 * Butchery's "Instant Bleeding" option skips the wait. Blood puddles follow Butchery's own rule on every click.
 *
 * <p>The blood class ({@link BloodClass}) and the fills already emitted, both saved with the carcass, decide how much
 * blood it still owes. Instant bleeding pays exactly that remainder through the same 50 mB grate fill timed bleeding uses,
 * one fill at a time, so both give 250, 1000 or 2000 mB whatever Butchery's own instant procedure does (Butchery 5.2
 * fills 1000 mB at once, 5.3 only 50). How much a full or missing grate accepts never changes when the carcass is done:
 * blood that finds no room is lost, as with timed bleeding.
 */
public final class Bleeding {

    /**
     * Whether this fresh carcass can bleed at its current position. Ground permission is independent of its serialized
     * pose; otherwise the hanging pose is only meaningful while a real Butchery hook or rope still supports it.
     */
    public static boolean canBleedAt(BlockGetter level, BlockPos pos, BlockState state, MobDef def) {
        if (!(state.getBlock() instanceof CarcassBlock fresh)) return false;
        return CarcassPolicy.current().canBleedOnGround(def)
                || fresh.hanging(state) && HangHandler.isSupport(level.getBlockState(pos.above()));
    }

    public static InteractionResult onCleaver(Level level, BlockPos pos, BlockState state, CarcassBlockEntity carcass, Player player, InteractionHand hand) {
        if (carcass.isDissolving()) return InteractionResult.CONSUME;
        if (!canBleedAt(level, pos, state, carcass.def())) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        MobDef def = carcass.def();
        if (!carcass.isBleeding()) {
            carcass.startBleeding();
            player.swing(hand, true);
            if (player instanceof ServerPlayer sp) ButcheringTrigger.INSTANCE.trigger(sp, def, ButcheringTrigger.Action.BLEED);
            level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        // Also a carcass that started bleeding before Instant Bleeding was turned on, or was carried mid-way: it finishes
        // with what it still owes, never a whole new carcass's worth.
        if (ButcheryHooks.instantBleed()) {
            finish(level, pos, state, carcass);
            return InteractionResult.CONSUME;
        }
        ButcheryHooks.puddle(level, pos);
        return InteractionResult.CONSUME;
    }

    /**
     * One tick of timed bleeding. A hookable carcass bleeds only while it hangs: carried and put down on the ground, it
     * keeps its clock and fills until it is hung again. Floor carcasses bleed where they lie.
     */
    public static void tick(Level level, BlockPos pos, BlockState state, CarcassBlockEntity carcass) {
        MobDef def = carcass.def();
        BloodClass blood = def.blood();
        if (blood == BloodClass.NONE || !canBleedAt(level, pos, state, def)) return;
        if (!carcass.tickBleed(blood.intervalTicks)) return;
        if (carcass.remainingFills() > 0) {
            ButcheryHooks.drip(level, pos, def.small());
            ButcheryHooks.fillGrate(level, pos, def.small());
            carcass.countFill();
        }
        if (carcass.remainingFills() == 0) convert(level, pos, state, carcass);
    }

    /** Instant bleeding: the fills this carcass still owes, at once, then the drained carcass. */
    static void finish(Level level, BlockPos pos, BlockState state, CarcassBlockEntity carcass) {
        MobDef def = carcass.def();
        int owed = carcass.remainingFills();
        if (owed > 0) ButcheryHooks.drip(level, pos, def.small());
        for (int i = 0; i < owed; i++) {
            ButcheryHooks.fillGrate(level, pos, def.small());
            carcass.countFill();
        }
        convert(level, pos, state, carcass);
    }

    /** Replaces the fresh carcass with the drained one in the matching position (hanging stays hanging). */
    public static void convert(Level level, BlockPos pos, BlockState state, CarcassBlockEntity carcass) {
        MobDef def = carcass.def();
        RegistryObject<DrainedCarcassBlock> drained = ModBlocks.of(def).drained();
        if (drained == null || !(state.getBlock() instanceof CarcassBlock fresh) || level.getBlockEntity(pos) != carcass
                || level.getBlockState(pos) != state) return;
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
