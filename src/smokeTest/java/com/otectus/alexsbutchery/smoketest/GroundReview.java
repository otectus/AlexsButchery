package com.otectus.alexsbutchery.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
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
 * Close-up ground-contact gallery ({@code -PgroundReview[=label]}): the reported creatures and the side-lying outliers,
 * each on its own gold anchor tile, shot from a low side view, a three-quarter view and within reach with the
 * selection outline. Uses only API present since 0.1.1, so the same gallery can be shot with the released build.
 * Writes {@code run/screenshots/ground_<label>_<subject>_<view>.png} and {@code ground-<label>.json}.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class GroundReview {
    static final String LABEL = System.getProperty("alexsbutchery.smoketest.groundReview", "");
    private static final String OUTPUT = System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY);
    private static final int SPACING = 26, SHOT_TICKS = 70;
    private static final String[][] SUBJECTS = {
            {"laviathan", "carcass", "0", "north"}, {"laviathan", "drained", "7", "east"}, {"void_worm", "carcass", "0", "north"},
            {"void_worm", "drained", "7", "east"}, {"centipede", "carcass", "0", "east"}, {"centipede", "carcass", "6", "north"},
            {"farseer", "carcass", "0", "north"}, {"farseer", "drained", "7", "east"}, {"farseer", "skeleton", "0", "south"},
            {"elephant", "carcass", "0", "north"}, {"cachalot_whale", "carcass", "0", "north"}, {"cachalot_whale", "skeleton", "0", "north"},
            {"sea_bear", "carcass", "0", "east"}, {"skreecher", "carcass", "0", "north"}, {"warped_mosco", "drained", "8", "north"},
            {"bunfungus", "drained", "0", "east"}, {"rhinoceros", "slab", "0", "north"}, {"kangaroo", "hanging", "1", "south"}};
    private static final String[] VIEWS = {"side", "three_quarter", "outline"};
    private static int titleTicks, worldTicks;
    private static boolean done;
    private static BlockPos base;
    private static final Map<String, Object> index = new LinkedHashMap<>();

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (LABEL.isEmpty() || OUTPUT == null || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            if (mc.screen instanceof TitleScreen && ++titleTicks == 40) {
                String name = "GroundReview" + System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(name, new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(20261012L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            return;
        }
        int t = ++worldTicks;
        mc.options.pauseOnLostFocus = false;
        if (mc.screen != null && t > 20) mc.setScreen(null);
        if (t == 20) mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        if (t == 60) stage(mc);
        int start = 140, shot = (t - start) / SHOT_TICKS, phase = (t - start) % SHOT_TICKS;
        if (t < start) return;
        if (shot >= SUBJECTS.length * VIEWS.length) {
            done = true;
            try {
                Files.writeString(Path.of(OUTPUT, "ground-" + LABEL + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(index),
                        StandardCharsets.UTF_8);
            } catch (java.io.IOException e) { AlexsButchery.LOGGER.error("GROUNDREVIEW could not write its index", e); }
            AlexsButchery.LOGGER.info("GROUNDREVIEW finished");
            mc.stop();
            return;
        }
        String[] subject = SUBJECTS[shot / VIEWS.length];
        String view = VIEWS[shot % VIEWS.length];
        BlockPos pos = anchor(shot / VIEWS.length);
        if (phase == 0) camera(mc, pos, view);
        if (phase == SHOT_TICKS - 3) {
            String name = "ground_" + LABEL + "_" + String.join("_", subject) + "_" + view;
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
            index.put(name, Map.of("subject", String.join("/", subject), "view", view, "anchor", pos.toShortString(),
                    "outline_target", mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit ? hit.getBlockPos().toShortString() : "none"));
        }
    }

    private static BlockPos anchor(int i) {
        return base.offset(i * SPACING, 0, 0);
    }

    private static void stage(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        base = server.getPlayerList().getPlayers().get(0).blockPosition().offset(8, 0, 20);
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            CompoundTag marker = new CompoundTag();
            marker.putBoolean("GroundReviewMarker", true);
            for (int i = 0; i < SUBJECTS.length; i++) try {
                String[] s = SUBJECTS[i];
                BlockPos pos = anchor(i);
                var entry = ModBlocks.of(MobDefs.byId(s[0]));
                Direction facing = Direction.byName(s[3]);
                var holder = switch (s[1]) {
                    case "drained" -> entry.drained();
                    case "skeleton" -> entry.skeleton();
                    default -> entry.carcass();
                };
                var block = (AbstractCarcassBlock) holder.get();
                BlockState state = block.defaultBlockState().setValue(block.stateProperty(), Integer.parseInt(s[2]))
                        .setValue(AbstractCarcassBlock.FACING, facing);
                level.setBlockAndUpdate(pos.below(), s[1].equals("slab") ? Blocks.SMOOTH_STONE_SLAB.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState());
                if (s[1].equals("hanging")) {
                    level.setBlockAndUpdate(pos.above(2), ButcheryHooks.hook().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
                    level.setBlockAndUpdate(pos.above(), state);
                } else level.setBlockAndUpdate(pos, state);
                if (level.getBlockEntity(s[1].equals("hanging") ? pos.above() : pos) instanceof com.otectus.alexsbutchery.block.entity.CarcassBlockEntity be)
                    be.setMobData(marker);
            } catch (RuntimeException e) {
                AlexsButchery.LOGGER.error("GROUNDREVIEW could not stage {}", String.join("/", SUBJECTS[i]), e);
                index.put("staging_error_" + i, e.toString());
            }
        });
    }

    private static void camera(Minecraft mc, BlockPos anchor, String view) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            BlockPos carcass = level.getBlockState(anchor).getBlock() instanceof AbstractCarcassBlock ? anchor : anchor.above();
            BlockState state = level.getBlockState(carcass);
            List<AABB> boxes = state.getBlock() instanceof AbstractCarcassBlock ? CarcassBounds.boxes(state, level, carcass)
                    : List.of(new AABB(0, 0, 0, 1, 1, 1));
            AABB envelope = boxes.stream().reduce(AABB::minmax).orElseThrow().move(carcass);
            Vec3 centre = envelope.getCenter();
            double span = Math.max(envelope.getXsize(), envelope.getZsize());
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            Vec3 eye;
            Vec3 target;
            switch (view) {
                case "side" -> {
                    eye = new Vec3(centre.x, anchor.getY() + .3, envelope.minZ - Math.max(5, span * .9));
                    target = new Vec3(centre.x, anchor.getY() + .3, centre.z);
                    player.setGameMode(GameType.SPECTATOR);
                }
                case "three_quarter" -> {
                    double d = Math.max(4, span * .75);
                    eye = new Vec3(envelope.minX - d * .7, envelope.maxY + d * .7, envelope.minZ - d * .7);
                    target = centre;
                    player.setGameMode(GameType.SPECTATOR);
                }
                default -> {
                    AABB largest = boxes.stream().max(Comparator.comparingDouble(b -> b.getXsize() * b.getYsize() * b.getZsize())).orElseThrow().move(carcass);
                    target = largest.getCenter();
                    eye = new Vec3(target.x, target.y + 1, largest.minZ - 2.4);
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                }
            }
            Vec3 d = target.subtract(eye);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
            player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
        });
    }

    private GroundReview() {}
}
