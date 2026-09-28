package com.otectus.alexsbutchery.client.compat.jei;

import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.def.MobDef;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** One butchering stage of one mob as JEI shows it: the carcass, the tool, and what the stage yields. */
public record ButcheringRecipe(MobDef def, Stages.Action action, ItemStack carcass, ItemStack tool, List<ItemStack> outputs) {}
