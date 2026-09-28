package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.advancement.ButcheringTrigger;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.SkinStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * One right-click of a cleaver or skinning knife on a carcass: performs the next stage, rolls its loot (re-rolled
 * once per Looting level when Butchery's looting option is on, exactly like Butchery), adds Butchery's organ table
 * at the cut stages, wears the tool and advances or removes the block.
 */
public final class CutMachine {

    public static InteractionResult click(Level level, BlockPos pos, BlockState state, Player player, InteractionHand hand, boolean freshBlock) {
        if (!(state.getBlock() instanceof AbstractCarcassBlock block)) return InteractionResult.PASS;
        MobDef def = block.def();
        int current = block.stage(state);
        Stages.Step step = Stages.next(def, freshBlock, current);
        if (step == null) return InteractionResult.PASS;
        ItemStack tool = player.getItemInHand(hand);
        boolean matches = step.action().needsKnife() ? ButcheryHooks.isSkinningKnife(tool) : ButcheryHooks.isCleaver(tool);
        if (!matches) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel server = (ServerLevel) level;

        player.swing(hand, true);
        tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        level.levelEvent(2001, pos, Block.getId(state));
        level.playSound(null, pos, step.action().needsKnife() ? SoundEvents.SHEEP_SHEAR : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.BLOCKS, 1.0F, 1.0F);

        int looting = ButcheryHooks.lootingEnabled() ? EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MOB_LOOTING, tool) : 0;
        int rolls = 1 + looting;
        CarcassBlockEntity carcass = level.getBlockEntity(pos) instanceof CarcassBlockEntity c ? c : null;
        List<ItemStack> drops = new ArrayList<>(CarcassLoot.roll(server, def.stageTable(step.action().table(def)), pos, state, tool, player, carcass, rolls));
        int cut = step.action().cutIndex();
        if (cut > 0 && def.organs() && ButcheryHooks.organs()) {
            drops.addAll(CarcassLoot.roll(server, ButcheryHooks.organsTable(cut), pos, state, tool, player, carcass, rolls));
        }
        CarcassLoot.drop(server, pos, drops);

        if (player instanceof ServerPlayer sp) ButcheringTrigger.INSTANCE.trigger(sp, def, trigger(step.action(), def));
        if (step.doneState() == Stages.REMOVED) {
            level.removeBlock(pos, false);
        } else {
            level.setBlock(pos, state.setValue(block.stateProperty(), step.doneState()), Block.UPDATE_ALL);
        }
        return InteractionResult.CONSUME;
    }

    private static ButcheringTrigger.Action trigger(Stages.Action action, MobDef def) {
        return switch (action) {
            case HEAD -> ButcheringTrigger.Action.HEAD;
            case SKIN -> def.skin() == SkinStep.PLUCK ? ButcheringTrigger.Action.PLUCK : ButcheringTrigger.Action.SKIN;
            case CUT_1 -> ButcheringTrigger.Action.CUT_1;
            case CUT_2 -> ButcheringTrigger.Action.CUT_2;
            case CUT_3 -> ButcheringTrigger.Action.CUT_3;
        };
    }

    private CutMachine() {}
}
