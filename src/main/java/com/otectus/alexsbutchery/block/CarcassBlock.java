package com.otectus.alexsbutchery.block;

import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.butcher.Bleeding;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A fresh carcass of a bleeding mob: on the ground ({@code blockstate 0}) it waits to be hung unless the resolved
 * server policy permits ground bleeding; hanging ({@code 1}) a cleaver starts the bleeding that drains it.
 */
public class CarcassBlock extends AbstractCarcassBlock {

    public CarcassBlock(MobDef def) {
        super(def);
    }

    @Override
    public IntegerProperty stateProperty() {
        return BLOCKSTATE_FRESH;
    }

    @Override
    public boolean hanging(BlockState state) {
        return state.getValue(BLOCKSTATE_FRESH) == 1;
    }

    @Override
    protected InteractionResult useTool(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!ButcheryHooks.isCleaver(held)) return InteractionResult.PASS;
        // Death representation is irrelevant: live hook support or the independent ground policy permits this cut.
        if (!Bleeding.canBleedAt(level, pos, state, def)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof CarcassBlockEntity carcass)) return InteractionResult.PASS;
        return Bleeding.onCleaver(level, pos, state, carcass, player, hand);
    }
}
