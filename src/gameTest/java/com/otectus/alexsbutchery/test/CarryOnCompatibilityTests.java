package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.BiFunction;

/** Actual Carry On pickup/place checks; reflection keeps the ordinary no-Carry-On server free of linkage. */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CarryOnCompatibilityTests {
    private record Api(Class<?> carryType, Constructor<?> constructor, Method getData, Method setData, Method getBlock,
                       Method getNbt, Method setKeyPressed, Method isCarrying, Method pickup, Method place) {}
    private record Carried(CompoundTag serialized, CompoundTag tile) {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void actualPickupFailureSessionReloadAndPlacePreserveTwoCarcasses(GameTestHelper h) {
        if (!ModList.get().isLoaded("carryon")) {
            h.succeed();
            return;
        }
        try {
            Api api = api();
            BlockPos firstSource = new BlockPos(1, 1, 1), secondSource = new BlockPos(3, 1, 1);
            BlockPos firstDest = new BlockPos(1, 1, 3), secondDest = new BlockPos(3, 1, 3);
            prepare(h, firstSource, false, true, 0, 0, "first-player-only");
            prepare(h, secondSource, true, false, 123, 2, "second-player-only");
            ServerPlayer firstPlayer = TestSupport.advancementPlayer(h);
            ServerPlayer secondPlayer = TestSupport.advancementPlayer(h);
            firstPlayer.setPos(h.absolutePos(firstSource).getX() + .5, h.absolutePos(firstSource).getY(), h.absolutePos(firstSource).getZ() + 2.5);
            secondPlayer.setPos(h.absolutePos(secondSource).getX() + .5, h.absolutePos(secondSource).getY(), h.absolutePos(secondSource).getZ() + 2.5);

            Carried first = pickUp(h, api, firstPlayer, firstSource);
            Carried second = pickUp(h, api, secondPlayer, secondSource);
            h.assertTrue(h.getBlockState(firstSource).isAir() && h.getBlockState(secondSource).isAir(),
                    "the real pickup handler detached both source blocks");
            h.assertTrue(!first.tile().equals(second.tile()), "two actual players hold independent authoritative payloads");
            assertTile(h, first.tile(), false, 0, 0, "first-player-only");
            assertTile(h, second.tile(), true, 123, 2, "second-player-only");

            reload(h, api, firstPlayer, first.serialized());
            reload(h, api, secondPlayer, second.serialized());
            firstPlayer.setPos(firstPlayer.getX(), firstPlayer.getY() + 10, firstPlayer.getZ());
            secondPlayer.setPos(secondPlayer.getX(), secondPlayer.getY() + 10, secondPlayer.getZ());

            boolean refused = place(h, api, firstPlayer, firstDest, (pos, state) -> false);
            h.assertTrue(!refused && h.getBlockState(firstDest).isAir() && carrying(api, firstPlayer),
                    "a refused placement keeps both world and carried state unchanged");

            h.runAfterDelay(20, () -> {
                try {
                    h.assertTrue(place(h, api, firstPlayer, firstDest, (pos, state) -> true), "first real placement succeeds");
                    h.assertTrue(place(h, api, secondPlayer, secondDest, (pos, state) -> true), "second real placement succeeds");
                    h.assertTrue(!carrying(api, firstPlayer) && !carrying(api, secondPlayer), "successful placement clears each carrier");
                    CarcassBlockEntity one = (CarcassBlockEntity) h.getBlockEntity(firstDest);
                    CarcassBlockEntity two = (CarcassBlockEntity) h.getBlockEntity(secondDest);
                    h.assertTrue(h.getBlockState(firstDest).getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == 9,
                            "processed stage survives real pickup and placement");
                    h.assertTrue(!one.mobData().getBoolean("Antlered") && two.mobData().getBoolean("Antlered"),
                            "same-species appearance remains isolated after placement");
                    h.assertTrue(two.isBleeding() && two.bleedTicks() == 123 && two.fills() == 2,
                            "detached processing resumes from its captured values without catch-up");
                    h.assertTrue("first-player-only".equals(one.getPersistentData().getString("CarryUnknown"))
                                    && "second-player-only".equals(two.getPersistentData().getString("CarryUnknown")),
                            "unknown block-entity fields survive the real handler round trip");
                    h.succeed();
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Carry On 2.1.2.7 placement API changed", e);
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Carry On 2.1.2.7 pickup API changed", e);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 1600)
    public static void actualPickupOfHangingCarcassPausesUntilPlacedSupportReturns(GameTestHelper h) {
        if (!ModList.get().isLoaded("carryon")) {
            h.succeed();
            return;
        }
        try {
            Api api = api();
            var def = MobDefs.byId("moose");
            BlockPos sourceGrate = new BlockPos(1, 1, 1);
            BlockPos source = sourceGrate.above();
            BlockPos sourceHook = source.above();
            BlockPos destinationGrate = new BlockPos(3, 1, 3);
            BlockPos destination = destinationGrate.above();
            BlockPos destinationHook = destination.above();
            h.setBlock(sourceGrate, TestSupport.butcheryBlock("blood_grate").defaultBlockState());
            h.setBlock(destinationGrate, TestSupport.butcheryBlock("blood_grate").defaultBlockState());
            h.setBlock(sourceHook, TestSupport.butcheryBlock("hook").defaultBlockState());
            h.setBlock(source, ModBlocks.of(def).carcass().get().defaultBlockState()
                    .setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, 1));
            ServerPlayer player = TestSupport.advancementPlayer(h);
            player.setPos(h.absolutePos(source).getX() + .5, h.absolutePos(source).getY(), h.absolutePos(source).getZ() + 2.5);
            player.setItemInHand(InteractionHand.MAIN_HAND, TestSupport.butcheryItem("iron_cleaver"));
            TestSupport.use(h, source, player);
            h.assertTrue(((CarcassBlockEntity) h.getBlockEntity(source)).isBleeding(),
                    "a genuinely supported hanging carcass starts bleeding");
            player.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);

            Carried[] carried = new Carried[1];
            int[] pausedTicks = new int[1];
            int[] pausedFills = new int[1];
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(ProcessSafetyTests.grate(h, sourceGrate) >= 100,
                            "the supported source pays at least two fills before pickup"))
                    .thenExecute(() -> {
                        try {
                            carried[0] = pickUp(h, api, player, source);
                            h.assertTrue(h.getBlockState(source).isAir(), "Carry On detached the actively bleeding source");
                            player.setPos(player.getX(), player.getY() + 10, player.getZ());
                        } catch (ReflectiveOperationException e) {
                            throw new AssertionError("Carry On 2.1.2.7 pickup API changed", e);
                        }
                    })
                    // Carry On deliberately refuses pickup and placement in the same player tick.
                    .thenIdle(1)
                    .thenExecute(() -> {
                        try {
                            // This unregistered test player is not ticked by the player list.
                            player.tickCount++;
                            h.assertTrue(place(h, api, player, destination, (pos, state) -> true),
                                    "Carry On placed the carried carcass on the floor");
                            h.assertTrue(h.getBlockState(destination).getValue(AbstractCarcassBlock.BLOCKSTATE_FRESH) == 1,
                                    "the pinned Carry On version preserves the serialized hanging state");
                            h.assertTrue(h.getBlockState(destinationHook).isAir(), "the destination really has no support");
                            var placedBlockEntity = h.getBlockEntity(destination);
                            h.assertTrue(placedBlockEntity instanceof CarcassBlockEntity,
                                    "real Carry On placement installed no carcass block entity; state=" + h.getBlockState(destination));
                            CarcassBlockEntity placed = (CarcassBlockEntity) placedBlockEntity;
                            pausedTicks[0] = placed.bleedTicks();
                            pausedFills[0] = placed.fills();
                            h.assertTrue(placed.isBleeding() && pausedFills[0] >= 2,
                                    "real placement preserved active progress: " + carried[0].tile());
                        } catch (ReflectiveOperationException e) {
                            throw new AssertionError("Carry On 2.1.2.7 placement API changed", e);
                        }
                    })
                    .thenIdle(100)
                    .thenExecute(() -> {
                        var pausedBlockEntity = h.getBlockEntity(destination);
                        h.assertTrue(pausedBlockEntity instanceof CarcassBlockEntity,
                                "paused Carry On placement lost its block entity; state=" + h.getBlockState(destination));
                        CarcassBlockEntity paused = (CarcassBlockEntity) pausedBlockEntity;
                        h.assertTrue(paused.isBleeding() && paused.bleedTicks() == pausedTicks[0]
                                        && paused.fills() == pausedFills[0],
                                "unsupported state=1 placement pauses ticks and fills without clearing progress");
                        h.assertTrue(ProcessSafetyTests.grate(h, destinationGrate) == 0,
                                "the unsupported placement emitted no blood");
                        h.setBlock(destinationHook, TestSupport.butcheryBlock("hook").defaultBlockState());
                    })
                    .thenWaitUntil(() -> h.assertTrue(h.getBlockState(destination).is(ModBlocks.of(def).drained().get()),
                            "adding real support resumes and finishes bleeding"))
                    .thenExecute(() -> {
                        int total = ProcessSafetyTests.grate(h, sourceGrate) + ProcessSafetyTests.grate(h, destinationGrate);
                        h.assertTrue(total == def.blood().millibuckets(),
                                "pickup, pause and resume pay one carcass of blood, got " + total);
                    })
                    .thenSucceed();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Carry On 2.1.2.7 API changed", e);
        }
    }

    private static void prepare(GameTestHelper h, BlockPos pos, boolean antlered, boolean processed,
                                int bleedTicks, int fills, String unknown) {
        BlockState state = processed
                ? ModBlocks.of(MobDefs.byId("moose")).drained().get().defaultBlockState()
                        .setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 9)
                : ModBlocks.of(MobDefs.byId("moose")).carcass().get().defaultBlockState()
                        .setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, 1);
        h.setBlock(pos, state);
        CarcassBlockEntity entity = (CarcassBlockEntity) h.getBlockEntity(pos);
        CompoundTag saved = new CompoundTag();
        CompoundTag mob = new CompoundTag();
        mob.putBoolean("Antlered", antlered);
        saved.put(CarcassBlockEntity.MOB_DATA, mob);
        if (!processed) {
            saved.putBoolean("Bleeding", true);
            saved.putInt("BleedTicks", bleedTicks);
            saved.putInt("Fills", fills);
        }
        saved.put("ForgeData", new CompoundTag());
        saved.getCompound("ForgeData").putString("CarryUnknown", unknown);
        entity.load(saved);
    }

    private static Carried pickUp(GameTestHelper h, Api api, ServerPlayer player, BlockPos relative) throws ReflectiveOperationException {
        arm(api, player);
        boolean[] callback = {false};
        BiFunction<BlockState, BlockPos, Boolean> allowed = (state, pos) -> { callback[0] = true; return true; };
        boolean picked = (boolean) api.pickup().invoke(null, player, h.absolutePos(relative), h.getLevel(), allowed);
        h.assertTrue(picked && callback[0], "Carry On's real pickup and permission callback succeed");
        Object data = api.getData().invoke(null, player);
        CompoundTag serialized = ((CompoundTag) api.getNbt().invoke(data)).copy();
        return new Carried(serialized, serialized.getCompound("tile").copy());
    }

    private static void reload(GameTestHelper h, Api api, ServerPlayer player, CompoundTag serialized) throws ReflectiveOperationException {
        Object reloaded = api.constructor().newInstance(serialized.copy());
        api.setData().invoke(null, player, reloaded);
        player.tickCount++;
        h.assertTrue(carrying(api, player), "session-reloaded CarryOnData is still carrying the block");
    }

    private static void arm(Api api, ServerPlayer player) throws ReflectiveOperationException {
        Object data = api.getData().invoke(null, player);
        api.setKeyPressed().invoke(data, true);
        api.setData().invoke(null, player, data);
        player.tickCount++;
    }

    private static boolean place(GameTestHelper h, Api api, ServerPlayer player, BlockPos relative,
                                 BiFunction<BlockPos, BlockState, Boolean> permission) throws ReflectiveOperationException {
        return (boolean) api.place().invoke(null, player, h.absolutePos(relative), Direction.UP, permission);
    }

    private static boolean carrying(Api api, ServerPlayer player) throws ReflectiveOperationException {
        return (boolean) api.isCarrying().invoke(api.getData().invoke(null, player));
    }

    private static void assertTile(GameTestHelper h, CompoundTag tile, boolean antlered, int ticks, int fills, String unknown) {
        h.assertTrue(tile.getCompound(CarcassBlockEntity.MOB_DATA).getBoolean("Antlered") == antlered, "captured tile keeps MobData variant");
        if (ticks > 0) h.assertTrue(tile.getInt("BleedTicks") == ticks && tile.getInt("Fills") == fills, "captured tile keeps processing counters");
        h.assertTrue(unknown.equals(tile.getCompound("ForgeData").getString("CarryUnknown")), "captured tile keeps unknown fields");
    }

    private static Api api() throws ReflectiveOperationException {
        Class<?> carry = Class.forName("tschipp.carryon.common.carry.CarryOnData");
        Class<?> manager = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
        Class<?> pickup = Class.forName("tschipp.carryon.common.carry.PickupHandler");
        Class<?> placement = Class.forName("tschipp.carryon.common.carry.PlacementHandler");
        return new Api(carry, carry.getConstructor(CompoundTag.class), manager.getMethod("getCarryData", Player.class),
                manager.getMethod("setCarryData", Player.class, carry), carry.getMethod("getBlock"), carry.getMethod("getNbt"),
                carry.getMethod("setKeyPressed", boolean.class), carry.getMethod("isCarrying"),
                pickup.getMethod("tryPickUpBlock", ServerPlayer.class, BlockPos.class, net.minecraft.world.level.Level.class, BiFunction.class),
                placement.getMethod("tryPlaceBlock", ServerPlayer.class, BlockPos.class, Direction.class, BiFunction.class));
    }

    private CarryOnCompatibilityTests() {}
}
