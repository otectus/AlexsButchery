package com.otectus.alexsbutchery.compat.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one profile yields in place of one of the mob's native product groups.
 *
 * <ul>
 *   <li>{@code native_group}: the group's native drops are rolled exactly as without the profile (same counts, chances
 *   and functions, same loot context), and their total count is given as the profile's item instead. Applies to every
 *   stage where the group has native drops, or to the listed {@code stages}.</li>
 *   <li>{@code per_stage}: an explicit count per stage replaces the native drops of that stage ({@link CountRange});
 *   the stages are the keys of {@code counts}.</li>
 * </ul>
 *
 * The product is either one {@code item} or {@code variants}: an integer tag of the carcass's saved mob snapshot
 * selecting the item. A snapshot without that tag, or with an unlisted value, makes the profile inapplicable for that
 * carcass, so the next provider or the native drops apply. {@code death_equivalents} are the items an ordinary kill
 * of this creature yields for the same product; a carcass kill removes them from the death drops.
 */
public record GroupSpec(CountMode countMode, @Nullable ResourceLocation item, @Nullable Variants variants,
                        List<String> stages, Map<String, CountRange> counts, List<ResourceLocation> deathEquivalents) {

    public enum CountMode {
        NATIVE_GROUP("native_group"),
        PER_STAGE("per_stage");

        public final String json;

        CountMode(String json) {
            this.json = json;
        }

        @Nullable
        public static CountMode byJson(String name) {
            for (CountMode mode : values()) if (mode.json.equals(name)) return mode;
            return null;
        }
    }

    /**
     * An integer snapshot tag ({@code nbt}) choosing the item, for example Alex's Mobs' mantis shrimp {@code Variant}.
     * The items are kept in ascending value order, so every listing (the client view, JEI, diagnostics) is stable.
     */
    public record Variants(String nbt, Map<Integer, ResourceLocation> items) {
        public Variants {
            items = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(items));
        }

        /** The item for this snapshot, or null when the snapshot does not name one of the listed values. */
        @Nullable
        public ResourceLocation select(CompoundTag snapshot) {
            if (!snapshot.contains(nbt, Tag.TAG_ANY_NUMERIC)) return null;
            return items.get(snapshot.getInt(nbt));
        }
    }

    public GroupSpec {
        if ((item == null) == (variants == null)) throw new IllegalArgumentException("exactly one of item or variants");
        stages = List.copyOf(stages);
        counts = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(counts));
        deathEquivalents = List.copyOf(deathEquivalents);
    }

    /** Every item this group can produce: the item, or each variant's item. */
    public List<ResourceLocation> outputs() {
        if (item != null) return List.of(item);
        return new ArrayList<>(variants.items().values());
    }

    /** The stages this group covers: the {@code counts} keys for per-stage yields, else the listed stages (empty: all). */
    public List<String> coveredStages() {
        return countMode == CountMode.PER_STAGE ? List.copyOf(counts.keySet()) : stages;
    }

    /** Whether the group answers {@code stage}, given the stages where the mob has native drops of the group. */
    public boolean covers(String stage, Set<String> nativeStages) {
        if (countMode == CountMode.PER_STAGE) return counts.containsKey(stage);
        return stages.isEmpty() ? nativeStages.contains(stage) : stages.contains(stage);
    }
}
