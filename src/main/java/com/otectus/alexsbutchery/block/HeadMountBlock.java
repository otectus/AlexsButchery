package com.otectus.alexsbutchery.block;

import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MountSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A head on a wall plaque, made at Butchery's taxidermy table; its footprint follows the plaque's size and trim.
 *
 * <p>A trophy hangs on a wall: {@code FACING} is the way the head looks out, and the plaque needs a sturdy face behind it
 * at {@code pos.relative(FACING.getOpposite())}, like a wall sign. Placing it prefers the clicked side of a wall, then the
 * horizontal directions the player looks along, and refuses a spot with nothing to hang on. When its wall goes the
 * trophy drops as its item, appearance and all, through the ordinary block drop. A trophy saved before this rule keeps
 * its place until something next to its back changes. Selection is the drawn head plus the plaque ({@link CarcassBounds},
 * form {@code mount}); collision is the plaque alone.
 */
public class HeadMountBlock extends HeadBlock {
    /** North-facing wood surface; the raised rim is at 14/16 and must not hold the skull away from the wood. */
    public static final double FACE = 15D / 16;
    private static final VoxelShape[][] PLAQUES = new VoxelShape[MountSize.values().length][4];
    static {
        for (MountSize size : MountSize.values()) for (Direction facing : Direction.Plane.HORIZONTAL) {
            double min = size == MountSize.LARGE ? 0 : size == MountSize.SMALL ? 4.5 : 1;
            double max = 16 - min;
            double low = size == MountSize.LARGE ? -5 : size == MountSize.SMALL ? 4 : 0;
            double high = size == MountSize.LARGE ? 15 : size == MountSize.SMALL ? 12 : 16;
            PLAQUES[size.ordinal()][facing.get2DDataValue()] = switch (facing) {
                case NORTH -> Block.box(min, low, 13.8, max, high, 16);
                case SOUTH -> Block.box(min, low, 0, max, high, 2.2);
                case EAST -> Block.box(0, low, min, 2.2, high, max);
                default -> Block.box(13.8, low, min, 16, high, max);
            };
        }
    }

    public HeadMountBlock(MobDef def) {
        super(def);
    }

    /** Envelope of the actual plaque, including the large plaque's lower trim. */
    public static VoxelShape plaque(MountSize size, Direction facing) {
        return PLAQUES[size.ordinal()][facing.get2DDataValue()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return plaque(def().mount(), state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction clicked = context.getClickedFace();
        if (clicked.getAxis().isHorizontal()) {
            BlockState state = defaultBlockState().setValue(FACING, clicked);
            if (state.canSurvive(level, pos)) return state;
        }
        for (Direction looking : context.getNearestLookingDirections()) {
            if (!looking.getAxis().isHorizontal()) continue;
            BlockState state = defaultBlockState().setValue(FACING, looking.getOpposite());
            if (state.canSurvive(level, pos)) return state;
        }
        return null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }
}
