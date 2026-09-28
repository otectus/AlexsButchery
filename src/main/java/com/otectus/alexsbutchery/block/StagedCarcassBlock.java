package com.otectus.alexsbutchery.block;

import com.otectus.alexsbutchery.butcher.CutMachine;
import com.otectus.alexsbutchery.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The carcass of a mob with nothing to drain (insects, fish, bone and slime creatures): like Butchery's spider, the
 * cut stages run on this block. Ground: 0, head off 4, cut 5, cut 6, gone. Hanging: 1, head off 7, cut 8, cut 9, gone.
 */
public class StagedCarcassBlock extends CarcassBlock {

    public StagedCarcassBlock(MobDef def) {
        super(def);
    }

    @Override
    public IntegerProperty stateProperty() {
        return BLOCKSTATE_STAGED;
    }

    @Override
    public boolean hanging(BlockState state) {
        int s = state.getValue(BLOCKSTATE_STAGED);
        return s == 1 || s >= 7;
    }

    @Override
    protected InteractionResult useTool(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return CutMachine.click(level, pos, state, player, hand, true);
    }
}
