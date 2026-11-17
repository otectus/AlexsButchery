package com.otectus.alexsbutchery.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.CompatPolicy;
import com.otectus.alexsbutchery.compat.CompatState;
import com.otectus.alexsbutchery.compat.CompatView;
import com.otectus.alexsbutchery.def.Drop;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import net.mcreator.butchery.configuration.ButcheryconfigConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.otectus.alexsbutchery.test.CompatTestSupport.AD;
import static com.otectus.alexsbutchery.test.CompatTestSupport.AMD;
import static com.otectus.alexsbutchery.test.CompatTestSupport.amd;
import static com.otectus.alexsbutchery.test.CompatTestSupport.ad;
import static com.otectus.alexsbutchery.test.CompatTestSupport.drops;
import static com.otectus.alexsbutchery.test.CompatTestSupport.expectedMeatProvider;
import static com.otectus.alexsbutchery.test.CompatTestSupport.item;
import static com.otectus.alexsbutchery.test.CompatTestSupport.key;
import static com.otectus.alexsbutchery.test.CompatTestSupport.keys;
import static com.otectus.alexsbutchery.test.CompatTestSupport.mob;
import static com.otectus.alexsbutchery.test.CompatTestSupport.state;
import static com.otectus.alexsbutchery.test.TestSupport.ORIGIN;
import static com.otectus.alexsbutchery.test.TestSupport.alexsMob;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryItem;
import static com.otectus.alexsbutchery.test.TestSupport.clearItems;

/**
 * What carcasses yield with each set of cooking addons: real kills processed stage by stage, rule by rule. Every test
 * computes its expectation from the loaded mods, so the same tests prove the core, Farmer's Delight, Alex's Mobs
 * Delight, Alex's Delight and both-addons profiles; policy variants run synchronously under temporary rules.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompatYieldTests {
    private static final Set<String> NATIVE_BEAR = Set.of("alexsbutchery:raw_bear_meat");
    private static final Set<String> NATIVE_REPTILE = Set.of("alexsbutchery:raw_reptile_meat");
    private static final Set<String> STEAKS = Set.of("butchery:raw_chuck_steak", "butchery:raw_ribeye_steak", "butchery:raw_sirloin_steak",
            "butchery:raw_tbone_steak", "butchery:raw_rump_steak");
    private static final Set<String> CUT_STAGES = Set.of(MobDef.CUT_1, MobDef.CUT_2, MobDef.CUT_3);

    /** Every meat item any provider could give a creature: what must not appear besides the expected one. */
    private static Set<String> allMeats(String mob) {
        return switch (mob) {
            case "grizzly_bear" -> Set.of("alexsbutchery:raw_bear_meat", "alexsmobsdelight:raw_bear_meat");
            case "crocodile" -> Set.of("alexsbutchery:raw_reptile_meat", "alexsmobsdelight:raw_crocodile_meat");
            case "bison" -> union(STEAKS, Set.of("alexsmobsdelight:raw_bison_meat", "alexsdelight:raw_bison"));
            default -> throw new IllegalArgumentException(mob);
        };
    }

    private static Set<String> expectedMeat(String mob, String provider) {
        return switch (mob + "/" + provider) {
            case "grizzly_bear/native" -> NATIVE_BEAR;
            case "grizzly_bear/alexsmobsdelight" -> Set.of("alexsmobsdelight:raw_bear_meat");
            case "crocodile/native" -> NATIVE_REPTILE;
            case "crocodile/alexsmobsdelight" -> Set.of("alexsmobsdelight:raw_crocodile_meat");
            case "bison/native" -> STEAKS;
            case "bison/alexsmobsdelight" -> Set.of("alexsmobsdelight:raw_bison_meat");
            case "bison/alexsdelight" -> Set.of("alexsdelight:raw_bison");
            default -> throw new IllegalArgumentException(mob + "/" + provider);
        };
    }

    /** Side products that must survive any provider: each creature's hide, fur, scutes, fat, hooves and bones. */
    private static Set<String> sideProducts(String mob) {
        return switch (mob) {
            case "grizzly_bear" -> Set.of("alexsbutchery:grizzly_pelt", "alexsmobs:bear_fur", "butchery:animal_fat", "minecraft:bone", "alexsbutchery:grizzly_bear_head");
            case "crocodile" -> Set.of("alexsbutchery:crocodile_hide", "alexsmobs:crocodile_scute", "butchery:animal_fat", "minecraft:bone", "alexsbutchery:crocodile_head");
            case "bison" -> Set.of("alexsbutchery:bison_hide", "alexsmobs:bison_fur", "butchery:animal_fat", "butchery:hoof", "minecraft:bone", "alexsbutchery:bison_head");
            default -> throw new IllegalArgumentException(mob);
        };
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return out;
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void threeAnimalRegression(GameTestHelper h) {
        CompatState actual = state(h);
        List<CompatPolicy> policies = new ArrayList<>();
        policies.add(actual.policy());
        if (amd() && ad()) policies.add(actual.policy().withPriority(List.of(AD, AMD)));
        policies.add(actual.policy().withPreferExternal(false));
        for (CompatPolicy policy : policies) {
            CompatState rules = policy.equals(actual.policy()) ? actual : CompatTestSupport.with(h, policy);
            CompatState.withState(h.getLevel().getServer(), rules, () -> {
                for (String mobId : List.of("grizzly_bear", "crocodile", "bison")) checkAnimal(h, mobId, policy);
            });
        }
        h.succeed();
    }

    private static void checkAnimal(GameTestHelper h, String mobId, CompatPolicy policy) {
        MobDef def = mob(mobId);
        String provider = expectedMeatProvider(mobId, policy);
        String label = mobId + " with " + policy.describe() + " -> " + provider;
        var player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        clearItems(h);
        LivingEntity victim = (LivingEntity) h.spawn(alexsMob(mobId), ORIGIN);
        TestSupport.kill(h, victim, player);
        CompoundTag snapshot = CompatTestSupport.droppedSnapshot(h, def);
        Map<Item, Integer> death = drops(h);
        h.assertTrue(!CompatTestSupport.anyOf(death, allMeats(mobId)), label + ": no ordinary meat at death, got " + keys(death));
        clearItems(h);
        BlockPos at = CompatTestSupport.placeReady(h, def, ORIGIN.east(2), snapshot);
        Map<String, Map<Item, Integer>> stages = CompatTestSupport.process(h, at, player);
        Set<String> meats = allMeats(mobId);
        Set<String> expected = expectedMeat(mobId, provider);
        int meatTotal = 0;
        for (Map.Entry<String, Map<Item, Integer>> stage : stages.entrySet()) {
            for (Map.Entry<Item, Integer> e : stage.getValue().entrySet()) {
                String id = key(e.getKey());
                if (!meats.contains(id)) continue;
                h.assertTrue(expected.contains(id), label + ": " + stage.getKey() + " gave " + id + ", expected " + expected);
                h.assertTrue(CUT_STAGES.contains(stage.getKey()), label + ": meat outside the cuts at " + stage.getKey());
                meatTotal += e.getValue();
            }
        }
        h.assertTrue(meatTotal > 0, label + ": the cuts gave no meat: " + stages);
        if (mobId.equals("bison") && !provider.equals("native"))
            h.assertTrue(meatTotal >= 4 && meatTotal <= 7, label + ": whole bison meat budget is 4-7, got " + meatTotal);
        if (mobId.equals("bison") && provider.equals("native"))
            h.assertTrue(meatTotal >= 8 && meatTotal <= 14, label + ": Butchery steak assortment is 8-14, got " + meatTotal);
        Map<Item, Integer> all = CompatTestSupport.total(stages);
        for (String side : sideProducts(mobId)) {
            h.assertTrue(all.containsKey(item(side)), label + ": side product " + side + " kept, got " + keys(all));
        }
    }

    /** Shared inputs are never converted: other reptiles, birds and fish keep their own products whatever is installed. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void speciesIsolation(GameTestHelper h) {
        var player = TestSupport.player(h);
        for (String mobId : List.of("rattlesnake", "komodo_dragon")) {
            MobDef def = mob(mobId);
            BlockPos at = CompatTestSupport.placeReady(h, def, ORIGIN, new CompoundTag());
            Map<Item, Integer> all = CompatTestSupport.total(CompatTestSupport.process(h, at, player));
            h.assertTrue(all.containsKey(item("alexsbutchery:raw_reptile_meat")), mobId + " keeps reptile meat: " + keys(all));
            h.assertTrue(all.keySet().stream().map(CompatTestSupport::key).noneMatch(k -> k.contains("crocodile") || k.contains("anaconda")),
                    mobId + " never yields crocodile or anaconda meat: " + keys(all));
        }
        for (String mobId : List.of("roadrunner", "crow")) {
            BlockPos at = CompatTestSupport.placeReady(h, mob(mobId), ORIGIN, new CompoundTag());
            Map<Item, Integer> all = CompatTestSupport.total(CompatTestSupport.process(h, at, player));
            h.assertTrue(all.containsKey(item("alexsbutchery:raw_game_bird")), mobId + " keeps game bird: " + keys(all));
        }
        BlockPos fish = CompatTestSupport.placeReady(h, mob("catfish"), ORIGIN, new CompoundTag());
        Map<Item, Integer> catfish = CompatTestSupport.total(CompatTestSupport.process(h, fish, player));
        h.assertTrue(catfish.containsKey(item("alexsmobs:raw_catfish")), "a catfish stays Alex's Mobs' raw catfish: " + keys(catfish));
        // No rule anywhere yields bison meat or steaks for another creature, nor treats generic items as a species product.
        for (CompatState.Key key : state(h).resolutions().keySet()) {
            if (key.entity().getPath().equals("bison")) continue;
            for (CompatState.Choice c : state(h).resolutions().get(key).chain()) {
                if (c instanceof CompatState.Provided p && p.item() != null)
                    h.assertTrue(!key(p.item()).contains("bison"), key + " must not yield " + key(p.item()));
            }
        }
        h.succeed();
    }

    /** The shrimp tail follows the saved Variant; an old snapshot without one, or an unknown value, keeps the native tail. */
    @GameTest(template = "empty")
    public static void shrimpTailFollowsSavedVariant(GameTestHelper h) {
        MobDef def = mob("mantis_shrimp");
        String[] colours = {"green", "red", "lime", "white"};
        for (int variant = -1; variant <= 4; variant++) {
            CompoundTag snapshot = new CompoundTag();
            if (variant >= 0) snapshot.putInt("Variant", variant);
            CompatTestSupport.placeReady(h, def, ORIGIN, snapshot);
            Map<Item, Integer> got = CompatTestSupport.totals(CompatTestSupport.roll(h, def, MobDef.CUT_1, ORIGIN, null, butcheryItem("iron_cleaver"), 1));
            boolean known = variant >= 0 && variant < 4;
            String expected = amd() && known && state(h).policy().preferExternal()
                    ? "alexsmobsdelight:mantis_shrimp_tail_" + colours[variant] : "alexsbutchery:raw_mantis_shrimp_tail";
            h.assertTrue(got.size() == 1 && got.containsKey(item(expected)), "variant " + variant + ": expected " + expected + ", got " + keys(got));
            if (!expected.startsWith("alexsbutchery")) h.assertTrue(got.get(item(expected)) == 1, "one shrimp has one tail: " + keys(got));
        }
        h.succeed();
    }

    /** Looting adds whole rolls of the stage table (Butchery's rule); the compat entry is not multiplied again. */
    @GameTest(template = "empty")
    public static void lootingAddsRollsOnly(GameTestHelper h) {
        var looting = ButcheryconfigConfiguration.LOOTING_ENCHANT;
        Boolean old = looting.get();
        try {
            looting.set(true);
            MobDef emu = mob("emu");
            CompatTestSupport.placeReady(h, emu, ORIGIN, new CompoundTag());
            ItemStack cleaver = butcheryItem("iron_cleaver");
            cleaver.enchant(Enchantments.MOB_LOOTING, 3);
            var player = TestSupport.player(h);
            player.setItemInHand(InteractionHand.MAIN_HAND, cleaver);
            clearItems(h);
            TestSupport.use(h, ORIGIN, player); // head
            clearItems(h);
            player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_skinning_knife"));
            TestSupport.use(h, ORIGIN, player); // pluck
            clearItems(h);
            player.setItemInHand(InteractionHand.MAIN_HAND, cleaver);
            TestSupport.use(h, ORIGIN, player); // first cut, 4 rolls
            Map<Item, Integer> cut = drops(h);
            if (expectedMeatProvider("emu", state(h).policy()).equals(AMD)) {
                h.assertTrue(cut.getOrDefault(item("alexsmobsdelight:raw_emu"), 0) == 4, "Looting III: four rolls of one whole emu, got " + keys(cut));
            } else {
                int meat = cut.getOrDefault(item("alexsbutchery:raw_emu_meat"), 0);
                h.assertTrue(meat >= 4 && meat <= 8, "Looting III: four rolls of 1-2 emu meat, got " + meat);
            }
        } finally {
            looting.set(old);
        }
        clearItems(h);
        h.succeed();
    }

    /** Explicit budgets: whole bison meat per cut, one whole bird, two to three bunfungus legs; never above the stack size. */
    @GameTest(template = "empty")
    public static void yieldBudgetsHold(GameTestHelper h) {
        CompatState rules = state(h);
        MobDef bison = mob("bison");
        CompatTestSupport.placeReady(h, bison, ORIGIN, new CompoundTag());
        String provider = expectedMeatProvider("bison", rules.policy());
        Map<String, int[]> budget = Map.of(MobDef.CUT_1, new int[]{1, 2}, MobDef.CUT_2, new int[]{2, 3}, MobDef.CUT_3, new int[]{1, 2});
        for (String stage : List.of(MobDef.CUT_1, MobDef.CUT_2, MobDef.CUT_3)) {
            Set<Integer> seen = new LinkedHashSet<>();
            for (int i = 0; i < 40; i++) {
                Map<Item, Integer> got = CompatTestSupport.totals(CompatTestSupport.roll(h, bison, stage, ORIGIN, null, butcheryItem("iron_cleaver"), 1));
                int meat = 0;
                for (Map.Entry<Item, Integer> e : got.entrySet()) if (allMeats("bison").contains(key(e.getKey()))) meat += e.getValue();
                seen.add(meat);
                if (!provider.equals("native")) {
                    int[] b = budget.get(stage);
                    h.assertTrue(meat >= b[0] && meat <= b[1], "bison " + stage + " budget " + b[0] + "-" + b[1] + ", got " + meat);
                }
            }
            if (!provider.equals("native")) h.assertTrue(seen.size() == 2, "bison " + stage + " reaches both ends of its budget, saw " + seen);
        }
        if (amd() && rules.policy().preferExternal()) {
            for (String bird : List.of("seagull", "hummingbird")) {
                MobDef def = mob(bird);
                CompatTestSupport.placeReady(h, def, ORIGIN, new CompoundTag());
                int total = 0;
                for (String stage : List.of(MobDef.CUT_1, MobDef.CUT_2)) {
                    for (ItemStack s : CompatTestSupport.roll(h, def, stage, ORIGIN, null, butcheryItem("iron_cleaver"), 1))
                        if (key(s.getItem()).equals("alexsmobsdelight:raw_" + bird)) total += s.getCount();
                }
                h.assertTrue(total == 1, bird + " gives one whole bird, got " + total);
            }
            if (expectedMeatProvider("bunfungus", rules.policy()).equals(AMD)) {
                MobDef fungus = mob("bunfungus");
                CompatTestSupport.placeReady(h, fungus, ORIGIN, new CompoundTag());
                for (int i = 0; i < 20; i++) {
                    int legs = 0;
                    for (String stage : List.of(MobDef.CUT_1, MobDef.CUT_2, MobDef.CUT_3))
                        legs += CompatTestSupport.totals(CompatTestSupport.roll(h, fungus, stage, ORIGIN, null, butcheryItem("iron_cleaver"), 1))
                                .getOrDefault(item("alexsmobsdelight:raw_bunfungus_leg"), 0);
                    h.assertTrue(legs >= 2 && legs <= 3, "a bunfungus has 2-3 legs, got " + legs);
                }
            }
        }
        h.succeed();
    }

    /**
     * With no provider (absent, disabled or native mode) every generated stage table plus its internal native tables
     * is exactly the mob table's drop list: the same items, counts and chances, pool for pool, side products included.
     */
    @GameTest(template = "empty")
    public static void nativeDistributionsMatchTheMobTable(GameTestHelper h) {
        var loot = h.getLevel().getServer().getLootData();
        int checked = 0;
        for (MobDef def : MobDefs.all()) {
            for (Map.Entry<String, List<Drop>> stage : def.drops().entrySet()) {
                List<String> generated = new ArrayList<>();
                JsonObject table = json(loot.getLootTable(def.stageTable(stage.getKey())));
                for (JsonElement pool : table.getAsJsonArray("pools")) {
                    JsonObject entry = pool.getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
                    if (entry.get("type").getAsString().equals("alexsbutchery:compat_yield")) {
                        h.assertTrue(entry.get("stage").getAsString().equals(stage.getKey()) && entry.get("entity").getAsString().equals(def.entity().toString()),
                                def.id() + "/" + stage.getKey() + ": the entry names its own creature and stage");
                        JsonObject fallback = json(loot.getLootTable(new ResourceLocation(entry.get("fallback").getAsString())));
                        for (JsonElement p : fallback.getAsJsonArray("pools")) generated.add(describe(p.getAsJsonObject(), entry.get("group").getAsString()));
                    } else generated.add(describe(pool.getAsJsonObject(), null));
                }
                List<String> declared = new ArrayList<>();
                for (Drop drop : stage.getValue()) declared.add(drop.item() + " " + drop.min() + "-" + drop.max() + " @" + drop.chance()
                        + (drop.needs() == null ? "" : " needs " + drop.needs()) + (drop.group() == null ? "" : " [" + drop.group() + "]"));
                h.assertTrue(new java.util.HashSet<>(generated).equals(new java.util.HashSet<>(declared)) && generated.size() == declared.size(),
                        def.id() + "/" + stage.getKey() + ": generated " + generated + " but the mob table says " + declared);
                checked++;
            }
        }
        h.assertTrue(checked > 300, "every stage table checked, " + checked);
        // And rolled: native mode gives only declared items within their ranges.
        CompatState nativeRules = CompatTestSupport.with(h, state(h).policy().withPreferExternal(false));
        CompatState.withState(h.getLevel().getServer(), nativeRules, () -> {
            MobDef bison = mob("bison");
            CompatTestSupport.placeReady(h, bison, ORIGIN, new CompoundTag());
            for (String stage : List.of(MobDef.CUT_1, MobDef.CUT_2, MobDef.CUT_3)) {
                for (int i = 0; i < 30; i++) {
                    Map<Item, Integer> got = CompatTestSupport.totals(CompatTestSupport.roll(h, bison, stage, ORIGIN, null, butcheryItem("iron_cleaver"), 1));
                    for (Drop drop : bison.drops().get(stage)) {
                        int n = got.getOrDefault(item(drop.item().toString()), 0);
                        h.assertTrue(n >= drop.min() && n <= drop.max(), "native bison " + stage + " " + drop.item() + " " + n);
                    }
                    for (Item it : got.keySet()) h.assertTrue(bison.drops().get(stage).stream().anyMatch(d -> d.item().equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(it))),
                            "native bison " + stage + " yields only declared items, got " + key(it));
                }
            }
        });
        h.succeed();
    }

    /**
     * A pack's own stage tables: a plain replacement yields exactly what it says (no compatibility entry, nothing
     * rewritten afterwards) and is described exactly; one with logic that cannot be summarised is labelled custom.
     */
    @GameTest(template = "empty")
    public static void packStageTablesAreLeftAsWritten(GameTestHelper h) {
        MobDef komodo = mob("komodo_dragon");
        MobDef rattlesnake = mob("rattlesnake");
        LootTable plain = table(h, "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:stick\"}]}]}");
        LootTable dynamic = table(h, "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:alternatives\","
                + "\"children\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:bone\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]},"
                + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:stick\"}]}]}]}");
        CompatTestSupport.withLootTables(h, Map.of(komodo.stageTable(MobDef.CUT_1), plain, rattlesnake.stageTable(MobDef.CUT_1), dynamic), () -> {
            CompatTestSupport.placeReady(h, komodo, ORIGIN, new CompoundTag());
            for (int i = 0; i < 10; i++) {
                Map<Item, Integer> got = CompatTestSupport.totals(CompatTestSupport.roll(h, komodo, MobDef.CUT_1, ORIGIN, null, butcheryItem("iron_cleaver"), 1));
                h.assertTrue(got.size() == 1 && got.getOrDefault(net.minecraft.world.item.Items.STICK, 0) == 1,
                        "the pack's komodo cut_1 table gives one stick and nothing else, got " + keys(got));
            }
            CompatView view = CompatTestSupport.with(h, state(h).policy()).view();
            CompatView.StageView described = view.stage("komodo_dragon", MobDef.CUT_1);
            h.assertTrue(described != null && !described.custom() && described.outputs().size() == 1
                    && described.outputs().get(0).items().get(0).equals(new ResourceLocation("minecraft:stick")), "the plain pack table is described exactly: " + described);
            CompatView.StageView custom = view.stage("rattlesnake", MobDef.CUT_1);
            h.assertTrue(custom != null && custom.custom(), "a pack table with alternatives is labelled server-defined: " + custom);
        });
        CompatView.StageView restored = state(h).view().stage("komodo_dragon", MobDef.CUT_1);
        h.assertTrue(restored != null && !restored.custom() && restored.outputs().stream().anyMatch(o -> o.items().contains(new ResourceLocation("alexsbutchery:raw_reptile_meat"))),
                "the generated table is back: " + restored);
        h.succeed();
    }

    /** An item that does not stack is given as separate stacks, never as one oversized stack. */
    @GameTest(template = "empty")
    public static void outputsRespectTheStackLimit(GameTestHelper h) {
        java.util.Map<ResourceLocation, String> file = java.util.Map.of(new ResourceLocation("pack:komodo_saddles"),
                "{\"schema_version\":1,\"entity\":\"alexsmobs:komodo_dragon\",\"provider\":\"stacktest\",\"groups\":{\"meat\":"
                        + "{\"item\":\"minecraft:saddle\",\"count_mode\":\"per_stage\",\"counts\":{\"cut_1\":3}}}}");
        var set = CompatRuleTests.merge(CompatTestSupport.loadedProfiles(h), com.otectus.alexsbutchery.compat.profile.ProfileLoader.parse(file, java.util.Map.of()));
        CompatState rules = CompatTestSupport.with(h, set, state(h).policy());
        MobDef komodo = mob("komodo_dragon");
        CompatState.withState(h.getLevel().getServer(), rules, () -> {
            CompatTestSupport.placeReady(h, komodo, ORIGIN, new CompoundTag());
            List<ItemStack> stacks = CompatTestSupport.roll(h, komodo, MobDef.CUT_1, ORIGIN, null, butcheryItem("iron_cleaver"), 1);
            List<ItemStack> saddles = stacks.stream().filter(s -> s.is(net.minecraft.world.item.Items.SADDLE)).toList();
            h.assertTrue(saddles.size() == 3 && saddles.stream().allMatch(s -> s.getCount() == 1), "three saddles as three stacks: " + saddles);
        });
        h.succeed();
    }

    /** Parses a loot table the way the server loads one (Forge's loading context included). */
    static LootTable table(GameTestHelper h, String json) {
        return LootDataType.TABLE.deserialize(AlexsButchery.id("test_table"), com.google.gson.JsonParser.parseString(json),
                h.getLevel().getServer().getResourceManager()).orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("unparsable table " + json));
    }

    private static JsonObject json(LootTable table) {
        return LootDataType.TABLE.parser().toJsonTree(table).getAsJsonObject();
    }

    /** "item min-max @chance [group]" of a generated single-item pool. */
    private static String describe(JsonObject pool, String group) {
        JsonObject entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();
        float chance = 1F;
        String needs = "";
        if (pool.has("conditions")) for (JsonElement c : pool.getAsJsonArray("conditions")) {
            JsonObject condition = c.getAsJsonObject();
            if (condition.get("condition").getAsString().equals("alexsbutchery:mob_data")) needs = " needs " + condition.get("key").getAsString();
            else chance *= condition.get("chance").getAsFloat();
        }
        int min = 1, max = 1;
        JsonArray functions = entry.has("functions") ? entry.getAsJsonArray("functions") : new JsonArray();
        for (JsonElement f : functions) {
            String function = f.getAsJsonObject().get("function").getAsString();
            if (function.equals("minecraft:copy_nbt")) {
                // Only the mob's own head copies the carcass's look (MobData); it never changes the item or its count.
                if (!entry.get("name").getAsString().matches("alexsbutchery:[a-z_]+_head"))
                    throw new IllegalStateException("copy_nbt on a product that is not a head: " + entry);
                continue;
            }
            JsonElement count = f.getAsJsonObject().get("count");
            if (count.isJsonPrimitive()) min = max = (int) count.getAsDouble();
            else {
                min = (int) count.getAsJsonObject().get("min").getAsDouble();
                max = (int) count.getAsJsonObject().get("max").getAsDouble();
            }
        }
        return entry.get("name").getAsString() + " " + min + "-" + max + " @" + chance + needs + (group == null ? "" : " [" + group + "]");
    }
}
