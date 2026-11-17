package com.otectus.alexsbutchery.compat;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.compat.CompatState.Choice;
import com.otectus.alexsbutchery.compat.CompatState.Diagnostic;
import com.otectus.alexsbutchery.compat.CompatState.Key;
import com.otectus.alexsbutchery.compat.CompatState.Provided;
import com.otectus.alexsbutchery.compat.CompatState.Rejection;
import com.otectus.alexsbutchery.compat.CompatState.Resolution;
import com.otectus.alexsbutchery.compat.CompatState.Severity;
import com.otectus.alexsbutchery.compat.profile.CompatProfile;
import com.otectus.alexsbutchery.compat.profile.GroupSpec;
import com.otectus.alexsbutchery.compat.profile.ProfileSet;
import com.otectus.alexsbutchery.compat.profile.ProviderSelection;
import com.otectus.alexsbutchery.def.CompatDefs;
import com.otectus.alexsbutchery.def.Drop;
import com.otectus.alexsbutchery.def.ItemDefs;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds a {@link CompatState} from the loaded profile files, the server's policy and its loaded registries, tags,
 * recipes and loot tables. Item ids are resolved here, once: a missing or air item invalidates its profile (an error
 * when the profile's mods are installed), and an inactive profile (its mods absent, its provider disabled) is simply
 * skipped. Nothing here chooses by tag order, map iteration or display names.
 */
public final class CompatResolver {

    /** A loaded profile with its items resolved, or the reason it cannot be used. */
    private record Loaded(ResourceLocation id, CompatProfile profile, MobDef def, @Nullable String inactive, Severity severity,
                          Map<String, Map<Integer, Item>> variantItems, Map<String, Item> items, Map<String, List<Item>> deathItems) {
        boolean active() {
            return inactive == null;
        }
    }

    public static CompatState build(MinecraftServer server, ProfileSet profiles, CompatPolicy policy, RecipePolicy.Result recipes,
                                    List<Diagnostic> carried, boolean degraded) {
        List<Diagnostic> diagnostics = new ArrayList<>(carried);
        for (String error : recipes.errors()) diagnostics.add(new Diagnostic(Severity.ERROR, "recipes", error));
        for (RecipePolicy.Outcome outcome : recipes.outcomes()) {
            if (outcome.status() == RecipePolicy.Status.REPORTED || outcome.status() == RecipePolicy.Status.UNRESOLVED)
                diagnostics.add(new Diagnostic(Severity.WARNING, "recipes", outcome.conflict() + ": " + outcome.detail()));
            else if (!outcome.keptReplaced().isEmpty())
                diagnostics.add(new Diagnostic(Severity.INFO, "recipes", outcome.conflict() + ": " + outcome.detail()));
        }
        for (ResourceLocation id : profiles.disabled())
            diagnostics.add(new Diagnostic(Severity.INFO, id.toString(), "disabled by " + profiles.sources().getOrDefault(id, "?")));

        // ---- profiles ----------------------------------------------------------------------------------------------
        Map<ResourceLocation, List<Loaded>> byEntity = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, CompatProfile> e : profiles.profiles().entrySet()) {
            Loaded loaded = load(e.getKey(), e.getValue(), policy, diagnostics);
            if (loaded != null) byEntity.computeIfAbsent(e.getValue().entity(), k -> new ArrayList<>()).add(loaded);
        }

        // ---- selections ----------------------------------------------------------------------------------------------
        Map<String, ProviderSelection> selections = new HashMap<>();
        Map<String, List<ResourceLocation>> selectionSources = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, ProviderSelection> e : profiles.selections().entrySet()) {
            ProviderSelection selection = e.getValue();
            MobDef def = MobDefs.byEntityId(selection.entity());
            if (def == null) {
                diagnostics.add(new Diagnostic(Severity.ERROR, e.getKey().toString(), "selection for unknown creature " + selection.entity()));
                continue;
            }
            for (String group : selection.groups()) {
                if (!def.groups().contains(group))
                    diagnostics.add(new Diagnostic(Severity.ERROR, e.getKey().toString(), def.id() + " has no " + group + " group (has " + def.groups() + ")"));
            }
            for (String group : def.groups()) {
                if (!selection.appliesTo(group)) continue;
                String key = def.entity() + "#" + group;
                selectionSources.computeIfAbsent(key, k -> new ArrayList<>()).add(e.getKey());
                selections.put(key, selection);
            }
        }
        for (Map.Entry<String, List<ResourceLocation>> e : selectionSources.entrySet()) {
            if (e.getValue().size() < 2) continue;
            selections.remove(e.getKey());
            diagnostics.add(new Diagnostic(Severity.ERROR, e.getValue().toString(), "ambiguous: several selections for " + e.getKey()
                    + "; none applied, the server order is used"));
        }

        // ---- resolutions -----------------------------------------------------------------------------------------------
        Map<Key, Resolution> resolutions = new LinkedHashMap<>();
        for (MobDef def : MobDefs.all()) {
            List<Loaded> candidates = byEntity.getOrDefault(def.entity(), List.of());
            for (String group : def.groups()) {
                Set<String> nativeStages = new LinkedHashSet<>(def.stagesOf(group));
                for (String stage : nativeStages) {
                    Key key = new Key(def.entity(), group, stage);
                    resolutions.put(key, resolve(key, nativeStages, candidates, selections.get(def.entity() + "#" + group), policy, diagnostics));
                }
            }
        }

        // ---- death drops ---------------------------------------------------------------------------------------------
        Level level = server.overworld();
        Set<Item> tagged = new LinkedHashSet<>();
        BuiltInRegistries.ITEM.getTag(ModTags.Items.REPLACED_BY_CARCASS).ifPresent(tag -> tag.forEach(h -> tagged.add(h.value())));
        Set<Item> nativeUnion = new LinkedHashSet<>();
        for (MobDef def : MobDefs.all()) for (ResourceLocation id : def.replacedDrops()) {
            Item item = item(id);
            if (item != null) nativeUnion.add(item);
        }
        Set<Item> packGlobal = new LinkedHashSet<>(tagged);
        packGlobal.removeAll(nativeUnion);
        Map<ResourceLocation, Set<Item>> suppression = new LinkedHashMap<>();
        Map<ResourceLocation, Set<Item>> nativeSuppression = new LinkedHashMap<>();
        for (MobDef def : MobDefs.all()) {
            Set<Item> natives = new LinkedHashSet<>();
            for (ResourceLocation id : def.replacedDrops()) {
                Item item = item(id);
                // Our generated tag lists every native replacement; a pack that removes one keeps that drop.
                if (item != null && tagged.contains(item)) natives.add(item);
            }
            natives.addAll(cooked(natives, server, level));
            Set<Item> all = new LinkedHashSet<>(natives);
            for (Loaded loaded : byEntity.getOrDefault(def.entity(), List.of())) {
                if (!loaded.active()) continue;
                for (List<Item> items : loaded.deathItems().values()) all.addAll(items);
            }
            all.addAll(cooked(all, server, level));
            suppression.put(def.entity(), all);
            nativeSuppression.put(def.entity(), natives);
        }

        // ---- presentation --------------------------------------------------------------------------------------------
        Map<String, List<CompatView.StageView>> stages = stageViews(server, resolutions);
        Set<ResourceLocation> hidden = hidden(resolutions, stages);
        Map<String, CompatState.ProviderStatus> providers = providers(byEntity, profiles, policy);
        CompatView view = view(stages, hidden, providers);
        if (degraded) diagnostics.add(new Diagnostic(Severity.ERROR, "profiles", "the profile files could not be loaded; every carcass yields native items"));
        return new CompatState(policy, profiles, resolutions, suppression, nativeSuppression, packGlobal, hidden, providers, diagnostics,
                recipes, view, degraded);
    }

    @Nullable
    private static Loaded load(ResourceLocation id, CompatProfile profile, CompatPolicy policy, List<Diagnostic> diagnostics) {
        String source = id.toString();
        MobDef def = MobDefs.byEntityId(profile.entity());
        if (def == null) {
            diagnostics.add(new Diagnostic(Severity.ERROR, source, "unknown creature " + profile.entity() + " (not one of this mod's carcasses)"));
            return null;
        }
        for (Map.Entry<String, GroupSpec> group : profile.groups().entrySet()) {
            List<String> nativeStages = def.stagesOf(group.getKey());
            if (nativeStages.isEmpty()) {
                diagnostics.add(new Diagnostic(Severity.ERROR, source, def.id() + " has no native " + group.getKey() + " group (has " + def.groups() + ")"));
                return null;
            }
            for (String stage : group.getValue().coveredStages()) {
                if (!nativeStages.contains(stage)) {
                    diagnostics.add(new Diagnostic(Severity.ERROR, source, def.id() + " " + group.getKey() + " has no native drops at " + stage
                            + " (it has them at " + nativeStages + ")"));
                    return null;
                }
            }
        }
        List<String> missingMods = profile.requiredMods().stream().filter(m -> !ModList.get().isLoaded(m)).toList();
        if (!missingMods.isEmpty()) {
            return new Loaded(id, profile, def, "requires " + String.join(", ", missingMods) + " (not installed)", Severity.INFO, Map.of(), Map.of(), Map.of());
        }
        if (!policy.enabled(profile.provider())) {
            return new Loaded(id, profile, def, "provider " + profile.provider() + " disabled by compat.disabledProviders", Severity.INFO,
                    Map.of(), Map.of(), Map.of());
        }
        Map<String, Item> items = new LinkedHashMap<>();
        Map<String, Map<Integer, Item>> variants = new LinkedHashMap<>();
        Map<String, List<Item>> death = new LinkedHashMap<>();
        for (Map.Entry<String, GroupSpec> group : profile.groups().entrySet()) {
            GroupSpec spec = group.getValue();
            String where = def.id() + " " + group.getKey() + " " + spec.coveredStages();
            if (spec.item() != null) {
                Item item = item(spec.item());
                if (item == null) return missing(id, profile, def, where, spec.item(), diagnostics);
                items.put(group.getKey(), item);
            } else {
                Map<Integer, Item> resolved = new LinkedHashMap<>();
                for (Map.Entry<Integer, ResourceLocation> v : spec.variants().items().entrySet()) {
                    Item item = item(v.getValue());
                    if (item == null) return missing(id, profile, def, where, v.getValue(), diagnostics);
                    resolved.put(v.getKey(), item);
                }
                variants.put(group.getKey(), resolved);
            }
            List<Item> equivalents = new ArrayList<>();
            for (ResourceLocation eq : spec.deathEquivalents()) {
                Item item = item(eq);
                if (item == null) diagnostics.add(new Diagnostic(Severity.ERROR, id.toString(), where + ": death equivalent " + eq
                        + " is not a registered item; it cannot drop, so nothing is removed for it"));
                else equivalents.add(item);
            }
            death.put(group.getKey(), equivalents);
        }
        return new Loaded(id, profile, def, null, Severity.INFO, variants, items, death);
    }

    private static Loaded missing(ResourceLocation id, CompatProfile profile, MobDef def, String where, ResourceLocation item, List<Diagnostic> diagnostics) {
        String reason = "missing item " + item + " for " + where;
        diagnostics.add(new Diagnostic(Severity.ERROR, id.toString(), reason + " although " + profile.requiredMods()
                + " is installed; the next provider or the native drops apply"));
        return new Loaded(id, profile, def, reason, Severity.ERROR, Map.of(), Map.of(), Map.of());
    }

    private static Resolution resolve(Key key, Set<String> nativeStages, List<Loaded> loaded, @Nullable ProviderSelection selection,
                                      CompatPolicy policy, List<Diagnostic> diagnostics) {
        List<Rejection> rejected = new ArrayList<>();
        Map<String, List<Loaded>> byProvider = new LinkedHashMap<>();
        for (Loaded l : loaded) {
            GroupSpec spec = l.profile().groups().get(key.group());
            if (spec == null || !spec.covers(key.stage(), nativeStages)) continue;
            if (!l.active()) {
                rejected.add(new Rejection(l.id(), l.profile().provider(), l.inactive(), l.severity()));
                continue;
            }
            byProvider.computeIfAbsent(l.profile().provider(), k -> new ArrayList<>()).add(l);
        }
        if (!policy.preferExternal()) {
            for (List<Loaded> list : byProvider.values()) for (Loaded l : list)
                rejected.add(new Rejection(l.id(), l.profile().provider(), "native mode (compat.preferExternalProducts=false)", Severity.INFO));
            return new Resolution(List.of(CompatState.Native.INSTANCE), rejected);
        }
        List<String> order = new ArrayList<>();
        boolean nativeStop = false;
        if (selection != null) {
            for (String provider : selection.providerPriority()) {
                if (ProviderSelection.NATIVE.equals(provider)) {
                    nativeStop = true;
                    break;
                }
                if (!order.contains(provider)) order.add(provider);
            }
        }
        if (!nativeStop) {
            for (String provider : policy.providerPriority()) if (!order.contains(provider)) order.add(provider);
            List<String> unlisted = new ArrayList<>(new TreeSet<>(byProvider.keySet()));
            unlisted.removeAll(order);
            if (unlisted.size() == 1) order.add(unlisted.get(0));
            else if (unlisted.size() > 1) {
                for (String provider : unlisted) for (Loaded l : byProvider.get(provider)) rejected.add(new Rejection(l.id(), provider,
                        "ambiguous: providers " + unlisted + " have the same priority; list them in compat.providerPriority or a selection file", Severity.ERROR));
                diagnostics.add(new Diagnostic(Severity.ERROR, key.toString(), "ambiguous providers " + unlisted + "; none used"));
            }
        }
        List<Choice> chain = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (String provider : order) {
            List<Loaded> list = byProvider.get(provider);
            if (list == null) continue;
            used.add(provider);
            if (list.size() > 1) {
                List<ResourceLocation> ids = list.stream().map(Loaded::id).toList();
                for (Loaded l : list) rejected.add(new Rejection(l.id(), provider, "ambiguous: " + ids + " all supply " + provider + " here", Severity.ERROR));
                diagnostics.add(new Diagnostic(Severity.ERROR, key.toString(), "ambiguous profiles " + ids + " for provider " + provider + "; none used"));
                continue;
            }
            Loaded l = list.get(0);
            GroupSpec spec = l.profile().groups().get(key.group());
            chain.add(new Provided(l.id(), provider, spec, l.items().get(key.group()), l.variantItems().getOrDefault(key.group(), Map.of())));
        }
        if (nativeStop) {
            for (Map.Entry<String, List<Loaded>> e : byProvider.entrySet()) {
                if (used.contains(e.getKey())) continue;
                for (Loaded l : e.getValue()) rejected.add(new Rejection(l.id(), e.getKey(), "after \"native\" in a selection file", Severity.INFO));
            }
        }
        chain.add(CompatState.Native.INSTANCE);
        return new Resolution(chain, rejected);
    }

    /**
     * What {@code minecraft:furnace_smelt} turns these foods into on a burning death, by the server's smelting recipes.
     * Only edible items count: a pack smelting a hide into leather does not make leather a removable death drop.
     */
    private static Set<Item> cooked(Collection<Item> raw, MinecraftServer server, Level level) {
        Set<Item> out = new LinkedHashSet<>();
        for (Item item : raw) {
            if (!item.isEdible()) continue;
            server.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(new ItemStack(item)), level).ifPresent(recipe -> {
                Item result = recipe.getResultItem(server.registryAccess()).getItem();
                if (result != Items.AIR) out.add(result);
            });
        }
        return out;
    }

    /**
     * Our items that no creature yields under these rules and loaded tables: every native group drop of the item is
     * replaced by an unconditional provider, it is no stage's side product, no loaded stage table yields it (a pack's own
     * table can name it outright), and no stage whose table cannot be read exactly could. Their cooked forms follow
     * them. Presentation only.
     */
    private static Set<ResourceLocation> hidden(Map<Key, Resolution> resolutions, Map<String, List<CompatView.StageView>> stages) {
        Set<ResourceLocation> raw = replacedEverywhere(resolutions);
        for (MobDef def : MobDefs.all()) {
            for (CompatView.StageView stage : stages.getOrDefault(def.id(), List.of())) {
                for (CompatView.OutputView output : stage.outputs()) raw.removeAll(output.items());
                if (stage.custom()) for (Drop drop : def.drops().getOrDefault(stage.stage(), List.of())) raw.remove(drop.item());
            }
        }
        Set<ResourceLocation> hidden = new LinkedHashSet<>(raw);
        for (ResourceLocation item : raw) {
            ItemDefs.ItemDef def = ItemDefs.byId(item.getPath());
            if (def != null && def.cookedId() != null) hidden.add(AlexsButchery.id(def.cookedId()));
        }
        return hidden;
    }

    /** Our items whose every native drop is a group drop an unconditional provider answers, by the rules alone. */
    private static Set<ResourceLocation> replacedEverywhere(Map<Key, Resolution> resolutions) {
        Map<ResourceLocation, Boolean> replaced = new LinkedHashMap<>();
        for (MobDef def : MobDefs.all()) {
            for (Map.Entry<String, List<Drop>> stage : def.drops().entrySet()) {
                for (Drop drop : stage.getValue()) {
                    if (!drop.item().getNamespace().equals(AlexsButchery.MOD_ID)) continue;
                    boolean gone = false;
                    if (drop.group() != null) {
                        Resolution r = resolutions.get(new Key(def.entity(), drop.group(), stage.getKey()));
                        gone = r != null && r.chain().get(0) instanceof Provided p && !p.conditional();
                    }
                    replaced.merge(drop.item(), gone, Boolean::logicalAnd);
                }
            }
        }
        Set<ResourceLocation> gone = new LinkedHashSet<>();
        replaced.forEach((item, replacedHere) -> {
            if (replacedHere) gone.add(item);
        });
        return gone;
    }

    private static Map<String, CompatState.ProviderStatus> providers(Map<ResourceLocation, List<Loaded>> byEntity, ProfileSet profiles, CompatPolicy policy) {
        Set<String> ids = new LinkedHashSet<>(CompatDefs.DEFAULT_PRIORITY);
        ids.addAll(policy.providerPriority());
        for (CompatProfile p : profiles.profiles().values()) ids.add(p.provider());
        Map<String, CompatState.ProviderStatus> out = new LinkedHashMap<>();
        for (String id : ids) {
            int active = 0;
            for (List<Loaded> list : byEntity.values()) for (Loaded l : list) if (l.active() && l.profile().provider().equals(id)) active++;
            out.put(id, new CompatState.ProviderStatus(id, ProviderNames.installed(id), ProviderNames.version(id), policy.enabled(id), active));
        }
        return out;
    }

    /** Every creature's stages as the loaded tables and these rules describe them (see {@link LootSummary}). */
    private static Map<String, List<CompatView.StageView>> stageViews(MinecraftServer server, Map<Key, Resolution> resolutions) {
        LootSummary summary = new LootSummary(server.getLootData(), key -> resolutions.getOrDefault(key, CompatState.NATIVE_ONLY));
        Map<String, List<CompatView.StageView>> mobs = new LinkedHashMap<>();
        for (MobDef def : MobDefs.all()) {
            List<CompatView.StageView> stages = new ArrayList<>();
            for (Stages.Action action : Stages.actions(def)) {
                String stage = action.table(def);
                LootSummary.Summary s = summary.table(def.stageTable(stage));
                stages.add(new CompatView.StageView(stage, s.custom(), s.outputs()));
            }
            mobs.put(def.id(), stages);
        }
        return mobs;
    }

    private static CompatView view(Map<String, List<CompatView.StageView>> mobs, Set<ResourceLocation> hidden,
                                   Map<String, CompatState.ProviderStatus> providers) {
        List<CompatView.ProviderView> providerViews = new ArrayList<>();
        for (CompatState.ProviderStatus p : providers.values())
            providerViews.add(new CompatView.ProviderView(p.provider(), ProviderNames.name(p.provider()), p.version(), p.installed(), p.enabled() && p.activeProfiles() > 0));
        return CompatView.create(mobs, hidden, providerViews);
    }

    @Nullable
    static Item item(ResourceLocation id) {
        if (!ForgeRegistries.ITEMS.containsKey(id)) return null;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? null : item;
    }

    private CompatResolver() {}
}
