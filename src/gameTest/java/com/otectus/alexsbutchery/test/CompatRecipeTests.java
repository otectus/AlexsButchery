package com.otectus.alexsbutchery.test;

import com.google.gson.JsonParser;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.CompatPolicy;
import com.otectus.alexsbutchery.compat.RecipePolicy;
import com.otectus.alexsbutchery.def.CompatDefs;
import com.otectus.alexsbutchery.def.ItemDefs;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.otectus.alexsbutchery.test.CompatTestSupport.AD;
import static com.otectus.alexsbutchery.test.CompatTestSupport.AMD;
import static com.otectus.alexsbutchery.test.CompatTestSupport.ad;
import static com.otectus.alexsbutchery.test.CompatTestSupport.amd;
import static com.otectus.alexsbutchery.test.CompatTestSupport.fd;
import static com.otectus.alexsbutchery.test.CompatTestSupport.item;
import static com.otectus.alexsbutchery.test.CompatTestSupport.key;
import static com.otectus.alexsbutchery.test.CompatTestSupport.state;

/** The recipes around the awarded ingredients: conversions, the dual-addon conflict policy and real cooking chains. */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompatRecipeTests {
    private static final ResourceLocation CUTTING = new ResourceLocation("farmersdelight:cutting");
    private static final ResourceLocation COOKING = new ResourceLocation("farmersdelight:cooking");

    /** Old native stacks reach the addon's recipes through one-way 1:1 conversions, raw to raw and cooked to cooked. */
    @GameTest(template = "empty")
    public static void conversionsFollowThePolicy(GameTestHelper h) {
        RecipeManager recipes = h.getLevel().getRecipeManager();
        CompatPolicy policy = state(h).policy();
        for (CompatDefs.Equivalence e : CompatDefs.equivalences()) {
            ResourceLocation id = CompatDefs.equivalenceRecipe(e);
            boolean expected = net.minecraftforge.fml.ModList.get().isLoaded(e.provider()) && policy.preferExternal() && policy.enabled(e.provider());
            Optional<? extends Recipe<?>> recipe = recipes.byKey(id);
            h.assertTrue(recipe.isPresent() == expected, id + " present=" + recipe.isPresent() + ", expected " + expected);
            if (recipe.isEmpty()) continue;
            List<Ingredient> ingredients = recipe.get().getIngredients().stream().filter(i -> !i.isEmpty()).toList();
            h.assertTrue(ingredients.size() == 1 && ingredients.get(0).test(new ItemStack(ModItemsAccess.simple(e.ours()))), id + " takes our " + e.ours());
            ItemStack result = recipe.get().getResultItem(h.getLevel().registryAccess());
            h.assertTrue(result.getCount() == 1 && key(result.getItem()).equals(e.theirs().toString()), id + " gives one " + e.theirs());
            boolean cooked = ItemDefs.byId(e.ours()).kind() == ItemDefs.Kind.COOKED_MEAT;
            h.assertTrue(cooked == e.theirs().getPath().startsWith("cooked_"), id + ": raw to raw, cooked to cooked");
        }
        if (amd() && policy.preferExternal()) {
            h.assertTrue(recipes.byKey(AlexsButchery.id("compat/alexsmobsdelight/raw_bear_meat")).isPresent(), "old bear meat has a route into the addon's bear recipes");
        }
        // Native mode and a disabled provider remove them, from the clean list.
        RecipePolicy.Plan nativeMode = RecipePolicy.plan(clean(h), h.getLevel().registryAccess(), policy.withPreferExternal(false));
        h.assertTrue(nativeMode.recipes().stream().noneMatch(r -> CompatDefs.providerOfEquivalenceRecipe(r.getId()) != null), "native mode has no conversions");
        // Reversal: no recipe turns an addon item back into ours.
        for (Recipe<?> r : recipes.getRecipes()) {
            Item out = r.getResultItem(h.getLevel().registryAccess()).getItem();
            if (!key(out).startsWith(AlexsButchery.MOD_ID + ":")) continue;
            for (Ingredient ingredient : r.getIngredients()) for (ItemStack in : ingredient.getItems()) {
                String ns = ForgeRegistries.ITEMS.getKey(in.getItem()).getNamespace();
                h.assertTrue(!ns.equals(AMD) && !ns.equals(AD), r.getId() + " must not turn " + key(in.getItem()) + " back into " + key(out));
            }
        }
        h.succeed();
    }

    /** A stack carrying item data is not converted (its data would be lost); a plain stack is. */
    @GameTest(template = "empty")
    public static void conversionsSkipStacksWithItemData(GameTestHelper h) {
        int checked = 0;
        for (CompatDefs.Equivalence e : CompatDefs.equivalences()) {
            Optional<? extends Recipe<?>> recipe = h.getLevel().getRecipeManager().byKey(CompatDefs.equivalenceRecipe(e));
            if (recipe.isEmpty()) continue;
            Ingredient ingredient = recipe.get().getIngredients().stream().filter(i -> !i.isEmpty()).findFirst().orElseThrow();
            ItemStack plain = new ItemStack(ModItemsAccess.simple(e.ours()));
            ItemStack tagged = plain.copy();
            tagged.getOrCreateTag().putString("CustomData", "kept");
            h.assertTrue(ingredient.test(plain), e.ours() + ": a plain stack converts");
            h.assertTrue(!ingredient.test(tagged), e.ours() + ": a stack with item data does not");
            checked++;
        }
        h.assertTrue(checked > 0 == (amd() || ad()) || !state(h).policy().preferExternal(), "conversions checked: " + checked);
        h.succeed();
    }

    /**
     * Both addons cut kangaroo meat, moose ribs and catfish, and cook centipede legs. Exactly one recipe owns each shared
     * input, the provider order decides which, the other's product stays reachable, and switching the order swaps them.
     */
    @GameTest(template = "empty")
    public static void sharedInputsHaveOneOwner(GameTestHelper h) {
        RecipePolicy.Result result = state(h).recipes();
        CompatPolicy policy = state(h).policy();
        boolean both = amd() && ad();
        for (RecipePolicy.Conflict conflict : RecipePolicy.KNOWN) {
            RecipePolicy.Outcome outcome = result.outcome(conflict.name());
            h.assertTrue(outcome != null, conflict.name() + " has an outcome");
            if (!both) {
                h.assertTrue(outcome.status() == RecipePolicy.Status.NOT_PRESENT, conflict.name() + " needs both addons: " + outcome);
                continue;
            }
            String owner = policy.resolveConflicts() ? policy.providerPriority().stream().filter(p -> p.equals(AMD) || p.equals(AD)).findFirst().orElse(null) : null;
            checkOwner(h, h.getLevel().getRecipeManager().getRecipes(), conflict, owner, "server order");
            // Reversed order and resolution switched off, computed from the clean list.
            RecipePolicy.Plan reversed = RecipePolicy.plan(clean(h), h.getLevel().registryAccess(), policy.withPriority(List.of(AD, AMD)).withResolveConflicts(true));
            checkOwner(h, reversed.recipes(), conflict, AD, "reversed order");
            RecipePolicy.Plan off = RecipePolicy.plan(clean(h), h.getLevel().registryAccess(), policy.withResolveConflicts(false));
            checkOwner(h, off.recipes(), conflict, null, "resolution off");
            h.assertTrue(off.result().outcome(conflict.name()).status() == RecipePolicy.Status.REPORTED, "unresolved conflicts are reported");
        }
        h.succeed();
    }

    private static void checkOwner(GameTestHelper h, java.util.Collection<Recipe<?>> recipes, RecipePolicy.Conflict conflict, @Nullable String owner, String label) {
        Set<ResourceLocation> types = new HashSet<>();
        for (RecipePolicy.KnownRecipe known : conflict.recipes()) types.add(known.type());
        Item input = item(conflict.input().toString());
        for (ResourceLocation type : types) {
            List<Recipe<?>> matching = new ArrayList<>();
            for (Recipe<?> r : recipes) if (type.equals(ForgeRegistries.RECIPE_TYPES.getKey(r.getType())) && takesOnly(r, input)) matching.add(r);
            if (owner == null) {
                h.assertTrue(matching.size() == 2, label + ": " + conflict.name() + " keeps both " + type + " recipes, got " + ids(matching));
                continue;
            }
            h.assertTrue(matching.size() == 1, label + ": " + conflict.name() + " has one " + type + " recipe, got " + ids(matching));
            String result = key(matching.get(0).getResultItem(h.getLevel().registryAccess()).getItem());
            h.assertTrue(result.startsWith(owner + ":"), label + ": " + conflict.name() + " owned by " + owner + ", gives " + result);
        }
        if (owner == null) return;
        // The other addon's product is still the result of some recipe (an alternate in this mod's namespace).
        for (RecipePolicy.KnownRecipe known : conflict.recipes()) {
            if (known.provider().equals(owner)) continue;
            boolean reachable = recipes.stream().anyMatch(r -> key(r.getResultItem(h.getLevel().registryAccess()).getItem()).equals(known.result().toString()));
            h.assertTrue(reachable, label + ": " + known.result() + " stays reachable");
        }
    }

    /** A pack that rewrote one of the audited recipe ids keeps its recipe; it is reported instead of removed. */
    @GameTest(template = "empty")
    public static void packRewrittenRecipesAreKept(GameTestHelper h) {
        if (!(amd() && ad())) {
            h.succeed();
            return;
        }
        List<Recipe<?>> clean = new ArrayList<>(clean(h));
        ResourceLocation id = new ResourceLocation("alexsdelight:cutting/kangaroo_shank");
        clean.removeIf(r -> r.getId().equals(id));
        clean.add(RecipeManager.fromJson(id, JsonParser.parseString("{\"type\":\"farmersdelight:cutting\",\"ingredients\":[{\"item\":\"alexsmobs:kangaroo_meat\"}],"
                + "\"tool\":{\"tag\":\"forge:tools/knives\"},\"result\":[{\"item\":\"alexsdelight:kangaroo_shank\",\"count\":3}]}").getAsJsonObject()));
        RecipePolicy.Plan plan = RecipePolicy.plan(clean, h.getLevel().registryAccess(), state(h).policy().withPriority(List.of(AMD, AD)));
        h.assertTrue(plan.recipes().stream().anyMatch(r -> r.getId().equals(id)), "the rewritten recipe is kept");
        RecipePolicy.Outcome outcome = plan.result().outcome("kangaroo_meat_cutting");
        h.assertTrue(outcome.keptReplaced().contains(id), "and reported: " + outcome);
        h.succeed();
    }

    /** No alternate or conversion of this mod can be cycled back into its input through single-ingredient recipes. */
    @GameTest(template = "empty")
    public static void noRecipeLoops(GameTestHelper h) {
        var access = h.getLevel().registryAccess();
        Map<Item, Set<Item>> edges = new HashMap<>();
        for (Recipe<?> r : h.getLevel().getRecipeManager().getRecipes()) {
            List<Ingredient> ingredients = r.getIngredients().stream().filter(i -> !i.isEmpty()).toList();
            if (ingredients.size() != 1 || ingredients.get(0).getItems().length > 8) continue;
            for (ItemStack in : ingredients.get(0).getItems()) edges.computeIfAbsent(in.getItem(), k -> new HashSet<>()).add(r.getResultItem(access).getItem());
        }
        int checked = 0;
        for (Recipe<?> r : h.getLevel().getRecipeManager().getRecipes()) {
            String path = r.getId().getPath();
            if (!r.getId().getNamespace().equals(AlexsButchery.MOD_ID) || !(path.startsWith("compat/") || path.startsWith("compat_conflict/"))) continue;
            Item from = r.getIngredients().get(0).getItems()[0].getItem();
            Item to = r.getResultItem(access).getItem();
            h.assertTrue(r.getResultItem(access).getCount() == 1, r.getId() + " is 1:1");
            Deque<Item> queue = new ArrayDeque<>(List.of(to));
            Set<Item> seen = new HashSet<>(queue);
            while (!queue.isEmpty()) {
                Item next = queue.poll();
                h.assertTrue(next != from, r.getId() + " loops back from " + key(to) + " to " + key(from));
                for (Item n : edges.getOrDefault(next, Set.of())) if (seen.add(n)) queue.add(n);
            }
            checked++;
        }
        h.assertTrue(checked > 0 == (amd() || ad()), "conversions or alternates exist with an addon installed: " + checked);
        h.succeed();
    }

    /** The awarded ingredients work in furnaces, smokers, campfires, the cutting board, the cooking pot and crafting. */
    @GameTest(template = "empty")
    public static void awardedIngredientsWorkInRecipes(GameTestHelper h) {
        // Native items always cook.
        expectCooks(h, "alexsbutchery:raw_bear_meat", "alexsbutchery:cooked_bear_meat");
        expectCooks(h, "alexsbutchery:raw_reptile_meat", "alexsbutchery:cooked_reptile_meat");
        h.assertTrue(cutting(h, "alexsbutchery:raw_bear_meat").isPresent() == fd(), "Farmer's Delight cuts native bear meat only when installed");
        if (amd()) {
            expectCooks(h, "alexsmobsdelight:raw_bear_meat", "alexsmobsdelight:cooked_bear_meat");
            expectCut(h, "alexsmobsdelight:raw_bear_meat", "alexsmobsdelight:raw_bear_meat_slice");
            expectInRecipe(h, "alexsmobsdelight:cooking/canned_bear_meat", "alexsmobsdelight:raw_bear_meat");
            expectInRecipe(h, "alexsmobsdelight:honey_glazed_bear_meat_with_salmon", "alexsmobsdelight:cooked_bear_meat");
            expectCooks(h, "alexsmobsdelight:raw_crocodile_meat", "alexsmobsdelight:cooked_crocodile_meat");
            expectInRecipe(h, "alexsmobsdelight:crocodile_barbecue_stick", "alexsmobsdelight:cooked_crocodile_meat");
            expectInRecipe(h, "alexsmobsdelight:cooking/crocodile_stewed_with_catfish", "alexsmobsdelight:raw_crocodile_meat");
            for (String part : List.of("alexsmobsdelight:raw_crocodile_claw", "alexsmobsdelight:raw_crocodile_meat", "alexsmobsdelight:raw_crocodile_tail",
                    "alexsmobs:crocodile_chestplate")) expectInRecipe(h, "alexsmobsdelight:raw_whole_crocodile", part);
            expectCut(h, "alexsmobsdelight:raw_crocodile_claw", "alexsmobsdelight:raw_crocodile_meat");
            expectCooks(h, "alexsmobsdelight:raw_bison_meat", "alexsmobsdelight:cooked_bison_meat");
            expectCut(h, "alexsmobsdelight:raw_bison_meat", "alexsmobsdelight:raw_bison_meat_cubes");
            expectInRecipe(h, "alexsmobsdelight:bison_tartare", "alexsmobsdelight:raw_bison_meat_cubes");
            for (String colour : List.of("green", "red", "lime", "white"))
                expectCooks(h, "alexsmobsdelight:mantis_shrimp_tail_" + colour, "alexsmobsdelight:cooked_mantis_shrimp_tail");
            expectCooks(h, "alexsmobsdelight:raw_bunfungus_leg", "alexsmobsdelight:cooked_bunfungus_leg");
            expectInRecipe(h, "alexsmobsdelight:cooking/spores_bunfungus_leg_pasta", "alexsmobsdelight:raw_bunfungus_leg");
            expectCooks(h, "alexsmobsdelight:raw_emu", "alexsmobsdelight:cooked_emu");
            expectCut(h, "alexsmobsdelight:raw_emu", "alexsmobsdelight:raw_emu_leg");
        }
        if (ad()) {
            expectCooks(h, "alexsdelight:raw_bison", "alexsdelight:cooked_bison");
            expectCut(h, "alexsdelight:raw_bison", "alexsdelight:bison_mince");
            expectCooks(h, "alexsdelight:bison_mince", "alexsdelight:bison_patty");
            expectInRecipe(h, "alexsdelight:bison_burger", "alexsdelight:bison_patty");
            expectCut(h, "alexsdelight:raw_bunfungus", "alexsdelight:raw_bunfungus_drumstick");
            expectInRecipe(h, "alexsdelight:bunfungus_sandwich", "alexsdelight:cooked_bunfungus");
            if (amd()) expectInRecipe(h, "alexsdelight:bunfungus_sandwich", "alexsmobsdelight:cooked_bunfungus_leg");
        }
        if (amd() || ad()) {
            // Kangaroo, moose and catfish: the whole Alex's Mobs item is cut by the owning addon, and its cuts reach meals.
            for (String whole : List.of("alexsmobs:kangaroo_meat", "alexsmobs:moose_ribs", "alexsmobs:raw_catfish")) {
                h.assertTrue(cutting(h, whole).isPresent(), whole + " can be cut on the cutting board");
            }
            if (ad()) expectInRecipe(h, "alexsdelight:cooking/kangaroo_stew", "alexsmobs:cooked_kangaroo_meat");
            if (amd()) expectInRecipe(h, "alexsmobsdelight:cooking/catfish_stew", "alexsmobs:raw_catfish");
        }
        h.succeed();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static List<Recipe<?>> clean(GameTestHelper h) {
        List<Recipe<?>> clean = RecipePolicy.clean(h.getLevel().getRecipeManager());
        return clean.isEmpty() ? List.copyOf(h.getLevel().getRecipeManager().getRecipes()) : clean;
    }

    private static boolean takesOnly(Recipe<?> recipe, Item input) {
        List<Ingredient> ingredients = recipe.getIngredients().stream().filter(i -> !i.isEmpty()).toList();
        if (ingredients.size() != 1) return false;
        ItemStack[] items = ingredients.get(0).getItems();
        return items.length == 1 && items[0].is(input);
    }

    private static List<ResourceLocation> ids(List<Recipe<?>> recipes) {
        return recipes.stream().map(Recipe::getId).toList();
    }

    private static void expectCooks(GameTestHelper h, String raw, String cooked) {
        var level = h.getLevel();
        for (RecipeType<?> type : List.of(RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING)) {
            @SuppressWarnings("unchecked") RecipeType<net.minecraft.world.item.crafting.AbstractCookingRecipe> t = (RecipeType<net.minecraft.world.item.crafting.AbstractCookingRecipe>) type;
            var recipe = level.getRecipeManager().getRecipeFor(t, new SimpleContainer(new ItemStack(item(raw))), level);
            h.assertTrue(recipe.isPresent() && key(recipe.get().getResultItem(level.registryAccess()).getItem()).equals(cooked),
                    raw + " " + ForgeRegistries.RECIPE_TYPES.getKey(type) + " gives " + cooked + ", got " + recipe.map(r -> key(r.getResultItem(level.registryAccess()).getItem())));
        }
    }

    private static Optional<Recipe<?>> cutting(GameTestHelper h, String input) {
        Item item = item(input);
        for (Recipe<?> r : h.getLevel().getRecipeManager().getRecipes()) {
            if (CUTTING.equals(ForgeRegistries.RECIPE_TYPES.getKey(r.getType())) && r.getIngredients().stream().anyMatch(i -> i.test(new ItemStack(item)))) return Optional.of(r);
        }
        return Optional.empty();
    }

    private static void expectCut(GameTestHelper h, String input, String result) {
        Optional<Recipe<?>> recipe = cutting(h, input);
        h.assertTrue(recipe.isPresent(), input + " has a cutting board recipe");
        h.assertTrue(key(recipe.get().getResultItem(h.getLevel().registryAccess()).getItem()).equals(result), input + " cuts into " + result);
    }

    private static void expectInRecipe(GameTestHelper h, String recipeId, String ingredient) {
        Optional<? extends Recipe<?>> recipe = h.getLevel().getRecipeManager().byKey(new ResourceLocation(recipeId));
        h.assertTrue(recipe.isPresent(), recipeId + " is loaded");
        ItemStack stack = new ItemStack(item(ingredient));
        h.assertTrue(recipe.get().getIngredients().stream().anyMatch(i -> i.test(stack)), recipeId + " accepts " + ingredient);
    }

    /** Our simple items by id, without exposing the registry class's internals to the test. */
    private static final class ModItemsAccess {
        static Item simple(String id) {
            return com.otectus.alexsbutchery.registry.ModItems.simple(id).get();
        }
    }
}
