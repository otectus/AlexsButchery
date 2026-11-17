package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.butcher.KillHandler;
import com.otectus.alexsbutchery.butcher.OfficialAddonGuard;
import com.otectus.alexsbutchery.config.ServerConfig;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.otectus.alexsbutchery.test.TestSupport.alexsMob;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryItem;

/**
 * Death drops the carcass pays again (review finding AB-11), with deterministic drop lists instead of random death
 * tables: each creature's own feathers, ink, bones, maggots, mushrooms and the tusklin's porkchops (raw, or cooked by a
 * burning death) go, for that creature only; independent rewards and stacks carrying item data stay; and nothing changes
 * when no carcass is made (a creature left to the official addon included) or {@code replaceMobDrops} is off.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpeciesDropTests {
    /** Per creature: what its Alex's Mobs death table drops that the carcass yields again (from the 1.22.9 jar). */
    private static final Map<String, List<String>> REPAID = Map.ofEntries(
            Map.entry("emu", List.of("alexsmobs:emu_feather", "minecraft:feather")),
            Map.entry("roadrunner", List.of("alexsmobs:roadrunner_feather", "minecraft:feather")),
            Map.entry("shoebill", List.of("minecraft:feather")), Map.entry("seagull", List.of("minecraft:feather")),
            Map.entry("crow", List.of("minecraft:feather")), Map.entry("blue_jay", List.of("minecraft:feather")),
            Map.entry("toucan", List.of("minecraft:feather")), Map.entry("potoo", List.of("minecraft:feather")),
            Map.entry("bald_eagle", List.of("minecraft:feather")),
            Map.entry("giant_squid", List.of("minecraft:ink_sac")), Map.entry("mimic_octopus", List.of("minecraft:ink_sac")),
            Map.entry("fly", List.of("alexsmobs:maggot")),
            Map.entry("bone_serpent", List.of("minecraft:bone")), Map.entry("skelewag", List.of("minecraft:bone")),
            Map.entry("soul_vulture", List.of("minecraft:bone")),
            Map.entry("bunfungus", List.of("minecraft:red_mushroom")),
            Map.entry("tusklin", List.of("minecraft:porkchop", "minecraft:cooked_porkchop")));

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void eachCreaturesRepaidPartsLeaveItsOwnDeathDrops(GameTestHelper h) {
        for (Map.Entry<String, List<String>> e : REPAID.entrySet()) {
            List<ItemStack> drops = new ArrayList<>();
            for (String id : e.getValue()) drops.add(new ItemStack(item(id), 3));
            ItemStack named = new ItemStack(item(e.getValue().get(0)));
            named.setHoverName(net.minecraft.network.chat.Component.literal("Keepsake"));
            drops.add(named);
            drops.add(new ItemStack(Items.EMERALD));
            if (OfficialAddonGuard.covers(MobDefs.byId(e.getKey()))) {
                // Left to the official addon (installed, deferred to): no carcass of ours, so nothing leaves its death drops.
                List<ItemStack> kept = kill(h, e.getKey(), butcheryItem("iron_cleaver"), drops);
                for (String id : e.getValue())
                    h.assertTrue(kept.stream().anyMatch(s -> s.is(item(id)) && !s.hasTag()), e.getKey() + " is the official addon's: " + id + " stays, kept " + kept);
                continue;
            }
            List<ItemStack> kept = carcassKill(h, e.getKey(), drops);
            for (String id : e.getValue()) {
                h.assertTrue(kept.stream().noneMatch(s -> s.is(item(id)) && !s.hasTag()), e.getKey() + ": " + id + " is the carcass's again, kept " + kept);
            }
            h.assertTrue(kept.stream().anyMatch(s -> s.is(item(e.getValue().get(0))) && s.hasTag()), e.getKey() + ": a stack carrying data stays");
            h.assertTrue(kept.stream().anyMatch(s -> s.is(Items.EMERALD)), e.getKey() + ": an independent reward stays");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void otherCreaturesKeepThoseParts(GameTestHelper h) {
        // A kangaroo's feathers, bones and ink are nobody's carcass product; the hummingbird's death table has no feathers.
        for (String mob : List.of("kangaroo", "hummingbird")) {
            List<ItemStack> kept = carcassKill(h, mob, List.of(new ItemStack(Items.FEATHER), new ItemStack(Items.BONE), new ItemStack(Items.INK_SAC),
                    new ItemStack(Items.PORKCHOP), new ItemStack(Items.RED_MUSHROOM)));
            for (Item item : List.of(Items.FEATHER, Items.BONE, Items.INK_SAC, Items.PORKCHOP, Items.RED_MUSHROOM))
                h.assertTrue(kept.stream().anyMatch(s -> s.is(item)), mob + " keeps " + item + ": " + kept);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void noCarcassOrNoReplacementKeepsEverything(GameTestHelper h) {
        List<ItemStack> sword = kill(h, "seagull", new ItemStack(Items.IRON_SWORD), List.of(new ItemStack(Items.FEATHER, 2)));
        h.assertTrue(sword.stream().anyMatch(s -> s.is(Items.FEATHER)), "a kill that makes no carcass keeps its feathers: " + sword);
        boolean replace = ServerConfig.REPLACE_MOB_DROPS.get();
        ServerConfig.REPLACE_MOB_DROPS.set(false);
        try {
            List<ItemStack> kept = carcassKill(h, "tusklin", List.of(new ItemStack(Items.PORKCHOP, 4)));
            h.assertTrue(kept.stream().anyMatch(s -> s.is(Items.PORKCHOP)), "replaceMobDrops=false keeps the porkchops: " + kept);
        } finally {
            ServerConfig.REPLACE_MOB_DROPS.set(replace);
        }
        h.succeed();
    }

    /** What stays of {@code drops} when a cleaver kill of {@code mob} makes a carcass, through the real drop handler. */
    private static List<ItemStack> carcassKill(GameTestHelper h, String mob, List<ItemStack> drops) {
        List<ItemStack> kept = kill(h, mob, butcheryItem("iron_cleaver"), drops);
        var def = MobDefs.byId(mob);
        if (def.deathSpawnMode() == com.otectus.alexsbutchery.def.DeathSpawnMode.ITEM)
            h.assertTrue(kept.stream().anyMatch(s -> s.is(ModItems.of(def).carcass().get())), mob + ": the kill made a carcass, " + kept);
        return kept;
    }

    private static List<ItemStack> kill(GameTestHelper h, String mob, ItemStack weapon, List<ItemStack> drops) {
        var player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
        var def = MobDefs.byId(mob);
        LivingEntity victim = (LivingEntity) h.spawn(alexsMob(def.entity().getPath()), new BlockPos(2, 1, 2));
        List<ItemEntity> entities = new ArrayList<>();
        for (ItemStack stack : drops) entities.add(new ItemEntity(h.getLevel(), victim.getX(), victim.getY(), victim.getZ(), stack.copy()));
        LivingDropsEvent event = new LivingDropsEvent(victim, h.getLevel().damageSources().playerAttack(player), entities, 0, true);
        KillHandler.onLivingDrops(event);
        List<ItemStack> kept = event.getDrops().stream().map(ItemEntity::getItem).toList();
        victim.discard();
        // A floor carcass (the giant squid) was placed as a block where the creature stood: clear it for the next one.
        h.getLevel().setBlock(h.absolutePos(new BlockPos(2, 1, 2)), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        return kept;
    }

    private static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null || item == Items.AIR) throw new IllegalStateException("no item " + id);
        return item;
    }

    private SpeciesDropTests() {}
}
