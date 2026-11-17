package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

/** Loaded-column search and transactional block creation shared by every block-at-death species. */
public final class CarcassPlacement {
    public enum Failure {
        NONE,
        NO_LOADED_DESTINATION,
        OUTSIDE_WORLD,
        PROTECTED,
        PLACE_REFUSED,
        INITIALIZATION_FAILED,
        DESTINATION_CHANGED
    }

    public record Result(@Nullable BlockPos position, Failure failure) {
        public boolean succeeded() {
            return failure == Failure.NONE;
        }

        static Result success(BlockPos position) {
            return new Result(position.immutable(), Failure.NONE);
        }

        static Result failed(@Nullable BlockPos position, Failure failure) {
            return new Result(position == null ? null : position.immutable(), failure);
        }
    }

    public static Result placeAtDeath(ServerLevel level, LivingEntity victim, Entity actor, MobDef def, CompoundTag mobData) {
        BlockPos pos = restingPlace(level, victim.blockPosition());
        if (pos == null) return Result.failed(null, Failure.NO_LOADED_DESTINATION);
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return Result.failed(pos, Failure.OUTSIDE_WORLD);
        }
        if (actor instanceof Player player && (!player.getAbilities().mayBuild || !level.mayInteract(player, pos))) {
            return Result.failed(pos, Failure.PROTECTED);
        }

        Direction facing = victim.getDirection().getOpposite();
        BlockState placedState = ModBlocks.of(def).carcass().get().defaultBlockState()
                .setValue(AbstractCarcassBlock.FACING, facing);
        BlockSnapshot original = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, placedState, Block.UPDATE_ALL)) return Result.failed(pos, Failure.PLACE_REFUSED);
        if (!(level.getBlockEntity(pos) instanceof CarcassBlockEntity carcass)) {
            restoreIfOurs(level, pos, placedState, null, original);
            return Result.failed(pos, Failure.INITIALIZATION_FAILED);
        }
        carcass.setMobData(mobData);

        // The real killer remains the actor seen by protection mods; no fabricated privileged player is introduced.
        if (ForgeEventFactory.onBlockPlace(actor, original, Direction.UP)) {
            restoreIfOurs(level, pos, placedState, carcass, original);
            return Result.failed(pos, Failure.PROTECTED);
        }
        if (level.getBlockState(pos) != placedState || level.getBlockEntity(pos) != carcass) {
            // An event handler deliberately changed the destination. Never roll that newer block back over its owner.
            return Result.failed(pos, Failure.DESTINATION_CHANGED);
        }
        return Result.success(pos);
    }

    /**
     * Lowest replaceable space in the same loaded column. The scan is bounded by build height and never obtains a chunk,
     * so a death cannot force-load terrain. Partial supports (slabs, paths, soul sand, carpet and deep snow) remain intact.
     */
    @Nullable
    public static BlockPos restingPlace(ServerLevel level, BlockPos start) {
        BlockPos pos = start;
        if (!replaceable(level, pos)) {
            pos = pos.above();
            if (!replaceable(level, pos)) return null;
        }
        while (pos.getY() > level.getMinBuildHeight()) {
            BlockPos below = pos.below();
            if (!replaceable(level, below)) return level.isOutsideBuildHeight(pos) ? null : pos.immutable();
            pos = below;
        }
        return null;
    }

    private static boolean replaceable(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return false;
        if (!level.hasChunkAt(pos)) return false;
        return level.getBlockState(pos).canBeReplaced(
                new DirectionalPlaceContext(level, pos, Direction.DOWN, ItemStack.EMPTY, Direction.UP));
    }

    /** Restore only while the destination is still the block this transaction installed. */
    private static void restoreIfOurs(ServerLevel level, BlockPos pos, BlockState placedState,
                                      @Nullable CarcassBlockEntity placedEntity, BlockSnapshot original) {
        if (level.getBlockState(pos) != placedState) return;
        if (placedEntity != null && level.getBlockEntity(pos) != placedEntity) return;
        boolean wasRestoring = level.restoringBlockSnapshots;
        level.restoringBlockSnapshots = true;
        try {
            original.restore(true, false);
        } finally {
            level.restoringBlockSnapshots = wasRestoring;
        }
    }

    private CarcassPlacement() {}
}
