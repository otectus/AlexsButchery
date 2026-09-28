package com.otectus.alexsbutchery.def;

/** What the skinning knife does at the skinning stage: take a skin, pluck feathers, or nothing (stage skipped). */
public enum SkinStep {
    NONE,
    SKIN,
    PLUCK;

    public String table() {
        return this == PLUCK ? "pluck" : "skin";
    }
}
