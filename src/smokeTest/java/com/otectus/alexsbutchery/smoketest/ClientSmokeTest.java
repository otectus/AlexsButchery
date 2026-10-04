package com.otectus.alexsbutchery.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.HeadBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.client.render.CarcassModels;
import com.otectus.alexsbutchery.client.render.StageTextures;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Drives a dev client from the title screen into a fresh flat world, stages every pilot carcass in its main stages
 * (ground, hanging, drained, skinned, cut, head, trophy) next to a live mob, the Phase 5 blocks (skeletons, rugs),
 * catfish sizes and multipart bodies, and a gallery cell per mob (lying, hanging, head), takes framebuffer
 * screenshots, writes {@code smoketest-result.json} and quits. Screenshots land in {@code run/screenshots/smoketest_*.png}.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static final String OUTPUT = System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY);
    private static final boolean HANGING_REVIEW = Boolean.getBoolean("alexsbutchery.smoketest.hangingReview");
    private static final Map<String, Object> checks = new LinkedHashMap<>();
    private static final long START = Util.getMillis();
    private static int titleTicks;
    private static int worldTicks = -1;
    private static boolean worldRequested;
    private static boolean finished;
    private static String geometryWorld;
    private static BlockPos base;
    private static int settledAt = -1;
    private static int quietTicks;
    private static final int GRID_COLS = 8;
    private static final int GRID_STEP = 5;
    private static final int GRID_X0 = -18;
    private static final int GRID_Z0 = -70;
    /** Feature rows south of the pilots, then one gallery cell per mob. */
    private static final int FEATURE_Z0 = 10;
    private static final int GALLERY_COLS = 10;
    private static final int GALLERY_STEP = 13;
    private static final int GALLERY_X0 = -60;
    private static final int GALLERY_Z0 = 56;
    private static final int SHOT_TICKS = 24;
    /** Camera stops after the fixed pilot shots: grid rows, feature shots and gallery cells. */
    private static final java.util.List<Shot> SHOTS = new java.util.ArrayList<>();

    private record Shot(String name, double dx, double dz, float yaw, float pitch, double dy) {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (OUTPUT == null || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player == null || mc.level == null) {
                if (mc.screen instanceof TitleScreen && ++titleTicks == 1) mc.getWindow().setWindowed(1280, 720);
                if (mc.screen instanceof TitleScreen && titleTicks == 40 && !worldRequested) {
                    worldRequested = true;
                    checks.put("title_ms", Util.getMillis() - START);
                    createWorld(mc);
                }
                return;
            }
            worldTicks++;
            if (settledAt < 0 && !settle(mc)) return;
            step(mc, worldTicks - settledAt);
        } catch (Exception e) {
            AlexsButchery.LOGGER.error("SMOKETEST failed at world tick {}", worldTicks, e);
            checks.put("agent_exception", e.toString());
            finish(mc, false, "agent exception");
        }
    }

    /**
     * Packs open their own screens on first join (Ultima: the Origins choice); staging and shots need them gone.
     * Waits until no screen has been open for 2 s, taking Origins' offered origin as a player would and closing
     * anything else. Without a pack this settles after 40 ticks.
     */
    private static boolean settle(Minecraft mc) {
        if (worldTicks % 5 == 0) settlePackScreen(mc);
        quietTicks = mc.screen == null ? quietTicks + 1 : 0;
        boolean quiet = worldTicks >= 40 && quietTicks >= 40;
        if (quiet || worldTicks >= 1200) {
            settledAt = worldTicks;
            checks.put("pack_screens_settled_ticks", settledAt);
            log("pack screens " + (quiet ? "settled" : "still open") + " after " + worldTicks + " ticks");
            return true;
        }
        return false;
    }

    private static void settlePackScreen(Minecraft mc) {
        net.minecraft.client.gui.screens.Screen screen = mc.screen;
        if (screen == null) return;
        if (screen.getClass().getName().contains(".origins.")) {
            for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button button && button.active
                        && "Select".equals(button.getMessage().getString())) {
                    log("choosing the offered origin on " + screen.getClass().getName());
                    button.onPress();
                    return;
                }
            }
            return;
        }
        log("closing pack screen " + screen.getClass().getName());
        mc.setScreen(null);
    }

    private static void step(Minecraft mc, int t) {
        if (Boolean.getBoolean("alexsbutchery.smoketest.exportBounds")) {
            if (t == 60) {
                try { BoundsExport.write(Path.of(OUTPUT, "carcass_bounds.json"), checks); }
                catch (IOException e) { throw new IllegalStateException(e); }
                finish(mc, true, "bounds exported");
            }
            return;
        }
        if (Boolean.getBoolean("alexsbutchery.smoketest.geometryReview")) {
            if (t == 60) stageGeometryReview(mc);
            if (t == 95) GeometryChecks.run(checks);
            if (t >= 100) {
                mc.options.hideGui = true;
                int index = (t - 100) / SHOT_TICKS, phase = (t - 100) % SHOT_TICKS;
                if (index < SHOTS.size()) {
                    Shot shot = SHOTS.get(index);
                    if (phase == 0) camera(mc, shot.dx(), shot.dz(), shot.yaw(), shot.pitch(), shot.dy());
                    if (phase == SHOT_TICKS - 4) shot(mc, shot.name());
                } else {
                    int playTick = t - 100 - SHOTS.size() * SHOT_TICKS;
                    if (Boolean.getBoolean("alexsbutchery.smoketest.geometryReload")) {
                        if (phase == 10) finish(mc, allPassed(), "geometry save/reload complete");
                    } else {
                        mc.options.hideGui = false;
                        if (playTick % 40 == 9) shot(mc, "smoketest_target_laviathan_" + playTick / 40);
                        if (GeometryPlayChecks.tick(mc, playTick, base, checks)) finish(mc, allPassed(), "geometry review and gameplay complete");
                    }
                }
            }
            return;
        }
        if (HANGING_REVIEW) {
            if (t == 60) stageWorld(mc);
            if (t == 95) HangingChecks.run(checks);
            if (t >= 100) {
                mc.options.hideGui = true;
                int index = (t - 100) / SHOT_TICKS, phase = (t - 100) % SHOT_TICKS;
                if (index < SHOTS.size()) {
                    Shot shot = SHOTS.get(index);
                    if (phase == 0) camera(mc, shot.dx(), shot.dz(), shot.yaw(), shot.pitch(), shot.dy());
                    if (phase == SHOT_TICKS - 4) shot(mc, shot.name());
                } else if (phase == 10) finish(mc, allPassed(), "hanging review complete");
            }
            return;
        }
        if (t > 40 && t <= 330 && t % 5 == 1) settlePackScreen(mc);
        switch (t) {
            case 60 -> {
                checks.put("world_ms", Util.getMillis() - START);
                stageWorld(mc);
            }
            case 95 -> runChecks(mc);
            case 100 -> camera(mc, 0.0, 6.0, 180F, 16F);
            case 140 -> {
                mc.options.hideGui = true;
                shot(mc, "smoketest_overview");
            }
            case 142 -> camera(mc, -5.0, 2.5, 180F, 30F);
            case 165 -> shot(mc, "smoketest_kangaroo_ground");
            case 167 -> camera(mc, -0.5, 3.2, 180F, 10F);
            case 190 -> shot(mc, "smoketest_kangaroo_hanging_fresh_and_skinned");
            case 192 -> camera(mc, 4.0, 2.5, 180F, 30F);
            case 215 -> shot(mc, "smoketest_kangaroo_ground_cut");
            case 217 -> camera(mc, -4.0, 4.5, 180F, 32F);
            case 240 -> shot(mc, "smoketest_kangaroo_head_and_mount");
            case 242 -> camera(mc, 0.0, 4.5, 180F, 30F);
            case 265 -> shot(mc, "smoketest_roadrunner");
            case 267 -> camera(mc, 5.0, 7.0, 180F, 24F);
            case 290 -> shot(mc, "smoketest_elephant");
            case 292 -> camera(mc, 2.0, -8.0, 0F, 20F);
            case 315 -> shot(mc, "smoketest_from_behind");
            default -> {
                int shotTick = t - 330;
                if (shotTick < 0) return;
                int index = shotTick / SHOT_TICKS;
                int phase = shotTick % SHOT_TICKS;
                if (index < SHOTS.size()) {
                    Shot shot = SHOTS.get(index);
                    if (phase == 0) camera(mc, shot.dx(), shot.dz(), shot.yaw(), shot.pitch(), shot.dy());
                    if (phase == SHOT_TICKS - 4) shot(mc, shot.name());
                } else if (index == SHOTS.size() && phase == 10) {
                    finish(mc, allPassed(), "complete");
                }
            }
        }
    }

    private static void createWorld(Minecraft mc) {
        if (Boolean.getBoolean("alexsbutchery.smoketest.geometryReload")) {
            try {
                var saved = com.google.gson.JsonParser.parseString(Files.readString(Path.of(OUTPUT, "geometry-save.json"))).getAsJsonObject();
                geometryWorld = saved.get("world").getAsString();
                base = new BlockPos(saved.get("x").getAsInt(), saved.get("y").getAsInt(), saved.get("z").getAsInt());
                mc.createWorldOpenFlows().loadLevel(mc.screen, geometryWorld);
                return;
            } catch (IOException e) { throw new IllegalStateException(e); }
        }
        String name = "SmokeTest" + System.currentTimeMillis();
        geometryWorld = name;
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20260924L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static void stageGeometryReview(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            if (Boolean.getBoolean("alexsbutchery.smoketest.geometryReload")) {
                boolean rugs = true;
                int row = 0;
                for (var def : MobDefs.all()) if (def.hasRug()) {
                    int col = 0;
                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        var state = level.getBlockState(base.offset(col++ * 5, 0, row * 6));
                        rugs &= state.is(ModBlocks.of(def).rug().get()) && state.getValue(AbstractCarcassBlock.FACING) == facing;
                    }
                    row++;
                }
                checks.put("reload_rugs_all_species_and_facings", rugs);
                var be = level.getBlockEntity(base.offset(5, 0, 6));
                checks.put("reload_rug_snapshot", be instanceof CarcassBlockEntity carcass && carcass.mobData().getBoolean("ReviewMarker"));
                var cut = level.getBlockState(base.offset(78, 0, 10));
                checks.put("reload_laviathan_cut_stage", cut.is(ModBlocks.of(MobDefs.byId("laviathan")).drained().get())
                        && cut.getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == 9);
                SHOTS.add(new Shot("smoketest_reload_bison", 5, 10, 180F, 23F, 1.7));
                SHOTS.add(new Shot("smoketest_reload_laviathan_cut", 95, 8, 90F, 9F, 3.0));
                return;
            }
            base = server.getPlayerList().getPlayers().get(0).blockPosition();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            for (int x = -6; x < 105; x++) for (int z = -12; z < 40; z++)
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
            int row = 0;
            for (var def : MobDefs.all()) if (def.hasRug()) {
                int col = 0;
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    int x = col++ * 5, z = row * 6;
                    level.setBlockAndUpdate(base.offset(x, 0, z), ModBlocks.of(def).rug().get().defaultBlockState().setValue(AbstractCarcassBlock.FACING, facing));
                    SHOTS.add(new Shot("smoketest_rug_" + def.id() + "_" + facing.getName(), x, z + 4.0, 180F, 23F, 1.7));
                }
                row++;
            }
            var def = MobDefs.byId("laviathan");
            for (int stage = 0; stage < 6; stage++) {
                BlockState state = stage == 0 ? ModBlocks.of(def).carcass().get().defaultBlockState()
                        : stage == 5 ? ModBlocks.of(def).skeleton().get().defaultBlockState()
                        : ModBlocks.of(def).drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, new int[]{0, 0, 7, 8, 9}[stage]);
                int x = 30 + stage * 12;
                level.setBlockAndUpdate(base.offset(x, 0, 10), state);
                SHOTS.add(new Shot("smoketest_laviathan_stage_" + stage, x + 17, 8, 90F, 9F, 3.0));
            }
            var marker = new CompoundTag(); marker.putBoolean("ReviewMarker", true);
            ((CarcassBlockEntity) level.getBlockEntity(base.offset(5, 0, 6))).setMobData(marker);
            try {
                Files.createDirectories(Path.of(OUTPUT));
                Files.writeString(Path.of(OUTPUT, "geometry-save.json"), new GsonBuilder().create().toJson(Map.of(
                        "world", geometryWorld, "x", base.getX(), "y", base.getY(), "z", base.getZ())));
            } catch (IOException e) { checks.put("save_metadata_written", false); }
        });
    }

    private static void stageWorld(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            base = player.blockPosition();
            if (HANGING_REVIEW) {
                stageHangingReview(level);
                return;
            }
            for (int x = GRID_X0 - 4; x <= GRID_X0 + GRID_COLS * GRID_STEP; x++) for (int z = GRID_Z0 - 4; z <= 12; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
                for (int y = 0; y <= 6; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            Block hook = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("butchery:hook"));
            checks.put("butchery_hook_present", hook != null && hook != Blocks.AIR);
            MobDef kangaroo = MobDefs.byId("kangaroo");
            MobDef roadrunner = MobDefs.byId("roadrunner");
            MobDef elephant = MobDefs.byId("elephant");
            BlockState kFresh = facing(ModBlocks.of(kangaroo).carcass().get().defaultBlockState());
            BlockState kDrained = facing(ModBlocks.of(kangaroo).drained().get().defaultBlockState());
            // Row z=-3: kangaroo on the ground, hanging fresh, hanging skinned, ground half-cut.
            level.setBlockAndUpdate(base.offset(-5, 0, -3), kFresh);
            hang(level, hook, base.offset(-2, 1, -3), kFresh.setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, 1));
            hang(level, hook, base.offset(1, 1, -3), kDrained.setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 3));
            level.setBlockAndUpdate(base.offset(4, 0, -3), kDrained.setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 8));
            // Row z=1: head, trophy, roadrunner on the ground, roadrunner hanging headless, elephant.
            level.setBlockAndUpdate(base.offset(-5, 0, 1), facing(ModBlocks.of(kangaroo).head().get().defaultBlockState()));
            level.setBlockAndUpdate(base.offset(-3, 0, 1), facing(ModBlocks.of(kangaroo).mount().get().defaultBlockState()));
            level.setBlockAndUpdate(base.offset(-1, 0, 1), facing(ModBlocks.of(roadrunner).carcass().get().defaultBlockState()));
            hang(level, hook, base.offset(1, 1, 1), facing(ModBlocks.of(roadrunner).drained().get().defaultBlockState())
                    .setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 2));
            level.setBlockAndUpdate(base.offset(5, 0, 2), facing(ModBlocks.of(elephant).carcass().get().defaultBlockState()));
            // Every roster mob's fresh carcass on a grid (8 columns, 5 blocks apart), for the contact sheet.
            int i = 0;
            for (MobDef def : MobDefs.all()) {
                int col = i % GRID_COLS;
                int row = i / GRID_COLS;
                BlockPos at = base.offset(GRID_X0 + col * GRID_STEP, 0, GRID_Z0 + row * GRID_STEP);
                level.setBlockAndUpdate(at, facing(ModBlocks.of(def).carcass().get().defaultBlockState()));
                if (def.hasHead()) level.setBlockAndUpdate(at.offset(2, 0, 2), facing(ModBlocks.of(def).head().get().defaultBlockState()));
                i++;
            }
            int rows = (MobDefs.all().size() + GRID_COLS - 1) / GRID_COLS;
            for (int row = 0; row < rows; row++) {
                SHOTS.add(new Shot("smoketest_grid_row_" + row, GRID_X0 + (GRID_COLS - 1) * GRID_STEP / 2.0, GRID_Z0 + row * GRID_STEP + 9.0, 180F, 28F, 4.5));
            }
            SHOTS.add(new Shot("smoketest_grid_overview", GRID_X0 + (GRID_COLS - 1) * GRID_STEP / 2.0, GRID_Z0 + rows * GRID_STEP + 6.0, 180F, 55F, 34.0));
            stageFeatures(level, hook);
            stageGallery(level, hook);
            stageAssetReview(level);
            stageHangingReview(level);
            // A live kangaroo for scale and texture comparison.
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(kangaroo.entity());
            if (type != null && type.create(level) instanceof Mob mob) {
                mob.setNoAi(true);
                mob.moveTo(base.getX() + 8.5, base.getY(), base.getZ() - 1.5, 0F, 0F);
                mob.setYBodyRot(0F);
                mob.setYHeadRot(0F);
                level.addFreshEntity(mob);
            }
        });
    }

    /** Skeletons, rugs, catfish sizes and the multipart bodies, each row with its own shot. */
    private static void stageFeatures(ServerLevel level, Block hook) {
        for (int x = -24; x <= 26; x++) for (int z = FEATURE_Z0 - 3; z <= FEATURE_Z0 + 42; z++) {
            level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
        }
        MobDef kangaroo = MobDefs.byId("kangaroo");
        MobDef elephant = MobDefs.byId("elephant");
        MobDef tiger = MobDefs.byId("tiger");
        MobDef catfish = MobDefs.byId("catfish");
        int z = FEATURE_Z0;
        level.setBlockAndUpdate(base.offset(-12, 0, z), facing(ModBlocks.of(kangaroo).skeleton().get().defaultBlockState()));
        hang(level, hook, base.offset(-8, 1, z), facing(ModBlocks.of(kangaroo).skeleton().get().defaultBlockState())
                .setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, 1));
        level.setBlockAndUpdate(base.offset(-2, 0, z), facing(ModBlocks.of(elephant).skeleton().get().defaultBlockState()));
        level.setBlockAndUpdate(base.offset(4, 0, z), facing(ModBlocks.of(tiger).skeleton().get().defaultBlockState()));
        level.setBlockAndUpdate(base.offset(8, 0, z), facing(ModBlocks.of(catfish).skeleton().get().defaultBlockState()));
        SHOTS.add(new Shot("smoketest_feature_skeletons", -2.0, z + 8.0, 180F, 28F, 4.0));

        z = FEATURE_Z0 + 8;
        int x = -12;
        for (MobDef def : MobDefs.all()) {
            if (!def.hasRug()) continue;
            level.setBlockAndUpdate(base.offset(x, 0, z), facing(ModBlocks.of(def).rug().get().defaultBlockState()));
            x += 6;
        }
        SHOTS.add(new Shot("smoketest_feature_rugs", -3.0, z + 6.0, 180F, 42F, 5.0));
        SHOTS.add(new Shot("smoketest_feature_rugs_top", -3.0, z + 0.5, 180F, 90F, 9.0));

        z = FEATURE_Z0 + 16;
        for (int size = 0; size < 3; size++) {
            BlockPos at = base.offset(-12 + size * 4, 0, z);
            level.setBlockAndUpdate(at, facing(ModBlocks.of(catfish).carcass().get().defaultBlockState()));
            CompoundTag data = new CompoundTag();
            data.putInt("CatfishSize", size);
            if (level.getBlockEntity(at) instanceof CarcassBlockEntity be) be.setMobData(data);
        }
        SHOTS.add(new Shot("smoketest_feature_catfish_sizes", -8.0, z + 5.0, 180F, 30F, 3.0));

        z = FEATURE_Z0 + 26;
        MobDef anaconda = MobDefs.byId("anaconda");
        MobDef centipede = MobDefs.byId("centipede");
        level.setBlockAndUpdate(base.offset(-16, 0, z), facing(ModBlocks.of(anaconda).carcass().get().defaultBlockState()));
        hang(level, hook, base.offset(-10, 5, z), facing(ModBlocks.of(anaconda).drained().get().defaultBlockState())
                .setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 1));
        level.setBlockAndUpdate(base.offset(-16, 0, z + 8), facing(ModBlocks.of(anaconda).drained().get().defaultBlockState())
                .setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 8));
        level.setBlockAndUpdate(base.offset(-4, 0, z), facing(ModBlocks.of(centipede).carcass().get().defaultBlockState()));
        hang(level, hook, base.offset(2, 4, z), facing(ModBlocks.of(centipede).carcass().get().defaultBlockState())
                .setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 1));
        level.setBlockAndUpdate(base.offset(8, 0, z), facing(ModBlocks.of(MobDefs.byId("bone_serpent")).carcass().get().defaultBlockState()));
        level.setBlockAndUpdate(base.offset(18, 0, z + 6), facing(ModBlocks.of(MobDefs.byId("void_worm")).carcass().get().defaultBlockState()));
        SHOTS.add(new Shot("smoketest_feature_multipart", -6.0, z + 12.0, 180F, 32F, 7.0));
        SHOTS.add(new Shot("smoketest_feature_multipart_far", 4.0, z + 22.0, 180F, 30F, 12.0));
    }

    /** One cell per mob: the fresh carcass lying, a drained (or bloodless) one hanging, and the head. */
    private static void stageGallery(ServerLevel level, Block hook) {
        int i = 0;
        for (MobDef def : MobDefs.all()) {
            int cx = GALLERY_X0 + (i % GALLERY_COLS) * GALLERY_STEP;
            int cz = GALLERY_Z0 + (i / GALLERY_COLS) * GALLERY_STEP;
            for (int x = -6; x <= 7; x++) for (int z = -5; z <= 8; z++) {
                level.setBlockAndUpdate(base.offset(cx + x, -1, cz + z), Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
            // Side-on to the camera, which looks north: lying and hanging carcasses face east, the head faces the camera.
            level.setBlockAndUpdate(base.offset(cx, 0, cz), east(ModBlocks.of(def).carcass().get().defaultBlockState()));
            if (def.hasHead()) {
                level.setBlockAndUpdate(base.offset(cx - 3, 0, cz + 2), facing(ModBlocks.of(def).head().get().defaultBlockState()));
                level.setBlockAndUpdate(base.offset(cx - 5, 0, cz + 1), facing(ModBlocks.of(def).mount().get().defaultBlockState()));
            }
            if (!def.floor()) {
                BlockState hanging = def.bleeds()
                        ? east(ModBlocks.of(def).drained().get().defaultBlockState()).setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 1)
                        : east(ModBlocks.of(def).carcass().get().defaultBlockState()).setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 1);
                boolean chain = com.otectus.alexsbutchery.client.pose.PoseProfiles.get(def).segments != null;
                // Long bodies hang from a hook five blocks up.
                hang(level, hook, base.offset(cx + 4, chain ? 5 : 2, cz), hanging);
            }
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(def.entity());
            float size = type == null ? 1F : Math.max(type.getWidth(), type.getHeight());
            if (def.floor() || com.otectus.alexsbutchery.client.pose.PoseProfiles.get(def).segments != null) size = Math.max(size, 4F);
            double distance = 3.0 + size * 1.0;
            SHOTS.add(new Shot("smoketest_mob_" + def.id(), cx + 0.5, cz + distance, 180F, 28F, 1.4 + size * 0.55));
            i++;
        }
    }

    /** Each review row: drained, skinned, first cut, second cut, skeleton; includes tailless and aquatic bodies. */
    private static void stageAssetReview(ServerLevel level) {
        String[] mobs = {"grizzly_bear", "gorilla", "tiger", "elephant", "emu", "terrapin", "catfish", "crocodile", "rattlesnake", "anaconda", "straddler", "mimic_octopus"};
        for (int row = 0; row < mobs.length; row++) {
            MobDef def = MobDefs.byId(mobs[row]);
            var entry = ModBlocks.of(def);
            int z = 210 + row * 16;
            int spacing = def.id().equals("elephant") ? 10 : 5;
            for (int x = -6; x < spacing * 5; x++) for (int dz = -5; dz <= 6; dz++)
                level.setBlockAndUpdate(base.offset(x, -1, z + dz), Blocks.SPRUCE_PLANKS.defaultBlockState());
            for (int stage = 0; stage < 5; stage++) {
                BlockState state;
                if (stage == 4) {
                    if (entry.skeleton() == null) continue;
                    state = entry.skeleton().get().defaultBlockState();
                } else if (def.bleeds()) {
                    state = entry.drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED,
                            new int[]{0, 7, 8, 9}[stage]);
                } else {
                    state = entry.carcass().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED,
                            new int[]{0, 4, 5, 6}[stage]);
                }
                level.setBlockAndUpdate(base.offset(stage * spacing, 0, z), east(state));
            }
            SHOTS.add(new Shot("smoketest_asset_" + def.id(), spacing * 2.0, z + spacing * 2.2, 180F, 30F, spacing * 1.35));
        }
        var def = MobDefs.byId("terrapin");
        var entry = ModBlocks.of(def);
        for (int variant = 0; variant < 2; variant++) {
            int z = 410 + variant * 5;
            for (int x = -3; x < 13; x++) for (int dz = -2; dz <= 3; dz++)
                level.setBlockAndUpdate(base.offset(x, -1, z + dz), Blocks.SPRUCE_PLANKS.defaultBlockState());
            var turtle = (com.github.alexthe666.alexsmobs.entity.EntityTerrapin) ForgeRegistries.ENTITY_TYPES.getValue(def.entity()).create(level);
            CompoundTag data = AssetChecks.turtle(turtle, variant);
            turtle.setNoAi(true);
            turtle.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + z + 0.5, 90F, 0F);
            level.addFreshEntity(turtle);
            BlockState[] states = {entry.carcass().get().defaultBlockState(), entry.drained().get().defaultBlockState(), entry.head().get().defaultBlockState()};
            for (int i = 0; i < states.length; i++) {
                BlockPos at = base.offset(3 + i * 3, 0, z);
                level.setBlockAndUpdate(at, east(states[i]));
                ((CarcassBlockEntity) level.getBlockEntity(at)).setMobData(data);
            }
        }
        SHOTS.add(new Shot("smoketest_asset_terrapin_variants", 4.5, 423, 180F, 35F, 6));
    }

    private static void stageHangingReview(ServerLevel level) {
        String[] mobs = {"kangaroo", "tiger", "grizzly_bear", "gazelle", "emu", "terrapin", "crocodile", "catfish", "anaconda", "straddler", "mimic_octopus", "tarantula_hawk", "blobfish", "frilled_shark", "skelewag", "lobster"};
        Block hook = com.otectus.alexsbutchery.compat.ButcheryHooks.hook();
        Block rope = com.otectus.alexsbutchery.compat.ButcheryHooks.rope();
        for (int row = 0; row < mobs.length; row++) {
            var def = MobDefs.byId(mobs[row]);
            var entry = ModBlocks.of(def);
            int z = 450 + row * 14;
            int height = def.id().equals("anaconda") ? 7 : 4;
            for (int x = -4; x <= 25; x++) for (int dz = -3; dz <= 6; dz++)
                level.setBlockAndUpdate(base.offset(x, -1, z + dz), Blocks.SPRUCE_PLANKS.defaultBlockState());
            for (int stage = 0; stage < 6; stage++) {
                BlockState state;
                if (stage == 0) state = entry.carcass().get().defaultBlockState().setValue(def.bleeds()
                        ? AbstractCarcassBlock.BLOCKSTATE_FRESH : AbstractCarcassBlock.BLOCKSTATE_STAGED, 1);
                else if (stage == 5) {
                    if (entry.skeleton() == null) continue;
                    state = entry.skeleton().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, 1);
                } else if (def.bleeds()) state = entry.drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED,
                        new int[]{1, 1, 3, 4, 5}[stage]);
                else state = entry.carcass().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED,
                        new int[]{1, 1, 7, 8, 9}[stage]);
                BlockPos pos = base.offset(stage * 4, height, z);
                hang(level, hook, pos, east(state));
                Block support = stage == 1 || stage == 4 ? rope : hook;
                var variant = (net.minecraft.world.level.block.state.properties.IntegerProperty) support.getStateDefinition().getProperty("blockstate");
                BlockState above = support.defaultBlockState().setValue(variant, support == rope ? (stage == 1 ? 1 : 5) : stage % 3)
                        .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.from2DDataValue(stage % 4));
                level.setBlockAndUpdate(pos.above(), above);
            }
            SHOTS.add(new Shot("smoketest_hanging_" + def.id(), 10, z + 14, 180F, 10F, height + 1));
            SHOTS.add(new Shot("smoketest_attachment_" + def.id(), 0.5, z + 5.2, 180F, 14F, height - .7));
        }
        stageHookStudy(level, hook);
    }

    /** A neutral butcher-room backdrop makes hook contact and the full hanging silhouette readable. */
    private static void stageHookStudy(ServerLevel level, Block hook) {
        int z = 750, height = 3;
        for (int x = -3; x <= 13; x++) {
            for (int dz = -3; dz <= 8; dz++)
                level.setBlockAndUpdate(base.offset(x, -1, z + dz), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            for (int y = 0; y <= 6; y++)
                level.setBlockAndUpdate(base.offset(x, y, z - 2), Blocks.QUARTZ_BRICKS.defaultBlockState());
            level.setBlockAndUpdate(base.offset(x, height + 2, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            if (x % 3 == 0) level.setBlockAndUpdate(base.offset(x, height + 2, z - 1), Blocks.SEA_LANTERN.defaultBlockState());
        }
        String[] mobs = {"tiger", "gazelle", "emu", "mimic_octopus"};
        var variant = (net.minecraft.world.level.block.state.properties.IntegerProperty) hook.getStateDefinition().getProperty("blockstate");
        for (int i = 0; i < mobs.length; i++) {
            MobDef def = MobDefs.byId(mobs[i]);
            BlockPos pos = base.offset(i * 3, height, z);
            BlockState state = ModBlocks.of(def).carcass().get().defaultBlockState().setValue(def.bleeds()
                    ? AbstractCarcassBlock.BLOCKSTATE_FRESH : AbstractCarcassBlock.BLOCKSTATE_STAGED, 1);
            hang(level, hook, pos, east(state));
            level.setBlockAndUpdate(pos.above(), hook.defaultBlockState().setValue(variant, 1));
            SHOTS.add(new Shot("smoketest_hook_study_" + def.id(), i * 3 + .3, z + 4.7, 180F, 10F, height - .7));
        }
        SHOTS.add(new Shot("smoketest_hook_study", 4.5, z + 10.5, 180F, 7F, height - .7));
    }

    private static BlockState east(BlockState state) {
        return state.setValue(AbstractCarcassBlock.FACING, Direction.EAST);
    }

    private static BlockState facing(BlockState state) {
        if (state.hasProperty(AbstractCarcassBlock.FACING)) return state.setValue(AbstractCarcassBlock.FACING, Direction.SOUTH);
        if (state.hasProperty(HeadBlock.FACING)) return state.setValue(HeadBlock.FACING, Direction.SOUTH);
        return state;
    }

    private static void hang(ServerLevel level, Block hook, BlockPos carcassPos, BlockState carcass) {
        if (hook != null) level.setBlockAndUpdate(carcassPos.above(), hook.defaultBlockState());
        level.setBlockAndUpdate(carcassPos, carcass);
    }

    private static void runFeatureChecks(Minecraft mc) {
        // Skeleton and rug blocks carry the shared block entity.
        checks.put("skeleton_block_entity", mc.level.getBlockEntity(base.offset(-12, 0, FEATURE_Z0)) instanceof CarcassBlockEntity);
        checks.put("rug_block_entity", mc.level.getBlockEntity(base.offset(-12, 0, FEATURE_Z0 + 8)) instanceof CarcassBlockEntity);
        // Every segment entity named by a pose profile resolves to a model.
        java.util.List<String> missingSegments = new java.util.ArrayList<>();
        for (MobDef def : MobDefs.all()) {
            var chain = com.otectus.alexsbutchery.client.pose.PoseProfiles.get(def).segments;
            if (chain == null) continue;
            for (var segment : chain.segments) {
                if (CarcassModels.get(segment.entity()) == null) missingSegments.add(def.id() + ":" + segment.entity());
            }
        }
        checks.put("segments_without_model", missingSegments.toString());
        checks.put("all_segments_have_models", missingSegments.isEmpty());
        // The catfish renderer picks its model by size; the snapshot's size must reach it.
        CarcassModels.Handle catfish = CarcassModels.get(MobDefs.byId("catfish").entity());
        if (catfish != null) {
            CompoundTag small = new CompoundTag();
            small.putInt("CatfishSize", 0);
            CompoundTag large = new CompoundTag();
            large.putInt("CatfishSize", 2);
            CarcassModels.Shape smallShape = catfish.shape(small);
            Object smallModel = smallShape.model();
            float smallWidth = smallShape.width();
            CarcassModels.Shape largeShape = catfish.shape(large);
            checks.put("catfish_large_model_differs", largeShape.model() != smallModel);
            checks.put("catfish_large_is_wider", largeShape.width() > smallWidth);
        }
        // The renderer's own size hook reaches us: Alex's Mobs draws the emu at 0.85.
        CarcassModels.Handle emu = CarcassModels.get(MobDefs.byId("emu").entity());
        if (emu != null) {
            float scale = emu.shape(null).scale();
            checks.put("emu_renderer_scale", scale);
            checks.put("renderer_scale_applied", Math.abs(scale - 0.85F) < 0.01F);
        }
        patchouliCheck();
        // In a pack with Farmer's Delight, the conditional cutting board recipes load.
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null && net.minecraftforge.fml.ModList.get().isLoaded("farmersdelight")) {
            long cutting = server.getRecipeManager().getRecipeIds()
                    .filter(id -> id.getNamespace().equals(AlexsButchery.MOD_ID) && id.getPath().startsWith("farmersdelight/cutting/")).count();
            checks.put("fd_cutting_recipes", cutting);
            // data/ModRecipes#cutting: 14 raw meats to minced beef, 3 to chicken cuts, tusklin to bacon, shark to cod.
            checks.put("fd_cutting_recipes_loaded", cutting == 19);
        }
    }

    /** With Patchouli in the dev runtime, Butchery's guide must list our category and pages. */
    private static void patchouliCheck() {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("patchouli")) return;
        try {
            Class<?> registry = Class.forName("vazkii.patchouli.common.book.BookRegistry");
            Object instance = registry.getField("INSTANCE").get(null);
            Map<?, ?> books = (Map<?, ?>) registry.getField("books").get(instance);
            Object book = books.get(new ResourceLocation("butchery", "butchers_guide"));
            if (book == null) {
                checks.put("patchouli_guide_found", false);
                return;
            }
            Object contents = book.getClass().getMethod("getContents").invoke(book);
            Map<?, ?> categories = (Map<?, ?>) contents.getClass().getField("categories").get(contents);
            Map<?, ?> entries = (Map<?, ?>) contents.getClass().getField("entries").get(contents);
            long ours = entries.keySet().stream().filter(id -> id.toString().startsWith("butchery:alexsbutchery/")).count();
            checks.put("patchouli_category_loaded", categories.containsKey(new ResourceLocation("butchery", "alexsbutchery")));
            checks.put("patchouli_entries", ours);
            checks.put("patchouli_entries_loaded", ours >= 10);
        } catch (ReflectiveOperationException | RuntimeException e) {
            checks.put("patchouli_error", e.toString());
            checks.put("patchouli_entries_loaded", false);
        }
    }

    private static void runChecks(Minecraft mc) {
        java.util.List<String> noProfile = new java.util.ArrayList<>();
        for (MobDef def : MobDefs.all()) {
            if (!com.otectus.alexsbutchery.client.pose.PoseProfiles.has(def)) noProfile.add(def.id());
        }
        checks.put("mobs_without_pose_profile", noProfile.toString());
        checks.put("all_mobs_have_pose_profiles", noProfile.isEmpty());
        java.util.List<String> noModel = new java.util.ArrayList<>();
        java.util.List<String> noHead = new java.util.ArrayList<>();
        java.util.List<String> noTexture = new java.util.ArrayList<>();
        for (MobDef def : MobDefs.all()) {
            CarcassModels.Handle handle = CarcassModels.get(def.entity());
            if (handle == null) {
                noModel.add(def.id());
                continue;
            }
            var profile = com.otectus.alexsbutchery.client.pose.PoseProfiles.get(def);
            CarcassModels.Shape shape = handle.shape(null);
            if (def.hasHead() && !shape.parts().containsKey(profile.headRoot)) noHead.add(def.id() + "(" + profile.headRoot + ")");
            ResourceLocation texture = shape.texture();
            if (StageTextures.get(texture, StageTextures.Look.SKINNED).equals(texture)) noTexture.add(def.id());
        }
        checks.put("mobs", MobDefs.all().size());
        checks.put("mobs_without_model", noModel.toString());
        checks.put("all_mobs_have_models", noModel.isEmpty());
        checks.put("heads_without_head_part", noHead.toString());
        checks.put("all_heads_have_parts", noHead.isEmpty());
        checks.put("mobs_without_stage_textures", noTexture.toString());
        checks.put("all_stage_textures_generated", noTexture.isEmpty());
        checks.put("ground_block_entity", mc.level.getBlockEntity(base.offset(-5, 0, -3)) instanceof CarcassBlockEntity);
        checks.put("hanging_block_entity", mc.level.getBlockEntity(base.offset(-2, 1, -3)) instanceof CarcassBlockEntity);
        checks.put("mount_block_entity", mc.level.getBlockEntity(base.offset(-3, 0, 1)) instanceof CarcassBlockEntity);
        runFeatureChecks(mc);
        AssetChecks.run(checks);
        GeometryChecks.run(checks);
    }

    private static void camera(Minecraft mc, double dx, double dz, float yaw, float pitch) {
        camera(mc, dx, dz, yaw, pitch, 1.7);
    }

    private static void camera(Minecraft mc, double dx, double dz, float yaw, float pitch, double dy) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || base == null) return;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(server.overworld(), base.getX() + 0.5 + dx, base.getY() + dy, base.getZ() + 0.5 + dz, yaw, pitch);
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> log("screenshot " + name));
    }

    private static boolean allPassed() {
        for (Object value : checks.values()) {
            if (value instanceof Boolean b && !b) return false;
        }
        return !checks.containsKey("agent_exception");
    }

    private static void finish(Minecraft mc, boolean pass, String reason) {
        finished = true;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", pass);
        result.put("reason", reason);
        result.put("checks", checks);
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT, "smoketest-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            AlexsButchery.LOGGER.error("SMOKETEST could not write result", e);
        }
        log("finished: pass=" + pass + " (" + reason + ")");
        mc.stop();
    }

    private static void log(String message) {
        AlexsButchery.LOGGER.info("SMOKETEST {}", message);
    }

    private ClientSmokeTest() {}
}
