package com.otectus.alexsbutchery.client.compat.carryon.mixin;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import tschipp.carryon.common.carry.CarryOnData;

/** Read-only bridge to Carry On's separate {@code tile} snapshot; getContentNbt() returns {@code block}. */
@Mixin(value = CarryOnData.class, remap = false)
public interface CarryOnDataAccessor {
    @Accessor("nbt")
    CompoundTag alexsbutchery$getRawCarryData();
}
