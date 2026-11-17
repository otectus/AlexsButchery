package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.compat.CompatState;
import com.otectus.alexsbutchery.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.otectus.alexsbutchery.test.CompatTestSupport.amd;
import static com.otectus.alexsbutchery.test.CompatTestSupport.drops;
import static com.otectus.alexsbutchery.test.CompatTestSupport.expectedMeatProvider;
import static com.otectus.alexsbutchery.test.CompatTestSupport.item;
import static com.otectus.alexsbutchery.test.CompatTestSupport.key;
import static com.otectus.alexsbutchery.test.CompatTestSupport.keys;
import static com.otectus.alexsbutchery.test.CompatTestSupport.mob;
import static com.otectus.alexsbutchery.test.CompatTestSupport.state;
import static com.otectus.alexsbutchery.test.TestSupport.ORIGIN;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryItem;
import static com.otectus.alexsbutchery.test.TestSupport.clearItems;

/** Saved carcasses continuing under new rules, and stages that can only ever be paid once. */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompatIntegrityTests {

    /**
     * Carcasses saved by 0.1.2 mid-way (same blocks, Butchery state numbers and snapshot keys) continue under the current
     * rules: the remaining cuts yield the selected provider's meat and no completed stage pays again.
     */
    @GameTest(template = "empty")
    public static void savedCarcassesContinueWithoutRepeats(GameTestHelper h) {
        var player = TestSupport.player(h);
        MobDef bison = mob("bison");
        String provider = expectedMeatProvider("bison", state(h).policy());
        // Ground, drained, head taken and skinned (state 7): an old snapshot holds only Alex's Mobs' own keys.
        CompoundTag old = new CompoundTag();
        old.putInt("Age", 0);
        CompatTestSupport.place(h, bison, true, ORIGIN, 7, old);
        Map<String, Map<Item, Integer>> stages = CompatTestSupport.process(h, ORIGIN, player);
        h.assertTrue(stages.keySet().equals(Set.of(MobDef.CUT_1, MobDef.CUT_2, MobDef.CUT_3)), "only the three cuts remain: " + stages.keySet());
        Map<Item, Integer> all = CompatTestSupport.total(stages);
        h.assertTrue(!all.containsKey(item("alexsbutchery:bison_head")) && !all.containsKey(item("alexsbutchery:bison_hide"))
                && !all.containsKey(item("alexsmobs:bison_fur")), "the head and skin are not paid again: " + keys(all));
        String meat = switch (provider) {
            case "alexsmobsdelight" -> "alexsmobsdelight:raw_bison_meat";
            case "alexsdelight" -> "alexsdelight:raw_bison";
            default -> "butchery:raw_chuck_steak";
        };
        h.assertTrue(all.containsKey(item(meat)), "the remaining cuts follow today's rules (" + provider + "): " + keys(all));
        // Hanging, drained, one cut done (state 4): two cuts left.
        BlockPos hook = ORIGIN.above(2);
        h.setBlock(hook, TestSupport.butcheryBlock("hook").defaultBlockState());
        CompatTestSupport.place(h, mob("grizzly_bear"), true, hook.below(), 4, old);
        Map<String, Map<Item, Integer>> hanging = CompatTestSupport.process(h, hook.below(), player);
        h.assertTrue(hanging.keySet().equals(Set.of(MobDef.CUT_2, MobDef.CUT_3)), "a hanging carcass after its first cut has two left: " + hanging.keySet());
        // A carcass without any snapshot (the oldest saves) still resolves; variant rules fall back to native.
        MobDef shrimp = mob("mantis_shrimp");
        CompatTestSupport.place(h, shrimp, false, ORIGIN.east(2), 0, new CompoundTag());
        Map<Item, Integer> tail = CompatTestSupport.process(h, ORIGIN.east(2), player).get(MobDef.CUT_1);
        h.assertTrue(tail.containsKey(item("alexsbutchery:raw_mantis_shrimp_tail")), "no saved variant: the native tail, no invented colour: " + keys(tail));
        h.succeed();
    }

    /** A wrong tool, a cancelled interaction and repeated clicks never advance or pay a stage twice. */
    @GameTest(template = "empty")
    public static void stagesArePaidOnce(GameTestHelper h) {
        MobDef croc = mob("crocodile");
        var player = TestSupport.player(h);
        CompatTestSupport.placeReady(h, croc, ORIGIN, new CompoundTag());
        // Wrong tools: a sword never cuts; a knife does not take a head.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        h.assertTrue(TestSupport.use(h, ORIGIN, player) == InteractionResult.PASS, "a sword does nothing");
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_skinning_knife"));
        TestSupport.use(h, ORIGIN, player);
        h.assertTrue(stage(h) == 0 && drops(h).isEmpty(), "the knife cannot take the head; nothing dropped");
        // A protection mod cancelling the click: the server-side interaction path never reaches the block.
        Consumer<PlayerInteractEvent.RightClickBlock> deny = e -> {
            if (e.getPos().equals(h.absolutePos(ORIGIN))) e.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.RightClickBlock.class, deny);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
            BlockPos abs = h.absolutePos(ORIGIN);
            player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false));
            h.assertTrue(stage(h) == 0 && drops(h).isEmpty(), "a cancelled interaction neither advances nor pays");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(deny);
        }
        // Each click pays exactly the next stage, whoever clicks; the last removes the block.
        var other = TestSupport.player(h);
        int clicks = 0;
        Map<String, Integer> paid = new java.util.LinkedHashMap<>();
        while (h.getBlockState(ORIGIN).getBlock() instanceof AbstractCarcassBlock && clicks < 10) {
            var who = clicks % 2 == 0 ? player : other;
            int before = stage(h);
            boolean knife = before == 6;
            who.setItemInHand(InteractionHand.MAIN_HAND, knife ? butcheryItem("iron_skinning_knife") : butcheryItem("iron_cleaver"));
            clearItems(h);
            TestSupport.use(h, ORIGIN, who);
            paid.merge("stage " + before, drops(h).isEmpty() ? 0 : 1, Integer::sum);
            clicks++;
        }
        h.assertTrue(clicks == 5, "five actions (head, skin, three cuts) finish a crocodile, took " + clicks);
        h.assertTrue(paid.values().stream().allMatch(v -> v == 1), "every stage paid once: " + paid);
        clearItems(h);
        h.succeed();
    }

    /** Creative tabs hide an item of ours only when no creature yields it under the current rules. */
    @GameTest(template = "empty")
    public static void hiddenItemsFollowEveryRelevantSource(GameTestHelper h) {
        Set<ResourceLocation> hidden = state(h).hiddenItems();
        boolean amd = amd() && state(h).policy().preferExternal() && state(h).policy().enabled(CompatTestSupport.AMD);
        h.assertTrue(hidden.contains(AlexsButchery.id("raw_bear_meat")) == amd, "bear meat is hidden only when every bear yields the addon's: " + hidden);
        h.assertTrue(hidden.contains(AlexsButchery.id("cooked_bear_meat")) == amd, "its cooked form follows");
        h.assertTrue(!hidden.contains(AlexsButchery.id("raw_reptile_meat")), "reptile meat stays visible: rattlesnakes and komodos still yield it");
        h.assertTrue(!hidden.contains(AlexsButchery.id("raw_game_bird")), "game bird stays visible");
        h.assertTrue(!hidden.contains(AlexsButchery.id("raw_mantis_shrimp_tail")), "the shrimp tail stays: old carcasses without a variant yield it");
        h.assertTrue(state(h).view().hiddenItems().equals(hidden), "clients are told the same set");
        h.succeed();
    }

    private static int stage(GameTestHelper h) {
        var state = h.getBlockState(ORIGIN);
        return ((AbstractCarcassBlock) state.getBlock()).stage(state);
    }
}
