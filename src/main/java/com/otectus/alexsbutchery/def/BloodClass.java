package com.otectus.alexsbutchery.def;

/**
 * How much blood a carcass drains and how fast, mirroring Butchery: its regular carcasses fill a blood grate with
 * 50 mB every 45 ticks and convert to the drained carcass after 900 ticks (1000 mB), its small ones after 225 ticks
 * (250 mB). LARGE is ours, for the floor carcasses that have no Butchery counterpart.
 */
public enum BloodClass {
    NONE(0, 0),
    SMALL(5, 45),
    REGULAR(20, 45),
    LARGE(40, 45);

    /** Number of 50 mB grate fills before the carcass counts as drained. */
    public final int fills;
    public final int intervalTicks;

    BloodClass(int fills, int intervalTicks) {
        this.fills = fills;
        this.intervalTicks = intervalTicks;
    }

    public int millibuckets() {
        return fills * 50;
    }

    public int totalTicks() {
        return fills * intervalTicks;
    }

    /** Butchery has separate "small" fill and drip procedures for its small carcasses. */
    public boolean usesSmallProcedures() {
        return this == SMALL;
    }
}
