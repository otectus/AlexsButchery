package com.otectus.alexsbutchery.smoketest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.block.CarcassTargeting;
import com.otectus.alexsbutchery.block.DrainedCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opens the world {@link SaveCompatCreate} wrote with an earlier build ({@code -PsaveCompat=verify}) and checks that
 * every carcass kept its state, snapshot and bleeding or acid progress, resolves exported anatomy, is selectable, and
 * that the carcass left on a slab is now lowered onto it. Screenshots the row and writes {@code save-compat-result.json}.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class SaveCompatVerify {
    private static final String OUTPUT = System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY);
    private static final Map<String, Object> checks = new LinkedHashMap<>();
    private static JsonObject manifest;
    private static int titleTicks;
    private static int worldTicks;
    private static boolean done;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!SaveCompatCreate.MODE.equals("verify") || OUTPUT == null || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player == null || mc.level == null) {
                if (mc.screen instanceof TitleScreen && ++titleTicks == 40) {
                    manifest = JsonParser.parseString(Files.readString(Path.of(OUTPUT, "save-compat.json"))).getAsJsonObject();
                    mc.createWorldOpenFlows().loadLevel(mc.screen, manifest.get("world").getAsString());
                }
                return;
            }
            int t = ++worldTicks;
            mc.options.pauseOnLostFocus = false;
            if (mc.screen != null && t > 20) mc.setScreen(null);
            if (t == 60) mc.getSingleplayerServer().execute(SaveCompatVerify::verify);
            BlockPos base = new BlockPos(manifest.get("x").getAsInt(), manifest.get("y").getAsInt(), manifest.get("z").getAsInt());
            int count = manifest.getAsJsonArray("entries").size();
            Object[][] shots = {{"overview", count * 4.5, -9.0, 0F, 25F, 7.0}, {"low_side", count * 4.5, -6.0, 0F, 5F, 1.2},
                    {"slab_rhinoceros", 9 * 7.0, -4.5, 0F, 12F, 1.4}, {"farseer", 9 * 4.0, -4.5, 0F, 12F, 1.4}};
            int shotStart = 90;
            int index = (t - shotStart) / 30, phase = (t - shotStart) % 30;
            if (t >= shotStart && index < shots.length) {
                Object[] s = shots[index];
                if (phase == 0) camera(mc, base, (double) s[1], (double) s[2], (float) s[3], (float) s[4], (double) s[5]);
                if (phase == 26) Screenshot.grab(mc.gameDirectory, "savecompat_" + s[0] + ".png", mc.getMainRenderTarget(), m -> {});
            } else if (t >= shotStart && index >= shots.length) finish(mc);
        } catch (Exception e) {
            AlexsButchery.LOGGER.error("SAVECOMPAT failed", e);
            checks.put("exception", e.toString());
            finish(mc);
        }
    }

    private static void verify() {
        ServerLevel level = Minecraft.getInstance().getSingleplayerServer().overworld();
        ServerPlayer player = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
        checks.put("saved_by", manifest.get("mod_version").getAsString());
        for (var element : manifest.getAsJsonArray("entries")) {
            var entry = element.getAsJsonObject();
            BlockPos pos = new BlockPos(entry.get("x").getAsInt(), entry.get("y").getAsInt(), entry.get("z").getAsInt());
            try {
                var savedState = NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                        TagParser.parseTag(entry.get("state").getAsString()));
                CompoundTag saved = TagParser.parseTag(entry.get("block_entity").getAsString());
                var state = level.getBlockState(pos);
                String label = BuiltInRegistries.BLOCK.getKey(savedState.getBlock()).getPath() + "@" + pos.toShortString();
                boolean bleedingDone = saved.getBoolean("Bleeding") && state.getBlock() instanceof DrainedCarcassBlock
                        && !(savedState.getBlock() instanceof DrainedCarcassBlock);
                checks.put(label + "/state_kept", state == savedState || bleedingDone);
                if (!(level.getBlockEntity(pos) instanceof CarcassBlockEntity be)) {
                    checks.put(label + "/block_entity_kept", false);
                    continue;
                }
                CompoundTag now = be.saveWithoutMetadata();
                checks.put(label + "/snapshot_kept", now.getCompound(CarcassBlockEntity.MOB_DATA).equals(saved.getCompound(CarcassBlockEntity.MOB_DATA)));
                if (saved.getBoolean("Bleeding") && !bleedingDone)
                    checks.put(label + "/bleeding_resumed", now.getBoolean("Bleeding") && now.getInt("BleedTicks") >= saved.getInt("BleedTicks"));
                if (saved.getInt("AcidTicks") > 0)
                    checks.put(label + "/acid_resumed", now.getInt("AcidTicks") > 0 && now.getInt("AcidTicks") <= saved.getInt("AcidTicks"));
                if (state.getBlock() instanceof AbstractCarcassBlock) {
                    checks.put(label + "/anatomy_exported", CarcassBounds.exported(state, be.mobData()));
                    var geometry = CarcassBounds.geometry(state, level, pos);
                    AABB box = geometry.boxes().stream().max(Comparator.comparingDouble(b -> b.getXsize() * b.getYsize() * b.getZsize())).orElseThrow();
                    Vec3 target = box.getCenter().add(Vec3.atLowerCornerOf(pos));
                    Vec3 eye = new Vec3(target.x, target.y, pos.getZ() + box.minZ - 2);
                    var hit = CarcassTargeting.pick(level, eye, eye.add(0, 0, 4.5), player);
                    checks.put(label + "/selectable", hit != null && hit.getBlockPos().equals(pos));
                    if (level.getBlockState(pos.below()).getBlock() instanceof net.minecraft.world.level.block.SlabBlock)
                        checks.put(label + "/lowered_onto_slab", Math.abs(CarcassBounds.groundLevel(level, pos) + .5) < 1E-9
                                && geometry.envelope().minY < -.4);
                }
            } catch (Exception e) {
                checks.put(pos.toShortString() + "/error", e.toString());
            }
        }
        checks.put("entries", manifest.getAsJsonArray("entries").size());
    }

    private static void camera(Minecraft mc, BlockPos base, double dx, double dz, float yaw, float pitch, double dy) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(server.overworld(), base.getX() + .5 + dx, base.getY() + dy, base.getZ() + .5 + dz, yaw, pitch);
        });
    }

    private static void finish(Minecraft mc) {
        done = true;
        boolean pass = !checks.containsKey("exception") && checks.values().stream().noneMatch(v -> v instanceof Boolean b && !b)
                && checks.keySet().stream().noneMatch(k -> k.endsWith("/error"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", pass);
        result.put("checks", checks);
        try {
            Files.writeString(Path.of(OUTPUT, "save-compat-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result),
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { AlexsButchery.LOGGER.error("SAVECOMPAT could not write its result", e); }
        AlexsButchery.LOGGER.info("SAVECOMPAT finished: pass={}", pass);
        mc.stop();
    }

    private SaveCompatVerify() {}
}
