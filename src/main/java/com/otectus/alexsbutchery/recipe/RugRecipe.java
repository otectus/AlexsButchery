package com.otectus.alexsbutchery.recipe;

import com.google.gson.JsonObject;
import com.otectus.alexsbutchery.item.TrophyData;
import com.otectus.alexsbutchery.registry.ModItems;
import com.otectus.alexsbutchery.registry.ModRecipeSerializers;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * A pelt rug: an ordinary shaped recipe (three pelts around the mob's head) whose crafted rug takes the appearance of the
 * head used, so a white tiger's head makes a white tiger rug. Pattern, ingredients, recipe book, JEI, remaining items and
 * network sync are vanilla's; only {@link #assemble} differs, and the displayed result stays the plain rug.
 */
public class RugRecipe extends ShapedRecipe {

    public RugRecipe(ShapedRecipe base) {
        super(base.getId(), base.getGroup(), base.category(), base.getWidth(), base.getHeight(), base.getIngredients(),
                base.getResultItem(RegistryAccess.EMPTY), base.showNotification());
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        ItemStack rug = super.assemble(container, access);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack input = container.getItem(i);
            ModItems.Owner owner = ModItems.owner(input.getItem());
            if (owner != null && owner.kind() == ModItems.Kind.HEAD) return TrophyData.fromHead(rug, input, ModItems.Kind.RUG);
        }
        return rug;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.RUG.get();
    }

    /** {@code alexsbutchery:rug}: the JSON and network form of {@code minecraft:crafting_shaped}. */
    public static final class Serializer implements RecipeSerializer<RugRecipe> {
        @Override
        public RugRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new RugRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public RugRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new RugRecipe(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, RugRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe);
        }
    }
}
