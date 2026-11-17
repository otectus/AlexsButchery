package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.CompatPolicy;
import com.otectus.alexsbutchery.compat.CompatState;
import com.otectus.alexsbutchery.compat.CompatView;
import com.otectus.alexsbutchery.compat.profile.ProfileLoader;
import com.otectus.alexsbutchery.compat.profile.ProfileSet;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootTableReference;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.otectus.alexsbutchery.test.CompatTestSupport.state;

/**
 * What clients are told matches what the server's tables and rules do (review finding AB-15): every link of a provider
 * chain is shown with its own counts and provider and matches the drops rolled for each saved variant, a native group's
 * chance is that of any of its pools, the emission cap applies to the summary, and an item a loaded table yields, or an
 * unreadable table may yield, is never hidden. Built from stand-in providers with vanilla items, so every profile runs it.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PresentationTests {
    private static final MobDef SHRIMP = MobDefs.byId("mantis_shrimp");

    @GameTest(template = "empty")
    public static void everyLinkOfAVariantChainIsShownAndRolledAlike(GameTestHelper h) {
        Map<ResourceLocation, String> files = new LinkedHashMap<>();
        files.put(new ResourceLocation("pack:shrimp_one"), variantProfile("one", "{\"0\":\"minecraft:cod\",\"1\":\"minecraft:salmon\"}", "{\"min\":2,\"max\":3}"));
        files.put(new ResourceLocation("pack:shrimp_two"), variantProfile("two", "{\"1\":\"minecraft:pufferfish\",\"2\":\"minecraft:tropical_fish\"}", "1"));
        CompatState rules = rules(h, files, List.of("one", "two"));
        CompatView.StageView stage = rules.view().stage("mantis_shrimp", MobDef.CUT_1);
        List<CompatView.OutputView> tail = stage.outputs().stream().filter(o -> "tail".equals(o.group())).toList();
        h.assertTrue(tail.size() == 3, "both providers and the native tail are shown: " + tail);
        h.assertTrue(tail.get(0).provider().equals("one") && tail.get(0).min() == 2 && tail.get(0).max() == 3 && !tail.get(0).otherwise()
                && "Variant".equals(tail.get(0).variantTag()), "the first link with its own counts: " + tail.get(0));
        h.assertTrue(tail.get(1).provider().equals("two") && tail.get(1).min() == 1 && tail.get(1).max() == 1 && tail.get(1).otherwise(),
                "the second link only for carcasses the first does not match, with its own counts: " + tail.get(1));
        h.assertTrue(tail.get(2).provider().equals("native") && tail.get(2).otherwise() && tail.get(2).items().contains(AlexsButchery.id("raw_mantis_shrimp_tail")),
                "the native tail for the rest: " + tail.get(2));
        // The drops rolled for each saved variant follow the same chain.
        Map<Integer, Item> expected = Map.of(0, Items.COD, 1, Items.SALMON, 2, Items.TROPICAL_FISH, 3, item("alexsbutchery:raw_mantis_shrimp_tail"));
        CompatState.withState(h.getLevel().getServer(), rules, () -> {
            for (Map.Entry<Integer, Item> e : expected.entrySet()) {
                CompoundTag snapshot = new CompoundTag();
                snapshot.putInt("Variant", e.getKey());
                h.assertTrue(rolled(h, snapshot).contains(e.getValue()), "variant " + e.getKey() + " rolls " + e.getValue() + ", got " + rolled(h, snapshot));
            }
            h.assertTrue(rolled(h, new CompoundTag()).contains(item("alexsbutchery:raw_mantis_shrimp_tail")), "a carcass without a saved variant rolls native");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aNativeGroupsChanceIsThatOfAnyPool(GameTestHelper h) {
        Map<ResourceLocation, String> files = Map.of(new ResourceLocation("pack:shrimp_group"),
                "{\"schema_version\":1,\"entity\":\"alexsmobs:mantis_shrimp\",\"provider\":\"grp\",\"required_mods\":[\"minecraft\"],"
                        + "\"groups\":{\"tail\":{\"item\":\"minecraft:cod\",\"count_mode\":\"native_group\"}}}");
        Item tail = item("alexsbutchery:raw_mantis_shrimp_tail");
        LootTable twoHalves = LootTable.lootTable().withPool(half(tail, 1)).withPool(half(tail, 1)).build();
        CompatTestSupport.withLootTables(h, Map.of(SHRIMP.nativeGroupTable(MobDef.CUT_1, "tail"), twoHalves), () -> {
            CompatView.OutputView group = only(rules(h, files, List.of("grp")));
            h.assertTrue(Math.abs(group.chance() - 0.75F) < 1E-4 && group.min() == 1 && group.max() == 2,
                    "two independent 50% pools: 75% for one or two, got " + group);
        });
        LootTable huge = LootTable.lootTable().withPool(pool(tail, 400)).withPool(pool(tail, 400)).build();
        CompatTestSupport.withLootTables(h, Map.of(SHRIMP.nativeGroupTable(MobDef.CUT_1, "tail"), huge), () -> {
            CompatView.OutputView group = only(rules(h, files, List.of("grp")));
            h.assertTrue(group.max() == 576 && group.min() == 576 && group.chance() == 1F, "the 576-item emission cap applies: " + group);
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void itemsALoadedTableYieldsAreNeverHidden(GameTestHelper h) {
        // Every bear's meat answered by one stand-in provider: this mod's bear meat is hidden.
        Map<ResourceLocation, String> files = new LinkedHashMap<>();
        for (String bear : List.of("grizzly_bear", "dropbear", "sea_bear")) files.put(new ResourceLocation("pack:" + bear),
                "{\"schema_version\":1,\"entity\":\"alexsmobs:" + bear + "\",\"provider\":\"packfood\",\"required_mods\":[\"minecraft\"],"
                        + "\"groups\":{\"meat\":{\"item\":\"minecraft:mutton\",\"count_mode\":\"native_group\"}}}");
        ResourceLocation bearMeat = AlexsButchery.id("raw_bear_meat");
        h.assertTrue(rules(h, files, List.of("packfood")).hiddenItems().containsAll(Set.of(bearMeat, AlexsButchery.id("cooked_bear_meat"))),
                "bear meat no creature yields is hidden, raw and cooked");
        // A pack's grizzly table that names the bear meat outright: shown again.
        LootTable direct = LootTable.lootTable().withPool(pool(item("alexsbutchery:raw_bear_meat"), 2)).build();
        CompatTestSupport.withLootTables(h, Map.of(MobDefs.byId("grizzly_bear").stageTable(MobDef.CUT_1), direct), () -> {
            Set<ResourceLocation> hidden = rules(h, files, List.of("packfood")).hiddenItems();
            h.assertTrue(!hidden.contains(bearMeat) && !hidden.contains(AlexsButchery.id("cooked_bear_meat")), "a table that yields it keeps it shown: " + hidden);
        });
        // A pack's table the summary cannot read exactly (a nested table): kept shown too.
        LootTable nested = LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                .add(LootTableReference.lootTableReference(new ResourceLocation("minecraft:entities/cow")))).build();
        CompatTestSupport.withLootTables(h, Map.of(MobDefs.byId("grizzly_bear").stageTable(MobDef.CUT_2), nested), () -> {
            CompatState rules = rules(h, files, List.of("packfood"));
            h.assertTrue(rules.view().stage("grizzly_bear", MobDef.CUT_2).custom(), "the nested table is server-defined");
            h.assertTrue(!rules.hiddenItems().contains(bearMeat), "and what it may yield is not hidden: " + rules.hiddenItems());
        });
        h.succeed();
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private static String variantProfile(String provider, String items, String count) {
        return "{\"schema_version\":1,\"entity\":\"alexsmobs:mantis_shrimp\",\"provider\":\"" + provider + "\",\"required_mods\":[\"minecraft\"],"
                + "\"groups\":{\"tail\":{\"variants\":{\"nbt\":\"Variant\",\"items\":" + items + "},\"count_mode\":\"per_stage\",\"counts\":{\"cut_1\":" + count + "}}}}";
    }

    /** The server's rules with these files over the loaded profiles, the cooking addons set aside. */
    static CompatState rules(GameTestHelper h, Map<ResourceLocation, String> files, List<String> priority) {
        ProfileSet set = CompatRuleTests.merge(CompatTestSupport.loadedProfiles(h), ProfileLoader.parse(files, Map.of()));
        CompatPolicy policy = state(h).policy().withPreferExternal(true).withPriority(priority).withDisabled(Set.of("alexsmobsdelight", "alexsdelight"));
        return CompatTestSupport.with(h, set, policy);
    }

    private static CompatView.OutputView only(CompatState rules) {
        List<CompatView.OutputView> tail = rules.view().stage("mantis_shrimp", MobDef.CUT_1).outputs().stream().filter(o -> "tail".equals(o.group())).toList();
        if (tail.size() != 1) throw new net.minecraft.gametest.framework.GameTestAssertException("one tail output expected: " + tail);
        return tail.get(0);
    }

    private static Set<Item> rolled(GameTestHelper h, CompoundTag snapshot) {
        BlockPos rel = new BlockPos(2, 1, 2);
        CompatTestSupport.placeReady(h, SHRIMP, rel, snapshot);
        Set<Item> out = new java.util.HashSet<>();
        for (int i = 0; i < 6; i++) for (ItemStack s : CompatTestSupport.roll(h, SHRIMP, MobDef.CUT_1, rel, null, TestSupport.butcheryItem("iron_cleaver"), 1))
            out.add(s.getItem());
        return out;
    }

    private static LootPool.Builder half(Item item, int count) {
        return pool(item, count).when(LootItemRandomChanceCondition.randomChance(0.5F));
    }

    private static LootPool.Builder pool(Item item, int count) {
        return LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(item).apply(SetItemCountFunction.setCount(ConstantValue.exactly(count))));
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
    }

    private PresentationTests() {}
}
