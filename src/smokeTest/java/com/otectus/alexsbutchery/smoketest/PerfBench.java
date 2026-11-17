package com.otectus.alexsbutchery.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.block.CarcassTargeting;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Frame-time and subsystem benchmark for carcass targeting ({@code runClient -PsmokeTest -PperfBench}). Builds the same
 * scenes in a fresh flat world, fixes the camera, samples frame times with vsync and the frame cap off, times the
 * picker, vanilla's shape lookup, clip and outline traversal in place, and writes {@code perf-<label>.json}.
 * Presentation cases require this release; their legacy item measurement reconstructs the captured pre-fix transforms.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class PerfBench {
    static final boolean ENABLED = Boolean.getBoolean("alexsbutchery.smoketest.perfBench");
    private static final String OUTPUT = System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY);
    private static final String LABEL = System.getProperty("alexsbutchery.smoketest.perfLabel", "run");
    private static final double WARMUP_S = Double.parseDouble(System.getProperty("alexsbutchery.smoketest.perfWarmup", "3"));
    private static final double SAMPLE_S = Double.parseDouble(System.getProperty("alexsbutchery.smoketest.perfSeconds", "10"));
    private static final int SAMPLES = Integer.getInteger("alexsbutchery.smoketest.perfSamples", 3);
    private static final String[] NAMED = {"laviathan", "void_worm", "centipede", "farseer"};
    private static final List<String> SCENE_GROUPS = List.of("empty", "cold", "presentation", "items32",
            "laviathan", "void_worm", "centipede", "farseer", "mixed16", "dense64", "barrels16",
            "worst_ring", "lifecycle");
    private static final List<String> REMAINING_SCENE_GROUPS = List.of("mixed16", "dense64", "barrels16",
            "worst_ring", "lifecycle");
    private static final String SCENE_FILTER = System.getProperty("alexsbutchery.smoketest.perfScenes",
            System.getenv().getOrDefault("ALEXS_BUTCHERY_PERF_SCENES", "all"));
    private static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private static final Map<String, Object> results = new LinkedHashMap<>();
    private static final Map<String, Object> cases = new LinkedHashMap<>();
    private static final List<Runnable> plan = new ArrayList<>();
    private static int titleTicks;
    private static boolean worldRequested;
    private static boolean finished;
    private static int worldTicks;
    private static boolean begun;
    private static BlockPos base;

    // The case being measured.
    private static Case current;
    private static long caseStart;
    private static final List<Long> frameNanos = new ArrayList<>();
    private static long lastFrame;
    private static boolean sampling;
    private static int sampleIndex;
    private static final List<Map<String, Object>> sampleStats = new ArrayList<>();
    private static long waitUntil;
    private static Runnable afterWait;
    /** Server work the next step must not overlap: its lock and CPU would leak into the following case's frames. */
    private static java.util.concurrent.CompletableFuture<?> pending;
    private static long pendingDeadline;
    private static String pendingDescription;
    private static Runnable afterPending;
    /** Client-side arrival or synchronization condition that must become true before the plan may continue. */
    private static BooleanSupplier readiness;
    private static Supplier<String> readinessDetail;
    private static long readinessDeadline;
    private static String readinessDescription;
    private static Runnable afterReadiness;

    private static final List<Map<String, Object>> arenaSync = new ArrayList<>();
    private static final ThreadLocal<StageCapture> STAGING = new ThreadLocal<>();

    private record ExpectedBlock(BlockPos pos, BlockState state, boolean blockEntity) {}

    private static final class StageCapture {
        final List<ExpectedBlock> carcasses = new ArrayList<>();
        final List<ExpectedBlock> representatives = new ArrayList<>();
        int itemEntities;
    }

    /** One measured camera: where the eye is, what it looks at, and per-tick work (mining, target changes). */
    private record Case(String name, Vec3 eye, float yaw, float pitch, boolean survival, BlockPos expected,
                        Consumer<Minecraft> perTick) {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || OUTPUT == null || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player == null || mc.level == null) {
                if (mc.screen instanceof TitleScreen && ++titleTicks == 1) mc.getWindow().setWindowed(1280, 720);
                if (mc.screen instanceof TitleScreen && titleTicks == 40 && !worldRequested) {
                    worldRequested = true;
                    createWorld(mc);
                }
                return;
            }
            if (++worldTicks < 60) return;
            if (!begun) {
                begun = true;
                begin(mc);
            }
            if (reopened != null) {
                Runnable r = reopened;
                reopened = null;
                r.run();
            }
            if (mc.screen != null) mc.setScreen(null);
            if (pending != null) {
                if (!pending.isDone()) {
                    if (System.nanoTime() >= pendingDeadline)
                        throw new IllegalStateException("Timed out waiting for " + pendingDescription);
                    return;
                }
                pending.join();
                Runnable next = afterPending;
                pending = null;
                pendingDescription = null;
                afterPending = null;
                if (next != null) next.run();
                return;
            }
            if (readiness != null) {
                if (readiness.getAsBoolean()) {
                    Runnable next = afterReadiness;
                    readiness = null;
                    readinessDetail = null;
                    readinessDescription = null;
                    afterReadiness = null;
                    next.run();
                } else if (System.nanoTime() >= readinessDeadline) {
                    String detail = readinessDetail == null ? "not ready" : readinessDetail.get();
                    throw new IllegalStateException("Timed out waiting for " + readinessDescription + ": " + detail);
                }
                return;
            }
            if (afterWait != null) {
                if (System.nanoTime() >= waitUntil) {
                    Runnable next = afterWait;
                    afterWait = null;
                    next.run();
                }
                return;
            }
            if (current != null) {
                holdCamera(mc, current);
                if (current.perTick() != null) current.perTick().accept(mc);
                return;
            }
            if (!plan.isEmpty()) plan.remove(0).run();
            else finish(mc, null);
        } catch (Exception e) {
            AlexsButchery.LOGGER.error("PERFBENCH failed", e);
            finish(mc, e);
        }
    }

    @SubscribeEvent
    public static void onFrame(TickEvent.RenderTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || current == null) return;
        long now = System.nanoTime();
        if (sampling && lastFrame != 0) frameNanos.add(now - lastFrame);
        lastFrame = now;
        double elapsed = (now - caseStart) / 1E9;
        if (!sampling && elapsed >= WARMUP_S) {
            sampling = true;
            frameNanos.clear();
            caseStart = now;
        } else if (sampling && elapsed >= SAMPLE_S) {
            sampleStats.add(stats(frameNanos));
            frameNanos.clear();
            caseStart = now;
            if (++sampleIndex >= SAMPLES) endCase(Minecraft.getInstance());
        }
    }

    private static void createWorld(Minecraft mc) {
        String name = "PerfBench" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261010L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static void begin(Minecraft mc) {
        var options = mc.options;
        options.enableVsync().set(false);
        mc.getWindow().updateVsync(false);
        options.framerateLimit().set(260);
        options.renderDistance().set(8);
        options.simulationDistance().set(8);
        options.bobView().set(false);
        options.fov().set(70);
        options.pauseOnLostFocus = false;
        var server = mc.getSingleplayerServer();
        base = server.getPlayerList().getPlayers().get(0).blockPosition();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        });
        results.put("label", LABEL);
        results.put("mod_version", net.minecraftforge.fml.ModList.get().getModContainerById(AlexsButchery.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?"));
        results.put("settings", Map.of("warmup_s", WARMUP_S, "sample_s", SAMPLE_S, "samples", SAMPLES,
                "window", mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight(), "render_distance", 8, "vsync", false,
                "framerate_limit", "unlimited", "gl_renderer", com.mojang.blaze3d.platform.GlUtil.getRenderer(),
                "cpu", com.mojang.blaze3d.platform.GlUtil.getCpuInfo(), "java", System.getProperty("java.version")));
        results.put("cases", cases);
        results.put("arena_sync", arenaSync);
        buildPlan(mc);
    }

    // --- the plan ---------------------------------------------------------------------------------------------

    private static void buildPlan(Minecraft mc) {
        List<String> selectedGroups = selectedSceneGroups();
        Set<String> selected = new LinkedHashSet<>(selectedGroups);
        results.put("scene_filter", SCENE_FILTER);
        results.put("selected_scene_groups", selectedGroups);
        results.put("arena_layout", Map.ofEntries(Map.entry("empty", 1), Map.entry("items32", 2),
                Map.entry("laviathan", 3), Map.entry("void_worm", 4), Map.entry("centipede", 5),
                Map.entry("farseer", 6), Map.entry("mixed16", 7), Map.entry("dense64", 8),
                Map.entry("barrels16", 9), Map.entry("worst_ring", 10), Map.entry("after_world_switch", 21)));
        // Empty arena: the frame-time floor and a plain block target.
        if (selected.contains("empty")) plan.add(() -> arena(mc, 1, (level, centre) -> {}, centre -> List.of(
                () -> lookAway("empty/look_away", centre),
                () -> blockTarget("empty/block_target", centre))));
        if (selected.contains("cold")) plan.add(() -> coldBuilds(mc));
        if (selected.contains("presentation")) plan.add(() -> presentationCosts(mc));
        if (selected.contains("items32")) plan.add(() -> arena(mc, 2, PerfBench::itemPile, centre -> List.of(
                () -> overview("items32/view_all", centre, 18))));
        for (int i = 0; i < NAMED.length; i++) {
            String id = NAMED[i];
            int arena = i + 3;
            if (selected.contains(id)) plan.add(() -> arena(mc, arena,
                    (level, centre) -> place(level, centre, carcass(id, 0, Direction.NORTH), null),
                    centre -> named(mc, id, centre)));
        }
        if (selected.contains("mixed16")) plan.add(() -> arena(mc, 7, PerfBench::mixed16, centre -> List.of(
                () -> lookAway("mixed16/look_away", centre),
                () -> overview("mixed16/view_all", centre, 22),
                () -> hover("mixed16/hover", centre),
                () -> floorTarget("mixed16/same_view_floor_target", centre))));
        if (selected.contains("dense64")) plan.add(() -> arena(mc, 8, PerfBench::dense64, centre -> List.of(
                () -> overview("dense64/view_all", centre, 20),
                () -> hover("dense64/hover", centre))));
        if (selected.contains("barrels16")) plan.add(() -> arena(mc, 9,
                (level, centre) -> { mixed16(level, centre); barrels(level, centre); }, centre -> List.of(
                () -> lookAway("barrels16/look_away", centre),
                () -> hover("barrels16/hover", centre))));
        if (selected.contains("worst_ring"))
            plan.add(() -> arena(mc, 10, PerfBench::worstRing, centre -> List.of(() -> rapid(centre))));
        if (selected.contains("lifecycle")) plan.add(() -> lifecycle(mc));
        if (plan.isEmpty()) throw new IllegalArgumentException("PerfBench scene filter selected no work: " + SCENE_FILTER);
    }

    private static List<String> selectedSceneGroups() {
        String requested = SCENE_FILTER == null ? "all" : SCENE_FILTER.trim();
        if (requested.isEmpty() || requested.equalsIgnoreCase("all")) return SCENE_GROUPS;
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String token : requested.split("[,\\s]+")) {
            if (token.equalsIgnoreCase("remaining")) selected.addAll(REMAINING_SCENE_GROUPS);
            else if (token.equalsIgnoreCase("named")) selected.addAll(List.of(NAMED));
            else if (SCENE_GROUPS.contains(token)) selected.add(token);
            else throw new IllegalArgumentException("Unknown PerfBench scene group '" + token + "'; expected one of "
                        + SCENE_GROUPS + ", named, remaining, or all");
        }
        return List.copyOf(selected);
    }

    private interface Stage { void stage(ServerLevel level, BlockPos centre); }

    /**
     * A fresh arena 512 blocks along x. The server teleport, client arrival/chunk load, server staging and client receipt
     * are separate bounded barriers: a wall-clock delay alone can race an in-flight pre-stage chunk snapshot in a pack.
     */
    private static void arena(Minecraft mc, int arena, Stage stage,
                              java.util.function.Function<BlockPos, List<java.util.function.Supplier<Case>>> cameras) {
        BlockPos centre = base.offset(512 * arena, 0, 0);
        var server = mc.getSingleplayerServer();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("arena", arena);
        evidence.put("centre", List.of(centre.getX(), centre.getY(), centre.getZ()));
        evidence.put("status", "server_teleport");
        arenaSync.add(evidence);
        long teleportStart = System.nanoTime();
        awaitFuture(server.submit(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.getAbilities().invulnerable = true;
            player.onUpdateAbilities();
            player.teleportTo(server.overworld(), centre.getX() + .5, centre.getY() + 8, centre.getZ() - 12, 0, 20);
        }), "arena " + arena + " server teleport", 30, () -> {
            evidence.put("server_teleport_ms", elapsedMs(teleportStart));
            evidence.put("status", "client_arrival");
            long arrivalStart = System.nanoTime();
            awaitCondition("arena " + arena + " client arrival and chunks", 60,
                    () -> clientArrived(mc, centre), () -> arrivalStatus(mc, centre), () -> {
                        evidence.put("client_arrival_ms", elapsedMs(arrivalStart));
                        evidence.put("arrival_chunks", loadedArenaChunks(mc, centre));
                        evidence.put("arrival_chunk_check", "FULL_non_null_coordinate_match");
                        stageArena(mc, server, stage, cameras, centre, arena, evidence);
                    });
        });
    }

    private static void stageArena(Minecraft mc, net.minecraft.client.server.IntegratedServer server, Stage stage,
                                   java.util.function.Function<BlockPos, List<java.util.function.Supplier<Case>>> cameras,
                                   BlockPos centre, int arena, Map<String, Object> evidence) {
        StageCapture capture = new StageCapture();
        evidence.put("status", "server_stage");
        long stageStart = System.nanoTime();
        awaitFuture(server.submit(() -> {
            STAGING.set(capture);
            try {
                stage.stage(server.overworld(), centre);
                assertServerStage(server.overworld(), centre, capture);
            } finally {
                STAGING.remove();
            }
        }), "arena " + arena + " server stage", 60, () -> {
            evidence.put("server_stage_ms", elapsedMs(stageStart));
            evidence.put("expected_carcasses", capture.carcasses.size());
            evidence.put("expected_representatives", capture.representatives.size());
            evidence.put("expected_item_entities", capture.itemEntities);
            evidence.put("status", "client_stage_grace");
            evidence.put("resync_attempted", false);
            long visibilityStart = System.nanoTime();
            // Give normal tracked updates a brief chance to arrive. A missing block after this point is explicitly
            // resent from the unchanged authoritative state; no server block is rewritten to make the test pass.
            wait(2, () -> observeOrResyncArena(mc, server, cameras, centre, arena, capture, evidence, visibilityStart));
        });
    }

    private static void observeOrResyncArena(Minecraft mc, net.minecraft.client.server.IntegratedServer server,
                                             java.util.function.Function<BlockPos, List<java.util.function.Supplier<Case>>> cameras,
                                             BlockPos centre, int arena, StageCapture capture,
                                             Map<String, Object> evidence, long visibilityStart) {
        String observation = sceneStatus(mc, centre, capture);
        evidence.put("initial_client_stage_observation", observation);
        if (observation.equals("ready")) {
            queueArenaCases(mc, cameras, centre, capture, evidence, visibilityStart);
            return;
        }

        evidence.put("resync_attempted", true);
        evidence.put("status", "server_state_resync");
        int[] packetCounts = new int[2];
        awaitFuture(server.submit(() -> {
            ServerLevel level = server.overworld();
            assertServerStage(level, centre, capture);
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            if (player.level() != level || player.connection == null)
                throw new IllegalStateException("Arena " + arena + " player is not connected to the authoritative level");
            for (ExpectedBlock expected : allExpected(capture)) {
                BlockState actual = level.getBlockState(expected.pos());
                player.connection.send(new ClientboundBlockUpdatePacket(expected.pos(), actual));
                packetCounts[0]++;
                if (expected.blockEntity()) {
                    var blockEntity = level.getBlockEntity(expected.pos());
                    if (blockEntity == null)
                        throw new IllegalStateException("Server block entity disappeared before resync at " + expected.pos());
                    player.connection.send(ClientboundBlockEntityDataPacket.create(blockEntity));
                    packetCounts[1]++;
                }
            }
        }), "arena " + arena + " authoritative block resync", 30, () -> {
            evidence.put("resync_block_packets", packetCounts[0]);
            evidence.put("resync_block_entity_packets", packetCounts[1]);
            evidence.put("status", "client_stage_visibility");
            awaitCondition("arena " + arena + " staged scene visibility after authoritative resync", 60,
                    () -> sceneStatus(mc, centre, capture).equals("ready"),
                    () -> sceneStatus(mc, centre, capture),
                    () -> queueArenaCases(mc, cameras, centre, capture, evidence, visibilityStart));
        });
    }

    private static void queueArenaCases(Minecraft mc,
                                        java.util.function.Function<BlockPos, List<java.util.function.Supplier<Case>>> cameras,
                                        BlockPos centre, StageCapture capture, Map<String, Object> evidence,
                                        long visibilityStart) {
        evidence.put("client_stage_visibility_ms", elapsedMs(visibilityStart));
        evidence.put("client_item_entities", clientItemCount(mc, centre));
        evidence.put("final_client_stage_observation", sceneStatus(mc, centre, capture));
        evidence.put("status", "ready");
        // Camera suppliers resolve only after every captured carcass and representative reached the client.
        var list = cameras.apply(centre);
        for (int i = list.size() - 1; i >= 0; i--) {
            var c = list.get(i);
            plan.add(0, () -> startCase(mc, c.get()));
        }
    }

    private static boolean clientArrived(Minecraft mc, BlockPos centre) {
        if (mc.level == null || mc.player == null || !mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return false;
        double dx = mc.player.getX() - (centre.getX() + .5);
        double dy = mc.player.getY() - (centre.getY() + 8);
        double dz = mc.player.getZ() - (centre.getZ() - 12);
        return dx * dx + dy * dy + dz * dz < 4 && loadedArenaChunks(mc, centre) == 49;
    }

    /** Seven by seven real FULL chunks covers the widest stage (the 80x80 barrel square) before it is modified. */
    private static int loadedArenaChunks(Minecraft mc, BlockPos centre) {
        if (mc.level == null) return 0;
        int loaded = 0;
        for (int x = -48; x <= 48; x += 16) for (int z = -48; z <= 48; z += 16) {
            int chunkX = (centre.getX() + x) >> 4;
            int chunkZ = (centre.getZ() + z) >> 4;
            // ClientLevel.hasChunk(int, int) intentionally always returns true. Query the cache without its fallback
            // empty chunk and verify the coordinate because storage slots are reused as the view centre moves.
            var chunk = mc.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            if (chunk != null && chunk.getPos().x == chunkX && chunk.getPos().z == chunkZ) loaded++;
        }
        return loaded;
    }

    private static String arrivalStatus(Minecraft mc, BlockPos centre) {
        if (mc.level == null || mc.player == null) return "no client level/player";
        return "player=" + mc.player.position() + ", target=" + centre + ", loaded_chunks=" + loadedArenaChunks(mc, centre) + "/49";
    }

    private static void assertServerStage(ServerLevel level, BlockPos centre, StageCapture capture) {
        for (ExpectedBlock expected : allExpected(capture)) {
            BlockState actual = level.getBlockState(expected.pos());
            if (actual != expected.state())
                throw new IllegalStateException("Server stage changed " + expected.pos() + ": expected " + expected.state() + ", got " + actual);
            if (expected.blockEntity() && level.getBlockEntity(expected.pos()) == null)
                throw new IllegalStateException("Server stage has no block entity at " + expected.pos() + " for " + actual);
        }
        int items = serverItemCount(level, centre);
        if (items < capture.itemEntities)
            throw new IllegalStateException("Server stage has " + items + "/" + capture.itemEntities + " item entities at " + centre);
    }

    private static String sceneStatus(Minecraft mc, BlockPos centre, StageCapture capture) {
        if (mc.level == null) return "no client level";
        for (ExpectedBlock expected : allExpected(capture)) {
            BlockState actual = mc.level.getBlockState(expected.pos());
            if (actual != expected.state()) return "state " + expected.pos() + " expected " + expected.state() + ", got " + actual;
            if (expected.blockEntity() && mc.level.getBlockEntity(expected.pos()) == null)
                return "missing block entity at " + expected.pos() + " for " + actual;
        }
        int items = clientItemCount(mc, centre);
        if (items < capture.itemEntities) return "item entities " + items + "/" + capture.itemEntities;
        return "ready";
    }

    private static List<ExpectedBlock> allExpected(StageCapture capture) {
        List<ExpectedBlock> all = new ArrayList<>(capture.carcasses.size() + capture.representatives.size());
        all.addAll(capture.carcasses);
        all.addAll(capture.representatives);
        return all;
    }

    private static int clientItemCount(Minecraft mc, BlockPos centre) {
        if (mc.level == null) return 0;
        AABB area = new AABB(centre).inflate(12, 4, 12);
        return mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).size();
    }

    private static int serverItemCount(ServerLevel level, BlockPos centre) {
        AABB area = new AABB(centre).inflate(12, 4, 12);
        return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).size();
    }

    private static List<java.util.function.Supplier<Case>> named(Minecraft mc, String id, BlockPos centre) {
        List<java.util.function.Supplier<Case>> list = new ArrayList<>();
        list.add(() -> lookAway(id + "/look_away", centre));
        list.add(() -> overview(id + "/view_out_of_reach", centre, 11));
        list.add(() -> blockTarget(id + "/block_target", centre));
        list.add(() -> hover(id + "/hover", centre));
        list.add(() -> floorTarget(id + "/same_view_floor_target", centre));
        list.add(() -> {
            Case hover = hover(id + "/mine", centre);
            return new Case(hover.name(), hover.eye(), hover.yaw(), hover.pitch(), true, hover.expected(), PerfBench::mine);
        });
        return list;
    }

    // --- scenes -----------------------------------------------------------------------------------------------

    private static BlockState carcass(String id, int stage, Direction facing) {
        var block = (AbstractCarcassBlock) ModBlocks.of(MobDefs.byId(id)).carcass().get();
        return block.defaultBlockState().setValue(block.stateProperty(), stage).setValue(AbstractCarcassBlock.FACING, facing);
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state, CompoundTag data) {
        level.setBlockAndUpdate(pos, state);
        if (data != null && level.getBlockEntity(pos) instanceof com.otectus.alexsbutchery.block.entity.CarcassBlockEntity be) be.setMobData(data);
        BlockState actual = level.getBlockState(pos);
        if (actual != state || level.getBlockEntity(pos) == null)
            throw new IllegalStateException("Could not stage carcass at " + pos + ": expected " + state + ", got " + actual);
        StageCapture capture = STAGING.get();
        if (capture != null) capture.carcasses.add(new ExpectedBlock(pos.immutable(), state, true));
    }

    private static void mixed16(ServerLevel level, BlockPos centre) {
        for (int i = 0; i < 16; i++) {
            BlockPos pos = centre.offset((i % 4 - 1) * 11, 0, (i / 4 - 1) * 11);
            place(level, pos, carcass(NAMED[i % 4], 0, Direction.from2DDataValue(i % 4)), null);
        }
    }

    private static void dense64(ServerLevel level, BlockPos centre) {
        List<BlockState> states = new ArrayList<>();
        for (MobDef def : MobDefs.all()) {
            var entry = ModBlocks.of(def);
            for (var holder : new Object[]{entry.carcass(), entry.drained(), entry.skeleton()}) {
                if (holder == null) continue;
                var block = (AbstractCarcassBlock) ((net.minecraftforge.registries.RegistryObject<?>) holder).get();
                for (int stage : block.stateProperty().getPossibleValues()) {
                    var state = block.defaultBlockState().setValue(block.stateProperty(), stage);
                    if (!block.hanging(state)) states.add(state);
                }
            }
        }
        // Deterministic spread over species, stages and skeletons.
        for (int i = 0; i < 64; i++) {
            BlockState state = states.get((int) ((long) i * 7919 % states.size())).setValue(AbstractCarcassBlock.FACING, Direction.from2DDataValue(i % 4));
            BlockPos pos = centre.offset((i % 8 - 4) * 5, 0, (i / 8 - 4) * 5);
            CompoundTag data = null;
            if (i % 9 == 0) { data = new CompoundTag(); data.putInt("CatfishSize", 2); }
            place(level, pos, state, data);
        }
    }

    private static void barrels(ServerLevel level, BlockPos centre) {
        int count = 0;
        for (int x = -40; x < 40; x++) for (int z = -40; z < 40; z++) {
            level.setBlock(centre.offset(x, -3, z), Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
            count++;
        }
        results.put("barrels_block_entities", count);
        StageCapture capture = STAGING.get();
        if (capture != null) {
            for (BlockPos pos : List.of(centre.offset(-32, -3, -32), centre.offset(0, -3, 0), centre.offset(32, -3, 32))) {
                capture.representatives.add(new ExpectedBlock(pos.immutable(), Blocks.BARREL.defaultBlockState(), true));
            }
        }
    }

    private static final String[][] WORST = {{"anaconda", "skeleton", "1"}, {"cachalot_whale", "skeleton", "1"},
            {"void_worm", "drained", "1"}, {"laviathan", "skeleton", "1"}, {"orca", "skeleton", "1"},
            {"centipede", "carcass", "1"}, {"laviathan", "skeleton", "0"}, {"void_worm", "carcass", "1"}};
    private static final List<BlockPos> ring = new ArrayList<>();

    private static void worstRing(ServerLevel level, BlockPos centre) {
        ring.clear();
        for (int i = 0; i < WORST.length; i++) {
            var entry = ModBlocks.of(MobDefs.byId(WORST[i][0]));
            var holder = switch (WORST[i][1]) { case "skeleton" -> entry.skeleton(); case "drained" -> entry.drained(); default -> entry.carcass(); };
            if (holder == null) continue;
            var block = (AbstractCarcassBlock) holder.get();
            var state = block.defaultBlockState().setValue(block.stateProperty(), Integer.parseInt(WORST[i][2]));
            double angle = Math.PI * 2 * i / WORST.length;
            BlockPos pos = centre.offset((int) Math.round(Math.sin(angle) * 14), 0, (int) Math.round(Math.cos(angle) * 14));
            place(level, pos, state, null);
            ring.add(pos);
        }
    }

    // --- cameras ----------------------------------------------------------------------------------------------

    private static Case lookAway(String name, BlockPos centre) {
        return new Case(name, Vec3.atBottomCenterOf(centre).add(0, 1.6, -3.5), 180F, 0F, false, null, null);
    }

    private static Case overview(String name, BlockPos centre, double back) {
        return new Case(name, Vec3.atBottomCenterOf(centre).add(0, back * .45, -back), 0F, 24F, false, null, null);
    }

    /** A stone pillar beside the arena centre, aimed at from the same distance as a carcass hover. */
    private static Case blockTarget(String name, BlockPos centre) {
        BlockPos stone = centre.offset(0, 0, -8);
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            var level = Minecraft.getInstance().getSingleplayerServer().overworld();
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(stone.above(), Blocks.STONE.defaultBlockState());
        });
        Vec3 target = Vec3.atCenterOf(stone);
        Vec3 eye = target.add(0, .5, -2.8);
        return aimed(name, eye, target, false, stone, null);
    }

    /** Aims at the middle of the carcass's largest selection box from 2.6 blocks outside it. */
    private static Case hover(String name, BlockPos pos) {
        var level = Minecraft.getInstance().level;
        BlockState state = level.getBlockState(pos);
        List<AABB> boxes = state.getBlock() instanceof AbstractCarcassBlock ? CarcassBounds.boxes(state, level, pos) : List.of();
        if (boxes.isEmpty()) {
            // Preserve the historical mine fallback as a reported case distinction. Every ordinary hover is strict:
            // arena synchronization already proved it existed, so losing it is a failed benchmark, not a look-away case.
            if (name.endsWith("/mine")) return lookAway(name + "_missing", pos);
            throw new IllegalStateException("Staged carcass missing before " + name + " at " + pos + ": " + state);
        }
        AABB box = boxes.stream().max(Comparator.comparingDouble(b -> b.getXsize() * b.getYsize() * b.getZsize())).orElseThrow();
        Vec3 target = box.getCenter().add(Vec3.atLowerCornerOf(pos));
        Vec3 eye = new Vec3(target.x, target.y + .9, pos.getZ() + box.minZ - 2.6);
        return aimed(name, eye, target, false, pos, null);
    }

    /**
     * The hover camera's eye, with the crosshair on a floor block beside the anatomy instead: the same view, a plain
     * target. Takes the first nearby floor point whose ray provably meets no carcass.
     */
    private static Case floorTarget(String name, BlockPos pos) {
        Case hover = hover(name, pos);
        var mc = Minecraft.getInstance();
        for (double[] d : new double[][]{{0, 1.6}, {-1.2, 1.4}, {1.2, 1.4}, {-1.8, .6}, {1.8, .6}, {-1.6, -.4}, {1.6, -.4}, {0, -1.2}}) {
            Vec3 target = new Vec3(hover.eye().x + d[0], pos.getY(), hover.eye().z + d[1]);
            Vec3 end = hover.eye().add(target.subtract(hover.eye()).normalize().scale(4));
            if (CarcassTargeting.pick(mc.level, hover.eye(), end, mc.player) == null)
                return aimed(name, hover.eye(), target, false, BlockPos.containing(target.x, pos.getY() - .5, target.z), null);
        }
        return lookAway(name + "_no_clear_floor", pos);
    }

    private static Case aimed(String name, Vec3 eye, Vec3 target, boolean survival, BlockPos expected, Consumer<Minecraft> perTick) {
        Vec3 d = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        return new Case(name, eye, yaw, pitch, survival, expected, perTick);
    }

    private static Case rapid(BlockPos centre) {
        Vec3 eye = Vec3.atBottomCenterOf(centre).add(0, 1.6, 0);
        int[] index = {0};
        return new Case("worst_ring/rapid_targets", eye, 0, 10, false, null, mc -> {
            if (ring.isEmpty()) return;
            BlockPos pos = ring.get(index[0]++ % ring.size());
            var c = hover("ring", pos);
            mc.player.setPos(c.eye().x, c.eye().y - mc.player.getEyeHeight(), c.eye().z);
            mc.player.xo = mc.player.xOld = c.eye().x; mc.player.yo = mc.player.yOld = c.eye().y - mc.player.getEyeHeight(); mc.player.zo = mc.player.zOld = c.eye().z;
            setRotation(mc, c.yaw(), c.pitch());
        });
    }

    private static int mineTicks;

    private static void mine(Minecraft mc) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || mc.level.isEmptyBlock(hit.getBlockPos())) return;
        if (mineTicks++ % 20 == 0) {
            mc.gameMode.stopDestroyBlock();
            mc.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());
        } else if (mc.gameMode.continueDestroyBlock(hit.getBlockPos(), hit.getDirection())) {
            mc.particleEngine.crack(hit.getBlockPos(), hit.getDirection());
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    // --- measuring --------------------------------------------------------------------------------------------

    private static void startCase(Minecraft mc, Case c) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.setGameMode(c.survival() ? GameType.SURVIVAL : GameType.CREATIVE);
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.getAbilities().invulnerable = true;
            player.onUpdateAbilities();
            player.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            player.teleportTo(server.overworld(), c.eye().x, c.eye().y - player.getEyeHeight(), c.eye().z, c.yaw(), c.pitch());
        });
        mineTicks = 0;
        wait(2.5, () -> {
            current = c;
            sampling = false;
            sampleIndex = 0;
            sampleStats.clear();
            frameNanos.clear();
            lastFrame = 0;
            caseStart = System.nanoTime();
        });
    }

    private static void holdCamera(Minecraft mc, Case c) {
        if (c.name().startsWith("worst_ring")) return;
        setRotation(mc, c.yaw(), c.pitch());
    }

    private static void setRotation(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw); mc.player.setXRot(pitch);
        mc.player.yRotO = yaw; mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw); mc.player.yHeadRotO = yaw;
        mc.player.setYBodyRot(yaw); mc.player.yBodyRotO = yaw;
    }

    private static void endCase(Minecraft mc) {
        Case c = current;
        current = null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("samples", new ArrayList<>(sampleStats));
        List<Double> p95 = sampleStats.stream().map(s -> (Double) s.get("p95_ms")).toList();
        List<Double> median = sampleStats.stream().map(s -> (Double) s.get("median_ms")).toList();
        result.put("median_ms_mean", mean(median));
        result.put("p95_ms_mean", mean(p95));
        result.put("p99_ms_mean", mean(sampleStats.stream().map(s -> (Double) s.get("p99_ms")).toList()));
        result.put("fps_mean", mean(sampleStats.stream().map(s -> (Double) s.get("fps")).toList()));
        result.put("framebuffer_width", mc.getWindow().getWidth());
        result.put("framebuffer_height", mc.getWindow().getHeight());
        boolean hit = mc.hitResult instanceof BlockHitResult block && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && c.expected() != null && block.getBlockPos().equals(c.expected());
        if (c.expected() != null) result.put("crosshair_on_expected", hit);
        result.putAll(subsystems(mc, c));
        cases.put(c.name(), result);
        AlexsButchery.LOGGER.info("PERFBENCH {} median {} p95 {} ms", c.name(), result.get("median_ms_mean"), result.get("p95_ms_mean"));
        if (c.survival()) mc.gameMode.stopDestroyBlock();
    }

    /** In-place costs on the render thread with the case's own camera, level and caches. */
    private static Map<String, Object> subsystems(Minecraft mc, Case c) {
        Map<String, Object> out = new LinkedHashMap<>();
        ClientLevel level = mc.level;
        Vec3 eye = mc.player.getEyePosition(1F);
        Vec3 end = eye.add(mc.player.getViewVector(1F).scale(mc.player.getBlockReach()));
        long[] alloc = new long[1];
        out.put("pick_us", time(400, () -> CarcassTargeting.pick(level, eye, end, mc.player), alloc));
        out.put("pick_alloc_bytes", alloc[0]);
        out.put("game_pick_us", time(200, () -> mc.gameRenderer.pick(1F), alloc));
        if (mc.hitResult instanceof BlockHitResult hit && level.getBlockState(hit.getBlockPos()).getBlock() instanceof AbstractCarcassBlock) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            CollisionContext ctx = CollisionContext.of(mc.player);
            out.put("shape_lookup_us", time(2000, () -> state.getShape(level, pos, ctx), alloc));
            VoxelShape shape = state.getShape(level, pos, ctx);
            int[] edges = {0};
            double[] sink = {0};
            shape.forAllEdges((a, b, d, e, f, g) -> edges[0]++);
            out.put("outline_edges", edges[0]);
            out.put("outline_us", time(200, () -> shape.forAllEdges((a, b, d, e, f, g) -> sink[0] += a + g), alloc));
            out.put("vanilla_clip_us", time(400, () -> shape.clip(eye, end, pos), alloc));
            out.put("selection_boxes", CarcassBounds.boxes(state, level, pos).size());
            var server = mc.getSingleplayerServer();
            var serverTime = new java.util.concurrent.CompletableFuture<Double>();
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                serverTime.complete(time(200, () -> CarcassTargeting.canReach(player, pos), new long[1]));
            });
            try { out.put("server_validation_us", serverTime.get(10, java.util.concurrent.TimeUnit.SECONDS)); }
            catch (Exception e) { out.put("server_validation_us", e.toString()); }
        }
        return out;
    }

    /** Mean microseconds per call after a short warmup; {@code alloc[0]} receives bytes allocated per call. */
    private static double time(int repeats, Runnable call, long[] alloc) {
        for (int i = 0; i < Math.min(50, repeats); i++) call.run();
        long thread = Thread.currentThread().getId();
        long bytes = THREADS.getThreadAllocatedBytes(thread);
        long start = System.nanoTime();
        for (int i = 0; i < repeats; i++) call.run();
        long duration = System.nanoTime() - start;
        alloc[0] = (THREADS.getThreadAllocatedBytes(thread) - bytes) / repeats;
        return duration / 1E3 / repeats;
    }

    private static void itemPile(ServerLevel level, BlockPos centre) {
        String[] ids = {"moose", "elephant", "catfish", "anaconda", "centipede", "farseer", "terrapin", "jerboa"};
        for (int i = 0; i < 32; i++) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("CatfishSize", i % 3);
            tag.putBoolean("Antlered", i % 2 == 0);
            var item = new net.minecraft.world.entity.item.ItemEntity(level, centre.getX() + (i % 8 - 4) * 2.0,
                    centre.getY() + .1, centre.getZ() + (i / 8 - 2) * 3.0, ItemPresentationChecks.stack(ids[i % ids.length], tag));
            item.setNoGravity(true);
            item.setDeltaMovement(Vec3.ZERO);
            item.setUnlimitedLifetime();
            item.setNeverPickUp();
            if (!level.addFreshEntity(item)) throw new IllegalStateException("Could not stage item entity " + i + " at " + centre);
            StageCapture capture = STAGING.get();
            if (capture != null) capture.itemEntities++;
        }
    }

    private static void presentationCosts(Minecraft mc) {
        Map<String, Object> costs = new LinkedHashMap<>();
        for (String id : new String[]{"moose", "catfish", "anaconda", "void_worm"}) {
            var stack = ItemPresentationChecks.stack(id, new CompoundTag());
            var visual = com.otectus.alexsbutchery.client.render.CarcassVisualState.from(stack);
            var buffers = mc.renderBuffers().bufferSource();
            long start = System.nanoTime();
            com.otectus.alexsbutchery.client.render.CarcassPresentation.metrics(visual, "ground", false);
            double cold = (System.nanoTime() - start) / 1E6;
            long[] allocated = {0};
            double warm = time(40, () -> {
                var pose = new com.mojang.blaze3d.vertex.PoseStack();
                mc.getItemRenderer().renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.GROUND, 15728880, 0,
                        pose, buffers, mc.level, 0);
                buffers.endBatch();
            }, allocated);
            long itemBytes = allocated[0];
            double carry = time(40, () -> {
                com.otectus.alexsbutchery.client.render.CarcassPresentation.renderCarry(visual, false,
                        new com.mojang.blaze3d.vertex.PoseStack(), buffers, 15728880, 0);
                buffers.endBatch();
            }, allocated);
            long carryBytes = allocated[0];
            double before = time(40, () -> {
                ItemReviewScreen.legacy(stack, net.minecraft.world.item.ItemDisplayContext.GROUND,
                        new com.mojang.blaze3d.vertex.PoseStack(), buffers);
                buffers.endBatch();
            }, allocated);
            costs.put(id, Map.of("cold_metrics_ms", cold, "item_us", warm, "item_bytes", itemBytes,
                    "carry_us", carry, "carry_bytes", carryBytes, "legacy_source_item_us", before, "legacy_source_item_bytes", allocated[0]));
        }
        results.put("presentation", costs);
        var carried = java.util.stream.Stream.of("moose", "catfish", "anaconda", "void_worm")
                .map(id -> com.otectus.alexsbutchery.client.render.CarcassVisualState.from(
                        ItemPresentationChecks.stack(id, new CompoundTag()))).toList();
        var buffers = mc.renderBuffers().bufferSource();
        long[] allocated = {0};
        double batch = time(40, () -> {
            for (int i = 0; i < carried.size(); i++) {
                var pose = new com.mojang.blaze3d.vertex.PoseStack();
                pose.translate((i - 1.5) * 4, 0, -8);
                com.otectus.alexsbutchery.client.render.CarcassPresentation.renderCarry(carried.get(i), false,
                        pose, buffers, 15728880, 0);
            }
            buffers.endBatch();
        }, allocated);
        results.put("carry_batch", Map.of("count", carried.size(), "render_us", batch, "bytes", allocated[0]));
    }

    /** First-use costs for never-seen states: shape construction and outline extraction, on the server thread. */
    private static void coldBuilds(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        BlockPos at = base.offset(0, 0, 40);
        awaitFuture(server.submit(() -> {
            ServerLevel level = server.overworld();
            Map<String, Object> cold = new LinkedHashMap<>();
            // Load the bounds file once, outside the timed builds.
            place(level, at, carcass("kangaroo", 0, Direction.NORTH), null);
            level.getBlockState(at).getShape(level, at);
            String[][] picks = {{"laviathan", "0"}, {"void_worm", "0"}, {"centipede", "0"}, {"farseer", "0"}, {"void_worm", "1"}};
            for (String[] p : picks) for (Direction facing : new Direction[]{Direction.EAST, Direction.SOUTH}) {
                place(level, at, carcass(p[0], Integer.parseInt(p[1]), facing), null);
                BlockState state = level.getBlockState(at);
                long start = System.nanoTime();
                VoxelShape shape = state.getShape(level, at);
                double build = (System.nanoTime() - start) / 1E6;
                start = System.nanoTime();
                int[] edges = {0};
                shape.forAllEdges((a, b, c, d, e, f) -> edges[0]++);
                double outline = (System.nanoTime() - start) / 1E6;
                start = System.nanoTime();
                shape.forAllEdges((a, b, c, d, e, f) -> edges[0]++);
                double second = (System.nanoTime() - start) / 1E6;
                cold.put(p[0] + "/" + p[1] + "/" + facing, Map.of("shape_build_ms", build, "first_outline_ms", outline,
                        "second_outline_ms", second, "edges", edges[0] / 2));
            }
            level.removeBlock(at, false);
            results.put("cold", cold);
        }), "cold shape builds", 60, () -> wait(2, () -> {}));
    }

    /** Resource reload and a world switch: costs afterwards and whether the old level can be collected. */
    private static void lifecycle(Minecraft mc) {
        long start = System.nanoTime();
        results.put("resource_reload_completed", false);
        awaitFuture(mc.reloadResourcePacks(), "resource-pack reload", 180, () -> {
            results.put("resource_reload_ms", elapsedMs(start));
            results.put("resource_reload_completed", true);
            WeakReference<ClientLevel> oldLevel = new WeakReference<>(mc.level);
            String world = mc.getSingleplayerServer().getWorldData().getLevelName();
            String folder = mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toAbsolutePath().normalize().getFileName().toString();
            mc.level.disconnect();
            mc.clearLevel(new net.minecraft.client.gui.screens.GenericDirtMessageScreen(net.minecraft.network.chat.Component.literal("perf")));
            results.put("world_name", world);
            mc.createWorldOpenFlows().loadLevel(new TitleScreen(), folder);
            worldTicks = 0;
            // Runs once the reopened world has ticked again; the plan simply continues after it.
            reopened = () -> {
                for (int i = 0; i < 6; i++) { System.gc(); try { Thread.sleep(50); } catch (InterruptedException ignored) {} }
                results.put("previous_client_level_collected", oldLevel.get() == null);
                base = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).blockPosition();
                plan.add(() -> arena(mc, 21,
                        (level, centre) -> place(level, centre, carcass("laviathan", 0, Direction.NORTH), null),
                        centre -> List.of(() -> hover("after_world_switch/laviathan_hover", centre))));
            };
        });
    }

    private static Runnable reopened;

    private static void wait(double seconds, Runnable then) {
        waitUntil = System.nanoTime() + (long) (seconds * 1E9);
        afterWait = then;
    }

    private static void awaitFuture(java.util.concurrent.CompletableFuture<?> future, String description,
                                    double timeoutSeconds, Runnable then) {
        if (pending != null) throw new IllegalStateException("Already waiting for " + pendingDescription);
        pending = future;
        pendingDescription = description;
        pendingDeadline = System.nanoTime() + (long) (timeoutSeconds * 1E9);
        afterPending = then;
    }

    private static void awaitCondition(String description, double timeoutSeconds, BooleanSupplier condition,
                                       Supplier<String> detail, Runnable then) {
        if (readiness != null) throw new IllegalStateException("Already waiting for " + readinessDescription);
        readiness = condition;
        readinessDetail = detail;
        readinessDescription = description;
        readinessDeadline = System.nanoTime() + (long) (timeoutSeconds * 1E9);
        afterReadiness = then;
    }

    private static double elapsedMs(long start) {
        return (System.nanoTime() - start) / 1E6;
    }

    private static Map<String, Object> stats(List<Long> nanos) {
        double[] ms = nanos.stream().mapToDouble(n -> n / 1E6).sorted().toArray();
        Map<String, Object> s = new LinkedHashMap<>();
        if (ms.length == 0) { s.put("frames", 0); s.put("median_ms", 0D); s.put("p95_ms", 0D); s.put("p99_ms", 0D); s.put("fps", 0D); return s; }
        s.put("frames", ms.length);
        s.put("median_ms", ms[ms.length / 2]);
        s.put("p95_ms", ms[(int) Math.min(ms.length - 1, Math.floor(ms.length * .95))]);
        s.put("p99_ms", ms[(int) Math.min(ms.length - 1, Math.floor(ms.length * .99))]);
        s.put("max_ms", ms[ms.length - 1]);
        s.put("fps", ms.length / (Arrays.stream(ms).sum() / 1000));
        return s;
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static void finish(Minecraft mc, Exception failure) {
        finished = true;
        results.put("pass", failure == null);
        if (failure != null) results.put("failure", failure.toString());
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT, "perf-" + LABEL + ".json"),
                    new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(results), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            AlexsButchery.LOGGER.error("PERFBENCH could not write results", e);
        }
        AlexsButchery.LOGGER.info("PERFBENCH finished ({} cases) in {} s", cases.size(), (Util.getMillis() / 1000));
        mc.stop();
    }

    private PerfBench() {}
}
