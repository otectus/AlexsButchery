package com.otectus.alexsbutchery.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.def.ItemDefs;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.registry.ModItems;
import net.mcreator.butchery.jei_recipes.TaxidermyRecipe;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Cooking for every meat family, Butchery taxidermy (wheat + head + the right empty mount) for every head, pelt rugs,
 * and the recipes that only load beside another mod: Immersive Tanning hide mounting and the Farmer's Delight
 * cutting board.
 */
final class ModRecipes extends RecipeProvider {

    ModRecipes(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> out) {
        for (ItemDefs.ItemDef raw : ItemDefs.meatFamilies()) {
            Item rawItem = ModItems.simple(raw.id()).get();
            Item cooked = ModItems.simple(raw.cookedId()).get();
            String unlock = "has_" + raw.id();
            SimpleCookingRecipeBuilder.smelting(Ingredient.of(rawItem), RecipeCategory.FOOD, cooked, 0.35F, 200)
                    .unlockedBy(unlock, has(rawItem)).save(out, AlexsButchery.id("cooking/" + raw.cookedId() + "_smelting"));
            SimpleCookingRecipeBuilder.smoking(Ingredient.of(rawItem), RecipeCategory.FOOD, cooked, 0.35F, 100)
                    .unlockedBy(unlock, has(rawItem)).save(out, AlexsButchery.id("cooking/" + raw.cookedId() + "_smoking"));
            SimpleCookingRecipeBuilder.campfireCooking(Ingredient.of(rawItem), RecipeCategory.FOOD, cooked, 0.35F, 600)
                    .unlockedBy(unlock, has(rawItem)).save(out, AlexsButchery.id("cooking/" + raw.cookedId() + "_campfire"));
        }
        hideMounting(out, "small", List.of("rattlesnake_skin", "caiman_hide", "raccoon_pelt", "skunk_pelt", "tasmanian_devil_pelt", "platypus_pelt"));
        hideMounting(out, "medium", List.of("grizzly_pelt", "gazelle_hide", "gorilla_hide", "tiger_pelt", "snow_leopard_pelt", "maned_wolf_pelt",
                "anteater_hide", "gelada_pelt", "dropbear_pelt", "froststalker_hide", "tusklin_hide", "endergrade_hide", "anaconda_skin",
                "komodo_hide", "crocodile_hide", "seal_skin", "shark_skin"));
        hideMounting(out, "large", List.of("elephant_hide", "rhino_hide", "bison_hide", "moose_hide", "cosmaw_hide", "laviathan_hide",
                "orca_hide", "whale_hide", "sea_bear_hide"));
        for (ModItems.Entries e : ModItems.all()) {
            if (e.rug() == null || e.head() == null) continue;
            Item skin = ModItems.simple(e.def().rug()).get();
            // Butchery's bear rug asks five skins of a mob that drops one to three; ours drop one or two, so three.
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, e.rug().get())
                    .pattern("aba").pattern(" a ")
                    .define('a', skin).define('b', e.head().get())
                    .unlockedBy("has_" + e.def().rug(), has(skin))
                    .save(out, AlexsButchery.id("rug/" + e.def().id() + "_rug"));
        }
        cutting(out);
        for (ModItems.Entries e : ModItems.all()) {
            if (e.head() == null || e.mount() == null) continue;
            MobDef def = e.def();
            out.accept(new Taxidermy(AlexsButchery.id("taxidermy/" + def.id() + "_head_mount"),
                    ForgeRegistries.ITEMS.getKey(e.head().get()),
                    new ResourceLocation("butchery", def.mount().butcheryMount),
                    ForgeRegistries.ITEMS.getKey(e.mount().get())));
        }
    }

    /**
     * Farmer's Delight's knife on the cutting board turns our meats into its own generic cuts (minced beef, chicken
     * cuts, bacon, cod slices), as Butchery does for its beef, pork and mutton cuts, so its recipes can use them.
     * Slab meats of the largest animals give three; only loaded when Farmer's Delight is present.
     */
    private static void cutting(Consumer<FinishedRecipe> out) {
        String mince = "farmersdelight:minced_beef";
        String cuts = "farmersdelight:chicken_cuts";
        for (String meat : List.of("venison", "big_cat_meat", "bear_meat", "primate_meat", "bushmeat", "seal_meat", "cosmaw_meat",
                "endergrade_meat", "straddler_meat", "froststalker_meat")) {
            out.accept(new Cutting("raw_" + meat, mince, 2));
        }
        for (String meat : List.of("whale_meat", "elephant_meat", "rhino_meat", "laviathan_meat")) out.accept(new Cutting("raw_" + meat, mince, 3));
        for (String meat : List.of("game_bird", "emu_meat", "reptile_meat")) out.accept(new Cutting("raw_" + meat, cuts, 2));
        out.accept(new Cutting("raw_tusklin_meat", "farmersdelight:bacon", 2));
        out.accept(new Cutting("raw_shark_meat", "farmersdelight:cod_slice", 2));
    }

    private record Cutting(String raw, String result, int count) implements FinishedRecipe {
        @Override
        public JsonObject serializeRecipe() {
            JsonObject json = new JsonObject();
            json.addProperty("type", "farmersdelight:cutting");
            JsonArray conditions = new JsonArray();
            JsonObject loaded = new JsonObject();
            loaded.addProperty("type", "forge:mod_loaded");
            loaded.addProperty("modid", "farmersdelight");
            conditions.add(loaded);
            json.add("conditions", conditions);
            JsonArray ingredients = new JsonArray();
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("item", AlexsButchery.id(raw).toString());
            ingredients.add(ingredient);
            json.add("ingredients", ingredients);
            JsonObject tool = new JsonObject();
            tool.addProperty("tag", "forge:tools/knives");
            json.add("tool", tool);
            JsonArray results = new JsonArray();
            JsonObject item = new JsonObject();
            item.addProperty("item", result);
            item.addProperty("count", count);
            results.add(item);
            json.add("result", results);
            return json;
        }

        @Override
        public void serializeRecipeData(JsonObject json) {}

        @Override
        public ResourceLocation getId() {
            return AlexsButchery.id("farmersdelight/cutting/" + raw);
        }

        @Override
        public RecipeSerializer<?> getType() {
            return RecipeSerializer.SHAPELESS_RECIPE;
        }

        @Nullable
        @Override
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Nullable
        @Override
        public ResourceLocation getAdvancementId() {
            return null;
        }
    }

    /** Immersive Tanning accepts our skins as raw hides of a size; only loaded when that mod is present. */
    private static void hideMounting(Consumer<FinishedRecipe> out, String size, List<String> skins) {
        out.accept(new HideMounting(AlexsButchery.id("immersive_tanning/hide_mounting_" + size), size, skins));
    }

    private record HideMounting(ResourceLocation id, String size, List<String> skins) implements FinishedRecipe {
        @Override
        public JsonObject serializeRecipe() {
            JsonObject json = new JsonObject();
            json.addProperty("type", "immersive_tanning:hide_mounting");
            JsonArray conditions = new JsonArray();
            JsonObject loaded = new JsonObject();
            loaded.addProperty("type", "forge:mod_loaded");
            loaded.addProperty("modid", "immersive_tanning");
            conditions.add(loaded);
            json.add("conditions", conditions);
            JsonArray ingredient = new JsonArray();
            for (String skin : skins) {
                JsonObject o = new JsonObject();
                o.addProperty("item", AlexsButchery.id(skin).toString());
                ingredient.add(o);
            }
            json.add("ingredient", ingredient);
            json.addProperty("size", size);
            return json;
        }

        @Override
        public void serializeRecipeData(JsonObject json) {}

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return RecipeSerializer.SHAPELESS_RECIPE;
        }

        @Nullable
        @Override
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Nullable
        @Override
        public ResourceLocation getAdvancementId() {
            return null;
        }
    }

    private record Taxidermy(ResourceLocation id, ResourceLocation head, ResourceLocation emptyMount, ResourceLocation output) implements FinishedRecipe {
        @Override
        public void serializeRecipeData(JsonObject json) {
            JsonArray ingredients = new JsonArray();
            ingredients.add(item("minecraft:wheat"));
            ingredients.add(item(head.toString()));
            ingredients.add(item(emptyMount.toString()));
            json.add("ingredients", ingredients);
            JsonObject result = new JsonObject();
            result.addProperty("item", output.toString());
            result.addProperty("count", 1);
            json.add("output", result);
        }

        private static JsonObject item(String id) {
            JsonObject o = new JsonObject();
            o.addProperty("item", id);
            return o;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return TaxidermyRecipe.Serializer.INSTANCE;
        }

        @Nullable
        @Override
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Nullable
        @Override
        public ResourceLocation getAdvancementId() {
            return null;
        }
    }
}
