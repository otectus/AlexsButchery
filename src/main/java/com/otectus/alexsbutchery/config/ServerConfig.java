package com.otectus.alexsbutchery.config;

import com.otectus.alexsbutchery.def.MobDef;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Gameplay configuration, per world and synced to clients. Butchery's own options (cleaver-only kills, instant
 * bleeding, organs, looting, weight, puddles) are read from Butchery's config so both mods always agree.
 */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_MOBS;
    public static final ForgeConfigSpec.BooleanValue BUTCHER_TAMED_MOBS;
    public static final ForgeConfigSpec.BooleanValue REPLACE_MOB_DROPS;
    public static final ForgeConfigSpec.BooleanValue DEFER_TO_OFFICIAL_ADDON;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> OFFICIAL_ADDON_MOBS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("mobs");
        DISABLED_MOBS = b.comment("Alex's Mobs creatures that never drop a carcass, by mob name (for example \"kangaroo\").")
                .defineListAllowEmpty("disabledMobs", List.of(), o -> o instanceof String);
        BUTCHER_TAMED_MOBS = b.comment("Tamed creatures drop carcasses too, like Butchery's wolves and cats.")
                .define("butcherTamedMobs", true);
        REPLACE_MOB_DROPS = b.comment("A carcass kill removes the meat, hide and other parts the carcass yields later",
                        "(#alexsbutchery:replaced_by_carcass) from the death drops. Rare drops are always kept.")
                .define("replaceMobDrops", true);
        b.pop();
        b.push("compat");
        DEFER_TO_OFFICIAL_ADDON = b.comment("When the official \"Butchery: Alex's Mobs Addon\" (butchery_alexs_mobs) is installed, leave the",
                        "creatures it covers to it.")
                .define("deferToOfficialAddon", true);
        OFFICIAL_ADDON_MOBS = b.comment("Creatures the official addon covers (its 1.0.1 beta list).")
                .defineListAllowEmpty("officialAddonMobs", List.of("bald_eagle", "blobfish", "caiman", "capuchin_monkey", "crow", "emu"),
                        o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    public static boolean isDisabled(MobDef def) {
        return DISABLED_MOBS.get().contains(def.id());
    }

    private ServerConfig() {}
}
