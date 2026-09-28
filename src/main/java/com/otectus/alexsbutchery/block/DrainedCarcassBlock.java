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
 * A drained carcass, Butchery's numbering: hanging 1 (whole), 2 (head off), 3 (skinned), 4 (cut), 5 (cut), gone;
 * on the ground 0, 6, 7, 8, 9, gone. Skinless mobs skip 3/7, headless mobs skip 2/6.
 */
public class DrainedCarcassBlock extends AbstractCarcassBlock {

    public DrainedCarcassBlock(MobDef def) {
        super(def);
    }

    @Override
    public IntegerProperty stateProperty() {
        return BLOCKSTATE_STAGED;
    }

    @Override
    public boolean hanging(BlockState state) {
        int s = state.getValue(BLOCKSTATE_STAGED);
        return s >= 1 && s <= 5;
    }

    @Override
    protected InteractionResult useTool(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return CutMachine.click(level, pos, state, player, hand, false);
    }
}
