package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.CompatEvents;
import com.otectus.alexsbutchery.compat.CompatPolicy;
import com.otectus.alexsbutchery.compat.CompatState;
import com.otectus.alexsbutchery.compat.profile.CompatProfile;
import com.otectus.alexsbutchery.compat.profile.ProfileCodec;
import com.otectus.alexsbutchery.compat.profile.ProfileLoader;
import com.otectus.alexsbutchery.compat.profile.ProfileSet;
import com.otectus.alexsbutchery.def.CompatDefs;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.otectus.alexsbutchery.test.CompatTestSupport.AD;
import static com.otectus.alexsbutchery.test.CompatTestSupport.AMD;
import static com.otectus.alexsbutchery.test.CompatTestSupport.ad;
import static com.otectus.alexsbutchery.test.CompatTestSupport.amd;
import static com.otectus.alexsbutchery.test.CompatTestSupport.loadedProfiles;
import static com.otectus.alexsbutchery.test.CompatTestSupport.state;

/** The rules themselves: loading, validation, provider order, overrides, ambiguity and malformed data. */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompatRuleTests {
    private static final ResourceLocation BISON = new ResourceLocation("alexsmobs:bison");
    private static final ResourceLocation CROCODILE = new ResourceLocation("alexsmobs:crocodile");
    private static final ResourceLocation KOMODO = new ResourceLocation("alexsmobs:komodo_dragon");

    /** The shipped profile files are exactly the built-in table, parse cleanly, and are active only with their mods. */
    @GameTest(template = "empty")
    public static void builtInProfilesLoadExactly(GameTestHelper h) {
        ProfileSet loaded = loadedProfiles(h);
        h.assertTrue(!loaded.malformed(), "no profile file errors: " + loaded.errors());
        for (Map.Entry<ResourceLocation, CompatProfile> e : CompatDefs.profiles().entrySet()) {
            CompatProfile file = loaded.profiles().get(e.getKey());
            h.assertTrue(file != null, "profile " + e.getKey() + " is loaded");
            h.assertTrue(ProfileCodec.toJson(file).equals(ProfileCodec.toJson(e.getValue())), e.getKey() + " round-trips to the built-in definition");
        }
        CompatState state = state(h);
        for (CompatState.ProviderStatus p : state.providers().values()) {
            boolean installed = ModList.get().isLoaded(p.provider());
            h.assertTrue(p.installed() == installed, p.provider() + " installed=" + installed);
            if (!installed) h.assertTrue(p.activeProfiles() == 0, p.provider() + " has no active profile while absent");
        }
        if (amd()) h.assertTrue(state.providers().get(AMD).activeProfiles() == 22, "22 Alex's Mobs Delight profiles active: " + state.providers().get(AMD));
        if (ad()) h.assertTrue(state.providers().get(AD).activeProfiles() == 2, "2 Alex's Delight profiles active: " + state.providers().get(AD));
        h.assertTrue(state.count(CompatState.Severity.ERROR) == 0, "no errors: " + state.diagnostics());
        h.succeed();
    }

    /** Every mapping's item exists for the installed addons, and every group at every stage ends in its native drops. */
    @GameTest(template = "empty")
    public static void everyDeclaredMappingResolves(GameTestHelper h) {
        CompatState state = state(h);
        for (MobDef def : MobDefs.all()) {
            for (String group : def.groups()) {
                for (String stage : def.stagesOf(group)) {
                    CompatState.Resolution r = state.resolution(def.entity(), group, stage);
                    h.assertTrue(r.chain().get(r.chain().size() - 1) == CompatState.Native.INSTANCE, def.id() + " " + group + " " + stage + " ends in native");
                    for (CompatState.Choice c : r.chain()) {
                        if (!(c instanceof CompatState.Provided p)) continue;
                        h.assertTrue(ModList.get().isLoaded(p.provider()), p.provider() + " active only when installed");
                        if (p.item() != null) h.assertTrue(p.item() != net.minecraft.world.item.Items.AIR, "a real item for " + def.id());
                        else h.assertTrue(p.variantItems().size() == p.spec().variants().items().size(), "every variant resolved for " + def.id());
                    }
                }
            }
        }
        for (CompatProfile profile : CompatDefs.profiles().values()) {
            if (!ModList.get().isLoaded(profile.provider())) continue;
            for (var spec : profile.groups().values()) {
                for (ResourceLocation id : spec.outputs()) h.assertTrue(CompatTestSupport.itemOrNull(id.toString()) != null, id + " is registered");
                for (ResourceLocation id : spec.deathEquivalents()) h.assertTrue(CompatTestSupport.itemOrNull(id.toString()) != null, id + " is registered");
            }
        }
        h.succeed();
    }

    /** The server order, a reversed order, a disabled provider and native mode each choose as documented. */
    @GameTest(template = "empty")
    public static void providerOrderAndModes(GameTestHelper h) {
        CompatPolicy base = state(h).policy();
        for (CompatPolicy policy : List.of(base, base.withPriority(List.of(AD, AMD)), base.withDisabled(Set.of(AMD)), base.withDisabled(Set.of(AD)),
                base.withPreferExternal(false), base.withPriority(List.of()))) {
            CompatState rules = CompatTestSupport.with(h, policy);
            for (String mob : List.of("grizzly_bear", "crocodile", "bison", "bunfungus", "seal")) {
                MobDef def = CompatTestSupport.mob(mob);
                String expected = CompatTestSupport.expectedMeatProvider(mob, policy);
                for (String stage : def.stagesOf("meat")) {
                    CompatState.Choice first = rules.resolution(def.entity(), "meat", stage).chain().get(0);
                    String got = first instanceof CompatState.Provided p ? p.provider() : "native";
                    if (first instanceof CompatState.Provided p && p.spec().counts().containsKey(stage) && p.spec().counts().get(stage).max() == 0) continue;
                    h.assertTrue(got.equals(expected), mob + " " + stage + " with " + policy.describe() + ": expected " + expected + ", got " + got);
                }
            }
        }
        h.succeed();
    }

    /** Selection files, an explicit disable, ambiguous rules and a third-party provider, as the schema guide documents them. */
    @GameTest(template = "empty")
    public static void packFilesOverrideAsDocumented(GameTestHelper h) {
        Map<ResourceLocation, String> files = new LinkedHashMap<>();
        ProfileSet base = loadedProfiles(h);
        // Select Alex's Delight for bison, keep native crocodile, and add a third-party komodo provider.
        files.put(new ResourceLocation("pack:select_ad_bison"), "{\"schema_version\":1,\"entity\":\"alexsmobs:bison\",\"groups\":[\"meat\"],\"provider_priority\":[\"alexsdelight\"]}");
        files.put(new ResourceLocation("pack:native_crocodile"), "{\"schema_version\":1,\"entity\":\"alexsmobs:crocodile\",\"provider_priority\":[\"native\"]}");
        files.put(new ResourceLocation("pack:komodo_meat"), "{\"schema_version\":1,\"entity\":\"alexsmobs:komodo_dragon\",\"provider\":\"examplefood\","
                + "\"required_mods\":[\"minecraft\"],\"groups\":{\"meat\":{\"item\":\"minecraft:mutton\",\"count_mode\":\"native_group\","
                + "\"death_equivalents\":[\"minecraft:mutton\",\"minecraft:cooked_mutton\"]}}}");
        ProfileSet set = merge(base, ProfileLoader.parse(files, Map.of()));
        CompatState rules = CompatTestSupport.with(h, set, state(h).policy());
        String bison = provider(rules, BISON, "meat", MobDef.CUT_1);
        h.assertTrue(bison.equals(ad() ? AD : amd() ? AMD : "native"), "selection puts Alex's Delight first for bison, then the server order: " + bison);
        h.assertTrue(provider(rules, CROCODILE, "meat", MobDef.CUT_2).equals("native"), "\"native\" first keeps native crocodile meat");
        h.assertTrue(provider(rules, KOMODO, "meat", MobDef.CUT_1).equals("examplefood"), "an unlisted provider alone is used after the listed ones");
        h.assertTrue(rules.suppression(KOMODO).contains(net.minecraft.world.item.Items.MUTTON), "and its death equivalents are removed at a carcass kill");
        h.assertTrue(rules.suppression(KOMODO).contains(net.minecraft.world.item.Items.COOKED_MUTTON), "raw and cooked");

        // A disable file replacing a built-in profile's id removes it, whatever the server order says.
        Map<ResourceLocation, String> disable = Map.of(CompatDefs.profileId(AMD, "grizzly_bear"), "{\"schema_version\":1,\"disabled\":true}");
        ProfileSet disabled = merge(base, ProfileLoader.parse(disable, Map.of()));
        h.assertTrue(!disabled.profiles().containsKey(CompatDefs.profileId(AMD, "grizzly_bear")), "the disabled id has no profile");
        CompatState withoutBear = CompatTestSupport.with(h, disabled, state(h).policy());
        h.assertTrue(provider(withoutBear, new ResourceLocation("alexsmobs:grizzly_bear"), "meat", MobDef.CUT_1).equals("native"),
                "a disabled profile leaves native bear meat");
        h.assertTrue(provider(withoutBear, new ResourceLocation("alexsmobs:dropbear"), "meat", MobDef.CUT_1).equals(amd() ? AMD : "native"),
                "other bears keep their own profiles");

        // Two profiles for the same provider and creature, or two unlisted providers, are ambiguous: neither is used.
        Map<ResourceLocation, String> clash = new LinkedHashMap<>();
        clash.put(new ResourceLocation("pack:komodo_a"), komodo("one", "minecraft:mutton"));
        clash.put(new ResourceLocation("pack:komodo_b"), komodo("two", "minecraft:porkchop"));
        CompatState ambiguous = CompatTestSupport.with(h, merge(base, ProfileLoader.parse(clash, Map.of())), state(h).policy());
        h.assertTrue(provider(ambiguous, KOMODO, "meat", MobDef.CUT_1).equals("native"), "two unlisted providers at one priority: native");
        h.assertTrue(ambiguous.count(CompatState.Severity.ERROR) > 0, "and an error says why");
        Map<ResourceLocation, String> twin = new LinkedHashMap<>();
        twin.put(new ResourceLocation("pack:komodo_a"), komodo("one", "minecraft:mutton"));
        twin.put(new ResourceLocation("pack:komodo_c"), komodo("one", "minecraft:porkchop"));
        CompatState twins = CompatTestSupport.with(h, merge(base, ProfileLoader.parse(twin, Map.of())), state(h).policy().withPriority(List.of("one")));
        h.assertTrue(provider(twins, KOMODO, "meat", MobDef.CUT_1).equals("native"), "two files for one provider and creature: native");

        // A missing item in an active profile invalidates only that profile, with the resource, creature and item named.
        Map<ResourceLocation, String> broken = Map.of(new ResourceLocation("pack:komodo_missing"), komodo("ghost", "minecraft:not_an_item"));
        CompatState missing = CompatTestSupport.with(h, merge(base, ProfileLoader.parse(broken, Map.of())), state(h).policy());
        h.assertTrue(provider(missing, KOMODO, "meat", MobDef.CUT_1).equals("native"), "a missing item falls back to native");
        h.assertTrue(missing.diagnostics().stream().anyMatch(d -> d.severity() == CompatState.Severity.ERROR && d.source().equals("pack:komodo_missing")
                && d.message().contains("minecraft:not_an_item") && d.message().contains("komodo_dragon")), "the error names resource, creature and item");
        h.succeed();
    }

    /** Malformed files are errors with their path; a reload with any of them keeps the previous rules intact. */
    @GameTest(template = "empty")
    public static void malformedReloadKeepsTheLastValidRules(GameTestHelper h) {
        Map<ResourceLocation, String> bad = new LinkedHashMap<>();
        bad.put(new ResourceLocation("pack:syntax"), "{\"schema_version\":1,");
        bad.put(new ResourceLocation("pack:unknown_key"), "{\"schema_version\":1,\"entity\":\"alexsmobs:bison\",\"provider\":\"x\",\"groups\":{\"meat\":"
                + "{\"item\":\"minecraft:beef\",\"count_mode\":\"native_group\",\"count\":2}}}");
        bad.put(new ResourceLocation("pack:bad_stage"), "{\"schema_version\":1,\"entity\":\"alexsmobs:bison\",\"provider\":\"x\",\"groups\":{\"meat\":"
                + "{\"item\":\"minecraft:beef\",\"count_mode\":\"per_stage\",\"counts\":{\"cut_9\":2}}}}");
        bad.put(new ResourceLocation("pack:bad_version"), "{\"schema_version\":2,\"disabled\":true}");
        bad.put(new ResourceLocation("pack:bad_range"), "{\"schema_version\":1,\"entity\":\"alexsmobs:bison\",\"provider\":\"x\",\"groups\":{\"meat\":"
                + "{\"item\":\"minecraft:beef\",\"count_mode\":\"per_stage\",\"counts\":{\"cut_1\":{\"min\":3,\"max\":1}}}}}");
        ProfileSet parsed = ProfileLoader.parse(bad, Map.of());
        h.assertTrue(parsed.errors().size() == 5, "each malformed file is an error: " + parsed.errors());
        h.assertTrue(parsed.errors().stream().anyMatch(e -> e.contains("groups.meat.count") && e.contains("unknown key")), "unknown keys are named");
        h.assertTrue(parsed.errors().stream().anyMatch(e -> e.contains("cut_9")), "unknown stages are named");
        ProfileSet withErrors = merge(loadedProfiles(h), parsed);
        CompatState before = state(h);
        var server = h.getLevel().getServer();
        try {
            CompatState after = CompatEvents.rebuild(server, withErrors, "test: malformed reload");
            h.assertTrue(after.profiles() == before.profiles() || after.profiles().equals(before.profiles()), "the previous profile set stays in force");
            h.assertTrue(after.diagnostics().stream().anyMatch(d -> d.message().contains("previous rules stay")), "and the rejection is reported");
            h.assertTrue(after.view().digest().equals(before.view().digest()), "clients see the same rules");
        } finally {
            CompatEvents.rebuild(server, "test: restore");
        }
        h.assertTrue(state(h).view().digest().equals(before.view().digest()), "the restored rules match the original");
        h.succeed();
    }

    /** The datapack examples documented in docs/compat-profiles.md parse with the real parser and do what they say. */
    @GameTest(template = "empty")
    public static void documentedExamplesWork(GameTestHelper h) {
        Map<String, String> examples = Map.of(
                "select_alexsdelight_bison", "data/examplepack/alexsbutchery/compat_profiles/select_alexsdelight_bison.json",
                "keep_native_crocodile", "data/examplepack/alexsbutchery/compat_profiles/keep_native_crocodile.json",
                "third_party_komodo", "data/examplepack/alexsbutchery/compat_profiles/examplefood/komodo_dragon.json",
                "disable_grizzly", "data/alexsbutchery/alexsbutchery/compat_profiles/alexsmobsdelight/grizzly_bear.json");
        ProfileSet base = loadedProfiles(h);
        for (Map.Entry<String, String> example : examples.entrySet()) {
            String path = "/compat-examples/" + example.getKey() + "/" + example.getValue();
            String json = read(path);
            String file = example.getValue();
            String ns = file.split("/")[1];
            String rel = file.substring(file.indexOf("compat_profiles/") + "compat_profiles/".length(), file.length() - ".json".length());
            ProfileSet parsed = ProfileLoader.parse(Map.of(new ResourceLocation(ns, rel), json), Map.of());
            h.assertTrue(!parsed.malformed(), example.getKey() + " parses: " + parsed.errors());
            CompatState rules = CompatTestSupport.with(h, merge(base, parsed), state(h).policy());
            switch (example.getKey()) {
                case "select_alexsdelight_bison" -> h.assertTrue(provider(rules, BISON, "meat", MobDef.CUT_2).equals(ad() ? AD : amd() ? AMD : "native"), example.getKey());
                case "keep_native_crocodile" -> h.assertTrue(provider(rules, CROCODILE, "meat", MobDef.CUT_1).equals("native"), example.getKey());
                case "third_party_komodo" -> h.assertTrue(provider(rules, KOMODO, "meat", MobDef.CUT_1).equals("native"),
                        "the example's provider mod is not installed, so native: " + provider(rules, KOMODO, "meat", MobDef.CUT_1));
                case "disable_grizzly" -> h.assertTrue(provider(rules, new ResourceLocation("alexsmobs:grizzly_bear"), "meat", MobDef.CUT_1).equals("native"), example.getKey());
                default -> throw new GameTestAssertException(example.getKey());
            }
        }
        // The stage-table example is an ordinary loot table: it parses and contains no compat_yield entry.
        String table = read("/compat-examples/plain_stage_table/data/alexsbutchery/loot_tables/carcass/crocodile/cut_1.json");
        var parsedTable = CompatYieldTests.table(h, table);
        h.assertTrue(parsedTable != null && !table.contains("compat_yield"), "the plain stage table example is a plain loot table");
        h.succeed();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static String komodo(String provider, String item) {
        return "{\"schema_version\":1,\"entity\":\"alexsmobs:komodo_dragon\",\"provider\":\"" + provider + "\",\"groups\":{\"meat\":"
                + "{\"item\":\"" + item + "\",\"count_mode\":\"native_group\"}}}";
    }

    private static String provider(CompatState rules, ResourceLocation entity, String group, String stage) {
        CompatState.Choice first = rules.resolution(entity, group, stage).chain().get(0);
        return first instanceof CompatState.Provided p ? p.provider() : "native";
    }

    /** {@code extra} files layered over {@code base}, as a higher-priority pack: same ids replace, disables remove. */
    static ProfileSet merge(ProfileSet base, ProfileSet extra) {
        Map<ResourceLocation, CompatProfile> profiles = new LinkedHashMap<>(base.profiles());
        var selections = new LinkedHashMap<>(base.selections());
        var disabled = new java.util.LinkedHashSet<>(base.disabled());
        for (ResourceLocation id : extra.profiles().keySet()) { selections.remove(id); disabled.remove(id); }
        for (ResourceLocation id : extra.selections().keySet()) { profiles.remove(id); disabled.remove(id); }
        for (ResourceLocation id : extra.disabled()) { profiles.remove(id); selections.remove(id); }
        profiles.putAll(extra.profiles());
        selections.putAll(extra.selections());
        disabled.addAll(extra.disabled());
        var sources = new LinkedHashMap<>(base.sources());
        sources.putAll(extra.sources());
        var errors = new java.util.ArrayList<>(base.errors());
        errors.addAll(extra.errors());
        return new ProfileSet(profiles, selections, disabled, sources, errors);
    }

    private static String read(String path) {
        try (InputStream in = CompatRuleTests.class.getResourceAsStream(path)) {
            if (in == null) throw new GameTestAssertException("missing documented example " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GameTestAssertException("cannot read " + path + ": " + e);
        }
    }
}
