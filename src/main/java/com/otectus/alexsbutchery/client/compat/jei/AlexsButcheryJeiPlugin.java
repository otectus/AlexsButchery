package com.otectus.alexsbutchery.client.compat.jei;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.compat.Substitutions;
import com.otectus.alexsbutchery.def.Drop;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** A "Butchering" tab: every stage of every carcass and what it yields, read from the mob table. */
@JeiPlugin
public final class AlexsButcheryJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = AlexsButchery.id("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ButcheringCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ItemStack cleaver = butchery("iron_cleaver");
        ItemStack knife = butchery("iron_skinning_knife");
        List<ButcheringRecipe> recipes = new ArrayList<>();
        for (MobDef def : MobDefs.all()) {
            ModItems.Entries items = ModItems.of(def);
            ItemStack carcass = new ItemStack(def.bleeds() && items.drained() != null ? items.drained().get() : items.carcass().get());
            for (Stages.Action action : Stages.actions(def)) {
                List<Drop> drops = def.drops().get(action.table(def));
                if (drops == null || drops.isEmpty()) continue;
                List<ItemStack> outputs = new ArrayList<>();
                for (Drop drop : drops) {
                    Item item = ForgeRegistries.ITEMS.getValue(drop.item());
                    if (item == null) continue;
                    outputs.add(Substitutions.apply(new ItemStack(item, drop.max())));
                }
                recipes.add(new ButcheringRecipe(def, action, carcass, action.needsKnife() ? knife : cleaver, outputs));
            }
        }
        registration.addRecipes(ButcheringCategory.TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ButcheryHooks.hook()), ButcheringCategory.TYPE);
        registration.addRecipeCatalyst(butchery("iron_cleaver"), ButcheringCategory.TYPE);
        registration.addRecipeCatalyst(butchery("iron_skinning_knife"), ButcheringCategory.TYPE);
    }

    private static ItemStack butchery(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("butchery", path));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
