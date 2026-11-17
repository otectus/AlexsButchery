package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.butcher.CarcassPolicy;
import com.otectus.alexsbutchery.config.ServerConfig;
import com.otectus.alexsbutchery.def.BloodClass;
import com.otectus.alexsbutchery.def.DeathSpawnMode;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.otectus.alexsbutchery.test.TestSupport.ORIGIN;
import static com.otectus.alexsbutchery.test.TestSupport.alexsMob;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryItem;
import static com.otectus.alexsbutchery.test.TestSupport.count;

/** Death representation, independent ground processing and failure preservation for the resolved per-world policy. */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CarcassPolicyTests {
    private static final MobDef ELEPHANT = MobDefs.byId("elephant");
    private static final MobDef MOOSE = MobDefs.byId("moose");
    private static final MobDef BISON = MobDefs.byId("bison");

    @GameTest(template = "empty")
    public static void policyPrecedenceUsesActualEntityIdsAndKeepsBiologyIndependent(GameTestHelper h) {
        List<String> diagnostics = new ArrayList<>();
        CarcassPolicy legacy = CarcassPolicy.resolve(CarcassPolicy.GlobalDeathSpawnMode.SPECIES_DEFAULT,
                List.of(), List.of(), diagnostics::add);
        h.assertTrue(legacy.deathSpawnMode(ELEPHANT) == DeathSpawnMode.PLACED_BLOCK && legacy.canBleedOnGround(ELEPHANT),
                "legacy elephant stays a ground-bleeding placed block");
        h.assertTrue(legacy.deathSpawnMode(MOOSE) == DeathSpawnMode.ITEM && !legacy.canBleedOnGround(MOOSE),
                "legacy moose stays a hang-first item");

        CarcassPolicy configured = CarcassPolicy.resolve(CarcassPolicy.GlobalDeathSpawnMode.PLACED_BLOCK,
                List.of("alexsmobs:moose=ITEM", "alexsmobs:elephant=ITEM", "alexsmobs:centipede_head=PLACED_BLOCK"),
                List.of("alexsmobs:moose"), diagnostics::add);
        h.assertTrue(configured.deathSpawnMode(MOOSE) == DeathSpawnMode.ITEM,
                "an explicit ITEM exception wins over the global block mode");
        h.assertTrue(configured.deathSpawnMode(ELEPHANT) == DeathSpawnMode.ITEM,
                "an existing giant can independently become an item at death");
        h.assertTrue(configured.deathSpawnMode(MobDefs.byId("centipede")) == DeathSpawnMode.PLACED_BLOCK,
                "the definition key centipede resolves through its actual centipede_head entity ID");
        h.assertTrue(configured.canBleedOnGround(MOOSE), "ground bleeding is a separate additive option");
        h.assertTrue(MOOSE.hookable() && MOOSE.blood() == BloodClass.REGULAR,
                "representation and ground processing did not change moose hookability or blood class");
        CarcassPolicy frozenLegacy = CarcassPolicy.fromSnapshot(legacy.snapshot());
        CarcassPolicy frozenConfigured = CarcassPolicy.fromSnapshot(configured.snapshot());
        h.assertTrue(frozenLegacy.deathSpawnMode(BISON) == DeathSpawnMode.ITEM
                        && frozenConfigured.deathSpawnMode(BISON) == DeathSpawnMode.PLACED_BLOCK
                        && frozenConfigured.canBleedOnGround(MOOSE),
                "resolved snapshots preserve each world's frozen policy independently of later raw config parsing");

        diagnostics.clear();
        CarcassPolicy invalid = CarcassPolicy.resolve(CarcassPolicy.GlobalDeathSpawnMode.SPECIES_DEFAULT,
                List.of("moose=PLACED_BLOCK", "alexsmobs:unknown=ITEM", "alexsmobs:moose=PLACED_BLOCK", "alexsmobs:moose=ITEM", "alexsmobs:bison=NOPE"),
                List.of("alexsmobs:moose", "alexsmobs:moose", "alexsmobs:fly"), diagnostics::add);
        h.assertTrue(invalid.deathSpawnMode(MOOSE) == DeathSpawnMode.PLACED_BLOCK,
                "the first valid duplicate wins deterministically");
        h.assertTrue(diagnostics.stream().anyMatch(s -> s.contains("malformed entity ID"))
                        && diagnostics.stream().anyMatch(s -> s.contains("unsupported entity"))
                        && diagnostics.stream().anyMatch(s -> s.contains("duplicate/conflicting"))
                        && diagnostics.stream().anyMatch(s -> s.contains("invalid mode"))
                        && diagnostics.stream().anyMatch(s -> s.contains("bloodless")),
                "malformed, unknown, duplicate, invalid and irrelevant entries were all diagnosed: " + diagnostics);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void configuredMoosePlacementChangesOnlyDeathRepresentation(GameTestHelper h) {
        withPolicy(CarcassPolicy.GlobalDeathSpawnMode.SPECIES_DEFAULT,
                List.of("alexsmobs:moose=PLACED_BLOCK"), List.of(), () -> {
                    h.setBlock(ORIGIN.below(), Blocks.STONE);
                    var player = TestSupport.player(h);
                    player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
                    LivingEntity moose = (LivingEntity) h.spawn(alexsMob("moose"), ORIGIN);
                    TestSupport.kill(h, moose, player);
                    h.assertTrue(h.getBlockState(ORIGIN).is(ModBlocks.of(MOOSE).carcass().get()),
                            "the actual alexsmobs:moose override placed one carcass block");
                    h.assertTrue(h.getBlockEntity(ORIGIN) instanceof CarcassBlockEntity carcass && !carcass.mobData().isEmpty(),
                            "the placed block was initialized with the victim snapshot");
                    h.assertTrue(count(h, ModItems.of(MOOSE).carcass().get()) == 0, "the same kill did not also create an item");
                    h.assertTrue(MOOSE.hookable() && MOOSE.blood() == BloodClass.REGULAR,
                            "moose remains hookable and retains regular blood");
                });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void protectedDeathPlacementKeepsOrdinaryDropsAndLeavesNoCarcass(GameTestHelper h) {
        withPolicy(CarcassPolicy.GlobalDeathSpawnMode.SPECIES_DEFAULT, List.of(), List.of(), () -> {
            h.setBlock(ORIGIN.below(), Blocks.STONE);
            var player = TestSupport.player(h);
            player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
            LivingEntity elephant = (LivingEntity) h.spawn(alexsMob("elephant"), ORIGIN);
            BlockPos destination = h.absolutePos(ORIGIN);
            Consumer<BlockEvent.EntityPlaceEvent> protect = event -> {
                if (event.getPos().equals(destination)) event.setCanceled(true);
            };
            Consumer<LivingDropsEvent> ordinaryDrop = event -> {
                if (event.getEntity() == elephant) event.getDrops().add(new ItemEntity(h.getLevel(), elephant.getX(), elephant.getY(),
                        elephant.getZ(), new ItemStack(Items.DIAMOND)));
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, protect);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingDropsEvent.class, ordinaryDrop);
            try {
                TestSupport.kill(h, elephant, player);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(protect);
                MinecraftForge.EVENT_BUS.unregister(ordinaryDrop);
            }
            h.assertTrue(h.getBlockState(ORIGIN).isAir(), "the refused destination was rolled back to air");
            h.assertTrue(count(h, ModItems.of(ELEPHANT).carcass().get()) == 0, "failure did not fall back to a carcass item");
            h.assertTrue(count(h, Items.DIAMOND) == 1, "the unrelated ordinary death drop was preserved");
        });
        h.succeed();
    }

    private static void withPolicy(CarcassPolicy.GlobalDeathSpawnMode global, List<String> overrides, List<String> ground, Runnable body) {
        CarcassPolicy.GlobalDeathSpawnMode oldGlobal = ServerConfig.DEATH_SPAWN_MODE.get();
        List<? extends String> oldOverrides = List.copyOf(ServerConfig.DEATH_SPAWN_OVERRIDES.get());
        List<? extends String> oldGround = List.copyOf(ServerConfig.GROUND_BLEEDING_MOBS.get());
        try {
            ServerConfig.DEATH_SPAWN_MODE.set(global);
            ServerConfig.DEATH_SPAWN_OVERRIDES.set(overrides);
            ServerConfig.GROUND_BLEEDING_MOBS.set(ground);
            CarcassPolicy.reloadFromConfig();
            body.run();
        } finally {
            ServerConfig.DEATH_SPAWN_MODE.set(oldGlobal);
            ServerConfig.DEATH_SPAWN_OVERRIDES.set(oldOverrides);
            ServerConfig.GROUND_BLEEDING_MOBS.set(oldGround);
            CarcassPolicy.reloadFromConfig();
        }
    }

    private CarcassPolicyTests() {}
}
