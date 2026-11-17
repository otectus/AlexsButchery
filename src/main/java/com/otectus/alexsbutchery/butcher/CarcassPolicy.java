package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.config.ServerConfig;
import com.otectus.alexsbutchery.def.DeathSpawnMode;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Immutable, resolved gameplay policy for the current world's synchronized server config. Raw strings are parsed only
 * when that config loads; death handling and block ticks perform direct lookups by {@link MobDef}.
 */
public final class CarcassPolicy {
    public enum GlobalDeathSpawnMode {
        SPECIES_DEFAULT,
        PLACED_BLOCK
    }

    private static volatile CarcassPolicy current = resolve(GlobalDeathSpawnMode.SPECIES_DEFAULT, List.of(), List.of(), ignored -> {});
    private static volatile boolean authoritativeSnapshot;

    private final GlobalDeathSpawnMode globalDeathSpawnMode;
    private final Map<MobDef, DeathSpawnMode> deathOverrides;
    private final Set<MobDef> additionalGroundBleeding;

    private CarcassPolicy(GlobalDeathSpawnMode globalDeathSpawnMode, Map<MobDef, DeathSpawnMode> deathOverrides,
                          Set<MobDef> additionalGroundBleeding) {
        this.globalDeathSpawnMode = globalDeathSpawnMode;
        this.deathOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(deathOverrides));
        this.additionalGroundBleeding = Collections.unmodifiableSet(new LinkedHashSet<>(additionalGroundBleeding));
    }

    public static CarcassPolicy current() {
        return current;
    }

    /** Clears a departed world's synchronized rules before another local world or remote server is joined. */
    public static void reset() {
        current = resolve(GlobalDeathSpawnMode.SPECIES_DEFAULT, List.of(), List.of(), ignored -> {});
        authoritativeSnapshot = false;
    }

    /** Explicit override, then global all-block mode, then the species' legacy default. */
    public DeathSpawnMode deathSpawnMode(MobDef def) {
        DeathSpawnMode override = deathOverrides.get(def);
        if (override != null) return override;
        if (globalDeathSpawnMode == GlobalDeathSpawnMode.PLACED_BLOCK) return DeathSpawnMode.PLACED_BLOCK;
        return def.deathSpawnMode();
    }

    /** Ground bleeding is additive: existing giant defaults cannot be accidentally removed by an empty list. */
    public boolean canBleedOnGround(MobDef def) {
        return def.canBleedOnGround() || additionalGroundBleeding.contains(def);
    }

    public Map<MobDef, DeathSpawnMode> deathOverrides() {
        return deathOverrides;
    }

    public Set<MobDef> additionalGroundBleeding() {
        return additionalGroundBleeding;
    }

    /** Fully resolved wire form, so clients receive the policy frozen at world load rather than newly edited raw values. */
    public Snapshot snapshot() {
        Map<ResourceLocation, DeathSpawnMode> modes = new LinkedHashMap<>();
        Set<ResourceLocation> ground = new LinkedHashSet<>();
        for (MobDef def : MobDefs.all()) {
            modes.put(def.entity(), deathSpawnMode(def));
            if (canBleedOnGround(def)) ground.add(def.entity());
        }
        return new Snapshot(modes, ground);
    }

    public static CarcassPolicy fromSnapshot(Snapshot snapshot) {
        Map<MobDef, DeathSpawnMode> modes = new LinkedHashMap<>();
        snapshot.deathModes().forEach((id, mode) -> {
            MobDef def = MobDefs.byEntityId(id);
            if (def != null && mode != null) modes.put(def, mode);
        });
        Set<MobDef> ground = new LinkedHashSet<>();
        for (ResourceLocation id : snapshot.groundBleeding()) {
            MobDef def = MobDefs.byEntityId(id);
            if (def != null && def.bleeds()) ground.add(def);
        }
        return new CarcassPolicy(GlobalDeathSpawnMode.SPECIES_DEFAULT, modes, ground);
    }

    public static void acceptSnapshot(Snapshot snapshot) {
        current = fromSnapshot(snapshot);
        authoritativeSnapshot = true;
    }

    public static boolean hasAuthoritativeSnapshot() {
        return authoritativeSnapshot;
    }

    public record Snapshot(Map<ResourceLocation, DeathSpawnMode> deathModes, Set<ResourceLocation> groundBleeding) {
        public Snapshot {
            deathModes = Collections.unmodifiableMap(new LinkedHashMap<>(deathModes));
            groundBleeding = Collections.unmodifiableSet(new LinkedHashSet<>(groundBleeding));
        }
    }

    /** Rebuilds from the current Forge SERVER config. Forge loads and synchronizes that config per save/world. */
    public static void reloadFromConfig() {
        try {
            current = resolve(ServerConfig.DEATH_SPAWN_MODE.get(), ServerConfig.DEATH_SPAWN_OVERRIDES.get(),
                    ServerConfig.GROUND_BLEEDING_MOBS.get(), message -> AlexsButchery.LOGGER.warn("Carcass policy: {}", message));
            authoritativeSnapshot = false;
        } catch (IllegalStateException notLoadedYet) {
            // The static legacy policy remains valid until Forge finishes loading the world's SERVER config.
            AlexsButchery.LOGGER.debug("Carcass policy config is not loaded yet", notLoadedYet);
        }
    }

    /** Pure resolver used by config lifecycle and regression tests. Invalid entries are diagnosed and ignored. */
    public static CarcassPolicy resolve(GlobalDeathSpawnMode global, Iterable<? extends String> rawOverrides,
                                         Iterable<? extends String> rawGroundBleeding, Consumer<String> diagnostic) {
        Map<MobDef, DeathSpawnMode> overrides = new LinkedHashMap<>();
        Set<ResourceLocation> overrideIds = new LinkedHashSet<>();
        for (String raw : rawOverrides) {
            String entry = raw == null ? "" : raw.trim();
            int equals = entry.indexOf('=');
            if (equals <= 0 || equals != entry.lastIndexOf('=') || equals == entry.length() - 1) {
                diagnostic.accept("malformed deathSpawnOverrides entry '" + entry + "' (expected namespace:entity=ITEM|PLACED_BLOCK)");
                continue;
            }
            ResourceLocation entity = entityId(entry.substring(0, equals).trim(), "deathSpawnOverrides", diagnostic);
            if (entity == null) continue;
            MobDef def = MobDefs.byEntityId(entity);
            if (def == null) {
                diagnostic.accept("deathSpawnOverrides names unsupported entity " + entity);
                continue;
            }
            DeathSpawnMode mode;
            try {
                mode = DeathSpawnMode.valueOf(entry.substring(equals + 1).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                diagnostic.accept("deathSpawnOverrides has invalid mode in '" + entry + "' (expected ITEM or PLACED_BLOCK)");
                continue;
            }
            if (!overrideIds.add(entity)) {
                diagnostic.accept("duplicate/conflicting deathSpawnOverrides entry for " + entity + " (the first entry wins)");
                continue;
            }
            overrides.put(def, mode);
        }

        Set<MobDef> ground = new LinkedHashSet<>();
        Set<ResourceLocation> groundIds = new LinkedHashSet<>();
        for (String raw : rawGroundBleeding) {
            ResourceLocation entity = entityId(raw == null ? "" : raw.trim(), "groundBleedingMobs", diagnostic);
            if (entity == null) continue;
            MobDef def = MobDefs.byEntityId(entity);
            if (def == null) {
                diagnostic.accept("groundBleedingMobs names unsupported entity " + entity);
                continue;
            }
            if (!groundIds.add(entity)) {
                diagnostic.accept("duplicate groundBleedingMobs entry for " + entity);
                continue;
            }
            if (!def.bleeds()) {
                diagnostic.accept("groundBleedingMobs entry " + entity + " is bloodless and has no effect");
                continue;
            }
            ground.add(def);
        }
        return new CarcassPolicy(global == null ? GlobalDeathSpawnMode.SPECIES_DEFAULT : global, overrides, ground);
    }

    private static ResourceLocation entityId(String text, String option, Consumer<String> diagnostic) {
        if (text.isEmpty() || text.indexOf(':') <= 0) {
            diagnostic.accept(option + " has malformed entity ID '" + text + "' (use the full namespace:entity registry ID)");
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null) diagnostic.accept(option + " has malformed entity ID '" + text + "'");
        return id;
    }
}
