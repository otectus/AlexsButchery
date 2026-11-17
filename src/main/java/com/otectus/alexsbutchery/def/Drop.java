package com.otectus.alexsbutchery.def;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One entry of a butchering stage's loot table, generated into JSON by datagen. A drop in a {@code group} belongs to
 * one of the mob's replaceable products (its meat, a shell, a tail): datagen moves the group's drops into an internal
 * native table and leaves a {@code alexsbutchery:compat_yield} entry in the stage table, which a compatibility profile
 * may answer with another mod's item. Drops without a group are side products and are never replaced.
 */
public record Drop(ResourceLocation item, int min, int max, float chance, @Nullable String group, @Nullable String needs) {
    public static final String MEAT = "meat";
    public static final String SHELL = "shell";
    public static final String TAIL = "tail";
    public static final String TENTACLES = "tentacles";
    public static final String FISH = "fish";

    public Drop(ResourceLocation item, int min, int max, float chance) {
        this(item, min, max, chance, null, null);
    }

    public Drop(ResourceLocation item, int min, int max, float chance, @Nullable String group) {
        this(item, min, max, chance, group, null);
    }

    public static Drop of(String item, int min, int max) {
        return new Drop(new ResourceLocation(item), min, max, 1F);
    }

    public static Drop of(String item, int count) {
        return of(item, count, count);
    }

    public static Drop chance(String item, int min, int max, float chance) {
        return new Drop(new ResourceLocation(item), min, max, chance);
    }

    /** This drop as part of the named product group. */
    public Drop in(String group) {
        return new Drop(item, min, max, chance, group, needs);
    }

    /**
     * An anatomical part the creature may lack: never from a carcass whose snapshot records the boolean {@code key} as
     * false (a tuskless elephant), at its own chance otherwise (see {@code butcher/MobDataCondition}).
     */
    public Drop needs(String key) {
        return new Drop(item, min, max, chance, group, key);
    }
}
