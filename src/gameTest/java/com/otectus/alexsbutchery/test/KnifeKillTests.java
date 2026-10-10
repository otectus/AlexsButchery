package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.butcher.OfficialAddonGuard;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.config.ServerConfig;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import net.mcreator.butchery.configuration.ButcheryconfigConfiguration;
import net.mcreator.butchery.init.ButcheryModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import static com.otectus.alexsbutchery.test.TestSupport.ORIGIN;
import static com.otectus.alexsbutchery.test.TestSupport.alexsMob;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryItem;
import static com.otectus.alexsbutchery.test.TestSupport.clearItems;
import static com.otectus.alexsbutchery.test.TestSupport.count;

/**
 * Which weapons turn an Alex's Mobs kill into a carcass, against Butchery's real loaded config and real tagged items.
 * Every case sets Butchery's options, kills and counts synchronously, and restores the options before returning, so
 * no other test ever runs under them. Where Butchery itself butchers the creature (a vanilla cow), the same weapon and
 * options are checked against Butchery's own carcass drop as the reference.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KnifeKillTests {
    private static final String BATCH = "knives";
    private static final ResourceLocation COW_CARCASS = new ResourceLocation("butchery", "cow_carcass");
    private static final ResourceLocation KANGAROO_MEAT = new ResourceLocation("alexsmobs", "kangaroo_meat");
    /** gameTest data: a third-party knife, and a Butchery cleaver that is also tagged as a knife. */
    private static final Item THIRD_PARTY_KNIFE = Items.GOLDEN_SWORD;
    private static final Item KNIFE_TAGGED_CLEAVER = Items.GOLDEN_AXE;

    /** The run says whether Farmer's Delight is loaded; a mismatch fails instead of silently skipping its tests. */
    @GameTest(template = "empty")
    public static void farmersDelightPresenceMatchesRun(GameTestHelper h) {
        boolean expected = Boolean.getBoolean("alexsbutchery.gametest.farmersDelight");
        boolean loaded = ModList.get().isLoaded("farmersdelight");
        h.assertTrue(loaded == expected, "Farmer's Delight loaded=" + loaded + " but this run expects " + expected);
        if (!loaded) {
            // Butchery names the five knives without required:false, so the tag does not load without the mod.
            h.assertTrue(BuiltInRegistries.ITEM.getTag(ButcheryHooks.FARMERS_DELIGHT_KNIVES).map(tag -> tag.size() == 0).orElse(true),
                    "no item is a Farmer's Delight knife without Farmer's Delight");
            h.assertTrue(!ButcheryHooks.allowsCarcassKill(new ItemStack(THIRD_PARTY_KNIFE)), "an untagged sword never butchers");
        }
        h.succeed();
    }

    @GameTestGenerator
    public static Collection<TestFunction> farmersDelightKnives() {
        if (!ModList.get().isLoaded("farmersdelight")) return List.of();
        List<TestFunction> tests = new ArrayList<>();
        tests.add(test("fd_knife_follows_butchery_option", KnifeKillTests::knifeFollowsOption));
        tests.add(test("fd_knife_with_butchers_touch", KnifeKillTests::butchersTouch));
        tests.add(test("fd_knife_when_any_tool_butchers", KnifeKillTests::anyTool));
        tests.add(test("fd_cleaver_and_tag_overlaps", KnifeKillTests::cleaversAndOverlaps));
        tests.add(test("fd_offhand_knife_ignored", KnifeKillTests::offhand));
        tests.add(test("fd_arrow_kill_reads_main_hand_like_butchery", KnifeKillTests::arrowKill));
        tests.add(test("fd_refused_knife_keeps_drops_and_advancements", KnifeKillTests::refusedKnifeChangesNothing));
        tests.add(test("fd_floor_mob_follows_option", KnifeKillTests::floorMob));
        tests.add(test("fd_mob_gates_still_apply", KnifeKillTests::mobGates));
        return tests;
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> body) {
        return new TestFunction(BATCH, "knifekilltests." + name, AlexsButchery.MOD_ID + ":empty", 100, 0, true, body);
    }

    // --- cases --------------------------------------------------------------------------------------------------

    private static void knifeFollowsOption(GameTestHelper h) {
        ItemStack knife = fdKnife("iron_knife");
        h.assertTrue(knife.is(ButcheryHooks.FARMERS_DELIGHT_KNIVES) && knife.is(ButcheryHooks.FORGE_CLEAVER),
                "Butchery tags Farmer's Delight's knife as a knife and as a cleaver");
        boolean hasOption = fdOption() != null;
        withOptions(true, false, () -> {
            expect(h, "knife, option off", knife, !hasOption);
            if (hasOption) h.assertTrue(cowCarcasses(h, knife, KnifeKillTests::melee) == 0, "Butchery's own cow agrees (off)");
        });
        withOptions(true, true, () -> {
            expect(h, "knife, option on", knife, true);
            if (hasOption) h.assertTrue(cowCarcasses(h, knife, KnifeKillTests::melee) == 1, "Butchery's own cow agrees (on)");
        });
        for (String other : new String[]{"flint_knife", "golden_knife", "diamond_knife", "netherite_knife"})
            withOptions(true, false, () -> expect(h, other + ", option off", fdKnife(other), !hasOption));
        withOptions(true, false, () -> expect(h, "third-party knife, option off", new ItemStack(THIRD_PARTY_KNIFE), !hasOption));
        withOptions(true, true, () -> expect(h, "third-party knife, option on", new ItemStack(THIRD_PARTY_KNIFE), true));
        h.succeed();
    }

    private static void butchersTouch(GameTestHelper h) {
        ItemStack knife = fdKnife("iron_knife");
        knife.enchant(ButcheryModEnchantments.BUTCHERSTOUCH.get(), 1);
        withOptions(true, false, () -> {
            expect(h, "Butcher's Touch knife, option off", knife, true);
            h.assertTrue(cowCarcasses(h, knife, KnifeKillTests::melee) == 1, "Butchery's own cow agrees");
        });
        h.succeed();
    }

    private static void anyTool(GameTestHelper h) {
        withOptions(false, false, () -> {
            expect(h, "knife, any tool", fdKnife("iron_knife"), true);
            expect(h, "sword, any tool", new ItemStack(Items.IRON_SWORD), true);
            expect(h, "empty hand, any tool", ItemStack.EMPTY, true);
            h.assertTrue(cowCarcasses(h, fdKnife("iron_knife"), KnifeKillTests::melee) == 1, "Butchery's own cow agrees");
        });
        h.succeed();
    }

    private static void cleaversAndOverlaps(GameTestHelper h) {
        for (Boolean fd : new Boolean[]{false, true}) withOptions(true, fd, () -> {
            expect(h, "Butchery cleaver, option " + fd, butcheryItem("iron_cleaver"), true);
            expect(h, "knife-tagged Butchery cleaver, option " + fd, new ItemStack(KNIFE_TAGGED_CLEAVER), true);
            expect(h, "sword, option " + fd, new ItemStack(Items.IRON_SWORD), false);
            expect(h, "empty hand, option " + fd, ItemStack.EMPTY, false);
            if (fdOption() != null)
                h.assertTrue(cowCarcasses(h, new ItemStack(KNIFE_TAGGED_CLEAVER), KnifeKillTests::melee) == 1, "Butchery's own cow agrees");
        });
        h.succeed();
    }

    private static void offhand(GameTestHelper h) {
        withOptions(true, true, () -> {
            var player = TestSupport.player(h);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            player.setItemInHand(InteractionHand.OFF_HAND, fdKnife("iron_knife"));
            h.assertTrue(carcasses(h, player, KnifeKillTests::melee) == 0, "an offhand knife does not butcher");
        });
        withOptions(true, false, () -> {
            var player = TestSupport.player(h);
            player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
            player.setItemInHand(InteractionHand.OFF_HAND, fdKnife("iron_knife"));
            h.assertTrue(carcasses(h, player, KnifeKillTests::melee) == 1, "nor does it stop a cleaver");
        });
        h.succeed();
    }

    /** Butchery reads the shooter's main hand when the mob dies, so does this mod; both options are checked. */
    private static void arrowKill(GameTestHelper h) {
        boolean hasOption = fdOption() != null;
        for (Boolean fd : new Boolean[]{false, true}) withOptions(true, fd, () -> {
            boolean expected = fd || !hasOption;
            expect(h, "arrow kill holding a knife, option " + fd, fdKnife("iron_knife"), expected, KnifeKillTests::arrow);
            if (hasOption) h.assertTrue(cowCarcasses(h, fdKnife("iron_knife"), KnifeKillTests::arrow) == (expected ? 1 : 0),
                    "Butchery's own cow agrees (arrow, option " + fd + ")");
            expect(h, "arrow kill holding a bow, option " + fd, new ItemStack(Items.BOW), false, KnifeKillTests::arrow);
        });
        h.succeed();
    }

    private static void refusedKnifeChangesNothing(GameTestHelper h) {
        if (fdOption() == null) {
            h.succeed(); // Butchery 5.2: the knife always butchers, nothing is ever refused.
            return;
        }
        withOptions(true, false, () -> {
            ServerPlayer player = TestSupport.advancementPlayer(h);
            grantButcher(h, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, fdKnife("iron_knife"));
            LivingEntity kangaroo = (LivingEntity) h.spawn(alexsMob("kangaroo"), ORIGIN);
            melee(h, kangaroo, player);
            h.assertTrue(count(h, carcass("kangaroo")) == 0, "no carcass");
            h.assertTrue(count(h, KANGAROO_MEAT) >= 1, "Alex's Mobs' own meat is left in place");
            h.assertTrue(!done(h, player, AlexsButchery.id("wild_game")), "no Wild Game for a refused kill");
            h.assertTrue(!done(h, player, new ResourceLocation("butchery", "firstcarcass")), "no The Chosen One either");
            clearItems(h);
        });
        h.succeed();
    }

    private static void floorMob(GameTestHelper h) {
        boolean hasOption = fdOption() != null;
        for (Boolean fd : new Boolean[]{false, true}) withOptions(true, fd, () -> {
            var player = TestSupport.player(h);
            player.setItemInHand(InteractionHand.MAIN_HAND, fdKnife("iron_knife"));
            MobDef def = MobDefs.byId("elephant");
            LivingEntity elephant = (LivingEntity) h.spawn(alexsMob("elephant"), ORIGIN);
            melee(h, elephant, player);
            int blocks = 0;
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(4, 4, 4)))
                if (h.getBlockState(pos).is(ModBlocks.of(def).carcass().get())) {
                    blocks++;
                    h.getLevel().removeBlock(h.absolutePos(pos), false);
                }
            h.assertTrue(blocks == (fd || !hasOption ? 1 : 0), "elephant carcass blocks with option " + fd + ": " + blocks);
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                    new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(6)).isEmpty(), "no falling carcass entity");
            clearItems(h);
        });
        h.succeed();
    }

    private static void mobGates(GameTestHelper h) {
        withOptions(true, true, () -> {
            var player = TestSupport.player(h);
            player.setItemInHand(InteractionHand.MAIN_HAND, fdKnife("iron_knife"));
            // Baby.
            LivingEntity baby = (LivingEntity) h.spawn(alexsMob("kangaroo"), ORIGIN);
            ((AgeableMob) baby).setAge(-24_000);
            melee(h, baby, player);
            h.assertTrue(count(h, carcass("kangaroo")) == 0, "babies never drop carcasses");
            // Disabled mob.
            var disabled = ServerConfig.DISABLED_MOBS.get();
            try {
                ServerConfig.DISABLED_MOBS.set(List.of("kangaroo"));
                melee(h, (LivingEntity) h.spawn(alexsMob("kangaroo"), ORIGIN), player);
            } finally { ServerConfig.DISABLED_MOBS.set(disabled); }
            h.assertTrue(count(h, carcass("kangaroo")) == 0, "disabled mobs never drop carcasses");
            // Tamed, with tamed butchery off.
            boolean tamed = ServerConfig.BUTCHER_TAMED_MOBS.get();
            try {
                ServerConfig.BUTCHER_TAMED_MOBS.set(false);
                var pet = (TamableAnimal) h.spawn(alexsMob("kangaroo"), ORIGIN);
                pet.tame(player);
                melee(h, pet, player);
            } finally { ServerConfig.BUTCHER_TAMED_MOBS.set(tamed); }
            h.assertTrue(count(h, carcass("kangaroo")) == 0, "tamed mobs keep their drops when that option is off");
            // The boss tool gate: a knife is never Butchery's boss tool.
            var worm = (LivingEntity) h.spawn(alexsMob("void_worm"), ORIGIN.above(2));
            melee(h, worm, player);
            h.assertTrue(!worm.isAlive(), "the Void Worm died");
            boolean placed = false;
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(4, 4, 4)))
                placed |= h.getBlockState(pos).is(ModBlocks.of(MobDefs.byId("void_worm")).carcass().get());
            h.assertTrue(!placed, "the Void Worm needs the boss tool");
            // A creature the official addon owns, with deference on.
            boolean deferred = ServerConfig.DEFER_TO_OFFICIAL_ADDON.get();
            Boolean official = officialAddonDetection();
            var emu = (LivingEntity) h.spawn(alexsMob("emu"), ORIGIN);
            try {
                ServerConfig.DEFER_TO_OFFICIAL_ADDON.set(true);
                setOfficialAddonDetection(true);
                player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
                melee(h, emu, player);
            } finally {
                ServerConfig.DEFER_TO_OFFICIAL_ADDON.set(deferred);
                setOfficialAddonDetection(official);
            }
            h.assertTrue(!emu.isAlive() && count(h, carcass("emu")) == 0, "the official addon's creature is left to it");
            clearItems(h);
        });
        h.succeed();
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private interface Kill { void kill(GameTestHelper h, LivingEntity victim, Player killer); }

    private static void melee(GameTestHelper h, LivingEntity victim, Player killer) {
        victim.hurt(h.getLevel().damageSources().playerAttack(killer), 10_000F);
    }

    private static void arrow(GameTestHelper h, LivingEntity victim, Player killer) {
        Arrow arrow = new Arrow(h.getLevel(), killer);
        DamageSource source = h.getLevel().damageSources().arrow(arrow, killer);
        victim.hurt(source, 10_000F);
    }

    private static void expect(GameTestHelper h, String label, ItemStack weapon, boolean carcass) {
        expect(h, label, weapon, carcass, KnifeKillTests::melee);
    }

    /** Kills a kangaroo holding {@code weapon} and checks for exactly one carcass, or none and its own meat. */
    private static void expect(GameTestHelper h, String label, ItemStack weapon, boolean carcass, Kill kill) {
        var player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
        long found = carcasses(h, player, kill);
        h.assertTrue(found == (carcass ? 1 : 0), label + ": expected " + (carcass ? "one carcass" : "none") + ", found " + found);
    }

    private static long carcasses(GameTestHelper h, Player player, Kill kill) {
        LivingEntity kangaroo = (LivingEntity) h.spawn(alexsMob("kangaroo"), ORIGIN);
        kill.kill(h, kangaroo, player);
        long found = count(h, carcass("kangaroo"));
        if (found == 0 && count(h, KANGAROO_MEAT) == 0)
            throw new net.minecraft.gametest.framework.GameTestAssertException("a refused kill must keep Alex's Mobs' meat");
        clearItems(h);
        return found;
    }

    /** Butchery's own carcass drop for a vanilla cow killed the same way: the reference behaviour. */
    private static long cowCarcasses(GameTestHelper h, ItemStack weapon, Kill kill) {
        var player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
        LivingEntity cow = (LivingEntity) h.spawn(EntityType.COW, ORIGIN);
        kill.kill(h, cow, player);
        long found = count(h, COW_CARCASS);
        clearItems(h);
        return found;
    }

    private static Item carcass(String mob) {
        return ModItems.of(MobDefs.byId(mob)).carcass().get();
    }

    private static ItemStack fdKnife(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("farmersdelight", path));
        if (item == null || item == Items.AIR) throw new IllegalStateException("Farmer's Delight item missing: " + path);
        return new ItemStack(item);
    }

    /** Butchery 5.3's knife option, or null on a Butchery without it (5.2). */
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

    /** Runs {@code body} with Butchery's cleaver-only and knife options set, then puts back what was there. */
    private static void withOptions(boolean cleaverOnly, @Nullable Boolean fdKnives, Runnable body) {
        var specific = ButcheryconfigConfiguration.SPECIFIC_TOOLS;
        var fd = fdOption();
        Boolean oldSpecific = specific.get();
        Boolean oldFd = fd == null ? null : fd.get();
        try {
            specific.set(cleaverOnly);
            if (fd != null && fdKnives != null) fd.set(fdKnives);
            body.run();
        } finally {
            specific.set(oldSpecific);
            if (fd != null) fd.set(oldFd);
        }
    }

    /** The official addon detection the guard caches (null until first asked). */
    @Nullable
    private static Boolean officialAddonDetection() {
        try {
            return (Boolean) officialAddonField().get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setOfficialAddonDetection(@Nullable Boolean loaded) {
        try {
            officialAddonField().set(null, loaded);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static java.lang.reflect.Field officialAddonField() throws NoSuchFieldException {
        var field = OfficialAddonGuard.class.getDeclaredField("loaded");
        field.setAccessible(true);
        return field;
    }

    private static void grantButcher(GameTestHelper h, ServerPlayer player) {
        var advancement = h.getLevel().getServer().getAdvancements().getAdvancement(new ResourceLocation("butchery", "butcher"));
        if (advancement == null) throw new IllegalStateException("No butchery:butcher advancement");
        var progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) player.getAdvancements().award(advancement, criterion);
    }

    private static boolean done(GameTestHelper h, ServerPlayer player, ResourceLocation id) {
        var advancement = h.getLevel().getServer().getAdvancements().getAdvancement(id);
        if (advancement == null) throw new IllegalStateException("No advancement " + id);
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private KnifeKillTests() {}
}
