package com.otectus.alexsbutchery.block;

import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

/** Finds overhanging anatomy in loaded chunks; never creates proxy blocks or loads distant chunks. */
public final class CarcassTargeting {
    public static boolean isCarcass(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos)
                && level.getBlockState(pos).getBlock() instanceof AbstractCarcassBlock;
    }

    @Nullable
    public static BlockHitResult pick(Level level, Vec3 eye, Vec3 end, Entity viewer) {
        BlockHitResult obstacle = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer));
        double nearest = obstacle.getType() == HitResult.Type.MISS ? eye.distanceToSqr(end) : eye.distanceToSqr(obstacle.getLocation());
        BlockHitResult best = obstacle.getType() == HitResult.Type.BLOCK && isCarcass(level, obstacle.getBlockPos()) ? obstacle : null;
        int radius = CarcassBounds.SEARCH_RADIUS;
        int minX = ((int) Math.floor(Math.min(eye.x, end.x)) - radius) >> 4;
        int maxX = ((int) Math.floor(Math.max(eye.x, end.x)) + radius) >> 4;
        int minZ = ((int) Math.floor(Math.min(eye.z, end.z)) - radius) >> 4;
        int maxZ = ((int) Math.floor(Math.max(eye.z, end.z)) + radius) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            var chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            if (!(chunk instanceof LevelChunk loaded)) continue;
            for (var be : loaded.getBlockEntities().values()) {
                if (!(be instanceof CarcassBlockEntity) || !(be.getBlockState().getBlock() instanceof AbstractCarcassBlock)) continue;
                if (Math.abs(be.getBlockPos().getY() - eye.y) > radius + eye.distanceTo(end)) continue;
                for (var box : CarcassBounds.boxes(be.getBlockState(), level, be.getBlockPos())) {
                    BlockHitResult hit = Shapes.create(box).clip(eye, end, be.getBlockPos());
                    if (hit != null && eye.distanceToSqr(hit.getLocation()) <= nearest + 1E-7) {
                        nearest = eye.distanceToSqr(hit.getLocation());
                        best = hit;
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    private static BlockHitResult serverPick(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        // Use the same block-reach attribute and creative allowance as the client, without extra range.
        return pick(player.level(), eye, eye.add(player.getLookAngle().scale(player.getBlockReach())), player);
    }

    public static boolean canReach(ServerPlayer player, BlockPos pos) {
        if (!isCarcass(player.level(), pos)) return false;
        var hit = serverPick(player);
        return hit != null && hit.getBlockPos().equals(pos);
    }

    public static boolean validHit(ServerPlayer player, BlockPos pos, Vec3 point) {
        if (!isCarcass(player.level(), pos)) return false;
        var hit = serverPick(player);
        // Packet hit coordinates are floats; allow rounding, not a second target or extra reach.
        return hit != null && hit.getBlockPos().equals(pos) && hit.getLocation().distanceToSqr(point) < .01;
    }

    private CarcassTargeting() {}
}
