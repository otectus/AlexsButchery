package com.otectus.alexsbutchery.compat.profile;

import net.minecraft.util.RandomSource;

/**
 * An explicit per-stage yield of a {@code per_stage} profile group: a uniform count from {@code min} to {@code max},
 * produced with probability {@code chance}. A count of zero yields nothing at that stage while still replacing the
 * native drops there.
 */
public record CountRange(int min, int max, float chance) {
    /** Upper bound on a single stage's count; a carcass stage is never worth more than this many of one item. */
    public static final int MAX_COUNT = 64;

    public CountRange {
        if (min < 0 || max < min || max > MAX_COUNT) throw new IllegalArgumentException("count range " + min + ".." + max);
        if (!(chance > 0F && chance <= 1F)) throw new IllegalArgumentException("chance " + chance);
    }

    public static CountRange exactly(int count) {
        return new CountRange(count, count, 1F);
    }

    public static CountRange between(int min, int max) {
        return new CountRange(min, max, 1F);
    }

    /** One roll; consumes the same random draws whatever the outcome, so counts never depend on earlier skips. */
    public int roll(RandomSource random) {
        boolean hit = chance >= 1F || random.nextFloat() < chance;
        int count = min == max ? min : min + random.nextInt(max - min + 1);
        return hit ? count : 0;
    }
}
