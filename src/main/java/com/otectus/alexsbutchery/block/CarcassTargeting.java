package com.otectus.alexsbutchery.block;

import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Finds overhanging anatomy in loaded chunks; never creates proxy blocks or loads distant chunks. Candidates come from
 * {@link CarcassIndex}, are rejected by their envelope before their anatomy is tested, and only the nearest hit
 * becomes a hit result. The server runs the same code to validate actions, so client and server agree.
 */
public final class CarcassTargeting {
    public static boolean isCarcass(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos)
                && level.getBlockState(pos).getBlock() instanceof AbstractCarcassBlock;
    }

    @Nullable
    public static BlockHitResult pick(Level level, Vec3 eye, Vec3 end, Entity viewer) {
        // Vanilla's own clip finds the nearest real obstacle; a carcass shape on that ray answers it with its anatomy.
        BlockHitResult obstacle = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer));
        double length = eye.distanceTo(end);
        double[] nearest = {obstacle.getType() == HitResult.Type.MISS ? 1 : Math.sqrt(eye.distanceToSqr(obstacle.getLocation())) / length};
        BlockHitResult[] best = {obstacle.getType() == HitResult.Type.BLOCK && isCarcass(level, obstacle.getBlockPos()) ? obstacle : null};
        CarcassShape.Hit hit = new CarcassShape.Hit();
        BlockPos.MutableBlockPos winner = new BlockPos.MutableBlockPos();
        CarcassShape.Hit won = new CarcassShape.Hit();
        boolean[] found = {false};
        int radius = CarcassBounds.SEARCH_RADIUS;
        double verticalRange = radius + length;
        int minX = SectionPos.blockToSectionCoord(Math.floor(Math.min(eye.x, end.x)) - radius);
        int maxX = SectionPos.blockToSectionCoord(Math.floor(Math.max(eye.x, end.x)) + radius);
        int minZ = SectionPos.blockToSectionCoord(Math.floor(Math.min(eye.z, end.z)) - radius);
        int maxZ = SectionPos.blockToSectionCoord(Math.floor(Math.max(eye.z, end.z)) + radius);
        CarcassIndex.visit(level, minX, maxX, minZ, maxZ, packed -> {
            BlockPos pos = BlockPos.of(packed);
            var chunk = level.getChunkSource().getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()),
                    ChunkStatus.FULL, false);
            // An unloaded chunk keeps its entries for when it returns; a loaded one must still hold the carcass.
            if (!(chunk instanceof LevelChunk loaded)) return true;
            if (!(loaded.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) instanceof CarcassBlockEntity be)
                    || !(be.getBlockState().getBlock() instanceof AbstractCarcassBlock)) return false;
            if (Math.abs(pos.getY() - eye.y) > verticalRange) return true;
            var geometry = CarcassBounds.geometry(be.getBlockState(), level, pos);
            double entry = CarcassShape.entry(geometry.envelope(), eye, end, pos);
            if (entry < 0 || entry > nearest[0] + 1E-7) return true;
            if (CarcassShape.nearest(geometry.boxes(), eye, end, pos, hit) && hit.t <= nearest[0] + 1E-7 / length) {
                nearest[0] = hit.t;
                won.t = hit.t;
                won.face = hit.face;
                won.inside = hit.inside;
                winner.set(pos);
                found[0] = true;
            }
            return true;
        });
        return found[0] ? won.result(eye, end, winner.immutable()) : best[0];
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
