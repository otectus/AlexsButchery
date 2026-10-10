package com.otectus.alexsbutchery.block;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.LongPredicate;

/**
 * Where carcass anchors are, per level and chunk, so picking visits carcasses rather than every block entity in the
 * chunks around a ray. Fed by the carcass block entity's own lifecycle. It holds positions only, never block entities,
 * and levels only weakly; callers re-check every position against the live level, and a position that no longer holds
 * a carcass is dropped then, so a stale entry costs one lookup and nothing else.
 */
public final class CarcassIndex {
    private static final Map<Level, Long2ObjectOpenHashMap<LongOpenHashSet>> LEVELS = new WeakHashMap<>();

    public static void add(Level level, BlockPos pos) {
        var chunks = chunks(level, true);
        synchronized (chunks) {
            chunks.computeIfAbsent(ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())),
                    key -> new LongOpenHashSet()).add(pos.asLong());
        }
    }

    public static void remove(Level level, BlockPos pos) {
        var chunks = chunks(level, false);
        if (chunks == null) return;
        synchronized (chunks) {
            long key = ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
            var set = chunks.get(key);
            if (set != null && set.remove(pos.asLong()) && set.isEmpty()) chunks.remove(key);
        }
    }

    /**
     * Offers every indexed anchor in the chunk rectangle; {@code visit} answers whether the position still holds a
     * carcass, and positions it rejects are forgotten.
     */
    public static void visit(Level level, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ, LongPredicate visit) {
        var chunks = chunks(level, false);
        if (chunks == null) return;
        LongArrayList positions = new LongArrayList();
        synchronized (chunks) {
            if (chunks.isEmpty()) return;
            for (int x = minChunkX; x <= maxChunkX; x++) for (int z = minChunkZ; z <= maxChunkZ; z++) {
                var set = chunks.get(ChunkPos.asLong(x, z));
                if (set != null) positions.addAll(set);
            }
        }
        for (int i = 0; i < positions.size(); i++) {
            long pos = positions.getLong(i);
            if (!visit.test(pos)) remove(level, BlockPos.of(pos));
        }
    }

    private static Long2ObjectOpenHashMap<LongOpenHashSet> chunks(Level level, boolean create) {
        synchronized (LEVELS) {
            var chunks = LEVELS.get(level);
            if (chunks == null && create) LEVELS.put(level, chunks = new Long2ObjectOpenHashMap<>());
            return chunks;
        }
    }

    private CarcassIndex() {}
}
