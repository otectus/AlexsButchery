package com.otectus.alexsbutchery.block;

import com.otectus.alexsbutchery.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A head on one of Butchery's plaques, made at the taxidermy table; the same wall-slab footprint as Butchery's mounts. */
public class HeadMountBlock extends HeadBlock {
    private static final VoxelShape NORTH = Block.box(1, 0, 12, 15, 16, 16);
    private static final VoxelShape SOUTH = Block.box(1, 0, 0, 15, 16, 4);
    private static final VoxelShape EAST = Block.box(0, 0, 1, 4, 16, 15);
    private static final VoxelShape WEST = Block.box(12, 0, 1, 16, 16, 15);

    public HeadMountBlock(MobDef def) {
        super(def);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> SOUTH;
        };
    }
}
