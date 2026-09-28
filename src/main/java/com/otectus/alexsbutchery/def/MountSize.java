package com.otectus.alexsbutchery.def;

/** Which of Butchery's empty head mounts a head is mounted on at the taxidermy table. */
public enum MountSize {
    SMALL("small_empty_head_mount"),
    REGULAR("empty_head_mount"),
    LARGE("large_empty_head_mount");

    /** Butchery block/item id (namespace {@code butchery}). */
    public final String butcheryMount;

    MountSize(String butcheryMount) {
        this.butcheryMount = butcheryMount;
    }
}
