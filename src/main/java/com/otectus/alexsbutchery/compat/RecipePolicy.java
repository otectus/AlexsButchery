package com.otectus.alexsbutchery.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.def.CompatDefs;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * The server's recipe adjustments for the supported cooking addons, applied to the loaded recipe manager before the
 * recipe list is sent to players:
 *
 * <ul>
 *   <li>Known conflicts: Alex's Mobs Delight 1.1.0 and Alex's Delight 1.5 both cut {@code alexsmobs:kangaroo_meat},
 *   {@code alexsmobs:moose_ribs} and {@code alexsmobs:raw_catfish} on the cutting board, and both cook
 *   {@code alexsmobs:centipede_leg} in the furnace, smoker and campfire. With both installed the cutting board and the
 *   furnace would pick whichever recipe the recipe map happens to list first. The provider first in
 *   {@code compat.providerPriority} owns the shared input: the other's recipe is removed, by its known id and only if it
 *   is still exactly the recipe its addon ships (compared through the recipe's own serializer, so a pack or script that
 *   changed its tool, outputs, chances, item data or cooking is left alone and reported), and the other's product stays
 *   reachable through an alternate recipe in this mod's namespace: its cut from the owner's cut of the same size (1:1)
 *   on the cutting board, or its cooked centipede leg from the cooking pot with the same experience.</li>
 *   <li>This mod's one-way conversion recipes ({@code alexsbutchery:compat/<provider>/...}) are removed for providers
 *   disabled by {@code compat.disabledProviders}, and all of them in native mode.</li>
 * </ul>
 *
 * Every application starts from the clean recipe list captured when the recipe manager was loaded, so a reload or a
 * second application never edits an already filtered list. A conflict changes the list only as a whole: if its
 * alternate cannot be built, its id is a pack's recipe for something else, or it would loop back through other
 * single-ingredient recipes, both recipes stay and the conflict is reported unresolved. The published list never holds
 * two recipes with one id.
 */
public final class RecipePolicy {
    private static final ResourceLocation CUTTING = new ResourceLocation("farmersdelight", "cutting");
    private static final ResourceLocation COOKING = new ResourceLocation("farmersdelight", "cooking");
    private static final ResourceLocation SMELTING = new ResourceLocation("minecraft", "smelting");
    private static final ResourceLocation SMOKING = new ResourceLocation("minecraft", "smoking");
    private static final ResourceLocation CAMPFIRE = new ResourceLocation("minecraft", "campfire_cooking");

    /** One provider's recipe for a shared input, as audited: its id, recipe type, the input and the result. */
    public record KnownRecipe(String provider, ResourceLocation id, ResourceLocation type, ResourceLocation result, int count) {}

    /** One shared input both providers define recipes for, and how the losing provider's product stays reachable. */
    public record Conflict(String name, ResourceLocation input, List<KnownRecipe> recipes, Alternate alternate) {}

    /** How the loser's product is offered once the owner takes the shared input. */
    public enum Alternate {
        /** Cutting board: the owner's cut becomes one of the loser's cut (same creature, same size). */
        CUT_FROM_OWNER_CUT,
        /** Cooking pot: the shared input alone cooks into the loser's product, with the loser's experience. */
        COOKING_POT
    }

    public static final List<Conflict> KNOWN = List.of(
            cutting("kangaroo_meat_cutting", "alexsmobs:kangaroo_meat",
                    "alexsmobsdelight:cutting/raw_kangaroo_meat_slice", "alexsmobsdelight:raw_kangaroo_meat_slice",
                    "alexsdelight:cutting/kangaroo_shank", "alexsdelight:kangaroo_shank"),
            cutting("moose_ribs_cutting", "alexsmobs:moose_ribs",
                    "alexsmobsdelight:cutting/raw_moose_rib_piece", "alexsmobsdelight:raw_moose_rib_piece",
                    "alexsdelight:cutting/loose_moose_rib", "alexsdelight:loose_moose_rib"),
            cutting("catfish_cutting", "alexsmobs:raw_catfish",
                    "alexsmobsdelight:cutting/raw_catfish_slice", "alexsmobsdelight:raw_catfish_slice",
                    "alexsdelight:cutting/raw_catfish_slice", "alexsdelight:raw_catfish_slice"),
            new Conflict("centipede_leg_cooking", new ResourceLocation("alexsmobs:centipede_leg"), List.of(
                    known(CompatDefs.AMD, "alexsmobsdelight:smelting/cooked_centipede_leg", SMELTING, "alexsmobsdelight:cooked_centipede_leg", 1),
                    known(CompatDefs.AMD, "alexsmobsdelight:smoking/cooked_centipede_leg", SMOKING, "alexsmobsdelight:cooked_centipede_leg", 1),
                    known(CompatDefs.AMD, "alexsmobsdelight:campfire_cooking/cooked_centipede_leg", CAMPFIRE, "alexsmobsdelight:cooked_centipede_leg", 1),
                    known(CompatDefs.AD, "alexsdelight:smelting/centipede_leg_cook", SMELTING, "alexsdelight:cooked_centipede_leg", 1),
                    known(CompatDefs.AD, "alexsdelight:smelting/centipede_leg_smoke", SMOKING, "alexsdelight:cooked_centipede_leg", 1),
                    known(CompatDefs.AD, "alexsdelight:smelting/centipede_leg_campfire", CAMPFIRE, "alexsdelight:cooked_centipede_leg", 1)),
                    Alternate.COOKING_POT));

    /** What happened to one known conflict on this server. */
    public record Outcome(String conflict, Status status, @Nullable String owner, List<ResourceLocation> removed,
                          List<ResourceLocation> keptReplaced, List<ResourceLocation> alternates, String detail) {}

    public enum Status {
        /** Fewer than two providers' recipes are loaded: nothing to resolve. */
        NOT_PRESENT,
        RESOLVED,
        /**
         * Both recipes kept because {@code compat.resolveKnownRecipeConflicts} is off, no provider order decides, or a pack
         * changed one of the conflict's known recipes (the whole conflict is then left as loaded).
         */
        REPORTED,
        /**
         * Both recipes kept because the loser's product could not be kept reachable safely: its alternate could not be
         * built, a pack's recipe already uses the alternate's id for something else, or it would form a loop.
         */
        UNRESOLVED
    }

    public record Result(List<Outcome> outcomes, List<ResourceLocation> removedConversions, List<String> errors) {
        public static final Result NONE = new Result(List.of(), List.of(), List.of());

        public Result {
            outcomes = List.copyOf(outcomes);
            removedConversions = List.copyOf(removedConversions);
            errors = List.copyOf(errors);
        }

        @Nullable
        public Outcome outcome(String conflict) {
            for (Outcome o : outcomes) if (o.conflict().equals(conflict)) return o;
            return null;
        }
    }

    /** The clean recipe list of each loaded recipe manager, captured on first application. */
    private static final Map<RecipeManager, List<Recipe<?>>> CLEAN = new WeakHashMap<>();

    /** Applies the policy to {@code manager}, always from its clean list, and returns what was done. */
    public static synchronized Result apply(RecipeManager manager, RegistryAccess access, CompatPolicy policy) {
        List<Recipe<?>> clean = CLEAN.computeIfAbsent(manager, m -> List.copyOf(m.getRecipes()));
        Plan plan = plan(clean, access, policy);
        // The manager indexes recipes by id; a duplicate would fail half way through and leave it inconsistent.
        Set<ResourceLocation> ids = new HashSet<>();
        for (Recipe<?> recipe : plan.recipes) {
            if (!ids.add(recipe.getId())) throw new IllegalStateException("Recipe policy produced two recipes " + recipe.getId());
        }
        manager.replaceRecipes(plan.recipes);
        return plan.result;
    }

    /** The clean list captured for {@code manager}, for diagnostics and tests; empty before the first application. */
    public static synchronized List<Recipe<?>> clean(RecipeManager manager) {
        return CLEAN.getOrDefault(manager, List.of());
    }

    /** An adjusted recipe list and what was done to get it. */
    public record Plan(List<Recipe<?>> recipes, Result result) {}

    /** Computes the adjusted list from a clean list without touching any recipe manager (also for tests and previews). */
    public static Plan plan(Collection<Recipe<?>> clean, RegistryAccess access, CompatPolicy policy) {
        return plan(clean, access, policy, RecipePolicy::shipped);
    }

    /**
     * As above, comparing each loaded known recipe with {@code references} (by default the recipe file its addon ships).
     * Every conflict is resolved as one transaction on the working set: its alternates are built, their ids checked against
     * every recipe already there (a pack's recipe at an alternate's id is kept; it stands in for the alternate only if it
     * already gives the same route), and the result checked for loops; only then are the loser's recipes removed and the
     * alternates added. A conflict that fails any check keeps both recipes and is reported {@link Status#UNRESOLVED}. A
     * conflict in which a pack changed any known recipe is never applied in part: all of its recipes stay as loaded and it
     * is reported {@link Status#REPORTED}.
     */
    public static Plan plan(Collection<Recipe<?>> clean, RegistryAccess access, CompatPolicy policy, Function<KnownRecipe, Recipe<?>> references) {
        Map<ResourceLocation, Recipe<?>> current = new LinkedHashMap<>();
        for (Recipe<?> recipe : clean) current.put(recipe.getId(), recipe);
        List<Outcome> outcomes = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Conflict conflict : KNOWN) {
            Map<String, List<KnownRecipe>> present = new LinkedHashMap<>();
            Set<String> loaded = new LinkedHashSet<>();
            List<ResourceLocation> keptReplaced = new ArrayList<>();
            for (KnownRecipe known : conflict.recipes()) {
                Recipe<?> recipe = current.get(known.id());
                if (recipe == null) continue;
                loaded.add(known.provider());
                if (matches(recipe, known, conflict.input(), access, references)) present.computeIfAbsent(known.provider(), k -> new ArrayList<>()).add(known);
                else keptReplaced.add(known.id());
            }
            String replacedNote = keptReplaced.isEmpty() ? "" : "a pack replaced " + keptReplaced + "; left as written";
            if (loaded.size() < 2) {
                outcomes.add(new Outcome(conflict.name(), Status.NOT_PRESENT, null, List.of(), keptReplaced, List.of(), replacedNote));
                continue;
            }
            // A pack took this conflict's recipes over: removing the rest would apply it in part (the changed recipe may
            // still compete for the input), so every recipe of it stays as loaded.
            if (!keptReplaced.isEmpty()) {
                outcomes.add(new Outcome(conflict.name(), Status.REPORTED, null, List.of(), keptReplaced, List.of(),
                        replacedNote + "; the conflict's other recipes are kept too"));
                continue;
            }
            String owner = owner(present.keySet(), policy);
            if (!policy.resolveConflicts() || owner == null) {
                outcomes.add(new Outcome(conflict.name(), Status.REPORTED, null, List.of(), keptReplaced, List.of(), policy.resolveConflicts()
                        ? "no provider order decides between " + present.keySet() : "compat.resolveKnownRecipeConflicts=false; both recipes kept"));
                continue;
            }
            List<ResourceLocation> removed = new ArrayList<>();
            List<ResourceLocation> alternates = new ArrayList<>();
            List<Recipe<?>> additions = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            String problem = null;
            for (Map.Entry<String, List<KnownRecipe>> loser : present.entrySet()) {
                if (loser.getKey().equals(owner)) continue;
                for (KnownRecipe known : loser.getValue()) removed.add(known.id());
                ResourceLocation id = alternateId(conflict, loser.getKey(), present.get(loser.getKey()).get(0));
                Recipe<?> existing = current.get(id);
                if (existing != null) {
                    // Never overwrite or duplicate a pack's recipe at our id: use it if it already gives the route.
                    if (!givesRoute(existing, conflict, present.get(owner).get(0), present.get(loser.getKey()).get(0), access)) {
                        problem = "a pack's recipe " + id + " uses the alternate's id for something else";
                        break;
                    }
                    alternates.add(id);
                    notes.add("the pack's " + id + " already gives the route");
                    continue;
                }
                Recipe<?> alternate = alternate(id, conflict, owner, loser.getKey(), present, current);
                if (alternate == null) {
                    problem = "alternate " + id + " could not be built";
                    break;
                }
                additions.add(alternate);
                alternates.add(id);
            }
            if (problem == null) {
                Map<ResourceLocation, Recipe<?>> trial = new LinkedHashMap<>(current);
                removed.forEach(trial::remove);
                for (Recipe<?> alternate : additions) trial.put(alternate.getId(), alternate);
                // Loop check: no alternate's result may lead back to its input through single-ingredient recipes.
                for (Recipe<?> alternate : additions) {
                    Item from = single(alternate);
                    Item to = alternate.getResultItem(access).getItem();
                    if (from != null && reaches(trial.values(), access, to, from)) {
                        problem = "alternate " + alternate.getId() + " would form a loop from " + key(to) + " back to " + key(from);
                        break;
                    }
                }
                if (problem == null) {
                    current = trial;
                    String detail = String.join("; ", notes);
                    if (!replacedNote.isEmpty()) detail = detail.isEmpty() ? replacedNote : detail + "; " + replacedNote;
                    outcomes.add(new Outcome(conflict.name(), Status.RESOLVED, owner, removed, keptReplaced, alternates, detail));
                    continue;
                }
            }
            errors.add(conflict.name() + ": " + problem + "; both recipes kept");
            outcomes.add(new Outcome(conflict.name(), Status.UNRESOLVED, owner, List.of(), keptReplaced, List.of(), problem + "; both recipes kept"));
        }

        List<ResourceLocation> removedConversions = new ArrayList<>();
        for (ResourceLocation id : current.keySet()) {
            String provider = CompatDefs.providerOfEquivalenceRecipe(id);
            if (provider != null && (!policy.preferExternal() || !policy.enabled(provider))) removedConversions.add(id);
        }
        removedConversions.forEach(current::remove);
        return new Plan(new ArrayList<>(current.values()), new Result(outcomes, removedConversions, errors));
    }

    /** The first enabled provider in the server's order; null when neither is listed (ambiguous) or both are disabled. */
    @Nullable
    private static String owner(Collection<String> providers, CompatPolicy policy) {
        for (String provider : policy.providerPriority()) if (providers.contains(provider) && policy.enabled(provider)) return provider;
        List<String> enabled = providers.stream().filter(policy::enabled).toList();
        return enabled.size() == 1 ? enabled.get(0) : null;
    }

    /** Where the loser's product is offered: {@code alexsbutchery:compat_conflict/<conflict>/<loser>_<result>}. */
    public static ResourceLocation alternateId(Conflict conflict, String loser, KnownRecipe loserRecipe) {
        return AlexsButchery.id("compat_conflict/" + conflict.name() + "/" + loser + "_" + loserRecipe.result().getPath());
    }

    @Nullable
    private static Recipe<?> alternate(ResourceLocation id, Conflict conflict, String owner, String loser, Map<String, List<KnownRecipe>> present,
                                       Map<ResourceLocation, Recipe<?>> byId) {
        KnownRecipe ownerRecipe = present.get(owner).get(0);
        KnownRecipe loserRecipe = present.get(loser).get(0);
        JsonObject json = new JsonObject();
        JsonArray ingredients = new JsonArray();
        if (conflict.alternate() == Alternate.CUT_FROM_OWNER_CUT) {
            json.addProperty("type", CUTTING.toString());
            ingredients.add(item(ownerRecipe.result()));
            json.add("ingredients", ingredients);
            JsonObject tool = new JsonObject();
            tool.addProperty("tag", "forge:tools/knives");
            json.add("tool", tool);
            JsonArray result = new JsonArray();
            JsonObject r = item(loserRecipe.result());
            r.addProperty("count", 1);
            result.add(r);
            json.add("result", result);
        } else {
            float experience = byId.get(loserRecipe.id()) instanceof AbstractCookingRecipe cooking ? cooking.getExperience() : 0.35F;
            json.addProperty("type", COOKING.toString());
            ingredients.add(item(conflict.input()));
            json.add("ingredients", ingredients);
            json.add("result", item(loserRecipe.result()));
            json.addProperty("recipe_book_tab", "meals");
            json.addProperty("experience", experience);
            json.addProperty("cookingtime", 200);
        }
        try {
            return RecipeManager.fromJson(id, json);
        } catch (RuntimeException e) {
            AlexsButchery.LOGGER.debug("Alternate {} could not be built", id, e);
            return null;
        }
    }

    /** Whether a recipe already in place gives the loser's product the way the alternate would. */
    private static boolean givesRoute(Recipe<?> recipe, Conflict conflict, KnownRecipe ownerRecipe, KnownRecipe loserRecipe, RegistryAccess access) {
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        ResourceLocation input = conflict.alternate() == Alternate.CUT_FROM_OWNER_CUT ? ownerRecipe.result() : conflict.input();
        if (!(conflict.alternate() == Alternate.CUT_FROM_OWNER_CUT ? CUTTING : COOKING).equals(type)) return false;
        Item from = single(recipe);
        return from != null && input.equals(ForgeRegistries.ITEMS.getKey(from))
                && loserRecipe.result().equals(ForgeRegistries.ITEMS.getKey(recipe.getResultItem(access).getItem()));
    }

    /**
     * Whether the loaded recipe still is the audited one: the audited type, input and result, and in every respect the
     * recipe its addon ships ({@code reference}): the same serializer writing the same network form, which covers the
     * tool, every output with its count, chance and item data, ingredient item data and cooking time and experience. A
     * recipe that cannot be compared so (no reference, a serializer that fails) counts as a pack's own and is kept.
     */
    static boolean matches(Recipe<?> recipe, KnownRecipe known, ResourceLocation input, RegistryAccess access, Function<KnownRecipe, Recipe<?>> references) {
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        if (!known.type().equals(type)) return false;
        List<Ingredient> ingredients = recipe.getIngredients().stream().filter(i -> !i.isEmpty()).toList();
        if (ingredients.size() != 1) return false;
        ItemStack[] items = ingredients.get(0).getItems();
        if (items.length != 1 || !input.equals(ForgeRegistries.ITEMS.getKey(items[0].getItem()))) return false;
        ItemStack result = recipe.getResultItem(access);
        if (!known.result().equals(ForgeRegistries.ITEMS.getKey(result.getItem())) || result.getCount() != known.count()) return false;
        Recipe<?> reference;
        try {
            reference = references.apply(known);
        } catch (RuntimeException e) {
            AlexsButchery.LOGGER.debug("Cannot read the shipped recipe {}", known.id(), e);
            return false;
        }
        return reference != null && sameRecipe(recipe, reference);
    }

    /** The same serializer writes both into the same bytes (ids are not part of the network form). */
    static boolean sameRecipe(Recipe<?> a, Recipe<?> b) {
        if (a.getSerializer() != b.getSerializer()) return false;
        byte[] x = network(a), y = network(b);
        return x != null && y != null && java.util.Arrays.equals(x, y);
    }

    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static byte[] network(Recipe<?> recipe) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ((RecipeSerializer) recipe.getSerializer()).toNetwork(buf, recipe);
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } catch (RuntimeException e) {
            AlexsButchery.LOGGER.debug("Cannot serialize recipe {} for comparison", recipe.getId(), e);
            return null;
        } finally {
            buf.release();
        }
    }

    /** The recipe exactly as its addon ships it, read from the addon's own file (not the data pack stack). */
    @Nullable
    public static Recipe<?> shipped(KnownRecipe known) {
        JsonObject json = shippedJson(known.provider(), known.id());
        if (json == null) return null;
        try {
            return RecipeManager.fromJson(known.id(), json);
        } catch (RuntimeException e) {
            AlexsButchery.LOGGER.debug("Cannot read the shipped recipe {}", known.id(), e);
            return null;
        }
    }

    /** A recipe file as the mod {@code modId} ships it, or null. */
    @Nullable
    public static JsonObject shippedJson(String modId, ResourceLocation id) {
        var file = ModList.get().getModFileById(modId);
        if (file == null) return null;
        java.nio.file.Path path = file.getFile().findResource("data", id.getNamespace(), "recipes", id.getPath() + ".json");
        if (!java.nio.file.Files.isRegularFile(path)) return null;
        try {
            return GsonHelper.parse(java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException | RuntimeException e) {
            AlexsButchery.LOGGER.debug("Cannot read {} from {}", id, modId, e);
            return null;
        }
    }

    @Nullable
    private static Item single(Recipe<?> recipe) {
        List<Ingredient> ingredients = recipe.getIngredients().stream().filter(i -> !i.isEmpty()).toList();
        if (ingredients.size() != 1) return null;
        ItemStack[] items = ingredients.get(0).getItems();
        return items.length == 1 ? items[0].getItem() : null;
    }

    /** Breadth-first over single-ingredient recipes (bounded): can {@code from} be turned back into {@code target}? */
    private static boolean reaches(Collection<Recipe<?>> recipes, RegistryAccess access, Item from, Item target) {
        Map<Item, Set<Item>> edges = new HashMap<>();
        for (Recipe<?> recipe : recipes) {
            List<Ingredient> ingredients = recipe.getIngredients().stream().filter(i -> !i.isEmpty()).toList();
            if (ingredients.size() != 1) continue;
            ItemStack[] items = ingredients.get(0).getItems();
            if (items.length == 0 || items.length > 8) continue;
            Item out = recipe.getResultItem(access).getItem();
            for (ItemStack in : items) edges.computeIfAbsent(in.getItem(), k -> new HashSet<>()).add(out);
        }
        Deque<Item> queue = new ArrayDeque<>(List.of(from));
        Set<Item> seen = new HashSet<>(queue);
        for (int steps = 0; !queue.isEmpty() && steps < 512; steps++) {
            Item item = queue.poll();
            if (item == target) return true;
            for (Item next : edges.getOrDefault(item, Set.of())) if (seen.add(next)) queue.add(next);
        }
        return false;
    }

    private static Conflict cutting(String name, String input, String amdId, String amdResult, String adId, String adResult) {
        return new Conflict(name, new ResourceLocation(input), List.of(
                known(CompatDefs.AMD, amdId, CUTTING, amdResult, 2), known(CompatDefs.AD, adId, CUTTING, adResult, 2)), Alternate.CUT_FROM_OWNER_CUT);
    }

    private static KnownRecipe known(String provider, String id, ResourceLocation type, String result, int count) {
        return new KnownRecipe(provider, new ResourceLocation(id), type, new ResourceLocation(result), count);
    }

    private static JsonObject item(ResourceLocation id) {
        JsonObject o = new JsonObject();
        o.addProperty("item", id.toString());
        return o;
    }

    private static String key(Item item) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(item));
    }

    private RecipePolicy() {}
}
