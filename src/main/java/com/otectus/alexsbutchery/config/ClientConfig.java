package com.otectus.alexsbutchery.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Presentation only; never read on a server. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue CARCASS_RENDER_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue FLOOR_HINTS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        CARCASS_RENDER_DISTANCE = b.comment("Distance in blocks beyond which carcasses, heads and trophies are not drawn.")
                .defineInRange("carcassRenderDistance", 96, 16, 256);
        FLOOR_HINTS = b.comment("Show the tool hint when looking at a floor carcass (Butchery shows its own hints for hung carcasses).")
                .define("floorHints", true);
        SPEC = b.build();
    }

    private ClientConfig() {}
}
