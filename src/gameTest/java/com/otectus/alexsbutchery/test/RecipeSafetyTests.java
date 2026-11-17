package com.otectus.alexsbutchery.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.CompatPolicy;
import com.otectus.alexsbutchery.compat.RecipePolicy;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.otectus.alexsbutchery.test.CompatTestSupport.ad;
import static com.otectus.alexsbutchery.test.CompatTestSupport.amd;
import static com.otectus.alexsbutchery.test.CompatTestSupport.state;

/**
 * The dual-addon recipe policy (review findings AB-12 to AB-14): a known recipe a pack changed in any respect is kept,
 * an alternate's id never collides with a pack's recipe, and a conflict whose alternate cannot be made safely keeps both
 * recipes. Only meaningful with both cooking addons installed; elsewhere these pass trivially.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeSafetyTests {
    private static final String AMD = "alexsmobsdelight", AD = "alexsdelight";
    private static final ResourceLocation SHANK = new ResourceLocation("alexsdelight:cutting/kangaroo_shank");
    private static final ResourceLocation CENTIPEDE = new ResourceLocation("alexsdelight:smelting/centipede_leg_cook");
    private static final String KANGAROO = "kangaroo_meat_cutting";

    @GameTest(template = "empty")
    public static void changedKnownRecipesAreKeptInEveryRespect(GameTestHelper h) {
        if (!(amd() && ad())) { h.succeed(); return; }
        changed(h, SHANK, KANGAROO, "tool", j -> {
            JsonObject tool = new JsonObject();
            tool.addProperty("item", "minecraft:diamond_axe");
            j.add("tool", tool);
        });
        changed(h, SHANK, KANGAROO, "ingredient item data", j -> {
            JsonObject in = new JsonObject();
            in.addProperty("type", "forge:partial_nbt");
            in.addProperty("item", "alexsmobs:kangaroo_meat");
            in.add("nbt", JsonParser.parseString("{\"PackTag\":1}"));
            JsonArray list = new JsonArray();
            list.add(in);
            j.add("ingredients", list);
        });
        changed(h, SHANK, KANGAROO, "result item data", j -> results(j).get(0).getAsJsonObject().add("nbt", JsonParser.parseString("{\"PackTag\":1}")));
        changed(h, SHANK, KANGAROO, "secondary output", j -> {
            JsonObject bone = new JsonObject();
            bone.addProperty("item", "minecraft:bone");
            results(j).add(bone);
        });
        changed(h, SHANK, KANGAROO, "chance", j -> results(j).get(0).getAsJsonObject().addProperty("chance", 0.5));
        changed(h, CENTIPEDE, "centipede_leg_cooking", "cooking time", j -> j.addProperty("cookingtime", 977));
        changed(h, CENTIPEDE, "centipede_leg_cooking", "experience", j -> j.addProperty("experience", 4.5));
        // The audited originals themselves still resolve.
        RecipePolicy.Plan plan = RecipePolicy.plan(clean(h), h.getLevel().registryAccess(), policy(h, AMD, AD));
        h.assertTrue(plan.result().outcome(KANGAROO).status() == RecipePolicy.Status.RESOLVED, "the shipped recipes resolve: " + plan.result().outcome(KANGAROO));
        h.succeed();
    }

    private static void changed(GameTestHelper h, ResourceLocation id, String conflict, String what, Consumer<JsonObject> change) {
        JsonObject json = RecipePolicy.shippedJson(id.getNamespace(), id);
        h.assertTrue(json != null, "the addon ships " + id);
        json = json.deepCopy();
        change.accept(json);
        Recipe<?> packs = RecipeManager.fromJson(id, json);
        List<Recipe<?>> clean = new ArrayList<>(clean(h));
        clean.removeIf(r -> r.getId().equals(id));
        clean.add(packs);
        for (List<String> order : List.of(List.of(AMD, AD), List.of(AD, AMD))) {
            RecipePolicy.Plan plan = RecipePolicy.plan(clean, h.getLevel().registryAccess(), policy(h, order.get(0), order.get(1)));
            h.assertTrue(plan.recipes().contains(packs), id + " changed in its " + what + " is kept (" + order + ")");
            RecipePolicy.Outcome outcome = plan.result().outcome(conflict);
            h.assertTrue(outcome.keptReplaced().contains(id) && outcome.status() != RecipePolicy.Status.RESOLVED,
                    id + " changed in its " + what + " is reported, not resolved: " + outcome);
            unique(h, plan, what);
        }
    }

    @GameTest(template = "empty")
    public static void alternateIdsNeverCollideWithPackRecipes(GameTestHelper h) {
        if (!(amd() && ad())) { h.succeed(); return; }
        RecipePolicy.Conflict conflict = RecipePolicy.KNOWN.stream().filter(c -> c.name().equals(KANGAROO)).findFirst().orElseThrow();
        for (List<String> order : List.of(List.of(AMD, AD), List.of(AD, AMD))) {
            String owner = order.get(0), loser = order.get(1);
            RecipePolicy.KnownRecipe ownerRecipe = conflict.recipes().stream().filter(k -> k.provider().equals(owner)).findFirst().orElseThrow();
            RecipePolicy.KnownRecipe loserRecipe = conflict.recipes().stream().filter(k -> k.provider().equals(loser)).findFirst().orElseThrow();
            ResourceLocation id = RecipePolicy.alternateId(conflict, loser, loserRecipe);
            // A pack's cutting recipe at our id that makes something else: kept as written, the conflict left unresolved.
            Recipe<?> unrelated = cutting(id, ownerRecipe.result().toString(), "minecraft:bone");
            RecipePolicy.Plan plan = withRecipe(h, unrelated, owner, loser);
            h.assertTrue(plan.recipes().contains(unrelated), "the pack's " + id + " is kept (" + order + ")");
            h.assertTrue(plan.result().outcome(KANGAROO).status() == RecipePolicy.Status.UNRESOLVED, "an unrelated recipe at the id leaves it unresolved: "
                    + plan.result().outcome(KANGAROO));
            h.assertTrue(plan.recipes().stream().anyMatch(r -> r.getId().equals(loserRecipe.id())), "and the losing recipe stays");
            unique(h, plan, "unrelated recipe at the alternate's id");
            // A recipe of another type at the id: the same.
            Recipe<?> crafting = RecipeManager.fromJson(id, JsonParser.parseString("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\""
                    + ownerRecipe.result() + "\"}],\"result\":{\"item\":\"" + loserRecipe.result() + "\"}}").getAsJsonObject());
            plan = withRecipe(h, crafting, owner, loser);
            h.assertTrue(plan.recipes().contains(crafting) && plan.result().outcome(KANGAROO).status() == RecipePolicy.Status.UNRESOLVED,
                    "a crafting recipe at the id is kept and the conflict unresolved: " + plan.result().outcome(KANGAROO));
            unique(h, plan, "crafting recipe at the alternate's id");
            // A pack's recipe that already gives the route: it stands in for the alternate, nothing is duplicated.
            Recipe<?> route = cutting(id, ownerRecipe.result().toString(), loserRecipe.result().toString());
            plan = withRecipe(h, route, owner, loser);
            RecipePolicy.Outcome outcome = plan.result().outcome(KANGAROO);
            h.assertTrue(outcome.status() == RecipePolicy.Status.RESOLVED && outcome.alternates().contains(id) && plan.recipes().contains(route),
                    "a pack's recipe giving the route serves as the alternate: " + outcome);
            unique(h, plan, "route-giving recipe at the alternate's id");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unsafeAlternateKeepsTheLosingRecipe(GameTestHelper h) {
        if (!(amd() && ad())) { h.succeed(); return; }
        // Turning Alex's Delight's shank back into Alex's Mobs Delight's slice makes the forward alternate a loop.
        Recipe<?> reverse = cutting(AlexsButchery.id("test/reverse_shank"), "alexsdelight:kangaroo_shank", "alexsmobsdelight:raw_kangaroo_meat_slice");
        RecipePolicy.Plan plan = withRecipe(h, reverse, AMD, AD);
        RecipePolicy.Outcome outcome = plan.result().outcome(KANGAROO);
        h.assertTrue(outcome.status() == RecipePolicy.Status.UNRESOLVED && outcome.removed().isEmpty() && outcome.alternates().isEmpty(),
                "a looping alternate leaves the conflict unresolved without removing anything: " + outcome);
        h.assertTrue(plan.recipes().stream().anyMatch(r -> r.getId().equals(SHANK)), "Alex's Delight's shank recipe stays");
        h.assertTrue(plan.recipes().stream().noneMatch(r -> r.getId().getPath().startsWith("compat_conflict/" + KANGAROO)), "no alternate was added");
        h.assertTrue(plan.result().errors().stream().anyMatch(e -> e.contains("loop")), "and the reason is reported: " + plan.result().errors());
        // The other conflicts are unaffected.
        h.assertTrue(plan.result().outcome("moose_ribs_cutting").status() == RecipePolicy.Status.RESOLVED, "moose ribs still resolve");
        h.succeed();
    }

    /** The live manager after the policy: one recipe per id, applied again from the same clean list. */
    @GameTest(template = "empty")
    public static void appliedRecipesHaveUniqueIds(GameTestHelper h) {
        RecipeManager manager = h.getLevel().getRecipeManager();
        RecipePolicy.apply(manager, h.getLevel().registryAccess(), state(h).policy());
        Set<ResourceLocation> ids = new HashSet<>();
        int n = 0;
        for (Recipe<?> recipe : manager.getRecipes()) {
            h.assertTrue(ids.add(recipe.getId()), "one recipe per id, twice: " + recipe.getId());
            n++;
        }
        h.assertTrue(n == manager.getRecipeIds().count(), "the id index matches the recipes");
        h.succeed();
    }

    private static RecipePolicy.Plan withRecipe(GameTestHelper h, Recipe<?> extra, String first, String second) {
        List<Recipe<?>> clean = new ArrayList<>(clean(h));
        clean.removeIf(r -> r.getId().equals(extra.getId()));
        clean.add(extra);
        return RecipePolicy.plan(clean, h.getLevel().registryAccess(), policy(h, first, second));
    }

    private static Recipe<?> cutting(ResourceLocation id, String input, String result) {
        return RecipeManager.fromJson(id, JsonParser.parseString("{\"type\":\"farmersdelight:cutting\",\"ingredients\":[{\"item\":\"" + input
                + "\"}],\"tool\":{\"tag\":\"forge:tools/knives\"},\"result\":[{\"item\":\"" + result + "\"}]}").getAsJsonObject());
    }

    private static JsonArray results(JsonObject json) {
        return json.getAsJsonArray("result");
    }

    private static CompatPolicy policy(GameTestHelper h, String first, String second) {
        return state(h).policy().withPriority(List.of(first, second)).withResolveConflicts(true);
    }

    private static List<Recipe<?>> clean(GameTestHelper h) {
        List<Recipe<?>> clean = RecipePolicy.clean(h.getLevel().getRecipeManager());
        return clean.isEmpty() ? List.copyOf(h.getLevel().getRecipeManager().getRecipes()) : clean;
    }

    private static void unique(GameTestHelper h, RecipePolicy.Plan plan, String what) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (Recipe<?> recipe : plan.recipes()) h.assertTrue(ids.add(recipe.getId()), what + ": two recipes " + recipe.getId());
    }

    private RecipeSafetyTests() {}
}
