package com.otectus.alexsbutchery.client.compat.jei;

import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.def.MobDef;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One butchering stage of one mob as JEI shows it: the carcass, the tool, and what the server says the stage yields.
 * {@code custom}: the server's table for the stage is a pack's own logic that cannot be summarised exactly.
 */
public record ButcheringRecipe(MobDef def, Stages.Action action, ItemStack carcass, ItemStack tool, List<Output> outputs, boolean custom) {

    /**
     * One output slot: its alternatives (variant-chosen items), the count per roll, the chance, the supplying provider's
     * display name, for variants the snapshot tag that chooses, and whether only carcasses no earlier variant output
     * matched get it.
     */
    public record Output(List<ItemStack> stacks, int min, int max, float chance, String provider, boolean external,
                         @Nullable String variantTag, boolean otherwise) {}
}
