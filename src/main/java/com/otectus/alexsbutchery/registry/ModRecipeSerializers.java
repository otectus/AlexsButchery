package com.otectus.alexsbutchery.registry;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.recipe.RugRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, AlexsButchery.MOD_ID);

    /** Shaped pelt-rug recipes that keep the head's appearance ({@link RugRecipe}). */
    public static final RegistryObject<RugRecipe.Serializer> RUG = SERIALIZERS.register("rug", RugRecipe.Serializer::new);

    private ModRecipeSerializers() {}
}
