package com.otectus.alexsbutchery.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Presentation only; never read on a server. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue CARCASS_RENDER_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue FLOOR_HINTS;
    public static final ForgeConfigSpec.DoubleValue GROUND_ITEM_MAX_SPAN;
    public static final ForgeConfigSpec.BooleanValue CARRY_ON_PRESENTATION;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        CARCASS_RENDER_DISTANCE = b.comment("Distance in blocks beyond which carcasses, heads and trophies are not drawn.")
                .defineInRange("carcassRenderDistance", 96, 16, 256);
        FLOOR_HINTS = b.comment("Show the tool hint when looking at a floor carcass (Butchery shows its own hints for hung carcasses).")
                .define("floorHints", true);
        GROUND_ITEM_MAX_SPAN = b.comment("Optional visual cap in blocks for dropped carcass items. 0 keeps placed anatomy size; no gameplay effect.")
                .defineInRange("groundItemMaxSpan", 0D, 0D, 128D);
        CARRY_ON_PRESENTATION = b.comment("Use the built-in Carry On carcass grip and appearance adapter when no replacement render override is active.")
                .define("carryOnPresentation", true);
        SPEC = b.build();
    }

    private ClientConfig() {}
}
