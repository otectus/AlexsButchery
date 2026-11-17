package com.otectus.alexsbutchery.client.compat.jei;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.compat.CompatView;
import com.otectus.alexsbutchery.compat.ProviderNames;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A "Butchering" tab: every stage of every carcass and what it yields, as the connected server describes it (see
 * {@link CompatView}): the actual items of the installed cooking addons, count ranges, chances, each link of a variant
 * chain with what carcasses no variant matches get, and stages a server pack defines itself. Nothing is shown before the
 * server's view arrives; a new view (another world or server, a data reload) replaces the shown recipes, and leaving
 * clears them.
 *
 * <p>The view can arrive before JEI's runtime exists (registration happens first), so the runtime is reconciled with the
 * latest view as soon as it is available. Recipes are kept per creature and stage description: showing the same view
 * again changes nothing, and a changed view hides what no longer applies, shows again what applies once more, and adds
 * only what is new, so repeated reloads never pile up recipe objects.
 */
@JeiPlugin
public final class AlexsButcheryJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = AlexsButchery.id("jei_plugin");
    @Nullable private static IJeiRuntime runtime;
    /** Every recipe object JEI holds for this runtime, shown or hidden, by what it describes. */
    private static Map<List<Object>, ButcheringRecipe> known = new LinkedHashMap<>();
    /** The keys currently shown. */
    private static Set<List<Object>> shownKeys = new LinkedHashSet<>();

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
        known = new LinkedHashMap<>();
        shownKeys = new LinkedHashSet<>();
        Map<List<Object>, ButcheringRecipe> recipes = recipes(CompatView.received());
        known.putAll(recipes);
        shownKeys.addAll(recipes.keySet());
        registration.addRecipes(ButcheringCategory.TYPE, List.copyOf(recipes.values()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ButcheryHooks.hook()), ButcheringCategory.TYPE);
        registration.addRecipeCatalyst(butchery("iron_cleaver"), ButcheringCategory.TYPE);
        registration.addRecipeCatalyst(butchery("iron_skinning_knife"), ButcheringCategory.TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        // A view that arrived between registration and now is shown at once, not only after the next reload.
        refresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        known = new LinkedHashMap<>();
        shownKeys = new LinkedHashSet<>();
    }

    /** Shows the current view's recipes (none without one), changing only what differs. Client thread. */
    public static void refresh() {
        IJeiRuntime rt = runtime;
        if (rt == null) return;
        Map<List<Object>, ButcheringRecipe> next = recipes(CompatView.received());
        if (next.keySet().equals(shownKeys)) return;
        List<ButcheringRecipe> hide = new ArrayList<>(), unhide = new ArrayList<>(), add = new ArrayList<>();
        for (List<Object> key : shownKeys) if (!next.containsKey(key)) hide.add(known.get(key));
        for (Map.Entry<List<Object>, ButcheringRecipe> e : next.entrySet()) {
            if (shownKeys.contains(e.getKey())) continue;
            ButcheringRecipe existing = known.get(e.getKey());
            if (existing != null) unhide.add(existing);
            else {
                known.put(e.getKey(), e.getValue());
                add.add(e.getValue());
            }
        }
        if (!hide.isEmpty()) rt.getRecipeManager().hideRecipes(ButcheringCategory.TYPE, hide);
        if (!unhide.isEmpty()) rt.getRecipeManager().unhideRecipes(ButcheringCategory.TYPE, unhide);
        if (!add.isEmpty()) rt.getRecipeManager().addRecipes(ButcheringCategory.TYPE, add);
        shownKeys = new LinkedHashSet<>(next.keySet());
    }

    /** The recipes currently shown, for the client checks. */
    public static List<ButcheringRecipe> shown() {
        List<ButcheringRecipe> out = new ArrayList<>();
        for (List<Object> key : shownKeys) out.add(known.get(key));
        return out;
    }

    /** How many recipe objects JEI holds for this runtime, shown or hidden. */
    public static int held() {
        return known.size();
    }

    public static boolean runtimeAvailable() {
        return runtime != null;
    }

    static Map<List<Object>, ButcheringRecipe> recipes(@Nullable CompatView view) {
        Map<List<Object>, ButcheringRecipe> recipes = new LinkedHashMap<>();
        if (view == null) return recipes;
        ItemStack cleaver = butchery("iron_cleaver");
        ItemStack knife = butchery("iron_skinning_knife");
        for (MobDef def : MobDefs.all()) {
            ModItems.Entries items = ModItems.of(def);
            ItemStack carcass = new ItemStack(def.bleeds() && items.drained() != null ? items.drained().get() : items.carcass().get());
            for (Stages.Action action : Stages.actions(def)) {
                CompatView.StageView stage = view.stage(def.id(), action.table(def));
                if (stage == null || (stage.outputs().isEmpty() && !stage.custom())) continue;
                List<ButcheringRecipe.Output> outputs = new ArrayList<>();
                for (CompatView.OutputView o : stage.outputs()) {
                    List<ItemStack> stacks = stacks(o.items(), o.max());
                    if (stacks.isEmpty()) continue;
                    outputs.add(new ButcheringRecipe.Output(stacks, o.min(), o.max(), o.chance(), ProviderNames.name(o.provider()),
                            !ProviderNames.NATIVE.equals(o.provider()), o.variantTag(), o.otherwise()));
                }
                recipes.put(List.of(def.id(), stage), new ButcheringRecipe(def, action, carcass, action.needsKnife() ? knife : cleaver, outputs, stage.custom()));
            }
        }
        return recipes;
    }

    private static List<ItemStack> stacks(List<ResourceLocation> ids, int count) {
        List<ItemStack> out = new ArrayList<>();
        for (ResourceLocation id : ids) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) continue;
            out.add(new ItemStack(item, Math.max(1, Math.min(count, item.getMaxStackSize(new ItemStack(item))))));
        }
        return out;
    }

    private static ItemStack butchery(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("butchery", path));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
