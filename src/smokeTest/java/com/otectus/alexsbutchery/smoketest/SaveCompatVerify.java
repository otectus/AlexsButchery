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
import net.minecraft.resources.ResourceLocation;
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
                // Carcasses, skeletons and (since 0.2.1) heads and mounts are picked by their exported anatomy.
                if (CarcassBounds.selectable(state.getBlock())) {
                    checks.put(label + "/anatomy_exported", CarcassBounds.exported(state, be.mobData()));
                    var geometry = CarcassBounds.geometry(state, level, pos);
                    AABB box = geometry.boxes().stream().max(Comparator.comparingDouble(b -> b.getXsize() * b.getYsize() * b.getZsize())).orElseThrow();
                    Vec3 target = box.getCenter().add(Vec3.atLowerCornerOf(pos));
                    Vec3 eye = new Vec3(target.x, target.y, pos.getZ() + box.minZ - 2);
                    var hit = CarcassTargeting.pick(level, eye, eye.add(0, 0, 4.5), player);
                    checks.put(label + "/selectable", hit != null && hit.getBlockPos().equals(pos));
                    if (state.getBlock() instanceof AbstractCarcassBlock && level.getBlockState(pos.below()).getBlock() instanceof net.minecraft.world.level.block.SlabBlock)
                        checks.put(label + "/lowered_onto_slab", Math.abs(CarcassBounds.groundLevel(level, pos) + .5) < 1E-9
                                && geometry.envelope().minY < -.4);
                }
            } catch (Exception e) {
                checks.put(pos.toShortString() + "/error", e.toString());
            }
        }
        checks.put("entries", manifest.getAsJsonArray("entries").size());
        continueMeatCarcasses(level, player);
    }

    /**
     * The meat carcasses the old build saved mid-way are finished under today's rules: every remaining stage pays once,
     * no stage done before the update pays again, and the meat (or the shrimp's tail) comes from the provider the
     * server's rules choose for that carcass now, including the native tail for a shrimp saved without a colour.
     */
    private static void continueMeatCarcasses(ServerLevel level, ServerPlayer player) {
        var rules = com.otectus.alexsbutchery.compat.CompatState.get(level.getServer());
        checks.put("rules_loaded", rules != null);
        if (rules == null) return;
        int continued = 0;
        for (var element : manifest.getAsJsonArray("entries")) {
            var entry = element.getAsJsonObject();
            BlockPos pos = new BlockPos(entry.get("x").getAsInt(), entry.get("y").getAsInt(), entry.get("z").getAsInt());
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof AbstractCarcassBlock block) || state.getBlock() instanceof com.otectus.alexsbutchery.block.SkeletonBlock) continue;
            var def = block.def();
            if (!java.util.Set.of("bison", "crocodile", "grizzly_bear", "mantis_shrimp").contains(def.id())) continue;
            String label = "continue_" + def.id() + "@" + pos.toShortString();
            boolean fresh = !(state.getBlock() instanceof DrainedCarcassBlock);
            var doneBefore = com.otectus.alexsbutchery.butcher.Stages.done(def, fresh, block.stage(state));
            CompoundTag snapshot = ((CarcassBlockEntity) level.getBlockEntity(pos)).mobData().copy();
            Map<String, List<String>> paid = new LinkedHashMap<>();
            for (int guard = 0; guard < 6; guard++) {
                var now = level.getBlockState(pos);
                if (!(now.getBlock() instanceof AbstractCarcassBlock current)) break;
                var step = com.otectus.alexsbutchery.butcher.Stages.next(def, fresh, current.stage(now));
                if (step == null) break;
                String tool = step.action().needsKnife() ? "iron_skinning_knife" : "iron_cleaver";
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.item.ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("butchery", tool))));
                var area = new AABB(pos).inflate(2);
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).forEach(net.minecraft.world.entity.Entity::discard);
                now.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos),
                        net.minecraft.core.Direction.UP, pos, false));
                List<String> items = new java.util.ArrayList<>();
                for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area)) {
                    items.add(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString());
                    item.discard();
                }
                paid.put(step.action().table(def), items);
            }
            checks.put(label + "/paid", paid.toString());
            checks.put(label + "/finished", !(level.getBlockState(pos).getBlock() instanceof AbstractCarcassBlock));
            checks.put(label + "/no_completed_stage_repeated", doneBefore.stream().noneMatch(a -> paid.containsKey(a.table(def))));
            String group = def.id().equals("mantis_shrimp") ? "tail" : "meat";
            boolean providerMatches = true;
            for (String stage : def.stagesOf(group)) {
                if (!paid.containsKey(stage)) continue;
                java.util.Set<String> expected = new java.util.HashSet<>();
                java.util.Set<String> possible = new java.util.HashSet<>();
                def.groupDrops(stage, group).forEach(d -> possible.add(d.item().toString()));
                boolean chosen = false;
                for (var choice : rules.resolution(def.entity(), group, stage).chain()) {
                    if (choice instanceof com.otectus.alexsbutchery.compat.CompatState.Provided p) {
                        p.variantItems().values().forEach(i -> possible.add(BuiltInRegistries.ITEM.getKey(i).toString()));
                        if (p.item() != null) possible.add(BuiltInRegistries.ITEM.getKey(p.item()).toString());
                        var item = p.select(snapshot);
                        if (item != null && !chosen) {
                            expected.add(BuiltInRegistries.ITEM.getKey(item).toString());
                            chosen = true;
                        }
                    } else if (!chosen) {
                        def.groupDrops(stage, group).forEach(d -> expected.add(d.item().toString()));
                        chosen = true;
                    }
                }
                List<String> got = paid.get(stage);
                boolean ok = got.stream().anyMatch(expected::contains) && got.stream().noneMatch(i -> possible.contains(i) && !expected.contains(i));
                if (!ok) checks.put(label + "/" + stage + "_expected", expected.toString());
                providerMatches &= ok;
            }
            checks.put(label + "/yield_follows_current_rules", providerMatches);
            continued++;
        }
        checks.put("meat_carcasses_continued", continued);
        checks.put("meat_carcasses_present", continued == 5);
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
