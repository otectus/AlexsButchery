package com.otectus.alexsbutchery.test;

import com.mojang.authlib.GameProfile;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.block.CarcassTargeting;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CarcassTargetingTests {
    private static final BlockPos CENTRE = new BlockPos(32, 16, 32);

    private static ServerPlayer player(GameTestHelper h) {
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "BoundsTest"));
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) {}
        };
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** Approach an outer surface from its exposed side, without shooting down through the hanging support. */
    private static BlockHitResult aim(GameTestHelper h, ServerPlayer player, BlockPos pos, double distance) {
        var boxes = CarcassBounds.boxes(h.getLevel().getBlockState(pos), h.getLevel(), pos);
        AABB far = boxes.stream().max(Comparator.comparingDouble(b -> Math.pow(b.getCenter().x - .5, 2) + Math.pow(b.getCenter().z - .5, 2))).orElseThrow();
        boolean alongX = Math.abs(far.getCenter().x - .5) > Math.abs(far.getCenter().z - .5);
        boolean positive = (alongX ? far.getCenter().x : far.getCenter().z) > .5;
        Vec3 local = alongX ? new Vec3(positive ? far.maxX + distance : far.minX - distance, far.getCenter().y, far.getCenter().z)
                : new Vec3(far.getCenter().x, far.getCenter().y, positive ? far.maxZ + distance : far.minZ - distance);
        Vec3 eye = Vec3.atLowerCornerOf(pos).add(local);
        player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
        player.setYRot(alongX ? (positive ? 90 : -90) : (positive ? 180 : 0)); player.setXRot(0);
        return CarcassTargeting.pick(h.getLevel(), eye, eye.add(player.getLookAngle().scale(player.getBlockReach())), player);
    }

    @GameTest(template = "carcass_space", timeoutTicks = 200)
    public static void visibleAnatomyAcrossStagesAndFacings(GameTestHelper h) {
        var player = player(h);
        var pos = h.absolutePos(CENTRE);
        for (String id : new String[]{"laviathan", "elephant", "cachalot_whale", "anaconda", "kangaroo", "void_worm", "centipede", "farseer"}) {
            var def = MobDefs.byId(id);
            var entry = ModBlocks.of(def);
            var block = (AbstractCarcassBlock) (entry.drained() == null ? entry.carcass().get() : entry.drained().get());
            for (int stage : block.stateProperty().getPossibleValues()) for (Direction facing : Direction.Plane.HORIZONTAL) {
                var state = block.defaultBlockState().setValue(block.stateProperty(), stage).setValue(AbstractCarcassBlock.FACING, facing);
                h.getLevel().setBlockAndUpdate(pos, state);
                h.getLevel().setBlockAndUpdate(pos.above(), block.hanging(state) ? TestSupport.butcheryBlock("hook").defaultBlockState() : Blocks.AIR.defaultBlockState());
                var hit = aim(h, player, pos, 2);
                h.assertTrue(hit != null && hit.getBlockPos().equals(pos), id + "/" + stage + "/" + facing + " anatomy selectable");
                h.assertTrue(CarcassTargeting.canReach(player, pos), "server validates visible anatomy");
                h.assertTrue(state.getCollisionShape(h.getLevel(), pos).isEmpty(), "carcasses remain walk-through");
            }
        }
        h.succeed();
    }

    @GameTest(template = "carcass_space", timeoutTicks = 200)
    public static void distantAnchorUsesVanillaButcheryPacketsAndDrops(GameTestHelper h) {
        var player = player(h);
        var pos = h.absolutePos(CENTRE);
        var def = MobDefs.byId("laviathan");
        h.getLevel().setBlockAndUpdate(pos, ModBlocks.of(def).drained().get().defaultBlockState());
        int sequence = 0;
        for (var action : Stages.actions(def)) {
            var hit = aim(h, player, pos, 2);
            h.assertTrue(hit != null, "remaining stage anatomy selectable");
            h.assertTrue(!player.canReach(pos, 1.5), "anchor lies beyond the old server reach check");
            player.setItemInHand(InteractionHand.MAIN_HAND, TestSupport.butcheryItem(action.needsKnife() ? "iron_skinning_knife" : "iron_cleaver"));
            player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, sequence++));
            int next = Stages.doneState(action, false, false);
            h.assertTrue(next == Stages.REMOVED ? h.getLevel().isEmptyBlock(pos)
                    : h.getLevel().getBlockState(pos).getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == next, "packet performs " + action);
        }
        h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).isEmpty(), "normal stage loot dropped");
        h.succeed();
    }

    @GameTest(template = "carcass_space")
    public static void rayStartingOnOuterSurfaceKeepsInsideHit(GameTestHelper h) {
        var pos = h.absolutePos(CENTRE);
        var state = ModBlocks.of(MobDefs.byId("laviathan")).carcass().get().defaultBlockState();
        h.getLevel().setBlockAndUpdate(pos, state);
        var outer = CarcassBounds.boxes(state, h.getLevel(), pos).stream().max(Comparator.comparingDouble(b -> b.maxX)).orElseThrow();
        Vec3 eye = Vec3.atLowerCornerOf(pos).add(outer.maxX, outer.getCenter().y, outer.getCenter().z);
        var hit = CarcassTargeting.pick(h.getLevel(), eye, eye.add(-3, 0, 0), player(h));
        h.assertTrue(hit != null && hit.getBlockPos().equals(pos) && hit.isInside(),
                "an inward ray starting on the maximum face survives broad-phase rejection");
        h.succeed();
    }

    @GameTest(template = "carcass_space", timeoutTicks = 200)
    public static void forgedOccludedAndOutOfReachActionsAreRejected(GameTestHelper h) {
        var player = player(h);
        var pos = h.absolutePos(CENTRE);
        var state = ModBlocks.of(MobDefs.byId("laviathan")).drained().get().defaultBlockState();
        h.getLevel().setBlockAndUpdate(pos, state);
        player.setItemInHand(InteractionHand.MAIN_HAND, TestSupport.butcheryItem("iron_cleaver"));
        var hit = aim(h, player, pos, 2);
        h.assertTrue(hit != null && CarcassTargeting.validHit(player, pos, hit.getLocation()), "valid distant surface accepted");
        var forged = new BlockHitResult(hit.getLocation().add(12, 0, 0), hit.getDirection(), pos, false);
        player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, forged, 0));
        h.assertTrue(h.getLevel().getBlockState(pos) == state, "forged surface coordinates rejected");
        var wall = BlockPos.containing(player.getEyePosition().add(player.getLookAngle()));
        h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 1));
        h.assertTrue(h.getLevel().getBlockState(pos) == state && !CarcassTargeting.canReach(player, pos), "wall blocks interaction");
        h.getLevel().removeBlock(wall, false);
        aim(h, player, pos, player.getBlockReach() + 1);
        player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 2));
        h.assertTrue(h.getLevel().getBlockState(pos) == state, "out-of-reach interaction rejected");
        player.setGameMode(GameType.CREATIVE);
        aim(h, player, pos, player.getBlockReach() + 1);
        player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, h.getLevel().getMaxBuildHeight(), 3);
        h.assertTrue(h.getLevel().getBlockState(pos) == state, "out-of-reach breaking rejected");
        aim(h, player, pos, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, h.getLevel().getMaxBuildHeight(), 4);
        h.assertTrue(h.getLevel().isEmptyBlock(pos), "visible anatomy breaks through vanilla game mode");
        h.succeed();
    }

    /** A protection mod cancelling the use or the break still wins for a distant anchor; without it the same packets act. */
    @GameTest(template = "carcass_space", timeoutTicks = 100)
    public static void protectionEventsStillCancelDistantActions(GameTestHelper h) {
        var player = player(h);
        var pos = h.absolutePos(CENTRE);
        var def = MobDefs.byId("laviathan");
        var state = ModBlocks.of(def).drained().get().defaultBlockState();
        h.getLevel().setBlockAndUpdate(pos, state);
        player.setItemInHand(InteractionHand.MAIN_HAND, TestSupport.butcheryItem(Stages.actions(def).get(0).needsKnife() ? "iron_skinning_knife" : "iron_cleaver"));
        var hit = aim(h, player, pos, 2);
        h.assertTrue(hit != null && !player.canReach(pos, 1.5), "a far overhang, beyond the anchor reach check");
        java.util.function.Consumer<net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock> denyUse =
                e -> { if (e.getPos().equals(pos)) e.setCanceled(true); };
        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent> denyBreak =
                e -> { if (e.getPos().equals(pos)) e.setCanceled(true); };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(denyUse);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(denyBreak);
        try {
            player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 0));
            h.assertTrue(h.getLevel().getBlockState(pos) == state, "a cancelled use leaves the carcass alone");
            player.setGameMode(GameType.CREATIVE);
            player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, h.getLevel().getMaxBuildHeight(), 1);
            h.assertTrue(h.getLevel().getBlockState(pos) == state, "a cancelled break leaves the carcass alone");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(denyUse);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(denyBreak);
        }
        player.setGameMode(GameType.SURVIVAL);
        aim(h, player, pos, 2);
        player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 2));
        h.assertTrue(h.getLevel().getBlockState(pos) != state, "without the protection the same use acts");
        h.succeed();
    }

    @GameTest(template = "carcass_space")
    public static void snapshotAndBoundsSurviveBlockEntityReload(GameTestHelper h) {
        var pos = h.absolutePos(CENTRE);
        var state = ModBlocks.of(MobDefs.byId("catfish")).carcass().get().defaultBlockState().setValue(AbstractCarcassBlock.FACING, Direction.WEST);
        h.getLevel().setBlockAndUpdate(pos, state);
        var be = (CarcassBlockEntity) h.getLevel().getBlockEntity(pos);
        var data = new net.minecraft.nbt.CompoundTag(); data.putInt("CatfishSize", 2); be.setMobData(data);
        var boxes = CarcassBounds.boxes(state, h.getLevel(), pos);
        var saved = be.saveWithFullMetadata();
        h.getLevel().removeBlockEntity(pos);
        h.getLevel().setBlockEntity(BlockEntity.loadStatic(pos, state, saved));
        h.assertTrue(boxes.equals(CarcassBounds.boxes(state, h.getLevel(), pos)), "variant and rotated bounds survive reload");
        h.assertTrue(aim(h, player(h), pos, 2) != null, "reloaded carcass remains targetable");
        h.succeed();
    }

    /** Every form, stage and appearance variant the game can place has exported anatomy: no one-block fallback. */
    @GameTest(template = "empty")
    public static void boundsCoverEveryCarcassFormStageAndVariant(GameTestHelper h) {
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (var def : MobDefs.all()) {
            var entry = ModBlocks.of(def);
            for (var holder : new Object[]{entry.carcass(), entry.drained(), entry.skeleton()}) {
                if (holder == null) continue;
                var block = (AbstractCarcassBlock) ((net.minecraftforge.registries.RegistryObject<?>) holder).get();
                for (int stage : block.stateProperty().getPossibleValues()) for (var data : variants(def.id())) {
                    var state = block.defaultBlockState().setValue(block.stateProperty(), stage);
                    checked++;
                    if (!CarcassBounds.exported(state, data)) missing.add(CarcassBounds.key(state, data));
                }
            }
        }
        h.assertTrue(missing.isEmpty(), "no exported bounds for " + missing);
        h.assertTrue(checked > 1000, "checked " + checked + " forms");
        h.succeed();
    }

    /** A pose profile, dependency or geometry change without a fresh export cannot ship unnoticed. */
    @GameTest(template = "empty")
    public static void boundsWereExportedFromTheseInputs(GameTestHelper h) {
        var exported = CarcassBounds.exportInputs();
        var current = CarcassBounds.currentInputs();
        h.assertTrue(current.equals(exported), "carcass_bounds.json was exported from " + exported + ", sources are now " + current
                + "; re-run runClient -PsmokeTest -PexportBounds");
        h.succeed();
    }

    /** The appearance variants {@link CarcassBounds#variant} distinguishes, as the bounds export renders them. */
    private static List<CompoundTag> variants(String mob) {
        List<CompoundTag> data = new ArrayList<>();
        data.add(new CompoundTag());
        java.util.function.Consumer<java.util.function.Consumer<CompoundTag>> add = edit -> { var nbt = new CompoundTag(); edit.accept(nbt); data.add(nbt); };
        switch (mob) {
            case "catfish" -> { add.accept(n -> n.putInt("CatfishSize", 1)); add.accept(n -> n.putInt("CatfishSize", 2)); }
            case "elephant" -> add.accept(n -> n.putBoolean("Tusked", true));
            case "blobfish" -> add.accept(n -> n.putBoolean("Depressurized", true));
            case "bison" -> add.accept(n -> n.putInt("Age", -24000));
            case "gorilla" -> add.accept(n -> n.putBoolean("Silverback", true));
            case "gelada_monkey" -> add.accept(n -> n.putBoolean("Leader", true));
            case "leafcutter_ant" -> add.accept(n -> n.putBoolean("Queen", true));
            case "flutter" -> add.accept(n -> n.putBoolean("Potted", true));
            default -> {}
        }
        return data;
    }

    /** On a slab or path the lying anatomy sinks to the support's top, for selection exactly as for rendering. */
    @GameTest(template = "carcass_space")
    public static void partialSupportLowersSelection(GameTestHelper h) {
        var pos = h.absolutePos(CENTRE);
        var state = ModBlocks.of(MobDefs.byId("laviathan")).carcass().get().defaultBlockState();
        h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos, state);
        var full = CarcassBounds.geometry(state, h.getLevel(), pos).envelope();
        for (var support : new net.minecraft.world.level.block.state.BlockState[]{Blocks.SMOOTH_STONE_SLAB.defaultBlockState(),
                Blocks.DIRT_PATH.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState(), Blocks.WHITE_CARPET.defaultBlockState()}) {
            h.getLevel().setBlockAndUpdate(pos.below(), support);
            double top = support.getCollisionShape(h.getLevel(), pos.below()).max(net.minecraft.core.Direction.Axis.Y);
            var lowered = CarcassBounds.geometry(state, h.getLevel(), pos).envelope();
            h.assertTrue(Math.abs(lowered.minY - (full.minY + top - 1)) < 1E-9 && Math.abs(lowered.maxX - full.maxX) < 1E-9,
                    support + " lowers the anatomy by " + (1 - top));
            // A level ray just above the support's surface, under the full-block anatomy, now meets the carcass.
            Vec3 eye = Vec3.atLowerCornerOf(pos).add(lowered.maxX + 2, top - 1 + .03, .5);
            var hit = CarcassTargeting.pick(h.getLevel(), eye, eye.add(-4, 0, 0), player(h));
            h.assertTrue(hit != null && hit.getBlockPos().equals(pos), support + ": the lowered body is selectable");
        }
        h.getLevel().setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
        h.assertTrue(CarcassBounds.geometry(state, h.getLevel(), pos).envelope().equals(full), "nothing below: the block's own height");
        h.succeed();
    }

    /** The index finds a carcass placed anywhere, forgets one that was removed, and finds one placed again. */
    @GameTest(template = "carcass_space")
    public static void indexFollowsPlacementRemovalAndReload(GameTestHelper h) {
        var player = player(h);
        var pos = h.absolutePos(CENTRE);
        var state = ModBlocks.of(MobDefs.byId("elephant")).carcass().get().defaultBlockState();
        h.getLevel().setBlockAndUpdate(pos, state);
        h.assertTrue(aim(h, player, pos, 2) != null, "placed carcass is found");
        h.getLevel().removeBlock(pos, false);
        Vec3 eye = player.getEyePosition();
        h.assertTrue(CarcassTargeting.pick(h.getLevel(), eye, eye.add(player.getLookAngle().scale(5)), player) == null,
                "removed carcass is not found");
        h.getLevel().setBlockAndUpdate(pos, ModBlocks.of(MobDefs.byId("elephant")).drained().get().defaultBlockState());
        h.assertTrue(aim(h, player, pos, 2) != null, "a carcass placed again is found");
        // A block entity replaced by a reload of its saved data, as on chunk reload.
        var saved = h.getLevel().getBlockEntity(pos).saveWithFullMetadata();
        h.getLevel().removeBlockEntity(pos);
        h.getLevel().setBlockEntity(BlockEntity.loadStatic(pos, h.getLevel().getBlockState(pos), saved));
        h.assertTrue(aim(h, player, pos, 2) != null, "reloaded carcass is found");
        h.succeed();
    }

    @GameTest(template = "carcass_space", timeoutTicks = 100)
    public static void survivalMiningKeepsTimingAndSingleDrop(GameTestHelper h) {
        var player = player(h);
        var pos = h.absolutePos(CENTRE);
        var def = MobDefs.byId("laviathan");
        var block = ModBlocks.of(def).carcass().get();
        h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        aim(h, player, pos, 2);
        player.setOnGround(true);
        player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, h.getLevel().getMaxBuildHeight(), 0);
        h.assertTrue(h.getLevel().getBlockState(pos).is(block), "survival mining is not instantaneous");
        h.onEachTick(player.gameMode::tick);
        h.runAfterDelay(45, () -> {
            player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.UP, h.getLevel().getMaxBuildHeight(), 1);
            h.assertTrue(h.getLevel().isEmptyBlock(pos), "normal mining duration breaks distant anchor");
            long drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                    .filter(e -> e.getItem().is(block.asItem())).mapToLong(e -> e.getItem().getCount()).sum();
            h.assertTrue(drops == 1, "exactly one carcass drop");
            h.succeed();
        });
    }
}
