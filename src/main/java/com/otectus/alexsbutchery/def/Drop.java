package com.otectus.alexsbutchery.def;

import net.minecraft.resources.ResourceLocation;

/** One entry of a butchering stage's loot table, generated into JSON by datagen. */
public record Drop(ResourceLocation item, int min, int max, float chance) {

    public static Drop of(String item, int min, int max) {
        return new Drop(new ResourceLocation(item), min, max, 1F);
    }

    public static Drop of(String item, int count) {
        return of(item, count, count);
    }

    public static Drop chance(String item, int min, int max, float chance) {
        return new Drop(new ResourceLocation(item), min, max, chance);
    }
}
