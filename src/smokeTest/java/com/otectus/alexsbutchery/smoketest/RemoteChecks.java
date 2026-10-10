package com.otectus.alexsbutchery.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

    private static void plan(Minecraft mc) {
        BlockPos start = mc.player.blockPosition();
        target = start.offset(0, 6, 35);
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
    }

    private static BlockHitResult lastHit;
    private static BlockPos wallAt;

    private static boolean isFreshDrained(Minecraft mc, AbstractCarcassBlock drained) {
        BlockState state = mc.level.getBlockState(target);
        return state.getBlock() == drained && drained.stage(state) == 0;
    }

    /** Stands two blocks short of the anatomy furthest along -z and looks at it, as GeometryPlayChecks does. */
    private static void aimFar(Minecraft mc) {
        BlockState state = mc.level.getBlockState(target);
        if (!(state.getBlock() instanceof AbstractCarcassBlock)) return;
        AABB far = CarcassBounds.boxes(state, mc.level, target).stream().min(Comparator.comparingDouble(b -> b.minZ)).orElseThrow();
        double x = target.getX() + far.getCenter().x, y = target.getY() + far.getCenter().y, z = target.getZ() + far.minZ - 2;
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
