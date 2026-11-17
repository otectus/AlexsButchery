package com.otectus.alexsbutchery.def;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.profile.CompatProfile;
import com.otectus.alexsbutchery.compat.profile.CountRange;
import com.otectus.alexsbutchery.compat.profile.GroupSpec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The built-in compatibility data, audited against Alex's Mobs Delight 1.1.0 and Alex's Delight 1.5 (Forge 1.20.1).
 * Datagen writes it out; at runtime only the loaded data counts, so packs can replace or disable any of it.
 *
 * <ul>
 *   <li>Species yield profiles ({@code data/alexsbutchery/alexsbutchery/compat_profiles/<provider>/<mob>.json}): which
 *   addon item a creature's meat (or shell, tail, tentacles, whole fish) becomes, in what quantity, and which death
 *   drops count as the same product. Rules are per species: no shared input (reptile meat, Butchery steaks, game bird,
 *   tropical fish) is ever converted for every creature.</li>
 *   <li>Context-free equivalences: our own items that are the same product as an addon item whatever creature they came
 *   from, offered as one-way 1:1 crafting conversions (raw to raw, cooked to cooked) for stacks made before the addon
 *   was installed.</li>
 *   <li>Equivalence tag entries: addon cuts of the same creature and size joining the other addon's tag for that
 *   product, so both addons' dishes accept them.</li>
 * </ul>
 *
 * Yield budgets: {@code native_group} where the addon item is a portion like ours (bear, crocodile, anaconda, seal,
 * whale, tusklin, froststalker, turtle, octopus tentacles, Alex's Delight bunfungus, whole fish). Explicit budgets
 * where the units differ: a bison gives 4-7 whole bison meat (1-2, 2-3, 1-2) instead of 8-14 Butchery steaks; an emu,
 * seagull or hummingbird is one whole bird; a bunfungus has 2-3 Alex's Mobs Delight legs; a mantis shrimp has one
 * tail, coloured by its saved Variant. Rationale and food values: docs/compat-profiles.md.
 */
public final class CompatDefs {
    public static final String AMD = "alexsmobsdelight";
    public static final String AD = "alexsdelight";
    /** Providers the defaults know, in the default {@code compat.providerPriority} order. */
    public static final List<String> DEFAULT_PRIORITY = List.of(AMD, AD);

    /** One-way 1:1 conversion of our item into another mod's equivalent item. */
    public record Equivalence(String provider, String ours, ResourceLocation theirs) {}

    /** {@code item} joins the item tag {@code tag}; written with {@code required: false} and {@code replace: false}. */
    public record TagJoin(ResourceLocation tag, ResourceLocation item) {}

    private static final Map<ResourceLocation, CompatProfile> PROFILES = new LinkedHashMap<>();
    private static final List<Equivalence> EQUIVALENCES = new ArrayList<>();
    private static final List<TagJoin> TAG_JOINS = new ArrayList<>();

    static {
        // ---- Alex's Mobs Delight ------------------------------------------------------------------------------------
        for (String bear : List.of("grizzly_bear", "dropbear", "sea_bear"))
            profile(bear, AMD, Drop.MEAT, nativeGroup(amd("raw_bear_meat")), amd("raw_bear_meat"), amd("cooked_bear_meat"));
        for (String crocodilian : List.of("crocodile", "caiman"))
            profile(crocodilian, AMD, Drop.MEAT, nativeGroup(amd("raw_crocodile_meat")), amd("raw_crocodile_meat"), amd("cooked_crocodile_meat"));
        profile("anaconda", AMD, Drop.MEAT, nativeGroup(amd("raw_anaconda")), amd("raw_anaconda"), amd("cooked_anaconda"));
        profile("bison", AMD, Drop.MEAT, bisonBudget(amd("raw_bison_meat")), amd("raw_bison_meat"), amd("cooked_bison_meat"));
        profile("emu", AMD, Drop.MEAT, wholeBird(amd("raw_emu")), amd("raw_emu"), amd("cooked_emu"));
        profile("froststalker", AMD, Drop.MEAT, nativeGroup(amd("raw_froststalker_meat")), amd("raw_froststalker_meat"), amd("cooked_froststalker_meat"));
        profile("tusklin", AMD, Drop.MEAT, nativeGroup(amd("raw_tusklin_meat")), amd("raw_tusklin_meat"), amd("cooked_tusklin_meat"));
        profile("seal", AMD, Drop.MEAT, nativeGroup(amd("seal_meat")), amd("seal_meat"), amd("cooked_seal_meat"));
        for (String whale : List.of("orca", "cachalot_whale"))
            profile(whale, AMD, Drop.MEAT, nativeGroup(amd("whale_meat")), amd("whale_meat"), amd("cooked_whale_meat"));
        profile("bunfungus", AMD, Drop.MEAT, perStage(amd("raw_bunfungus_leg"),
                MobDef.CUT_1, CountRange.exactly(1), MobDef.CUT_2, CountRange.exactly(1), MobDef.CUT_3, CountRange.between(0, 1)),
                amd("raw_bunfungus_leg"), amd("cooked_bunfungus_leg"));
        profile("seagull", AMD, Drop.MEAT, wholeBird(amd("raw_seagull")), amd("raw_seagull"), amd("cooked_seagull"));
        profile("hummingbird", AMD, Drop.MEAT, wholeBird(amd("raw_hummingbird")), amd("raw_hummingbird"), amd("cooked_hummingbird"));
        profile(new Builder("terrapin", AMD)
                .group(Drop.MEAT, nativeGroup(amd("raw_turtle_meat")), amd("raw_turtle_meat"), amd("cooked_turtle_meat"))
                .group(Drop.SHELL, nativeGroup(amd("terrapin_shell")), amd("terrapin_shell")));
        profile(new Builder("alligator_snapping_turtle", AMD)
                .group(Drop.MEAT, nativeGroup(amd("raw_turtle_meat")), amd("raw_turtle_meat"), amd("cooked_turtle_meat"))
                .group(Drop.SHELL, nativeGroup(amd("alligator_snapping_turtle_shell")), amd("alligator_snapping_turtle_shell")));
        Map<Integer, ResourceLocation> tails = new LinkedHashMap<>();
        tails.put(0, amd("mantis_shrimp_tail_green"));
        tails.put(1, amd("mantis_shrimp_tail_red"));
        tails.put(2, amd("mantis_shrimp_tail_lime"));
        tails.put(3, amd("mantis_shrimp_tail_white"));
        profile("mantis_shrimp", AMD, Drop.TAIL,
                new GroupSpec(GroupSpec.CountMode.PER_STAGE, null, new GroupSpec.Variants("Variant", tails), List.of(),
                        Map.of(MobDef.CUT_1, CountRange.exactly(1)), List.of()),
                amd("mantis_shrimp_tail_green"), amd("mantis_shrimp_tail_red"), amd("mantis_shrimp_tail_lime"),
                amd("mantis_shrimp_tail_white"), amd("cooked_mantis_shrimp_tail"));
        profile("mimic_octopus", AMD, Drop.TENTACLES, nativeGroup(amd("mimic_octopus_tentacle")), amd("mimic_octopus_tentacle"), amd("cooked_octopus_tentacle"));
        profile("devils_hole_pupfish", AMD, Drop.FISH, nativeGroup(amd("raw_devils_hole_pupfish")), amd("raw_devils_hole_pupfish"), amd("cooked_devils_hole_pupfish"));
        profile("mudskipper", AMD, Drop.FISH, nativeGroup(amd("raw_mudskipper")), amd("raw_mudskipper"), amd("cooked_mudskipper"));

        // ---- Alex's Delight (registers no bear or crocodile meat) -------------------------------------------------
        profile("bison", AD, Drop.MEAT, bisonBudget(ad("raw_bison")), ad("raw_bison"), ad("cooked_bison"));
        profile("bunfungus", AD, Drop.MEAT, nativeGroup(ad("raw_bunfungus")), ad("raw_bunfungus"), ad("cooked_bunfungus"));

        // ---- Context-free equivalences: same product whatever the source creature ----------------------------------
        for (String family : List.of("bear_meat", "froststalker_meat", "tusklin_meat")) {
            equivalence(AMD, "raw_" + family, amd("raw_" + family));
            equivalence(AMD, "cooked_" + family, amd("cooked_" + family));
        }
        equivalence(AMD, "raw_seal_meat", amd("seal_meat"));
        equivalence(AMD, "cooked_seal_meat", amd("cooked_seal_meat"));
        equivalence(AMD, "raw_whale_meat", amd("whale_meat"));
        equivalence(AMD, "cooked_whale_meat", amd("cooked_whale_meat"));
        equivalence(AMD, "terrapin_shell", amd("terrapin_shell"));
        equivalence(AMD, "snapping_turtle_shell", amd("alligator_snapping_turtle_shell"));
        equivalence(AD, "raw_bunfungus_meat", ad("raw_bunfungus"));
        equivalence(AD, "cooked_bunfungus_meat", ad("cooked_bunfungus"));

        // ---- Equal cuts of the same creature accepted by the other addon's product tags ---------------------------
        TAG_JOINS.add(new TagJoin(amd("raw_kangaroo_meat"), ad("kangaroo_shank")));
        TAG_JOINS.add(new TagJoin(amd("cooked_kangaroo_meat"), ad("cooked_kangaroo_shank")));
        TAG_JOINS.add(new TagJoin(ad("cooked_kangaroo"), amd("cooked_kangaroo_meat_slice")));
        TAG_JOINS.add(new TagJoin(amd("raw_moose_meat"), ad("loose_moose_rib")));
        TAG_JOINS.add(new TagJoin(amd("cooked_moose_meat"), ad("cooked_loose_moose_rib")));
        TAG_JOINS.add(new TagJoin(ad("cooked_bunfungus"), amd("cooked_bunfungus_leg")));
    }

    // ---- shapes ------------------------------------------------------------------------------------------------------

    private static GroupSpec nativeGroup(ResourceLocation item) {
        return new GroupSpec(GroupSpec.CountMode.NATIVE_GROUP, item, null, List.of(), Map.of(), List.of());
    }

    private static GroupSpec perStage(ResourceLocation item, Object... stageCounts) {
        Map<String, CountRange> counts = new LinkedHashMap<>();
        for (int i = 0; i < stageCounts.length; i += 2) counts.put((String) stageCounts[i], (CountRange) stageCounts[i + 1]);
        return new GroupSpec(GroupSpec.CountMode.PER_STAGE, item, null, List.of(), counts, List.of());
    }

    /** 4-7 whole bison meat in place of 8-14 Butchery steaks; the fur, hide, fat, hooves, bones and organs stay. */
    private static GroupSpec bisonBudget(ResourceLocation item) {
        return perStage(item, MobDef.CUT_1, CountRange.between(1, 2), MobDef.CUT_2, CountRange.between(2, 3), MobDef.CUT_3, CountRange.between(1, 2));
    }

    /** The addon item is the whole bird: one per carcass, at the first cut. */
    private static GroupSpec wholeBird(ResourceLocation item) {
        return perStage(item, MobDef.CUT_1, CountRange.exactly(1), MobDef.CUT_2, CountRange.exactly(0));
    }

    // ---- registration ------------------------------------------------------------------------------------------------

    private static final class Builder {
        final String mob;
        final String provider;
        final Map<String, GroupSpec> groups = new LinkedHashMap<>();

        Builder(String mob, String provider) {
            this.mob = mob;
            this.provider = provider;
        }

        Builder group(String name, GroupSpec spec, ResourceLocation... deathEquivalents) {
            groups.put(name, new GroupSpec(spec.countMode(), spec.item(), spec.variants(), spec.stages(), spec.counts(), List.of(deathEquivalents)));
            return this;
        }
    }

    private static void profile(String mob, String provider, String group, GroupSpec spec, ResourceLocation... deathEquivalents) {
        profile(new Builder(mob, provider).group(group, spec, deathEquivalents));
    }

    private static void profile(Builder b) {
        MobDef def = MobDefs.byId(b.mob);
        if (def == null) throw new IllegalStateException("No mob " + b.mob);
        for (String group : b.groups.keySet()) {
            if (!def.groups().contains(group)) throw new IllegalStateException(b.mob + " has no native " + group + " group");
        }
        ResourceLocation id = profileId(b.provider, b.mob);
        if (PROFILES.put(id, new CompatProfile(def.entity(), b.provider, List.of(b.provider), b.groups)) != null)
            throw new IllegalStateException("Duplicate profile " + id);
    }

    private static void equivalence(String provider, String ours, ResourceLocation theirs) {
        if (ItemDefs.byId(ours) == null) throw new IllegalStateException("No item " + ours);
        EQUIVALENCES.add(new Equivalence(provider, ours, theirs));
    }

    /** The resource id of a built-in profile: {@code alexsbutchery:<provider>/<mob>}. */
    public static ResourceLocation profileId(String provider, String mob) {
        return AlexsButchery.id(provider + "/" + mob);
    }

    public static Map<ResourceLocation, CompatProfile> profiles() {
        return Collections.unmodifiableMap(PROFILES);
    }

    public static List<Equivalence> equivalences() {
        return Collections.unmodifiableList(EQUIVALENCES);
    }

    public static List<TagJoin> tagJoins() {
        return Collections.unmodifiableList(TAG_JOINS);
    }

    /** The recipe id of a context-free conversion: {@code alexsbutchery:compat/<provider>/<our item>}. */
    public static ResourceLocation equivalenceRecipe(Equivalence e) {
        return AlexsButchery.id("compat/" + e.provider() + "/" + e.ours());
    }

    @Nullable
    public static String providerOfEquivalenceRecipe(ResourceLocation recipe) {
        if (!recipe.getNamespace().equals(AlexsButchery.MOD_ID) || !recipe.getPath().startsWith("compat/")) return null;
        String rest = recipe.getPath().substring("compat/".length());
        int slash = rest.indexOf('/');
        return slash < 0 ? null : rest.substring(0, slash);
    }

    private static ResourceLocation amd(String path) {
        return new ResourceLocation(AMD, path);
    }

    private static ResourceLocation ad(String path) {
        return new ResourceLocation(AD, path);
    }

    private CompatDefs() {}
}
