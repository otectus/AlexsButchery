package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.butcher.KillHandler;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.otectus.alexsbutchery.test.TestSupport.alexsMob;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryItem;
import static com.otectus.alexsbutchery.test.TestSupport.count;

/**
 * Where a floor carcass comes to rest when its mob dies standing on or in something other than a full block. The
 * block the mob stood in must survive, and a kill with nowhere to put the carcass keeps the mob's ordinary drops.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FloorPlacementTests {
    private static final BlockPos COLUMN = new BlockPos(2, 1, 2);

    @GameTest(template = "empty")
    public static void slabSurvivesAndCarcassRestsOnIt(GameTestHelper h) {
        standOn(h, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), .5, COLUMN.above());
    }

    @GameTest(template = "empty")
    public static void dirtPathSurvives(GameTestHelper h) {
        standOn(h, Blocks.DIRT_PATH.defaultBlockState(), .9375, COLUMN.above());
    }

    @GameTest(template = "empty")
    public static void soulSandSurvives(GameTestHelper h) {
        standOn(h, Blocks.SOUL_SAND.defaultBlockState(), .875, COLUMN.above());
    }

    @GameTest(template = "empty")
    public static void carpetSurvives(GameTestHelper h) {
        standOn(h, Blocks.WHITE_CARPET.defaultBlockState(), .0625, COLUMN.above());
    }

    @GameTest(template = "empty")
    public static void deepSnowSurvives(GameTestHelper h) {
        standOn(h, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4), .375, COLUMN.above());
    }

    /** A single snow layer is replaceable, as it is for a falling block: the carcass takes its place. */
    @GameTest(template = "empty")
    public static void thinSnowIsReplaced(GameTestHelper h) {
        standOn(h, Blocks.SNOW.defaultBlockState(), 0, COLUMN);
    }

    /** Sinks through two blocks of water to the bottom, replacing only the water it lands in. */
    @GameTest(template = "empty")
    public static void sinksThroughWater(GameTestHelper h) {
        h.setBlock(COLUMN.below(), Blocks.STONE);
        h.setBlock(COLUMN, Blocks.WATER);
        h.setBlock(COLUMN.above(), Blocks.WATER);
        LivingEntity elephant = kill(h, new Vec3(2.5, 2.2, 2.5));
        assertOnlyCarcassAt(h, COLUMN);
        h.assertBlockState(COLUMN.above(), state -> state.is(Blocks.WATER), () -> "the water above is untouched");
        h.assertTrue(!elephant.isAlive(), "the elephant died");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sinksThroughLava(GameTestHelper h) {
        h.setBlock(COLUMN.below(), Blocks.STONE);
        h.setBlock(COLUMN, Blocks.LAVA);
        h.setBlock(COLUMN.above(), Blocks.LAVA);
        kill(h, new Vec3(2.5, 2.2, 2.5));
        assertOnlyCarcassAt(h, COLUMN);
        h.succeed();
    }

    /** Killed in the air: comes to rest on the ground straight below, with no falling entity left behind. */
    @GameTest(template = "empty")
    public static void midAirKillRestsOnTheGround(GameTestHelper h) {
        h.setBlock(COLUMN.below(), Blocks.STONE);
        kill(h, new Vec3(2.5, 3.6, 2.5));
        assertOnlyCarcassAt(h, COLUMN);
        h.succeed();
    }

    /** Entombed with no space above: no carcass, no block replaced, the drops stay ordinary. */
    @GameTest(template = "empty")
    public static void entombedKillKeepsItsDrops(GameTestHelper h) {
        h.setBlock(COLUMN.below(), Blocks.STONE);
        h.setBlock(COLUMN, Blocks.STONE);
        h.setBlock(COLUMN.above(), Blocks.STONE);
        kill(h, new Vec3(2.5, 1.1, 2.5));
        h.assertTrue(carcassBlocks(h).isEmpty(), "no carcass without a place for it");
        h.assertBlockState(COLUMN, state -> state.is(Blocks.STONE), () -> "the stone it died in is untouched");
        h.assertBlockState(COLUMN.above(), state -> state.is(Blocks.STONE), () -> "and the stone above");
        h.succeed();
    }

    /** Nothing below the build limit can hold a carcass. */
    @GameTest(template = "empty")
    public static void noRestingPlaceBelowTheWorld(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos below = new BlockPos(h.absolutePos(COLUMN).getX(), level.getMinBuildHeight() - 3, h.absolutePos(COLUMN).getZ());
        h.assertTrue(KillHandler.restingPlace(level, below) == null, "no resting place under the world");
        h.succeed();
    }

    /** Stands the elephant on {@code support} (its collision top at {@code top}), kills it and checks the result. */
    private static void standOn(GameTestHelper h, BlockState support, double top, BlockPos expected) {
        h.setBlock(COLUMN.below(), Blocks.STONE);
        h.setBlock(COLUMN, support);
        LivingEntity elephant = kill(h, new Vec3(2.5, 1 + top, 2.5));
        h.assertTrue(!elephant.isAlive(), "the elephant died");
        assertOnlyCarcassAt(h, expected);
        if (!expected.equals(COLUMN))
            h.assertBlockState(COLUMN, state -> state == support, () -> support + " was replaced");
        h.succeed();
    }

    private static LivingEntity kill(GameTestHelper h, Vec3 at) {
        var player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        LivingEntity elephant = (LivingEntity) h.spawn(alexsMob("elephant"), at);
        TestSupport.kill(h, elephant, player);
        return elephant;
    }

    private static void assertOnlyCarcassAt(GameTestHelper h, BlockPos expected) {
        List<BlockPos> found = carcassBlocks(h);
        h.assertTrue(found.equals(List.of(expected)), "carcass expected only at " + expected + ", found " + found);
        h.assertTrue(h.getBlockEntity(expected) instanceof CarcassBlockEntity carcass && !carcass.mobData().isEmpty(),
                "the carcass keeps the mob's snapshot");
        h.assertTrue(count(h, ModItems.of(MobDefs.byId("elephant")).carcass().get()) == 0, "floor carcasses are never items");
        h.assertTrue(h.getLevel().getEntitiesOfClass(FallingBlockEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(8)).isEmpty(),
                "no falling block entity");
    }

    private static List<BlockPos> carcassBlocks(GameTestHelper h) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 0, 0), new BlockPos(4, 4, 4)))
            if (h.getBlockState(pos).is(ModBlocks.of(MobDefs.byId("elephant")).carcass().get())) found.add(pos.immutable());
        return found;
    }

    private FloorPlacementTests() {}
}
