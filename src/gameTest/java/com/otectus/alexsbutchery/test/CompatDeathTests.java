package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.CompatState;
import com.otectus.alexsbutchery.config.ServerConfig;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import net.mcreator.butchery.configuration.ButcheryconfigConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.otectus.alexsbutchery.test.CompatTestSupport.amd;
import static com.otectus.alexsbutchery.test.CompatTestSupport.ad;
import static com.otectus.alexsbutchery.test.CompatTestSupport.drops;
import static com.otectus.alexsbutchery.test.CompatTestSupport.fd;
import static com.otectus.alexsbutchery.test.CompatTestSupport.item;
import static com.otectus.alexsbutchery.test.CompatTestSupport.itemOrNull;
import static com.otectus.alexsbutchery.test.CompatTestSupport.key;
import static com.otectus.alexsbutchery.test.CompatTestSupport.keys;
import static com.otectus.alexsbutchery.test.CompatTestSupport.mob;
import static com.otectus.alexsbutchery.test.CompatTestSupport.state;
import static com.otectus.alexsbutchery.test.TestSupport.ORIGIN;
import static com.otectus.alexsbutchery.test.TestSupport.alexsMob;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryItem;
import static com.otectus.alexsbutchery.test.TestSupport.clearItems;

/**
 * The death half of a carcass kill: real kills with each addon's loot modifiers and table overrides in place, checked
 * for ordinary meat paid twice, specialties lost, and upstream loot changed when no carcass results.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompatDeathTests {
    /** Every registered raw or cooked meat any source gives these creatures at death. */
    private static final Map<String, List<String>> DEATH_MEAT = Map.of(
            "grizzly_bear", List.of("alexsmobsdelight:raw_bear_meat", "alexsmobsdelight:cooked_bear_meat"),
            "crocodile", List.of("alexsmobsdelight:raw_crocodile_meat", "alexsmobsdelight:cooked_crocodile_meat", "alexsmobs:crocodile_scute"),
            "bison", List.of("minecraft:beef", "minecraft:cooked_beef", "alexsmobsdelight:raw_bison_meat", "alexsmobsdelight:cooked_bison_meat",
                    "alexsdelight:raw_bison", "alexsdelight:cooked_bison", "alexsmobs:bison_fur"),
            "seagull", List.of("alexsmobsdelight:raw_seagull", "alexsmobsdelight:cooked_seagull"),
            "tusklin", List.of("alexsmobsdelight:raw_tusklin_meat", "alexsmobsdelight:cooked_tusklin_meat"),
            "kangaroo", List.of("alexsmobs:kangaroo_meat", "alexsmobs:cooked_kangaroo_meat", "alexsmobs:kangaroo_hide"),
            "bunfungus", List.of("alexsmobsdelight:raw_bunfungus_leg", "alexsmobsdelight:cooked_bunfungus_leg", "alexsdelight:raw_bunfungus",
                    "alexsdelight:cooked_bunfungus"));

    private static Set<Item> deathMeat(String mob) {
        Set<Item> out = new java.util.LinkedHashSet<>();
        for (String id : DEATH_MEAT.get(mob)) {
            Item item = itemOrNull(id);
            if (item != null) out.add(item);
        }
        return out;
    }

    /** Kills a fresh {@code mob} holding {@code weapon}, optionally burning, and returns everything it dropped. */
    private static Map<Item, Integer> kill(GameTestHelper h, String mob, ItemStack weapon, boolean burning) {
        return kill(h, mob, weapon, burning, null);
    }

    private static Map<Item, Integer> kill(GameTestHelper h, String mob, ItemStack weapon, boolean burning, @Nullable Consumer<LivingEntity> prepare) {
        clearItems(h);
        var player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
        LivingEntity victim = (LivingEntity) h.spawn(alexsMob(mob), ORIGIN);
        if (prepare != null) prepare.accept(victim);
        if (burning) victim.setSecondsOnFire(10);
        TestSupport.kill(h, victim, player);
        Map<Item, Integer> out = drops(h);
        clearItems(h);
        return out;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void representedMeatIsNotPaidTwice(GameTestHelper h) {
        for (String mob : List.of("grizzly_bear", "crocodile", "bison", "seagull", "tusklin", "kangaroo", "bunfungus")) {
            for (boolean burning : new boolean[]{false, true}) {
                Map<Item, Integer> got = kill(h, mob, butcheryItem("iron_cleaver"), burning);
                Item carcass = ModItems.of(mob(mob)).carcass().get();
                h.assertTrue(got.getOrDefault(carcass, 0) == 1, mob + (burning ? " (burning)" : "") + ": one carcass, got " + keys(got));
                for (Item meat : deathMeat(mob)) {
                    h.assertTrue(!got.containsKey(meat), mob + (burning ? " (burning)" : "") + ": " + key(meat) + " is the carcass's product, got " + keys(got));
                }
            }
        }
        h.succeed();
    }

    /** Both addons installed: the non-preferred addon's equivalent meat (from its own table or modifier) is removed too. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nonPreferredAddonMeatIsRemovedToo(GameTestHelper h) {
        Set<Item> bison = state(h).suppression(new ResourceLocation("alexsmobs:bison"));
        for (String id : List.of("alexsmobsdelight:raw_bison_meat", "alexsmobsdelight:cooked_bison_meat", "alexsdelight:raw_bison", "alexsdelight:cooked_bison")) {
            Item item = itemOrNull(id);
            if (item == null) continue;
            boolean providerEnabled = state(h).policy().enabled(new ResourceLocation(id).getNamespace());
            h.assertTrue(bison.contains(item) == providerEnabled, "bison death removal of " + id + " follows its provider: " + bison);
        }
        h.assertTrue(bison.contains(Items.BEEF) && bison.contains(Items.COOKED_BEEF), "native beef, raw and cooked, is the bison carcass's meat");
        h.assertTrue(!bison.contains(item("butchery:raw_chuck_steak")), "nothing outside the bison's own drops is removed");
        Set<Item> crocodile = state(h).suppression(new ResourceLocation("alexsmobs:crocodile"));
        h.assertTrue(!crocodile.contains(Items.BEEF), "beef is the bison's, not the crocodile's");
        h.succeed();
    }

    /** Specialty parts the carcass does not supply stay with the death drops. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void specialtyPartsSurvive(GameTestHelper h) {
        Set<Item> crocodile = state(h).suppression(new ResourceLocation("alexsmobs:crocodile"));
        Set<Item> bunfungus = state(h).suppression(new ResourceLocation("alexsmobs:bunfungus"));
        Set<Item> shrimp = state(h).suppression(new ResourceLocation("alexsmobs:mantis_shrimp"));
        Set<Item> seagull = state(h).suppression(new ResourceLocation("alexsmobs:seagull"));
        for (String id : List.of("alexsmobsdelight:raw_crocodile_claw", "alexsmobsdelight:raw_crocodile_tail", "alexsmobsdelight:cooked_crocodile_claw",
                "alexsmobsdelight:cooked_crocodile_tail", "alexsmobs:crocodile_egg")) {
            Item item = itemOrNull(id);
            if (item != null) h.assertTrue(!crocodile.contains(item), id + " is a specialty part, never removed");
        }
        for (String id : List.of("alexsmobsdelight:raw_bunfungus_ear", "alexsmobsdelight:mushroom_hat")) {
            Item item = itemOrNull(id);
            if (item != null) h.assertTrue(!bunfungus.contains(item), id + " is a specialty part, never removed");
        }
        for (String id : List.of("alexsmobsdelight:mantis_shrimp_hammer", "alexsmobsdelight:mantis_shrimp_shovel", "alexsmobsdelight:mantis_shrimp_axe",
                "alexsmobsdelight:mantis_shrimp_scythe")) {
            Item item = itemOrNull(id);
            if (item != null) h.assertTrue(!shrimp.contains(item), id + " is a tool, never removed");
        }
        for (String id : List.of("alexsmobsdelight:eternal_cooked_seagull", "alexsmobsdelight:enchanted_cooked_seagull", "alexsmobsdelight:enchanted_eternal_cooked_seagull")) {
            Item item = itemOrNull(id);
            if (item != null) h.assertTrue(!seagull.contains(item), id + " is a special reward, never removed");
        }
        // A data-bearing stack of an otherwise removed item is kept as it is (an effect-holding reward, a named drop).
        Consumer<LivingDropsEvent> addReward = event -> {
            if (!event.getEntity().getTags().contains("compat_test_reward")) return;
            Item meat = amd() ? item("alexsmobsdelight:cooked_seagull") : Items.FEATHER;
            ItemStack reward = new ItemStack(meat);
            reward.getOrCreateTag().putString("TestRewardData", "kept");
            event.getDrops().add(new ItemEntity(event.getEntity().level(), event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), reward));
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingDropsEvent.class, addReward);
        try {
            clearItems(h);
            var player = TestSupport.player(h);
            player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
            LivingEntity gull = (LivingEntity) h.spawn(alexsMob("seagull"), ORIGIN);
            gull.addTag("compat_test_reward");
            TestSupport.kill(h, gull, player);
            boolean kept = TestSupport.items(h).stream().anyMatch(e -> e.getItem().hasTag() && "kept".equals(e.getItem().getTag().getString("TestRewardData")));
            h.assertTrue(kept, "a data-bearing reward survives the carcass kill: " + keys(drops(h)));
            clearItems(h);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(addReward);
        }
        if (fd()) {
            withKnivesButchering(() -> {
                // A Farmer's Delight knife kill: Alex's Mobs Delight adds claws and a tail (knife-only) and they stay.
                if (amd()) {
                    Map<Item, Integer> got = kill(h, "crocodile", new ItemStack(item("farmersdelight:iron_knife")), false);
                    h.assertTrue(got.containsKey(item("alexsmobsdelight:raw_crocodile_claw")) && got.containsKey(item("alexsmobsdelight:raw_crocodile_tail")),
                            "claws and tail stay with a knife kill: " + keys(got));
                    h.assertTrue(!got.containsKey(item("alexsmobsdelight:raw_crocodile_meat")), "while the meat moves into the carcass: " + keys(got));
                    Map<Item, Integer> fungus = kill(h, "bunfungus", new ItemStack(item("farmersdelight:iron_knife")), false);
                    h.assertTrue(fungus.containsKey(item("alexsmobsdelight:raw_bunfungus_ear")) && fungus.containsKey(item("alexsmobsdelight:mushroom_hat")),
                            "bunfungus ears and hat stay: " + keys(fungus));
                }
            });
        }
        h.succeed();
    }

    /**
     * Alex's Mobs Delight's Dimensional Slicer re-rolls the death table in its own drops listener; the carcass kill's
     * filter runs after it, so the extra ordinary meat is removed as well.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void dimensionalSlicerCannotBringMeatBack(GameTestHelper h) {
        if (!amd()) {
            h.assertTrue(itemOrNull("alexsmobsdelight:dimensional_slicer") == null, "no slicer without Alex's Mobs Delight");
            h.succeed();
            return;
        }
        withKnivesButchering(() -> {
            for (int i = 0; i < 4; i++) {
                Map<Item, Integer> got = kill(h, "bison", new ItemStack(item("alexsmobsdelight:dimensional_slicer")), false);
                h.assertTrue(got.getOrDefault(ModItems.of(mob("bison")).carcass().get(), 0) == 1, "the slicer kill made a carcass: " + keys(got));
                for (Item meat : deathMeat("bison")) h.assertTrue(!got.containsKey(meat), "slicer kill: no " + key(meat) + ", got " + keys(got));
            }
        });
        h.succeed();
    }

    /** Items another mechanic already dropped (the crocodile knife's on-hit rewards) are never touched by a later kill. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void earlierRewardsAreNotConfiscated(GameTestHelper h) {
        clearItems(h);
        Item meat = amd() ? item("alexsmobsdelight:raw_crocodile_meat") : item("alexsbutchery:raw_reptile_meat");
        BlockPos abs = h.absolutePos(ORIGIN.east(1));
        ItemEntity earlier = new ItemEntity(h.getLevel(), abs.getX() + 0.5, abs.getY() + 0.2, abs.getZ() + 0.5, new ItemStack(meat, 2));
        earlier.setPickUpDelay(32767);
        h.getLevel().addFreshEntity(earlier);
        var player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        LivingEntity croc = (LivingEntity) h.spawn(alexsMob("crocodile"), ORIGIN);
        TestSupport.kill(h, croc, player);
        h.assertTrue(earlier.isAlive() && earlier.getItem().getCount() == 2, "the meat already on the ground stays");
        clearItems(h);
        h.succeed();
    }

    /** No carcass, no change: babies, refused weapons, disabled mobs and an entombed floor kill keep their ordinary drops. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void declinedCarcassKeepsUpstreamLoot(GameTestHelper h) {
        Item crocMeat = amd() ? item("alexsmobsdelight:raw_crocodile_meat") : null;
        // Refused weapon (a sword with cleaver-only kills on).
        withCleaverOnly(() -> {
            Map<Item, Integer> sword = kill(h, "kangaroo", new ItemStack(Items.IRON_SWORD), false);
            h.assertTrue(sword.containsKey(item("alexsmobs:kangaroo_meat")), "sword kill keeps kangaroo meat: " + keys(sword));
            if (crocMeat != null) {
                boolean got = false;
                for (int i = 0; i < 4 && !got; i++) got = kill(h, "crocodile", new ItemStack(Items.IRON_SWORD), false).containsKey(crocMeat);
                h.assertTrue(got, "a sword kill keeps Alex's Mobs Delight's crocodile meat (1-4 per kill)");
            }
        });
        // Baby.
        Map<Item, Integer> baby = kill(h, "kangaroo", butcheryItem("iron_cleaver"), false, v -> ((AgeableMob) v).setAge(-24_000));
        h.assertTrue(baby.getOrDefault(ModItems.of(mob("kangaroo")).carcass().get(), 0) == 0, "no carcass from a baby");
        // Disabled creature.
        var disabled = ServerConfig.DISABLED_MOBS.get();
        try {
            ServerConfig.DISABLED_MOBS.set(List.of("kangaroo"));
            Map<Item, Integer> got = kill(h, "kangaroo", butcheryItem("iron_cleaver"), false);
            h.assertTrue(got.containsKey(item("alexsmobs:kangaroo_meat")) && !got.containsKey(ModItems.of(mob("kangaroo")).carcass().get()),
                    "a disabled creature keeps its drops: " + keys(got));
        } finally {
            ServerConfig.DISABLED_MOBS.set(disabled);
        }
        // A floor creature with nowhere to rest keeps its drops (Alex's Mobs Delight's whale meat included).
        h.setBlock(ORIGIN.below(), Blocks.STONE);
        h.setBlock(ORIGIN, Blocks.STONE);
        h.setBlock(ORIGIN.above(), Blocks.STONE);
        clearItems(h);
        var player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        LivingEntity whale = (LivingEntity) h.spawn(alexsMob("cachalot_whale"), ORIGIN);
        whale.setPos(whale.getX(), h.absolutePos(ORIGIN).getY() + 0.1, whale.getZ());
        TestSupport.kill(h, whale, player);
        boolean placed = false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(4, 4, 4)))
            placed |= h.getBlockState(pos).is(ModBlocks.of(mob("cachalot_whale")).carcass().get());
        h.assertTrue(!placed, "the entombed whale left no carcass");
        if (amd()) h.assertTrue(drops(h).containsKey(item("alexsmobsdelight:whale_meat")), "and kept Alex's Mobs Delight's whale meat: " + keys(drops(h)));
        clearItems(h);
        h.succeed();
    }

    /** {@code replaceMobDrops=false} is the documented opt-out: the carcass and every ordinary drop. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void replaceMobDropsOffKeepsEverything(GameTestHelper h) {
        boolean old = ServerConfig.REPLACE_MOB_DROPS.get();
        try {
            ServerConfig.REPLACE_MOB_DROPS.set(false);
            Map<Item, Integer> got = kill(h, "kangaroo", butcheryItem("iron_cleaver"), false);
            h.assertTrue(got.getOrDefault(ModItems.of(mob("kangaroo")).carcass().get(), 0) == 1, "still a carcass: " + keys(got));
            h.assertTrue(got.containsKey(item("alexsmobs:kangaroo_meat")), "and the ordinary meat: " + keys(got));
            if (amd()) {
                boolean bison = false;
                for (int i = 0; i < 3 && !bison; i++) bison = kill(h, "bison", butcheryItem("iron_cleaver"), false).containsKey(item("alexsmobsdelight:raw_bison_meat"));
                h.assertTrue(bison, "the addon's bison meat stays too");
            }
        } finally {
            ServerConfig.REPLACE_MOB_DROPS.set(old);
        }
        h.succeed();
    }

    /**
     * The {@code doMobLoot} game rule keeps its meaning: with it off the creature drops no loot, and a carcass kill still
     * gives its carcass exactly as before this release, as Butchery's own carcasses do for its animals.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mobLootRuleKeepsItsMeaning(GameTestHelper h) {
        var rule = h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT);
        boolean old = rule.get();
        try {
            rule.set(false, h.getLevel().getServer());
            Map<Item, Integer> got = kill(h, "kangaroo", butcheryItem("iron_cleaver"), false);
            h.assertTrue(got.getOrDefault(ModItems.of(mob("kangaroo")).carcass().get(), 0) == 1, "the carcass still drops: " + keys(got));
            h.assertTrue(!got.containsKey(item("alexsmobs:kangaroo_meat")) && !got.containsKey(item("alexsmobs:kangaroo_hide")), "and no loot: " + keys(got));
            clearItems(h);
            var player = TestSupport.player(h);
            player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
            LivingEntity cow = (LivingEntity) h.spawn(net.minecraft.world.entity.EntityType.COW, ORIGIN);
            TestSupport.kill(h, cow, player);
            Map<Item, Integer> butchery = drops(h);
            h.assertTrue(butchery.containsKey(item("butchery:cow_carcass")), "Butchery's own cow carcass drops with mob loot off too: " + keys(butchery));
            clearItems(h);
        } finally {
            rule.set(old, h.getLevel().getServer());
        }
        h.succeed();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** Runs with cleaver-only kills on and Farmer's Delight knives allowed (Butchery 5.3's option; 5.2 always allows). */
    private static void withKnivesButchering(Runnable body) {
        var specific = ButcheryconfigConfiguration.SPECIFIC_TOOLS;
        ForgeConfigSpec.ConfigValue<Boolean> fd = fdOption();
        Boolean oldSpecific = specific.get();
        Boolean oldFd = fd == null ? null : fd.get();
        try {
            specific.set(true);
            if (fd != null) fd.set(true);
            body.run();
        } finally {
            specific.set(oldSpecific);
            if (fd != null) fd.set(oldFd);
        }
    }

    private static void withCleaverOnly(Runnable body) {
        var specific = ButcheryconfigConfiguration.SPECIFIC_TOOLS;
        Boolean old = specific.get();
        try {
            specific.set(true);
            body.run();
        } finally {
            specific.set(old);
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static ForgeConfigSpec.ConfigValue<Boolean> fdOption() {
        try {
            return (ForgeConfigSpec.ConfigValue<Boolean>) ButcheryconfigConfiguration.class.getField("FD_CARCASS_DROP").get(null);
        } catch (NoSuchFieldException e) {
            return null;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
