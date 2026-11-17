package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.SkinRackBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.block.entity.SkinRackBlockEntity;
import com.otectus.alexsbutchery.butcher.Acid;
import com.otectus.alexsbutchery.def.BloodClass;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import net.mcreator.butchery.configuration.ButcheryconfigConfiguration;
import net.minecraft.advancements.critereon.EnchantmentPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.otectus.alexsbutchery.test.TestSupport.ORIGIN;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryBlock;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryItem;
import static com.otectus.alexsbutchery.test.TestSupport.clearItems;
import static com.otectus.alexsbutchery.test.TestSupport.count;
import static com.otectus.alexsbutchery.test.TestSupport.player;
import static com.otectus.alexsbutchery.test.TestSupport.use;

/**
 * Processing state, blood, tools, permissions and the skin rack (review findings AB-05 to AB-10): acid excludes every
 * other process, a carried carcass resumes instead of starting over, hanging and the rack takeover obey placement rules,
 * instant and timed bleeding pay the same blood, the cut that breaks a tool pays like any other, and a rack destroyed any
 * way pays its rack and contents once.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessSafetyTests {
    private static final MobDef KANGAROO = MobDefs.byId("kangaroo");
    private static final BlockPos HOOK = ORIGIN.above(2);
    private static final BlockPos HANGING = ORIGIN.above(1);

    // ---- AB-05: acid is exclusive ----------------------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void dissolvingCarcassRefusesCleaverAndStillBecomesSkeleton(GameTestHelper h) {
        var player = player(h);
        h.setBlock(HOOK, butcheryBlock("hook").defaultBlockState());
        h.setBlock(HANGING, ModBlocks.of(KANGAROO).drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("bottle_of_sulfuric_acid"));
        use(h, HANGING, player);
        h.assertTrue(carcass(h, HANGING).isDissolving(), "acid starts dissolving");
        int clock = carcass(h, HANGING).acidTicks();
        ItemStack cleaver = butcheryItem("iron_cleaver");
        player.setItemInHand(InteractionHand.MAIN_HAND, cleaver);
        InteractionResult result = use(h, HANGING, player);
        h.assertTrue(result == InteractionResult.CONSUME, "the cleaver's click is consumed, got " + result);
        h.assertTrue(h.getBlockState(HANGING).getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == 1, "no stage advanced while dissolving");
        h.assertTrue(count(h, ModItems.of(KANGAROO).head().get()) == 0, "no head was cut off a dissolving carcass");
        h.assertTrue(cleaver.getDamageValue() == 0, "the refused cleaver did not wear");
        h.assertTrue(carcass(h, HANGING).acidTicks() == clock, "the acid clock is untouched");
        // Polled, not timed: a test's chunk can start ticking a few ticks after the test does.
        h.startSequence().thenWaitUntil(() -> h.assertTrue(h.getBlockState(HANGING).is(ModBlocks.of(KANGAROO).skeleton().get()),
                "the whole carcass still becomes the skeleton")).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void dissolvingCarcassRefusesBleedingKnifeAndMoreAcid(GameTestHelper h) {
        var player = TestSupport.advancementPlayer(h);
        MobDef elephant = MobDefs.byId("elephant");
        h.setBlock(ORIGIN, butcheryBlock("blood_grate").defaultBlockState());
        h.setBlock(HANGING, ModBlocks.of(elephant).carcass().get().defaultBlockState());
        ItemStack acid = butcheryItem("bottle_of_sulfuric_acid");
        acid.setCount(2);
        player.setItemInHand(InteractionHand.MAIN_HAND, acid);
        use(h, HANGING, player);
        h.assertTrue(acid.getCount() == 1, "the first bottle is poured");
        int clock = carcass(h, HANGING).acidTicks();
        use(h, HANGING, player);
        h.assertTrue(acid.getCount() == 1, "a second bottle is refused");
        h.assertTrue(carcass(h, HANGING).acidTicks() == clock, "and does not restart the clock");
        ItemStack cleaver = butcheryItem("iron_cleaver");
        player.setItemInHand(InteractionHand.MAIN_HAND, cleaver);
        use(h, HANGING, player);
        h.assertTrue(!carcass(h, HANGING).isBleeding(), "a dissolving floor carcass does not start bleeding");
        h.assertTrue(grate(h, ORIGIN) == 0, "and pays no blood");
        h.assertTrue(!advancementDone(h, player, AlexsButchery.id("where_it_lies")), "nor grants the floor-bleeding advancement");
        ItemStack knife = butcheryItem("iron_skinning_knife");
        player.setItemInHand(InteractionHand.MAIN_HAND, knife);
        use(h, HANGING, player);
        h.assertTrue(knife.getDamageValue() == 0 && h.getBlockState(HANGING).is(ModBlocks.of(elephant).carcass().get()),
                "a skinning knife does nothing either");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bleedingCarcassRefusesAcid(GameTestHelper h) {
        var player = player(h);
        h.setBlock(HOOK, butcheryBlock("hook").defaultBlockState());
        h.setBlock(HANGING, ModBlocks.of(KANGAROO).carcass().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        use(h, HANGING, player);
        h.assertTrue(carcass(h, HANGING).isBleeding(), "the carcass bleeds");
        ItemStack acid = butcheryItem("bottle_of_sulfuric_acid");
        player.setItemInHand(InteractionHand.MAIN_HAND, acid);
        use(h, HANGING, player);
        h.assertTrue(!carcass(h, HANGING).isDissolving() && acid.getCount() == 1, "acid is refused while it bleeds");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void savedCarcassWithBleedingAndAcidKeepsOnlyTheAcid(GameTestHelper h) {
        h.setBlock(ORIGIN, ModBlocks.of(KANGAROO).carcass().get().defaultBlockState());
        CompoundTag legacy = new CompoundTag();
        legacy.putBoolean("Bleeding", true);
        legacy.putInt("BleedTicks", 300);
        legacy.putInt("Fills", 6);
        legacy.putInt("AcidTicks", 120);
        CarcassBlockEntity carcass = carcass(h, ORIGIN);
        carcass.load(legacy);
        h.assertTrue(carcass.isDissolving() && carcass.acidTicks() == 120, "the acid already working is kept");
        h.assertTrue(!carcass.isBleeding() && carcass.fills() == 0 && carcass.bleedTicks() == 0, "the contradictory bleeding is dropped");
        h.assertTrue(!carcass.getPersistentData().getBoolean("isBleeding"), "and Butchery's hint flag with it");
        CompoundTag nonsense = new CompoundTag();
        nonsense.putBoolean("Bleeding", true);
        nonsense.putInt("Fills", 9999);
        nonsense.putInt("BleedTicks", -5);
        carcass.load(nonsense);
        h.assertTrue(carcass.fills() == KANGAROO.blood().fills && carcass.bleedTicks() == 0, "fills and clock are clamped to the blood class");
        CompoundTag damaged = new CompoundTag();
        damaged.putInt("Fills", 3);
        damaged.putInt("BleedTicks", 90);
        carcass.load(damaged);
        h.assertTrue(carcass.isBleeding() && carcass.fills() == 3 && carcass.bleedTicks() == 90,
                "paid progress survives a damaged payload whose Bleeding flag was lost");
        carcass.load(new CompoundTag());
        h.assertTrue(!carcass.isBleeding() && carcass.fills() == 0, "a legitimate legacy no-field payload remains fresh");
        h.setBlock(ORIGIN.east(2), ModBlocks.of(KANGAROO).drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 7));
        carcass(h, ORIGIN.east(2)).load(legacy);
        h.assertTrue(!carcass(h, ORIGIN.east(2)).isDissolving() && !carcass(h, ORIGIN.east(2)).isBleeding(),
                "a skinned carcass keeps neither an acid clock nor bleeding");
        h.succeed();
    }

    // ---- AB-06: a carried carcass resumes --------------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void carriedBleedingCarcassPaysOneCarcassOfBlood(GameTestHelper h) {
        var player = player(h);
        h.setBlock(HOOK, butcheryBlock("hook").defaultBlockState());
        h.setBlock(ORIGIN, butcheryBlock("blood_grate").defaultBlockState());
        h.setBlock(HANGING, ModBlocks.of(KANGAROO).carcass().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, 1));
        CompoundTag look = new CompoundTag();
        look.putInt("Variant", 1);
        carcass(h, HANGING).setMobData(look);
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        use(h, HANGING, player);
        BlockPos floor = ORIGIN.east(2);
        ItemStack[] carried = new ItemStack[1];
        int[] paid = new int[1];
        h.startSequence()
                // Two fills or more, then picked up mid-way.
                .thenWaitUntil(() -> h.assertTrue(grate(h, ORIGIN) >= 100, "two fills before the pickup, got " + grate(h, ORIGIN)))
                .thenExecute(() -> {
                    paid[0] = grate(h, ORIGIN);
                    carried[0] = breakAndTake(h, HANGING, ModItems.of(KANGAROO).carcass().get());
                    CompoundTag saved = BlockItem.getBlockEntityData(carried[0]);
                    h.assertTrue(saved != null && saved.getBoolean("Bleeding") && saved.getInt("Fills") * 50 == paid[0],
                            "the carried carcass keeps its bleeding and the " + paid[0] / 50 + " fills it paid: " + saved);
                    h.assertTrue(saved.getCompound(CarcassBlockEntity.MOB_DATA).getInt("Variant") == 1, "and its look");
                    // Put down on the ground, a hookable carcass waits to be hung again instead of bleeding there.
                    h.setBlock(floor, ModBlocks.of(KANGAROO).carcass().get().defaultBlockState());
                    BlockItem.updateCustomBlockEntityTag(h.getLevel(), null, h.absolutePos(floor), carried[0]);
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    h.assertTrue(carcass(h, floor).isBleeding() && carcass(h, floor).fills() * 50 == paid[0], "paused on the ground with its fills");
                    ItemStack again = breakAndTake(h, floor, ModItems.of(KANGAROO).carcass().get());
                    hang(h, player, again);
                    var placed = h.getBlockEntity(HANGING);
                    h.assertTrue(placed instanceof CarcassBlockEntity,
                            "re-hanging installed no carcass block entity; state=" + h.getBlockState(HANGING) + ", stack=" + again);
                    CarcassBlockEntity resumed = (CarcassBlockEntity) placed;
                    h.assertTrue(resumed.isBleeding() && resumed.fills() * 50 == paid[0], "re-hung, it resumes where it stopped");
                })
                .thenWaitUntil(() -> h.assertTrue(h.getBlockState(HANGING).is(ModBlocks.of(KANGAROO).drained().get()), "it drained"))
                .thenExecute(() -> h.assertTrue(grate(h, ORIGIN) == BloodClass.REGULAR.millibuckets(),
                        "exactly one carcass of blood in total, got " + grate(h, ORIGIN)))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void removingHangingSupportPausesAndRestoringItResumes(GameTestHelper h) {
        h.setBlock(ORIGIN, butcheryBlock("blood_grate").defaultBlockState());
        h.setBlock(HOOK, butcheryBlock("hook").defaultBlockState());
        h.setBlock(HANGING, ModBlocks.of(KANGAROO).carcass().get().defaultBlockState()
                .setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, 1));
        CompoundTag progress = new CompoundTag();
        progress.putBoolean("Bleeding", true);
        progress.putInt("BleedTicks", KANGAROO.blood().intervalTicks - 1);
        progress.putInt("Fills", 2);
        CompoundTag look = new CompoundTag();
        look.putInt("Variant", 1);
        progress.put(CarcassBlockEntity.MOB_DATA, look);
        carcass(h, HANGING).load(progress);
        h.setBlock(HOOK, Blocks.AIR.defaultBlockState());

        int pausedTicks = carcass(h, HANGING).bleedTicks();
        int pausedFills = carcass(h, HANGING).fills();
        h.startSequence()
                .thenIdle(100)
                .thenExecute(() -> {
                    CarcassBlockEntity paused = carcass(h, HANGING);
                    h.assertTrue(paused.isBleeding() && paused.bleedTicks() == pausedTicks && paused.fills() == pausedFills,
                            "the unsupported hanging pose preserves its clock and paid fills");
                    h.assertTrue(paused.mobData().getInt("Variant") == 1 && grate(h, ORIGIN) == 0,
                            "pausing preserves appearance and emits no blood");
                    h.setBlock(HOOK, butcheryBlock("hook").defaultBlockState());
                })
                .thenWaitUntil(() -> h.assertTrue(grate(h, ORIGIN) == 50, "restored support resumes the next owed fill"))
                .thenExecute(() -> h.assertTrue(carcass(h, HANGING).fills() == pausedFills + 1,
                        "resume advances from the preserved fill count without restarting"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void carriedDissolvingCarcassResumesItsAcid(GameTestHelper h) {
        var player = player(h);
        h.setBlock(ORIGIN, ModBlocks.of(KANGAROO).carcass().get().defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("bottle_of_sulfuric_acid"));
        use(h, ORIGIN, player);
        BlockPos down = ORIGIN.east(2);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(carcass(h, ORIGIN).acidTicks() < Acid.DISSOLVE_TICKS - 20, "the acid has worked a while"))
                .thenExecute(() -> {
                    ItemStack carried = breakAndTake(h, ORIGIN, ModItems.of(KANGAROO).carcass().get());
                    CompoundTag saved = BlockItem.getBlockEntityData(carried);
                    int left = saved == null ? 0 : saved.getInt("AcidTicks");
                    h.assertTrue(left > 0 && left < Acid.DISSOLVE_TICKS - 20, "the acid clock travels with the carcass, " + left + " left");
                    h.setBlock(down, ModBlocks.of(KANGAROO).carcass().get().defaultBlockState());
                    BlockItem.updateCustomBlockEntityTag(h.getLevel(), null, h.absolutePos(down), carried);
                    h.assertTrue(carcass(h, down).acidTicks() == left, "and carries on where it was put down");
                })
                .thenWaitUntil(() -> h.assertTrue(h.getBlockState(down).is(ModBlocks.of(KANGAROO).skeleton().get()), "it dissolves without a second bottle"))
                .thenSucceed();
    }

    // ---- AB-07: placement permissions --------------------------------------------------------------------------

    @GameTest(template = "empty")
    public static void adventurePlayerCannotHangWithoutCanPlaceOn(GameTestHelper h) {
        var player = TestSupport.advancementPlayer(h);
        grantParents(h, player);
        h.setBlock(HOOK, butcheryBlock("hook").defaultBlockState());
        player.setGameMode(GameType.ADVENTURE);
        ItemStack carcass = new ItemStack(ModItems.of(KANGAROO).carcass().get());
        player.setItemInHand(InteractionHand.MAIN_HAND, carcass);
        InteractionResult result = useItemOn(h, player, HOOK, Direction.SOUTH);
        h.assertTrue(!result.consumesAction(), "the hanging is refused, got " + result);
        h.assertTrue(h.getBlockState(HANGING).isAir(), "nothing hangs under the hook");
        h.assertTrue(carcass.getCount() == 1, "the carcass stays in hand");
        h.assertTrue(!advancementDone(h, player, new ResourceLocation("butchery", "hangingaround")), "no advancement");

        // A new stack: vanilla caches each stack's last CanPlaceOn answer per block.
        ListTag allowed = new ListTag();
        allowed.add(StringTag.valueOf("butchery:hook"));
        carcass = new ItemStack(ModItems.of(KANGAROO).carcass().get());
        carcass.getOrCreateTag().put("CanPlaceOn", allowed);
        player.setItemInHand(InteractionHand.MAIN_HAND, carcass);
        result = useItemOn(h, player, HOOK, Direction.SOUTH);
        h.assertTrue(result.consumesAction(), "with CanPlaceOn naming the hook it hangs, got " + result);
        h.assertTrue(h.getBlockState(HANGING).is(ModBlocks.of(KANGAROO).carcass().get()), "the carcass hangs");
        h.assertTrue(carcass.isEmpty(), "and was used");
        h.assertTrue(advancementDone(h, player, new ResourceLocation("butchery", "hangingaround")), "now the advancement follows");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void spectatorCannotHang(GameTestHelper h) {
        var player = TestSupport.advancementPlayer(h);
        h.setBlock(HOOK, butcheryBlock("hook").defaultBlockState());
        player.setGameMode(GameType.SPECTATOR);
        ItemStack carcass = new ItemStack(ModItems.of(KANGAROO).carcass().get());
        player.setItemInHand(InteractionHand.MAIN_HAND, carcass);
        useItemOn(h, player, HOOK, Direction.DOWN);
        h.assertTrue(h.getBlockState(HANGING).isAir() && carcass.getCount() == 1, "spectators never hang carcasses");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalAndCreativeHangThroughTheGameMode(GameTestHelper h) {
        var player = TestSupport.advancementPlayer(h);
        h.setBlock(HOOK, butcheryBlock("hook").defaultBlockState());
        ItemStack carcass = new ItemStack(ModItems.of(KANGAROO).carcass().get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, carcass);
        h.assertTrue(useItemOn(h, player, HOOK, Direction.NORTH).consumesAction(), "a survival player hangs it");
        h.assertTrue(carcass.getCount() == 1 && h.getBlockState(HANGING).getValue(AbstractCarcassBlock.BLOCKSTATE_FRESH) == 1, "one used, hanging");
        h.setBlock(HANGING, Blocks.AIR.defaultBlockState());
        player.setGameMode(GameType.CREATIVE);
        h.assertTrue(useItemOn(h, player, HOOK, Direction.NORTH).consumesAction(), "a creative player hangs it");
        h.assertTrue(carcass.getCount() == 1, "creative keeps the stack");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void protectedDestinationUnderUsableHookIsRolledBack(GameTestHelper h) {
        var player = TestSupport.advancementPlayer(h);
        grantParents(h, player);
        BlockPos rope = HOOK;
        h.setBlock(rope, butcheryBlock("rope").defaultBlockState());
        IntegerProperty ropeStage = (IntegerProperty) h.getBlockState(rope).getBlock().getStateDefinition().getProperty("blockstate");
        BlockPos destination = h.absolutePos(HANGING);
        Consumer<BlockEvent.EntityPlaceEvent> protect = event -> {
            if (event.getPos().equals(destination)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, protect);
        ItemStack carcass = new ItemStack(ModItems.of(KANGAROO).carcass().get());
        player.setItemInHand(InteractionHand.MAIN_HAND, carcass);
        InteractionResult result;
        try {
            result = useItemOn(h, player, rope, Direction.SOUTH);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(protect);
        }
        h.assertTrue(!result.consumesAction(), "a protection mod refusing the destination refuses the hanging, got " + result);
        h.assertTrue(h.getBlockState(HANGING).isAir(), "the placement was rolled back");
        h.assertTrue(carcass.getCount() == 1, "the carcass came back to the stack");
        h.assertTrue(ropeStage == null || h.getBlockState(rope).getValue(ropeStage) == 0, "the rope did not advance");
        h.assertTrue(!advancementDone(h, player, new ResourceLocation("butchery", "hangingaround")), "no advancement");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void placementCallbackReplacementIsNotRolledBackAsOurs(GameTestHelper h) {
        var player = TestSupport.advancementPlayer(h);
        h.setBlock(HOOK, butcheryBlock("hook").defaultBlockState());
        ItemStack stack = new ItemStack(ModItems.of(KANGAROO).carcass().get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos destination = h.absolutePos(HANGING);
        CarcassBlockEntity[] replacement = new CarcassBlockEntity[1];
        Consumer<BlockEvent.EntityPlaceEvent> replace = event -> {
            if (!event.getPos().equals(destination)) return;
            BlockState state = h.getLevel().getBlockState(destination);
            h.getLevel().removeBlock(destination, false);
            h.getLevel().setBlock(destination, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
            replacement[0] = (CarcassBlockEntity) h.getLevel().getBlockEntity(destination);
            CompoundTag marker = new CompoundTag();
            marker.putBoolean("CallbackOwned", true);
            replacement[0].setMobData(marker);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, replace);
        InteractionResult result;
        try {
            result = useItemOn(h, player, HOOK, Direction.SOUTH);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(replace);
        }
        h.assertTrue(!result.consumesAction(), "replacement makes our hanging transaction fail, got " + result);
        h.assertTrue(stack.getCount() == 1, "our item was restored");
        h.assertTrue(replacement[0] != null && h.getBlockEntity(HANGING) == replacement[0]
                        && replacement[0].mobData().getBoolean("CallbackOwned"),
                "the callback's newer same-block/state block entity was preserved");
        h.assertTrue(h.getBlockState(HOOK).is(butcheryBlock("hook")), "the hook was not changed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledRightClickAndFullRopeLeaveEverything(GameTestHelper h) {
        var player = player(h);
        h.setBlock(HOOK, butcheryBlock("rope").defaultBlockState());
        IntegerProperty ropeStage = (IntegerProperty) h.getBlockState(HOOK).getBlock().getStateDefinition().getProperty("blockstate");
        h.setBlock(HOOK, h.getBlockState(HOOK).setValue(ropeStage, 3));
        ItemStack carcass = new ItemStack(ModItems.of(KANGAROO).carcass().get());
        player.setItemInHand(InteractionHand.MAIN_HAND, carcass);
        BlockPos abs = h.absolutePos(HOOK);
        var full = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, abs, new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false));
        MinecraftForge.EVENT_BUS.post(full);
        h.assertTrue(!full.isCanceled() && h.getBlockState(HANGING).isAir() && carcass.getCount() == 1, "a full rope takes nothing");
        h.setBlock(HOOK, butcheryBlock("hook").defaultBlockState());
        var canceled = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, abs, new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false));
        canceled.setCanceled(true);
        MinecraftForge.EVENT_BUS.post(canceled);
        h.assertTrue(h.getBlockState(HANGING).isAir() && carcass.getCount() == 1, "an already canceled click hangs nothing");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void refusedRackTakeoverKeepsButcheryRackAndSkin(GameTestHelper h) {
        var player = player(h);
        h.setBlock(ORIGIN, butcheryBlock("skin_rack").defaultBlockState());
        BlockState original = h.getBlockState(ORIGIN);
        BlockPos abs = h.absolutePos(ORIGIN);
        Consumer<BlockEvent.EntityPlaceEvent> protect = event -> {
            if (event.getPos().equals(abs)) event.setCanceled(true);
        };
        ItemStack pelt = new ItemStack(ModItems.simple("tiger_pelt").get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pelt);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, protect);
        try {
            MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, abs,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false)));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(protect);
        }
        h.assertTrue(h.getBlockState(ORIGIN) == original, "Butchery's rack is back as it was");
        h.assertTrue(pelt.getCount() == 1, "the pelt stays in hand");
        h.assertTrue(TestSupport.items(h).isEmpty(), "nothing dropped while rolling back");

        var adventurer = TestSupport.advancementPlayer(h);
        adventurer.setGameMode(GameType.ADVENTURE);
        ItemStack another = new ItemStack(ModItems.simple("tiger_pelt").get());
        adventurer.setItemInHand(InteractionHand.MAIN_HAND, another);
        useItemOn(h, adventurer, ORIGIN, Direction.SOUTH);
        h.assertTrue(h.getBlockState(ORIGIN) == original && another.getCount() == 1, "Adventure mode cannot take the rack over");
        h.succeed();
    }

    // ---- AB-08: instant bleeding pays the same blood -----------------------------------------------------------

    @GameTest(template = "empty")
    public static void instantBleedingFillsEachBloodClassBudget(GameTestHelper h) {
        withInstantBleed(() -> {
            // A small, a regular and a large (floor) carcass, each over its own grate.
            checkInstant(h, MobDefs.byId("raccoon"), new BlockPos(0, 1, 0), true);
            checkInstant(h, KANGAROO, new BlockPos(2, 1, 2), true);
            checkInstant(h, MobDefs.byId("elephant"), new BlockPos(4, 1, 4), false);
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void instantBleedingOfAPartlyBledCarcassPaysOnlyTheRest(GameTestHelper h) {
        h.setBlock(HOOK, butcheryBlock("hook").defaultBlockState());
        h.setBlock(ORIGIN, butcheryBlock("blood_grate").defaultBlockState());
        h.setBlock(HANGING, ModBlocks.of(KANGAROO).carcass().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, 1));
        CompoundTag partial = new CompoundTag();
        partial.putBoolean("Bleeding", true);
        partial.putInt("BleedTicks", 450);
        partial.putInt("Fills", 10);
        carcass(h, HANGING).load(partial);
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        withInstantBleed(() -> use(h, HANGING, player));
        h.assertTrue(h.getBlockState(HANGING).is(ModBlocks.of(KANGAROO).drained().get()), "it is drained");
        h.assertTrue(grate(h, ORIGIN) == 500, "only the ten fills still owed, got " + grate(h, ORIGIN));
        h.succeed();
    }

    private static void checkInstant(GameTestHelper h, MobDef def, BlockPos grate, boolean hanging) {
        h.setBlock(grate, butcheryBlock("blood_grate").defaultBlockState());
        BlockPos at = grate.above();
        if (hanging) h.setBlock(at.above(), butcheryBlock("hook").defaultBlockState());
        h.setBlock(at, ModBlocks.of(def).carcass().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, hanging ? 1 : 0));
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        use(h, at, player);
        h.assertTrue(h.getBlockState(at).is(ModBlocks.of(def).drained().get()), def.id() + " drained at once");
        h.assertTrue(grate(h, grate) == def.blood().millibuckets(), def.id() + ": " + def.blood().millibuckets() + " mB expected, got " + grate(h, grate));
    }

    // ---- AB-09: the breaking cut pays like any other -----------------------------------------------------------

    @GameTest(template = "empty")
    public static void lootingCleaverThatBreaksStillRollsEveryLevel(GameTestHelper h) {
        withLooting(() -> {
            var player = player(h);
            h.setBlock(HOOK, butcheryBlock("hook").defaultBlockState());
            h.setBlock(HANGING, ModBlocks.of(KANGAROO).drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 1));
            ItemStack cleaver = lootingTool("iron_cleaver", true);
            player.setItemInHand(InteractionHand.MAIN_HAND, cleaver);
            use(h, HANGING, player);
            h.assertTrue(cleaver.isEmpty(), "the last durability point broke the cleaver");
            h.assertTrue(h.getBlockState(HANGING).getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == 2, "one stage was cut");
            long heads = count(h, ModItems.of(KANGAROO).head().get());
            h.assertTrue(heads == 4, "Looting III still rolled four times on the breaking cut, got " + heads);
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void toolPredicatesSeeTheToolBeforeItBreaks(GameTestHelper h) {
        LootTable diamonds = LootTable.lootTable().withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.DIAMOND))
                .when(MatchTool.toolMatches(ItemPredicate.Builder.item().hasEnchantment(
                        new EnchantmentPredicate(Enchantments.MOB_LOOTING, MinMaxBounds.Ints.atLeast(3)))))).build();
        withLooting(() -> CompatTestSupport.withLootTables(h, Map.of(KANGAROO.stageTable("skin"), diamonds), () -> {
            var player = player(h);
            h.setBlock(HOOK, butcheryBlock("hook").defaultBlockState());
            h.setBlock(HANGING, ModBlocks.of(KANGAROO).drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 2));
            ItemStack knife = lootingTool("iron_skinning_knife", true);
            player.setItemInHand(InteractionHand.MAIN_HAND, knife);
            use(h, HANGING, player);
            h.assertTrue(knife.isEmpty(), "the knife broke on this skinning");
            h.assertTrue(count(h, Items.DIAMOND) == 4, "a match_tool table saw the Looting III knife on every roll, got " + count(h, Items.DIAMOND));
            ItemStack stick = new ItemStack(Items.STICK);
            player.setItemInHand(InteractionHand.MAIN_HAND, stick);
            h.assertTrue(use(h, HANGING, player) == InteractionResult.PASS, "a stick is not a butchering tool");
        }));
        h.succeed();
    }

    private static ItemStack lootingTool(String id, boolean lastUse) {
        ItemStack tool = butcheryItem(id);
        tool.enchant(Enchantments.MOB_LOOTING, 3);
        if (lastUse) tool.setDamageValue(tool.getMaxDamage() - 1);
        return tool;
    }

    // ---- AB-10: the rack pays once, whoever destroys it ----------------------------------------------------

    @GameTest(template = "empty")
    public static void rackDestroyedWithoutAPlayerDropsRackAndSkin(GameTestHelper h) {
        takeOverRack(h, ORIGIN);
        h.getLevel().destroyBlock(h.absolutePos(ORIGIN), true, null);
        h.assertTrue(count(h, butcheryItem("skin_rack").getItem()) == 1, "Butchery's rack drops");
        h.assertTrue(count(h, ModItems.simple("tiger_pelt").get()) == 1, "and the raw pelt, once");
        clearItems(h);
        takeOverRack(h, ORIGIN);
        ((SkinRackBlockEntity) h.getBlockEntity(ORIGIN)).setStage(SkinRackBlock.STAGE_DONE);
        h.getLevel().destroyBlock(h.absolutePos(ORIGIN), true, null);
        h.assertTrue(count(h, Items.LEATHER) == 1 && count(h, ModItems.simple("tiger_pelt").get()) == 0, "a cured rack drops the leather");
        h.assertTrue(count(h, butcheryItem("skin_rack").getItem()) == 1, "and the rack");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rackPaysOnceForPlayersAndCollection(GameTestHelper h) {
        var survivor = TestSupport.advancementPlayer(h);
        takeOverRack(h, ORIGIN);
        survivor.gameMode.destroyBlock(h.absolutePos(ORIGIN));
        h.assertTrue(count(h, butcheryItem("skin_rack").getItem()) == 1 && count(h, ModItems.simple("tiger_pelt").get()) == 1,
                "a survival break drops rack and pelt once each: " + TestSupport.items(h));
        clearItems(h);
        var creative = TestSupport.advancementPlayer(h);
        creative.setGameMode(GameType.CREATIVE);
        takeOverRack(h, ORIGIN);
        creative.gameMode.destroyBlock(h.absolutePos(ORIGIN));
        h.assertTrue(count(h, ModItems.simple("tiger_pelt").get()) == 1 && count(h, butcheryItem("skin_rack").getItem()) == 0,
                "a creative break gives back the pelt only");
        clearItems(h);
        takeOverRack(h, ORIGIN);
        ((SkinRackBlockEntity) h.getBlockEntity(ORIGIN)).setStage(SkinRackBlock.STAGE_DONE);
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(h, ORIGIN, player);
        h.assertTrue(h.getBlockState(ORIGIN).is(butcheryBlock("skin_rack")), "taking the leather puts Butchery's rack back");
        h.assertTrue(count(h, Items.LEATHER) == 1 && count(h, butcheryItem("skin_rack").getItem()) == 0, "one leather and no extra rack");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void explodedRackDropsItsSkin(GameTestHelper h) {
        takeOverRack(h, ORIGIN);
        BlockPos abs = h.absolutePos(ORIGIN);
        // What an explosion asks of the block: its drops with the explosion's radius (the rack survives 1/radius of them).
        int pelts = 0, racks = 0;
        for (int roll = 0; roll < 40; roll++) {
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, Vec3.atCenterOf(abs))
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, ItemStack.EMPTY)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.EXPLOSION_RADIUS, 4F)
                    .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY, h.getBlockEntity(ORIGIN));
            for (ItemStack drop : h.getBlockState(ORIGIN).getDrops(params)) {
                if (drop.is(ModItems.simple("tiger_pelt").get())) pelts += drop.getCount();
                if (drop.is(butcheryItem("skin_rack").getItem())) racks += drop.getCount();
            }
        }
        h.assertTrue(pelts == 40, "every exploded rack gives its pelt back, like a container, got " + pelts + "/40");
        h.assertTrue(racks > 0 && racks < 40, "the rack itself follows the explosion's survival roll, got " + racks + "/40");
        h.succeed();
    }

    private static void takeOverRack(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, butcheryBlock("skin_rack").defaultBlockState());
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.simple("tiger_pelt").get()));
        BlockPos abs = h.absolutePos(rel);
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, abs,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false)));
        h.assertTrue(h.getBlockState(rel).is(ModBlocks.SKIN_RACK.get()), "our rack took over");
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    static CarcassBlockEntity carcass(GameTestHelper h, BlockPos rel) {
        return (CarcassBlockEntity) h.getBlockEntity(rel);
    }

    static int grate(GameTestHelper h, BlockPos rel) {
        return h.getBlockEntity(rel).getCapability(ForgeCapabilities.FLUID_HANDLER)
                .map(handler -> handler.getFluidInTank(0).getAmount()).orElse(-1);
    }

    /** Breaks the block with drops, as a non-player destruction would, and takes the one dropped {@code item}. */
    static ItemStack breakAndTake(GameTestHelper h, BlockPos rel, Item item) {
        clearItems(h);
        h.getLevel().destroyBlock(h.absolutePos(rel), true, null);
        List<ItemEntity> drops = TestSupport.items(h).stream().filter(e -> e.getItem().is(item)).toList();
        if (drops.size() != 1) throw new net.minecraft.gametest.framework.GameTestAssertException("expected one " + item + ", got " + TestSupport.items(h));
        ItemStack stack = drops.get(0).getItem().copy();
        TestSupport.items(h).forEach(ItemEntity::discard);
        return stack;
    }

    /** Hangs {@code stack} under {@link #HOOK} the way a click on the hook does. */
    static void hang(GameTestHelper h, net.minecraft.world.entity.player.Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = h.absolutePos(HOOK);
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, abs,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false)));
    }

    /** The real server path of a right-click on a block: Forge's event, the block, then the item. */
    static InteractionResult useItemOn(GameTestHelper h, ServerPlayer player, BlockPos rel, Direction face) {
        BlockPos abs = h.absolutePos(rel);
        return player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).relative(face, .5), face, abs, false));
    }

    static void grantParents(GameTestHelper h, ServerPlayer player) {
        for (String id : new String[]{"butcher", "firstcarcass"}) {
            var advancement = h.getLevel().getServer().getAdvancements().getAdvancement(new ResourceLocation("butchery", id));
            if (advancement == null) throw new IllegalStateException("No advancement butchery:" + id);
            var progress = player.getAdvancements().getOrStartProgress(advancement);
            for (String criterion : progress.getRemainingCriteria()) player.getAdvancements().award(advancement, criterion);
        }
    }

    static boolean advancementDone(GameTestHelper h, ServerPlayer player, ResourceLocation id) {
        var advancement = h.getLevel().getServer().getAdvancements().getAdvancement(id);
        if (advancement == null) throw new IllegalStateException("No advancement " + id);
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static void withInstantBleed(Runnable body) {
        boolean old = ButcheryconfigConfiguration.INSTANT_BLEED.get();
        ButcheryconfigConfiguration.INSTANT_BLEED.set(true);
        try {
            body.run();
        } finally {
            ButcheryconfigConfiguration.INSTANT_BLEED.set(old);
        }
    }

    private static void withLooting(Runnable body) {
        boolean old = ButcheryconfigConfiguration.LOOTING_ENCHANT.get();
        ButcheryconfigConfiguration.LOOTING_ENCHANT.set(true);
        try {
            body.run();
        } finally {
            ButcheryconfigConfiguration.LOOTING_ENCHANT.set(old);
        }
    }

    private ProcessSafetyTests() {}
}
