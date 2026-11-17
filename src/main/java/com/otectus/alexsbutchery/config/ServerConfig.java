package com.otectus.alexsbutchery.config;

import com.otectus.alexsbutchery.def.CompatDefs;
import com.otectus.alexsbutchery.butcher.CarcassPolicy;
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
    public static final ForgeConfigSpec.EnumValue<CarcassPolicy.GlobalDeathSpawnMode> DEATH_SPAWN_MODE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DEATH_SPAWN_OVERRIDES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GROUND_BLEEDING_MOBS;
    public static final ForgeConfigSpec.BooleanValue DEFER_TO_OFFICIAL_ADDON;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> OFFICIAL_ADDON_MOBS;
    public static final ForgeConfigSpec.BooleanValue PREFER_EXTERNAL_PRODUCTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROVIDER_PRIORITY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_PROVIDERS;
    public static final ForgeConfigSpec.BooleanValue RESOLVE_KNOWN_RECIPE_CONFLICTS;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("mobs");
        DISABLED_MOBS = b.comment("Alex's Mobs creatures that never drop a carcass, by mob name (for example \"kangaroo\").")
                .defineListAllowEmpty("disabledMobs", List.of(), o -> o instanceof String);
        BUTCHER_TAMED_MOBS = b.comment("Tamed creatures drop carcasses too, like Butchery's wolves and cats.")
                .define("butcherTamedMobs", true);
        REPLACE_MOB_DROPS = b.comment("A carcass kill removes the meat, hide and other parts the carcass yields later from the death drops,",
                        "including an installed cooking addon's equivalent meat (raw or cooked). Rare and special drops are kept.",
                        "false: the creature keeps all its ordinary drops as well as dropping the carcass.")
                .define("replaceMobDrops", true);
        b.pop();
        b.push("carcasses");
        DEATH_SPAWN_MODE = b.comment("What qualifying carcass kills create. SPECIES_DEFAULT preserves the legacy table;",
                        "PLACED_BLOCK makes every supported species prefer a block at its loaded resting place.",
                        "Representation does not change hookability, blood amount, weight or trophy size. Takes effect on world load.")
                .worldRestart().defineEnum("deathSpawnMode", CarcassPolicy.GlobalDeathSpawnMode.SPECIES_DEFAULT);
        DEATH_SPAWN_OVERRIDES = b.comment("Per-species exceptions, using actual Alex's Mobs entity registry IDs and ITEM or PLACED_BLOCK.",
                        "Explicit entries override deathSpawnMode. The Cave Centipede ID is alexsmobs:centipede_head.",
                        "Large-game placement preset: [\"alexsmobs:moose=PLACED_BLOCK\", \"alexsmobs:bison=PLACED_BLOCK\",",
                        "\"alexsmobs:rhinoceros=PLACED_BLOCK\"]. Empty preserves legacy behavior. Takes effect on world load.")
                .worldRestart().defineListAllowEmpty("deathSpawnOverrides", List.of(), o -> o instanceof String);
        GROUND_BLEEDING_MOBS = b.comment("Additional species allowed to begin or resume bleeding while lying on the ground, by actual entity ID.",
                        "This is independent of death placement. For full large-game convenience, list alexsmobs:moose,",
                        "alexsmobs:bison and alexsmobs:rhinoceros here as well. Empty preserves hang-first progression.",
                        "Takes effect on world load.")
                .worldRestart().defineListAllowEmpty("groundBleedingMobs", List.of(), o -> o instanceof String);
        b.pop();
        b.push("compat");
        DEFER_TO_OFFICIAL_ADDON = b.comment("When the official \"Butchery: Alex's Mobs Addon\" (butchery_alexs_mobs) is installed, leave the",
                        "creatures it covers to it.")
                .define("deferToOfficialAddon", true);
        OFFICIAL_ADDON_MOBS = b.comment("Creatures the official addon covers (its 1.0.1 beta list).")
                .defineListAllowEmpty("officialAddonMobs", List.of("bald_eagle", "blobfish", "caiman", "capuchin_monkey", "crow", "emu"),
                        o -> o instanceof String);
        PREFER_EXTERNAL_PRODUCTS = b.comment("Carcasses yield the verified items of installed cooking addons (Alex's Mobs Delight, Alex's Delight)",
                        "instead of this mod's own, per creature. false: every carcass yields the native items. Takes effect on restart.")
                .worldRestart().define("preferExternalProducts", true);
        PROVIDER_PRIORITY = b.comment("Which addon supplies a product both can supply, first match wins; the native item is always the last",
                        "fallback. A datapack selection file can reorder one creature. Takes effect on restart.")
                .worldRestart().defineListAllowEmpty("providerPriority", CompatDefs.DEFAULT_PRIORITY, o -> o instanceof String);
        DISABLED_PROVIDERS = b.comment("Providers whose mappings are ignored entirely: no yields, no death-drop removal, no conversion",
                        "recipes (for example [\"alexsdelight\"]). Takes effect on restart.")
                .worldRestart().defineListAllowEmpty("disabledProviders", List.of(), o -> o instanceof String);
        RESOLVE_KNOWN_RECIPE_CONFLICTS = b.comment("With both addons installed, the preferred one owns the shared cutting-board and cooking recipes",
                        "they both define (kangaroo meat, moose ribs, catfish, centipede legs); the other's cuts stay reachable",
                        "through alternate recipes. false: leave both recipes and report the conflicts. Takes effect on restart.")
                .worldRestart().define("resolveKnownRecipeConflicts", true);
        DEBUG_LOGGING = b.comment("Log each resolved yield rule and every death-drop removal (rate limited).")
                .define("debugLogging", false);
        b.pop();
        SPEC = b.build();
    }

    public static boolean isDisabled(MobDef def) {
        return DISABLED_MOBS.get().contains(def.id());
    }

    private ServerConfig() {}
}
