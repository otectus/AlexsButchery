package com.otectus.alexsbutchery.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.block.HeadBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.compat.NativeButchery;
import com.otectus.alexsbutchery.def.BloodClass;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.item.TrophyData;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A dev client against a real dedicated server ({@code -PremoteServer=host:port}; the client must be an operator
 * there). Places carcasses by command, then cuts, breaks and probes them the way a player does: the client's own pick
 * on overhanging anatomy, normal use and mining packets, and forged, out-of-reach and walled-off packets that the
 * server must refuse. The server decides every outcome, so success means client and server geometry agree.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class RemoteChecks {
    static final String SERVER = System.getProperty("alexsbutchery.smoketest.remoteServer", "");
    private static final String OUTPUT = System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY);
    private static final Map<String, Object> checks = new LinkedHashMap<>();
    private static final List<Step> steps = new ArrayList<>();
    private static int titleTicks, worldTicks, stepTick;
    private static boolean done;
    private static BlockPos target;

    private interface Step { boolean run(Minecraft mc, int tick); }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (SERVER.isEmpty() || OUTPUT == null || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player == null || mc.level == null) {
                if (mc.screen instanceof TitleScreen && ++titleTicks == 40)
                    ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(SERVER), new ServerData("remote", SERVER, false), false);
                if (titleTicks > 0 && worldTicks > 0) finish(mc, "disconnected");
                return;
            }
            mc.options.pauseOnLostFocus = false;
            if (++worldTicks == 60) plan(mc);
            if (worldTicks < 60) return;
            if (mc.screen != null) mc.setScreen(null);
            // A creative player standing on the ground loses flight on its next tick, so keep re-asserting it: after
            // each teleport the eye must stay exactly where it was aimed.
            if (mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying) {
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
            }
            if (steps.isEmpty()) { finish(mc, "complete"); return; }
            if (steps.get(0).run(mc, stepTick++)) { steps.remove(0); stepTick = 0; }
        } catch (Exception e) {
            AlexsButchery.LOGGER.error("REMOTE failed", e);
            checks.put("exception", e.toString());
            finish(mc, "exception");
        }
    }

    private static void command(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
    }

    /** The digest in the server's {@code /alexsbutchery compat status} reply. */
    private static volatile String serverDigest;
    private static final java.util.regex.Pattern DIGEST = java.util.regex.Pattern.compile("client view ([0-9a-f]{16})");

    /** The fluid amount in the server's {@code /data get block} reply for the grate, in mB. */
    private static volatile Integer grateMb;
    private static final java.util.regex.Pattern AMOUNT = java.util.regex.Pattern.compile("Amount: (\\d+)");

    @SubscribeEvent
    public static void onChat(net.minecraftforge.client.event.ClientChatReceivedEvent event) {
        if (SERVER.isEmpty()) return;
        String text = event.getMessage().getString();
        var m = DIGEST.matcher(text);
        if (m.find()) serverDigest = m.group(1);
        var amount = AMOUNT.matcher(text);
        if (text.contains("has the following block data") && amount.find()) grateMb = Integer.parseInt(amount.group(1));
    }

    private static void plan(Minecraft mc) {
        BlockPos start = mc.player.blockPosition();
        target = start.offset(0, 6, 35);
        // The client's view is the server's: same digest, and a real cut on the server drops what the view lists.
        steps.add((mc2, t) -> {
            if (t == 0) {
                checks.put("remote_view_received", com.otectus.alexsbutchery.compat.CompatView.received() != null);
                command(mc2, "alexsbutchery compat status");
            }
            if (t == 30) {
                var view = com.otectus.alexsbutchery.compat.CompatView.received();
                checks.put("remote_server_digest", String.valueOf(serverDigest));
                checks.put("remote_view_matches_server", view != null && view.digest().equals(serverDigest));
            }
            return t >= 30;
        });
        BlockPos bisonAt = start.offset(6, 6, 35);
        MobDef bison = MobDefs.byId("bison");
        String bisonId = BuiltInRegistries.BLOCK.getKey(ModBlocks.of(bison).drained().get()).toString();
        var bisonBlock = (AbstractCarcassBlock) ModBlocks.of(bison).drained().get();
        steps.add((mc2, t) -> {
            if (t == 0) {
                command(mc2, "gamemode creative");
                command(mc2, "setblock " + xyz(bisonAt) + " " + bisonId + "[facing=north,blockstate=7]");
                command(mc2, "kill @e[type=item,distance=..64]");
            }
            if (t == 10) command(mc2, "item replace entity @s weapon.mainhand with butchery:iron_cleaver");
            if (t == 15) aimAt(mc2, bisonAt);
            // As for the cuts above: the teleport lands a few ticks later, so pick until the carcass itself is hit.
            if (t >= 30 && t <= 60 && t % 5 == 0 && !Boolean.TRUE.equals(checks.get("remote_bison_cut_sent"))) {
                mc2.gameRenderer.pick(1F);
                if (mc2.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(bisonAt)) {
                    mc2.gameMode.useItemOn(mc2.player, InteractionHand.MAIN_HAND, hit);
                    checks.put("remote_bison_cut_sent", true);
                }
            }
            // The drops appear on the client as item entities and may be picked up soon after: record every one seen.
            if (Boolean.TRUE.equals(checks.get("remote_bison_cut_sent")) && t <= 100) {
                for (var item : mc2.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(bisonAt).inflate(6)))
                    bisonDrops.put(item.getId(), BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString());
            }
            if (t == 100) {
                BlockState state = mc2.level.getBlockState(bisonAt);
                checks.put("remote_bison_cut_applied", state.getBlock() == bisonBlock && bisonBlock.stage(state) == 8);
                var view = com.otectus.alexsbutchery.compat.CompatView.received();
                List<String> listed = view == null ? List.of() : CompatClientChecks.outputs(view, "bison", "cut_1");
                List<String> dropped = new ArrayList<>(bisonDrops.values());
                List<String> meats = List.of("butchery:raw_chuck_steak", "butchery:raw_ribeye_steak", "alexsmobsdelight:raw_bison_meat", "alexsdelight:raw_bison");
                List<String> droppedMeat = dropped.stream().filter(meats::contains).distinct().toList();
                checks.put("remote_bison_cut_listed", listed);
                checks.put("remote_bison_cut_dropped", dropped);
                checks.put("remote_bison_cut_matches_view", !droppedMeat.isEmpty() && listed.containsAll(droppedMeat));
                command(mc2, "setblock " + xyz(bisonAt) + " minecraft:air");
                command(mc2, "kill @e[type=item,distance=..64]");
            }
            return t >= 105;
        });
        MobDef def = MobDefs.byId("laviathan");
        var drained = (AbstractCarcassBlock) ModBlocks.of(def).drained().get();
        String drainedId = BuiltInRegistries.BLOCK.getKey(drained).toString();
        steps.add((mc2, t) -> {
            if (t == 0) {
                command(mc2, "gamemode creative");
                command(mc2, "setblock " + xyz(target) + " " + drainedId + "[facing=north,blockstate=0]");
            }
            return t >= 20;
        });
        // Each cut from far down the body, where the anchor is beyond vanilla's reach check.
        for (var action : Stages.actions(def)) {
            int expected = Stages.doneState(action, false, false);
            String tool = action.needsKnife() ? "butchery:iron_skinning_knife" : "butchery:iron_cleaver";
            String label = "remote_cut_" + action.name().toLowerCase();
            steps.add((mc2, t) -> {
                if (t == 0) {
                    command(mc2, "item replace entity @s weapon.mainhand with " + tool);
                    aimFar(mc2);
                }
                // The teleport lands a few ticks after the command; pick up to three times before giving up.
                if ((t == 15 || t == 20 || t == 25) && !Boolean.TRUE.equals(checks.get(label + "_selected_far_from_anchor"))) {
                    mc2.gameRenderer.pick(1F);
                    if (mc2.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target)) {
                        checks.put(label + "_selected_far_from_anchor", hit.getLocation().distanceTo(Vec3.atCenterOf(target)) > 1.5);
                        mc2.gameMode.useItemOn(mc2.player, InteractionHand.MAIN_HAND, hit);
                    } else checks.put(label + "_selected_far_from_anchor", false);
                }
                if (t == 45) {
                    BlockState state = mc2.level.getBlockState(target);
                    checks.put(label + "_server_applied", expected == Stages.REMOVED ? state.isAir()
                            : state.getBlock() == drained && drained.stage(state) == expected);
                }
                return t >= 46;
            });
        }
        // A fresh carcass for the refusals, then a normal break.
        steps.add((mc2, t) -> {
            if (t == 0) command(mc2, "setblock " + xyz(target) + " " + drainedId + "[facing=north,blockstate=0]");
            if (t == 5) command(mc2, "item replace entity @s weapon.mainhand with butchery:iron_cleaver");
            if (t == 10) aimFar(mc2);
            if (t == 25) {
                mc2.gameRenderer.pick(1F);
                if (!(mc2.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(target)) {
                    checks.put("remote_refusals_setup", false);
                    return true;
                }
                var forged = new BlockHitResult(hit.getLocation().add(12, 0, 0), hit.getDirection(), target, false);
                mc2.player.connection.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, forged, 900));
                lastHit = hit;
            }
            if (t == 45) {
                checks.put("remote_forged_coordinates_refused", isFreshDrained(mc2, drained));
                Vec3 back = mc2.player.getEyePosition().add(mc2.player.getLookAngle().scale(-(mc2.player.getBlockReach() + 1)));
                command(mc2, "tp @s " + back.x + " " + (back.y - mc2.player.getEyeHeight()) + " " + back.z);
            }
            if (t == 60) mc2.player.connection.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, lastHit, 901));
            if (t == 80) {
                checks.put("remote_out_of_reach_refused", isFreshDrained(mc2, drained));
                aimFar(mc2);
            }
            if (t == 95) {
                Vec3 wall = mc2.player.getEyePosition().add(mc2.player.getLookAngle());
                command(mc2, "setblock " + xyz(BlockPos.containing(wall)) + " minecraft:stone");
                wallAt = BlockPos.containing(wall);
            }
            if (t == 110) mc2.player.connection.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, lastHit, 902));
            if (t == 130) {
                checks.put("remote_walled_off_refused", isFreshDrained(mc2, drained));
                command(mc2, "setblock " + xyz(wallAt) + " minecraft:air");
            }
            // Sword-like tools (Butchery's cleavers among them) cannot break blocks in creative: break by hand.
            if (t == 140) command(mc2, "item replace entity @s weapon.mainhand with minecraft:air");
            if (t == 145) aimFar(mc2);
            if (t == 160) {
                mc2.gameRenderer.pick(1F);
                boolean selected = mc2.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(target);
                checks.put("remote_break_selected_far_anatomy", selected);
                if (selected) mc2.gameMode.startDestroyBlock(target, ((BlockHitResult) mc2.hitResult).getDirection());
            }
            if (t == 180) checks.put("remote_creative_break_of_far_anatomy", mc2.level.getBlockState(target).isAir());
            return t >= 181;
        });
        trophySteps(start);
        jadeSteps(start);
        adventureSteps(start);
        resumptionSteps(start);
        // Last: the player is no operator afterwards, so the harness cannot stop the server.
        nonOperatorSteps();
    }

    // ---- trophies, Jade, permissions and resumption (review findings AB-01, AB-02, AB-04, AB-05..AB-08) -----------

    /**
     * A trophy as a player hangs one: a white tiger's mount used on a wall's face goes up facing out with that look, and
     * taking the wall away drops exactly one mount that keeps it.
     */
    private static void trophySteps(BlockPos start) {
        MobDef tiger = MobDefs.byId("tiger");
        Block mount = ModBlocks.of(tiger).mount().get();
        String mountItem = BuiltInRegistries.ITEM.getKey(mount.asItem()).toString();
        BlockPos wall = start.offset(20, 6, 35), at = wall.south();
        steps.add((mc, t) -> {
            if (t == 0) {
                command(mc, "gamemode creative");
                command(mc, "setblock " + xyz(wall) + " minecraft:stone");
                command(mc, "setblock " + xyz(at) + " minecraft:air");
                command(mc, "kill @e[type=item,distance=..64]");
                command(mc, "item replace entity @s weapon.mainhand with " + mountItem + "{BlockEntityTag:{" + CarcassBlockEntity.MOB_DATA + ":{White:1b}}}");
                standSouthOf(mc, wall);
            }
            if (t >= 20 && t <= 40 && t % 5 == 0 && !Boolean.TRUE.equals(checks.get("remote_trophy_used_on_wall"))) {
                lookAt(mc, aimPoint(mc, wall));
                if (useOn(mc, wall)) checks.put("remote_trophy_used_on_wall", true);
            }
            if (t == 60) {
                checks.putIfAbsent("remote_trophy_used_on_wall", false);
                BlockState state = mc.level.getBlockState(at);
                checks.put("remote_trophy_placed_facing_out", state.is(mount) && state.getValue(HeadBlock.FACING) == Direction.SOUTH);
                checks.put("remote_trophy_keeps_look", mc.level.getBlockEntity(at) instanceof CarcassBlockEntity be && be.mobData().getBoolean("White"));
                // Out of pickup range, so the drop stays on the ground to be counted.
                command(mc, "tp @s " + (wall.getX() + 0.5) + " " + (wall.getY() - 1) + " " + (wall.getZ() + 9.5) + " 180 0");
            }
            if (t == 70) command(mc, "setblock " + xyz(wall) + " minecraft:air");
            if (t == 100) {
                checks.put("remote_trophy_falls_without_wall", mc.level.getBlockState(at).isAir());
                // The mount hung in the air: its drop has fallen towards the ground below.
                List<ItemStack> dropped = mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3).expandTowards(0, -10, 0)).stream()
                        .map(ItemEntity::getItem).filter(stack -> stack.is(mount.asItem())).toList();
                CompoundTag look = dropped.size() == 1 ? TrophyData.appearance(dropped.get(0)) : null;
                checks.put("remote_trophy_drops", dropped.stream().map(stack -> stack.getCount() + " " + stack.getTag()).toList());
                checks.put("remote_trophy_drops_one_with_look", dropped.size() == 1 && dropped.get(0).getCount() == 1 && look != null && look.getBoolean("White"));
                command(mc, "kill @e[type=item,distance=..64]");
            }
            return t >= 105;
        });
    }

    /**
     * Jade's own request for a native carcass the player looks at: the server's provider answers with Butchery's flags,
     * and the lines follow them (a hung cow asks for the cleaver, then shows it bleeding once the server marks it so).
     */
    private static void jadeSteps(BlockPos start) {
        BlockPos cow = start.offset(26, 6, 35);
        steps.add((mc, t) -> {
            if (t == 0) {
                command(mc, "setblock " + xyz(cow.above()) + " butchery:hook");
                command(mc, "setblock " + xyz(cow) + " butchery:cow_carcass[blockstate=1]");
                command(mc, "item replace entity @s weapon.mainhand with minecraft:air");
                standSouthOf(mc, cow);
            }
            if (t >= 20) lookAt(mc, aimPoint(mc, cow));
            if (t == 20) checks.put("remote_jade_server_connected", jadeConnected());
            if (t > 20 && t < 120 && !checks.containsKey("remote_jade_hung_cow")) {
                List<String> lines = jadeNativeLines(mc, cow);
                if (lines != null) checks.put("remote_jade_hung_cow", lines);
            }
            if (t == 120) {
                checks.putIfAbsent("remote_jade_hung_cow", "no server data");
                checks.put("remote_jade_hung_cow_asks_for_cleaver", List.of("jade.alexsbutchery.next").equals(checks.get("remote_jade_hung_cow")));
                command(mc, "data merge block " + xyz(cow) + " {ForgeData:{isBleeding:1b}}");
            }
            if (t > 125 && t < 240 && !checks.containsKey("remote_jade_bleeding_cow")) {
                List<String> lines = jadeNativeLines(mc, cow);
                if (lines != null && lines.contains("jade.alexsbutchery.bleeding_wait")) checks.put("remote_jade_bleeding_cow", lines);
            }
            if (t == 240) {
                checks.putIfAbsent("remote_jade_bleeding_cow", String.valueOf(jadeNativeLines(mc, cow)));
                checks.put("remote_jade_flags_from_server", List.of("jade.alexsbutchery.bleeding_wait").equals(checks.get("remote_jade_bleeding_cow")));
                command(mc, "setblock " + xyz(cow) + " minecraft:air");
                command(mc, "setblock " + xyz(cow.above()) + " minecraft:air");
            }
            return t >= 241;
        });
    }

    /**
     * Adventure mode as the server enforces it on real clicks: a carcass used on a hook hangs nothing and a trophy used on
     * a wall goes nowhere (neither item names the block in {@code CanPlaceOn}); in creative both went up above.
     */
    private static void adventureSteps(BlockPos start) {
        MobDef kangaroo = MobDefs.byId("kangaroo");
        Item carcass = ModBlocks.of(kangaroo).carcass().get().asItem();
        Item mount = ModBlocks.of(kangaroo).mount().get().asItem();
        BlockPos feet = start.offset(32, 6, 35);
        BlockPos hook = feet.offset(-1, 2, -2), wall = feet.offset(1, 1, -2);
        steps.add((mc, t) -> {
            if (t == 0) {
                command(mc, "gamemode creative");
                command(mc, "fill " + xyz(feet.offset(-1, -1, -1)) + " " + xyz(feet.offset(1, -1, 1)) + " minecraft:stone");
                command(mc, "setblock " + xyz(hook) + " butchery:hook");
                command(mc, "setblock " + xyz(hook.below()) + " minecraft:air");
                command(mc, "setblock " + xyz(wall) + " minecraft:stone");
                command(mc, "setblock " + xyz(wall.south()) + " minecraft:air");
                command(mc, "tp @s " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5) + " 180 0");
                command(mc, "item replace entity @s weapon.mainhand with " + BuiltInRegistries.ITEM.getKey(carcass));
            }
            if (t == 10) command(mc, "gamemode adventure");
            if (t == 30) {
                lookAt(mc, aimPoint(mc, hook));
                checks.put("remote_adventure_hook_clicked", useOn(mc, hook));
            }
            if (t == 50) {
                checks.put("remote_adventure_hang_refused", mc.level.getBlockState(hook.below()).isAir());
                checks.put("remote_adventure_carcass_kept", mc.player.getMainHandItem().is(carcass) && mc.player.getMainHandItem().getCount() == 1);
                command(mc, "item replace entity @s weapon.mainhand with " + BuiltInRegistries.ITEM.getKey(mount));
            }
            if (t == 60) {
                lookAt(mc, aimPoint(mc, wall));
                checks.put("remote_adventure_wall_clicked", useOn(mc, wall));
            }
            if (t == 80) {
                checks.put("remote_adventure_trophy_refused", mc.level.getBlockState(wall.south()).isAir());
                checks.put("remote_adventure_trophy_kept", mc.player.getMainHandItem().is(mount));
                command(mc, "gamemode creative");
                command(mc, "setblock " + xyz(hook) + " minecraft:air");
                command(mc, "setblock " + xyz(wall) + " minecraft:air");
            }
            return t >= 85;
        });
    }

    /**
     * A bleeding carcass whose chunk unloads: hung and cut far from spawn, left for twice its whole bleed, then visited
     * again. Nobody near, it kept its clock; after the visit it drains on its own, and the grate below holds exactly the
     * blood its class owes (no fill repaid or lost).
     */
    private static void resumptionSteps(BlockPos start) {
        MobDef def = MobDefs.all().stream().filter(d -> d.blood() == BloodClass.SMALL && d.hookable()).findFirst().orElseThrow();
        Block fresh = ModBlocks.of(def).carcass().get();
        Block drained = ModBlocks.of(def).drained().get();
        String freshId = BuiltInRegistries.BLOCK.getKey(fresh).toString();
        int whole = def.blood().totalTicks();
        BlockPos at = start.offset(640, 6, 0);
        int leave = 100, back = leave + 2 * whole + 40, end = back + 2 * whole + 20;
        steps.add((mc, t) -> {
            if (t == 0) {
                checks.put("remote_resume_mob", def.id());
                command(mc, "gamemode creative");
                standSouthOf(mc, at);
            }
            if (t == 40) {
                command(mc, "setblock " + xyz(at.above()) + " butchery:hook");
                command(mc, "setblock " + xyz(at.below()) + " butchery:blood_grate");
                command(mc, "setblock " + xyz(at) + " " + freshId + "[facing=north,blockstate=1]");
                command(mc, "item replace entity @s weapon.mainhand with butchery:iron_cleaver");
            }
            if (t >= 60 && t <= 80 && t % 5 == 0 && !Boolean.TRUE.equals(checks.get("remote_resume_cut"))) {
                lookAt(mc, aimPoint(mc, at));
                if (useOn(mc, at)) checks.put("remote_resume_cut", true);
            }
            if (t == leave) {
                checks.putIfAbsent("remote_resume_cut", false);
                checks.put("remote_resume_bleeding", mc.level.getBlockEntity(at) instanceof CarcassBlockEntity be && be.isBleeding());
                command(mc, "tp @s " + start.getX() + " " + start.getY() + " " + start.getZ());
            }
            if (t == back - 40) standSouthOf(mc, at);
            if (t == back) {
                // The chunk was saved and is loaded again: the carcass is still bleeding, part way through its clock.
                boolean waited = mc.level.getBlockState(at).is(fresh) && mc.level.getBlockEntity(at) instanceof CarcassBlockEntity be
                        && be.isBleeding() && be.bleedTicks() > 0 && be.bleedTicks() < whole;
                checks.put("remote_resume_waited_while_unloaded", waited);
            }
            if (t > back && t < end && !checks.containsKey("remote_resume_drained")) {
                BlockState state = mc.level.getBlockState(at);
                if (state.is(drained)) {
                    checks.put("remote_resume_drained", state.getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == 1);
                    grateMb = null;
                    command(mc, "data get block " + xyz(at.below()));
                }
            }
            if (t == end) {
                checks.putIfAbsent("remote_resume_drained", false);
                checks.put("remote_resume_grate_mb", grateMb);
                checks.put("remote_resume_grate_holds_its_blood", grateMb != null && grateMb == def.blood().fills * 50);
                command(mc, "setblock " + xyz(at) + " minecraft:air");
            }
            return t >= end + 1;
        });
    }

    /**
     * Last, a player who is no operator, inside spawn protection (the server lists a second operator, so it applies): as
     * an operator a cut and a hanging go through, then after {@code /deop} the same clicks on a twin carcass and hook are
     * refused by the server.
     */
    private static void nonOperatorSteps() {
        MobDef kangaroo = MobDefs.byId("kangaroo");
        var drained = (AbstractCarcassBlock) ModBlocks.of(kangaroo).drained().get();
        String drainedId = BuiltInRegistries.BLOCK.getKey(drained).toString();
        Block fresh = ModBlocks.of(kangaroo).carcass().get();
        BlockPos[] at = new BlockPos[6];
        steps.add((mc, t) -> {
            if (t == 0) {
                BlockPos feet = mc.level.getSharedSpawnPos().above(6);
                at[0] = feet;
                at[1] = feet.offset(-2, 0, -3);
                at[2] = feet.offset(2, 0, -3);
                at[3] = feet.offset(-1, 3, -2);
                at[4] = feet.offset(1, 3, -2);
                checks.put("remote_protected_spawn", xyz(mc.level.getSharedSpawnPos()));
                command(mc, "gamemode creative");
                command(mc, "fill " + xyz(feet.offset(-3, -1, -4)) + " " + xyz(feet.offset(3, -1, 1)) + " minecraft:stone");
                for (int i = 1; i <= 2; i++) command(mc, "setblock " + xyz(at[i]) + " " + drainedId + "[facing=north,blockstate=0]");
                for (int i = 3; i <= 4; i++) {
                    command(mc, "setblock " + xyz(at[i]) + " butchery:hook");
                    command(mc, "setblock " + xyz(at[i].below()) + " minecraft:air");
                }
                command(mc, "item replace entity @s hotbar.0 with butchery:iron_cleaver");
                command(mc, "item replace entity @s hotbar.1 with " + BuiltInRegistries.ITEM.getKey(fresh.asItem()));
                command(mc, "tp @s " + (feet.getX() + 0.5) + " " + feet.getY() + " " + (feet.getZ() + 0.5) + " 180 0");
            }
            // As an operator: the cut and the hanging go through.
            if (t == 30) {
                mc.player.getInventory().selected = 0;
                lookAt(mc, aimPoint(mc, at[1]));
                checks.put("remote_operator_cut_clicked", useOn(mc, at[1]));
            }
            if (t == 40) {
                mc.player.getInventory().selected = 1;
                lookAt(mc, aimPoint(mc, at[3]));
                checks.put("remote_operator_hook_clicked", useOn(mc, at[3]));
            }
            if (t == 60) {
                checks.put("remote_operator_cut_applied", drained.stage(mc.level.getBlockState(at[1])) != 0);
                checks.put("remote_operator_hung", mc.level.getBlockState(at[3].below()).is(fresh));
                command(mc, "deop " + mc.player.getGameProfile().getName());
            }
            if (t == 80) checks.put("remote_no_longer_operator", !mc.player.hasPermissions(2));
            // No operator any more: the twin carcass and hook refuse the same clicks.
            if (t == 85) {
                mc.player.getInventory().selected = 0;
                lookAt(mc, aimPoint(mc, at[2]));
                checks.put("remote_protected_cut_clicked", useOn(mc, at[2]));
            }
            if (t == 95) {
                mc.player.getInventory().selected = 1;
                lookAt(mc, aimPoint(mc, at[4]));
                checks.put("remote_protected_hook_clicked", useOn(mc, at[4]));
            }
            if (t == 120) {
                BlockState twin = mc.level.getBlockState(at[2]);
                checks.put("remote_protected_cut_refused", twin.is(drained) && drained.stage(twin) == 0);
                checks.put("remote_protected_hang_refused", mc.level.getBlockState(at[4].below()).isAir());
            }
            return t >= 121;
        });
    }

    /** The point to look at: a carcass's largest anatomy box, otherwise the middle of the block's outline. */
    private static Vec3 aimPoint(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.getBlock() instanceof AbstractCarcassBlock) {
            List<AABB> boxes = CarcassBounds.boxes(state, mc.level, pos);
            if (!boxes.isEmpty()) {
                AABB big = boxes.stream().max(Comparator.comparingDouble(b -> b.getXsize() * b.getYsize() * b.getZsize())).orElseThrow();
                return big.getCenter().add(pos.getX(), pos.getY(), pos.getZ());
            }
        }
        var shape = state.getShape(mc.level, pos);
        return shape.isEmpty() ? Vec3.atCenterOf(pos) : shape.bounds().getCenter().add(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Turns the player to look at a point and tells the server at once, as a player who has settled their aim has: the
     * server re-picks carcass anatomy along its own copy of the look direction before it accepts a click.
     */
    private static void lookAt(Minecraft mc, Vec3 point) {
        Vec3 eye = mc.player.getEyePosition();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.player.yHeadRot = yaw;
        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround()));
    }

    /** Picks what the player looks at and right-clicks it with the held item when it is {@code expected}. */
    private static boolean useOn(Minecraft mc, BlockPos expected) {
        mc.gameRenderer.pick(1F);
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(expected)) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            return true;
        }
        return false;
    }

    /** Stands two and a half blocks south of a block, eyes level with its middle, facing it. */
    private static void standSouthOf(Minecraft mc, BlockPos pos) {
        command(mc, "tp @s " + (pos.getX() + 0.5) + " " + (pos.getY() + 0.5 - mc.player.getEyeHeight()) + " " + (pos.getZ() + 2.5) + " 180 0");
    }

    /** The native lines for what Jade last received from the server, or null before any answer. */
    @Nullable
    private static List<String> jadeNativeLines(Minecraft mc, BlockPos pos) {
        CompoundTag data = jadeServerData();
        if (data == null || !data.contains(NativeButchery.DATA, Tag.TAG_COMPOUND)) return null;
        List<String> keys = new ArrayList<>();
        for (Component line : NativeButchery.lines(mc.level.getBlockState(pos), data.getCompound(NativeButchery.DATA)))
            keys.add(line.getContents() instanceof TranslatableContents tr ? tr.getKey() : line.getString());
        return keys;
    }

    /** Jade's client copy of the server's answer for what the player looks at (Jade's own state, read reflectively). */
    @Nullable
    private static CompoundTag jadeServerData() {
        try {
            return (CompoundTag) Class.forName("snownee.jade.impl.ObjectDataCenter").getMethod("getServerData").invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static boolean jadeConnected() {
        try {
            return Class.forName("snownee.jade.impl.ObjectDataCenter").getField("serverConnected").getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    private static final Map<Integer, String> bisonDrops = new LinkedHashMap<>();
    private static BlockHitResult lastHit;
    private static BlockPos wallAt;

    private static boolean isFreshDrained(Minecraft mc, AbstractCarcassBlock drained) {
        BlockState state = mc.level.getBlockState(target);
        return state.getBlock() == drained && drained.stage(state) == 0;
    }

    /** Stands two blocks short of the anatomy furthest along -z and looks at it, as GeometryPlayChecks does. */
    private static void aimFar(Minecraft mc) {
        aimAt(mc, target);
    }

    private static void aimAt(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractCarcassBlock)) return;
        AABB far = CarcassBounds.boxes(state, mc.level, pos).stream().min(Comparator.comparingDouble(b -> b.minZ)).orElseThrow();
        double x = pos.getX() + far.getCenter().x, y = pos.getY() + far.getCenter().y, z = pos.getZ() + far.minZ - 2;
        command(mc, "tp @s " + x + " " + (y - mc.player.getEyeHeight()) + " " + z + " 0 0");
    }

    private static String xyz(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void finish(Minecraft mc, String reason) {
        done = true;
        boolean pass = reason.equals("complete") && checks.values().stream().noneMatch(v -> v instanceof Boolean b && !b) && !checks.isEmpty();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", pass);
        result.put("reason", reason);
        result.put("server", SERVER);
        result.put("checks", checks);
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT, "remote-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { AlexsButchery.LOGGER.error("REMOTE could not write its result", e); }
        AlexsButchery.LOGGER.info("REMOTE finished: pass={} ({})", pass, reason);
        if (mc.player != null) command(mc, "stop");
        mc.stop();
    }

    private RemoteChecks() {}
}
