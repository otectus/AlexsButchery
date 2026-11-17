package com.otectus.alexsbutchery.test;

import com.google.gson.GsonBuilder;
import com.otectus.alexsbutchery.AlexsButchery;
import net.mcreator.butchery.configuration.ButcheryconfigConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A development probe, not a regression test: registered only with {@code -PnativeProbe}. It drives every native
 * Butchery carcass and corpse block, in every {@code blockstate}, with each butchering tool through the real server use
 * path ({@code ServerPlayerGameMode#useItemOn}: Forge's right-click event with Butchery's own handlers, the block, the
 * item) and records what Butchery did: the block and state left behind, persistent flags, drops, tool wear. A second pass
 * repeats the hanging cleaver click with Instant Bleeding on, to see what bleeding turns each carcass into, and blocks
 * without a {@code blockstate} are walked step by step. The output, {@code run-gametest/native-butchery-probe-<version>.json},
 * is the evidence behind {@code data/alexsbutchery/native_butchery.json}; {@code NativeButcheryTests} then checks that
 * table against the same handlers on every run.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
public final class NativeButcheryProbe {
    static final String PROPERTY = "alexsbutchery.gametest.nativeProbe";
    private static final String[] TOOLS = {"iron_cleaver", "iron_skinning_knife", "netherite_cleaver", ""};

    @GameTestGenerator
    public static Collection<TestFunction> probe() {
        if (!Boolean.getBoolean(PROPERTY)) return List.of();
        return List.of(new TestFunction("nativeprobe", "alexsbutchery.nativebutcheryprobe", AlexsButchery.MOD_ID + ":empty", 400, 0L, true,
                        NativeButcheryProbe::run),
                new TestFunction("nativeprobe", "alexsbutchery.nativebutcherywalk", AlexsButchery.MOD_ID + ":empty", 48000, 0L, true,
                        NativeButcheryProbe::walk),
                new TestFunction("nativeprobe", "alexsbutchery.nativeskinrack", AlexsButchery.MOD_ID + ":empty", 400, 0L, true,
                        NativeButcheryProbe::rack),
                new TestFunction("nativeprobe", "alexsbutchery.nativetaxidermyhopper", AlexsButchery.MOD_ID + ":empty", 200, 0L, true,
                        NativeButcheryProbe::taxidermyHopper));
    }

    /**
     * An observation of Butchery's own station, not a check of this mod: a hopper under a taxidermy table holding one
     * wheat, a cow head and an empty mount. Records how many mounts the hopper pulled and what input is left, in
     * {@code native-taxidermy-hopper-<version>.json}.
     */
    private static void taxidermyHopper(GameTestHelper h) {
        BlockPos table = new BlockPos(2, 2, 2);
        h.setBlock(table.below(), Blocks.HOPPER.defaultBlockState());
        h.setBlock(table, TestSupport.butcheryBlock("taxidermy_table").defaultBlockState());
        var handler = (net.minecraftforge.items.IItemHandlerModifiable) h.getBlockEntity(table)
                .getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        handler.setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.WHEAT));
        handler.setStackInSlot(1, TestSupport.butcheryItem("cow_head"));
        handler.setStackInSlot(2, TestSupport.butcheryItem("empty_head_mount"));
        h.runAfterDelay(60, () -> {
            var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) h.getBlockEntity(table.below());
            Map<String, Integer> pulled = new TreeMap<>();
            for (int i = 0; i < hopper.getContainerSize(); i++) {
                ItemStack s = hopper.getItem(i);
                if (!s.isEmpty()) pulled.merge(String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem())), s.getCount(), Integer::sum);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("butchery", ModList.get().getModContainerById("butchery").map(c -> c.getModInfo().getVersion().toString()).orElse("?"));
            out.put("hopper", pulled);
            List<String> left = new ArrayList<>();
            for (int i = 0; i < 4; i++) left.add(String.valueOf(handler.getStackInSlot(i)));
            out.put("table_slots", left);
            try {
                Files.writeString(Path.of("native-taxidermy-hopper-" + out.get("butchery") + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(out),
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            AlexsButchery.LOGGER.info("Native taxidermy table under a hopper: pulled {}, table {}", pulled, left);
            h.succeed();
        });
    }

    // ---- Butchery's skin rack: which item hangs as which state, and what salt, a wet sponge or rag and a hand do --------

    /**
     * Every Butchery item (and vanilla leather and rabbit hide) clicked onto an empty rack, and salt, a wet sponge, a wet
     * rag and an empty hand clicked on every rack state, each at a fresh position so no queued curing reaches another
     * click. Writes {@code native-skin-rack-<version>.json}.
     */
    private static void rack(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = TestSupport.advancementPlayer(h);
        Block rack = TestSupport.butcheryBlock("skin_rack");
        IntegerProperty property = stateProperty(rack);
        BlockPos base = h.absolutePos(BlockPos.ZERO).offset(-40, 60, -40);
        int[] slot = {0};
        java.util.function.Supplier<BlockPos> next = () -> {
            int i = slot[0]++;
            return base.offset((i % 40) * 3, 0, (i / 40) * 3);
        };
        Map<String, Object> hung = new TreeMap<>();
        List<ItemStack> candidates = new ArrayList<>();
        for (var e : ForgeRegistries.ITEMS.getEntries()) if (e.getKey().location().getNamespace().equals("butchery")) candidates.add(new ItemStack(e.getValue()));
        candidates.add(new ItemStack(net.minecraft.world.item.Items.LEATHER));
        candidates.add(new ItemStack(net.minecraft.world.item.Items.RABBIT_HIDE));
        for (ItemStack item : candidates) {
            BlockPos at = next.get();
            level.setBlock(at, rack.defaultBlockState(), 3);
            String id = String.valueOf(ForgeRegistries.ITEMS.getKey(item.getItem()));
            Map<String, Object> result = rackClick(level, player, at, item, false);
            if (!Integer.valueOf(0).equals(result.get("state"))) hung.put(id, result);
        }
        Map<String, Object> actions = new TreeMap<>();
        for (int state : property.getPossibleValues()) {
            Map<String, Object> byAction = new LinkedHashMap<>();
            for (String action : new String[]{"salt", "sponge", "rag", "hand", "shift_hand"}) {
                BlockPos at = next.get();
                level.setBlock(at, rack.defaultBlockState().setValue(property, state), 3);
                ItemStack held = switch (action) {
                    case "salt" -> TestSupport.butcheryItem("salt");
                    case "sponge" -> { ItemStack s = TestSupport.butcheryItem("sponge"); s.getOrCreateTag().putDouble("spongeWetness", 2); yield s; }
                    case "rag" -> { ItemStack s = TestSupport.butcheryItem("rag"); s.getOrCreateTag().putDouble("ragWetness", 2); yield s; }
                    default -> ItemStack.EMPTY;
                };
                byAction.put(action, rackClick(level, player, at, held, action.equals("shift_hand")));
            }
            actions.put(String.valueOf(state), byAction);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        String version = ModList.get().getModContainerById("butchery").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        out.put("butchery", version);
        out.put("hung", hung);
        out.put("actions", actions);
        try {
            Files.writeString(Path.of("native-skin-rack-" + version + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(out), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        for (int i = 0; i < slot[0]; i++) {
            BlockPos at = base.offset((i % 40) * 3, 0, (i / 40) * 3);
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 3 | 16);
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(base).expandTowards(130, 3, 130).inflate(3)).forEach(ItemEntity::discard);
        AlexsButchery.LOGGER.info("Native Butchery skin rack probe: {} hanging items for Butchery {}", hung.size(), version);
        h.succeed();
    }

    private static Map<String, Object> rackClick(ServerLevel level, ServerPlayer player, BlockPos at, ItemStack held, boolean shift) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.setShiftKeyDown(shift);
        int count = held.getCount();
        clearItems(level, at);
        InteractionResult result = player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, .5), Direction.UP, at, false));
        player.setShiftKeyDown(false);
        Map<String, Object> out = new LinkedHashMap<>();
        BlockState after = level.getBlockState(at);
        out.put("result", result.name());
        out.put("block", after.isAir() ? "removed" : key(after.getBlock()));
        IntegerProperty property = stateProperty(after.getBlock());
        if (property != null) out.put("state", after.getValue(property));
        out.put("flags", flags(level, at));
        Map<String, Integer> drops = new TreeMap<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(1))) {
            drops.merge(String.valueOf(ForgeRegistries.ITEMS.getKey(item.getItem().getItem())), item.getItem().getCount(), Integer::sum);
        }
        out.put("drops", drops);
        out.put("used", count > 0 && held.getCount() != count);
        if (held.hasTag()) out.put("held_tag", held.getTag().toString());
        clearItems(level, at);
        return out;
    }

    // ---- the walk: every reachable (block, state, persistent flags), with ticks between clicks ---------------------

    /**
     * Butchery queues some of its work for later ({@code ButcheryMod.queueServerWork}: a cat is swapped for the skinned
     * cat one tick after skinning, the wither crumbles 20 ticks after its cut), so a lane rests {@value #COOLDOWN} ticks
     * between jobs, and a job that leaves something other than a Butchery block (or nothing) is run once more.
     */
    private static final int LANES = 7, SPACING = 9, WAIT = 3, MAX_DEPTH = 9, COOLDOWN = 40;

    private record Job(Block block, int start, Map<String, Double> placed, List<String> path, boolean retry) {
        Job(Block block, int start, Map<String, Double> placed, List<String> path) { this(block, start, placed, path, false); }
        String key() { return NativeButcheryProbe.key(block) + "@" + start + (placed.isEmpty() ? "" : new TreeMap<>(placed).toString()) + path; }
        Job then(List<String> next) { return new Job(block, start, placed, next); }
    }

    /**
     * Flags Butchery's own placement writes that placing the block does not: a sniffer item used on a hook puts the
     * same block under it at butcherStage 1 (fresh, SniffercarcassplacedProcedure) or 5 (drained,
     * DrainedsniffercarcassplacedProcedure), and only that hung fresh sniffer bleeds. The walk starts there as well.
     */
    static final Map<String, List<Map<String, Double>>> HOOK_PLACED = Map.of(
            "butchery:sniffer_carcass_block", List.of(Map.of("butcherStage", 1.0)),
            "butchery:drained_sniffer_carcass_block", List.of(Map.of("butcherStage", 5.0)));

    private static final class Lane {
        BlockPos pos;
        Job job;
        int step;
        int wait;
        int cooldown;
        final List<Map<String, Object>> clicks = new ArrayList<>();
    }

    /**
     * Breadth-first from every state of every native carcass, as placed and as Butchery hangs it ({@link #HOOK_PLACED}):
     * each job places the start state with those flags, replays a tool sequence with {@value #WAIT} ticks after each
     * click (Butchery converts cats one tick later), and records the (block, state, flags) it ends in. Each new node is
     * expanded with every tool. Instant Bleeding is on, so bleeding converts on the click and no timed work outlives its
     * job. Lanes sit high above the test structures, each under a hook.
     */
    private static void walk(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = TestSupport.advancementPlayer(h);
        boolean instant = ButcheryconfigConfiguration.INSTANT_BLEED.get();
        ButcheryconfigConfiguration.INSTANT_BLEED.set(true);
        BlockPos base = h.absolutePos(BlockPos.ZERO).offset(-24, 48, -24);
        List<Lane> lanes = new ArrayList<>();
        for (int x = 0; x < LANES; x++) for (int z = 0; z < LANES; z++) {
            Lane lane = new Lane();
            lane.pos = base.offset(x * SPACING, 0, z * SPACING);
            lanes.add(lane);
        }
        java.util.ArrayDeque<Job> queue = new java.util.ArrayDeque<>();
        for (Block block : candidates()) {
            String id = key(block);
            if (id.contains("playercorpse")) continue;
            IntegerProperty property = stateProperty(block);
            List<Map<String, Double>> starts = new ArrayList<>();
            starts.add(Map.of());
            starts.addAll(HOOK_PLACED.getOrDefault(id, List.of()));
            for (Map<String, Double> placed : starts) {
                if (property == null) queue.add(new Job(block, -1, placed, List.of()));
                else for (int value : property.getPossibleValues()) queue.add(new Job(block, value, placed, List.of()));
            }
        }
        Map<String, Object> nodes = new TreeMap<>();
        Map<String, Map<String, Object>> edges = new TreeMap<>();
        Map<String, String> nodeOfPath = new java.util.HashMap<>();
        java.util.Set<String> expanded = new java.util.HashSet<>();
        int[] done = {0};
        int[] retried = {0};
        h.onEachTick(() -> {
            boolean busy = false;
            for (Lane lane : lanes) {
                if (lane.cooldown > 0) { lane.cooldown--; busy = true; continue; }
                if (lane.job == null) {
                    Job next = queue.poll();
                    if (next == null) continue;
                    lane.job = next;
                    lane.step = -1;
                    lane.wait = 0;
                    lane.clicks.clear();
                }
                busy = true;
                if (lane.wait > 0) { lane.wait--; continue; }
                Job job = lane.job;
                if (lane.step < 0) {
                    reset(level, lane.pos);
                    BlockState state = job.start() < 0 ? job.block().defaultBlockState()
                            : job.block().defaultBlockState().setValue(stateProperty(job.block()), job.start());
                    level.setBlock(lane.pos, state, 3);
                    BlockEntity placedEntity = level.getBlockEntity(lane.pos);
                    if (placedEntity != null) job.placed().forEach(placedEntity.getPersistentData()::putDouble);
                    lane.step = 0;
                    lane.wait = WAIT;
                    continue;
                }
                if (lane.step < job.path().size()) {
                    if (!level.getBlockState(lane.pos).isAir()) {
                        Map<String, Object> click = click(level, player, lane.pos, toolId(job.path().get(lane.step)), null);
                        lane.clicks.add(click);
                    }
                    lane.step++;
                    lane.wait = WAIT;
                    continue;
                }
                // Observe.
                BlockState now = level.getBlockState(lane.pos);
                String node;
                ResourceLocation nowId = ForgeRegistries.BLOCKS.getKey(now.getBlock());
                if (!now.isAir() && (nowId == null || !nowId.getNamespace().equals("butchery")) && !job.retry()) {
                    // Not something Butchery's handler leaves: run the job again later instead of recording it.
                    queue.add(new Job(job.block(), job.start(), job.placed(), job.path(), true));
                    reset(level, lane.pos);
                    lane.job = null;
                    lane.cooldown = COOLDOWN;
                    retried[0]++;
                    continue;
                }
                if (now.isAir()) node = "removed";
                else {
                    IntegerProperty property = stateProperty(now.getBlock());
                    node = key(now.getBlock()) + "@" + (property == null ? "-" : now.getValue(property)) + flags(level, lane.pos);
                }
                nodeOfPath.put(job.key(), node);
                nodes.putIfAbsent(node, Map.of("first_path", job.key()));
                if (!job.path().isEmpty()) {
                    String from = nodeOfPath.get(job.then(job.path().subList(0, job.path().size() - 1)).key());
                    Map<String, Object> last = lane.clicks.isEmpty() ? Map.of() : lane.clicks.get(lane.clicks.size() - 1);
                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("to", node);
                    edge.put("drops", last.getOrDefault("drops", Map.of()));
                    edge.put("tool_worn", last.getOrDefault("tool_worn", false));
                    edges.computeIfAbsent(String.valueOf(from), k -> new TreeMap<>()).putIfAbsent(job.path().get(job.path().size() - 1), edge);
                }
                if (!node.equals("removed") && job.path().size() < MAX_DEPTH && followed(now.getBlock()) && expanded.add(node)) {
                    for (String tool : new String[]{"cleaver", "knife", "boss", "hand"}) {
                        List<String> path = new ArrayList<>(job.path());
                        path.add(tool);
                        queue.add(job.then(List.copyOf(path)));
                    }
                }
                reset(level, lane.pos);
                lane.job = null;
                lane.cooldown = COOLDOWN;
                done[0]++;
            }
            if (!busy && queue.isEmpty()) {
                ButcheryconfigConfiguration.INSTANT_BLEED.set(instant);
                Map<String, Object> out = new LinkedHashMap<>();
                String version = ModList.get().getModContainerById("butchery").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
                out.put("butchery", version);
                out.put("jobs", done[0]);
                out.put("retried", retried[0]);
                out.put("nodes", nodes);
                out.put("edges", edges);
                try {
                    Files.writeString(Path.of("native-butchery-walk-" + version + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(out),
                            StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                AlexsButchery.LOGGER.info("Native Butchery walk: {} jobs, {} nodes for Butchery {}", done[0], nodes.size(), version);
                h.succeed();
            }
        });
    }

    private static String toolId(String tool) {
        return switch (tool) {
            case "cleaver" -> "iron_cleaver";
            case "knife" -> "iron_skinning_knife";
            case "boss" -> "netherite_cleaver";
            default -> "";
        };
    }

    /** Butchery's carcass tags: whatever their ids say, their members are native carcasses. */
    static final List<String> CARCASS_TAGS = List.of("carcass", "small_carcass", "drained_carcass", "carcass_no_states", "cat_carcass",
            "drained_cat_carcass", "corpses");

    /**
     * Native blocks a player butchers: carcasses and corpses by id or by Butchery's carcass tags (the drained villager is
     * neither by name), not the skeletons acid leaves or heads.
     */
    static List<Block> candidates() {
        java.util.Set<Block> out = new java.util.LinkedHashSet<>();
        for (var e : ForgeRegistries.BLOCKS.getEntries()) {
            ResourceLocation id = e.getKey().location();
            if (!id.getNamespace().equals("butchery")) continue;
            String path = id.getPath();
            if ((path.contains("carcass") || path.contains("corpse")) && !path.endsWith("_skeleton")) out.add(e.getValue());
        }
        for (String tag : CARCASS_TAGS) {
            var key = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, new ResourceLocation("butchery", tag));
            for (var holder : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTagOrEmpty(key)) {
                if (holder.unwrapKey().orElseThrow().location().getNamespace().equals("butchery")) out.add(holder.value());
            }
        }
        List<Block> sorted = new ArrayList<>(out);
        sorted.sort((a, b) -> key(a).compareTo(key(b)));
        return sorted;
    }

    /** A block the walk follows into: any of Butchery's blocks but the metal tray a frog leaves. */
    static boolean followed(Block block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        return id != null && id.getNamespace().equals("butchery") && !id.getPath().equals("metal_tray");
    }

    static String key(Block block) {
        return String.valueOf(ForgeRegistries.BLOCKS.getKey(block));
    }

    private static void run(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = TestSupport.advancementPlayer(h);
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("butchery", ModList.get().getModContainerById("butchery").map(c -> c.getModInfo().getVersion().toString()).orElse("?"));
        Map<String, Object> blocks = new LinkedHashMap<>();
        boolean instant = ButcheryconfigConfiguration.INSTANT_BLEED.get();
        try {
            ButcheryconfigConfiguration.INSTANT_BLEED.set(false);
            for (Block block : candidates()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                IntegerProperty property = stateProperty(block);
                entry.put("class", block.getClass().getSimpleName());
                entry.put("block_entity", block instanceof EntityBlock);
                entry.put("states", property == null ? List.of() : List.copyOf(property.getPossibleValues()));
                List<String> tags = new ArrayList<>();
                block.defaultBlockState().getTags().forEach(t -> tags.add(t.location().toString()));
                tags.sort(null);
                entry.put("tags", tags);
                Map<String, Object> placed = new LinkedHashMap<>();
                Map<String, Object> clicks = new LinkedHashMap<>();
                for (BlockState state : states(block, property)) {
                    String s = property == null ? "-" : String.valueOf(state.getValue(property));
                    reset(level, at);
                    level.setBlock(at, state, 3);
                    placed.put(s, flags(level, at));
                    Map<String, Object> byTool = new LinkedHashMap<>();
                    for (String tool : TOOLS) {
                        reset(level, at);
                        level.setBlock(at, state, 3);
                        byTool.put(tool.isEmpty() ? "hand" : tool, click(level, player, at, tool, property));
                    }
                    clicks.put(s, byTool);
                }
                entry.put("placed_flags", placed);
                entry.put("clicks", clicks);
                if (property == null) entry.put("walks", walks(level, player, at, block));
                blocks.put(key(block), entry);
            }
            // Instant Bleeding converts on the click itself: what each bleeding carcass becomes.
            ButcheryconfigConfiguration.INSTANT_BLEED.set(true);
            Map<String, Object> bled = new LinkedHashMap<>();
            for (Block block : candidates()) {
                IntegerProperty property = stateProperty(block);
                for (BlockState state : states(block, property)) {
                    reset(level, at);
                    level.setBlock(at, state, 3);
                    Map<String, Object> result = click(level, player, at, "iron_cleaver", property);
                    if (!key(block).equals(result.get("block")) || !String.valueOf(property == null ? "-" : state.getValue(property)).equals(String.valueOf(result.get("state"))))
                        bled.put(key(block) + "@" + (property == null ? "-" : state.getValue(property)), result);
                }
            }
            out.put("instant_bleed_changes", bled);
        } finally {
            ButcheryconfigConfiguration.INSTANT_BLEED.set(instant);
            reset(level, at);
        }
        out.put("blocks", blocks);
        String version = String.valueOf(out.get("butchery"));
        try {
            Files.writeString(Path.of("native-butchery-probe-" + version + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(out),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        AlexsButchery.LOGGER.info("Native Butchery probe: {} blocks recorded for Butchery {}", blocks.size(), version);
        h.succeed();
    }

    /** One right-click through the server's use path, and what it changed. */
    static Map<String, Object> click(ServerLevel level, ServerPlayer player, BlockPos at, String tool, IntegerProperty property) {
        ItemStack held = tool.isEmpty() ? ItemStack.EMPTY : TestSupport.butcheryItem(tool);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        int damage = held.getDamageValue();
        clearItems(level, at);
        InteractionResult result = player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, .5), Direction.UP, at, false));
        Map<String, Object> out = new LinkedHashMap<>();
        BlockState after = level.getBlockState(at);
        out.put("result", result.name());
        out.put("block", after.isAir() ? "removed" : key(after.getBlock()));
        IntegerProperty afterProperty = stateProperty(after.getBlock());
        if (afterProperty != null) out.put("state", after.getValue(afterProperty));
        out.put("flags", flags(level, at));
        Map<String, Integer> drops = new TreeMap<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3))) {
            drops.merge(String.valueOf(ForgeRegistries.ITEMS.getKey(item.getItem().getItem())), item.getItem().getCount(), Integer::sum);
        }
        out.put("drops", drops);
        out.put("tool_worn", !held.isEmpty() && (held.getDamageValue() != damage || held.isEmpty()));
        clearItems(level, at);
        return out;
    }

    /** A block without a blockstate is clicked again and again with each tool, recording each step. */
    private static Map<String, Object> walks(ServerLevel level, ServerPlayer player, BlockPos at, Block block) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String tool : TOOLS) {
            reset(level, at);
            level.setBlock(at, block.defaultBlockState(), 3);
            List<Object> steps = new ArrayList<>();
            for (int i = 0; i < 8 && level.getBlockState(at).is(block); i++) {
                Map<String, Object> step = click(level, player, at, tool, null);
                steps.add(step);
                if (!level.getBlockState(at).is(block)) break;
            }
            out.put(tool.isEmpty() ? "hand" : tool, steps);
        }
        return out;
    }

    static IntegerProperty stateProperty(Block block) {
        Property<?> property = block.getStateDefinition().getProperty("blockstate");
        return property instanceof IntegerProperty ip ? ip : null;
    }

    static List<BlockState> states(Block block, IntegerProperty property) {
        if (property == null) return List.of(block.defaultBlockState());
        List<BlockState> out = new ArrayList<>();
        for (int value : property.getPossibleValues()) out.add(block.defaultBlockState().setValue(property, value));
        return out;
    }

    /** The block entity's persistent data, values only for numbers and flags. */
    static Map<String, Object> flags(ServerLevel level, BlockPos at) {
        Map<String, Object> out = new TreeMap<>();
        BlockEntity be = level.getBlockEntity(at);
        if (be == null) return out;
        CompoundTag data = be.getPersistentData();
        for (String key : data.getAllKeys()) {
            Tag tag = data.get(key);
            out.put(key, tag instanceof NumericTag n ? (Object) n.getAsDouble() : String.valueOf(tag));
        }
        return out;
    }

    private static void reset(ServerLevel level, BlockPos at) {
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(2, 2, 2))) {
            if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3 | 16);
        }
        level.setBlock(at.above(), TestSupport.butcheryBlock("hook").defaultBlockState(), 3);
        clearItems(level, at);
    }

    private static void clearItems(ServerLevel level, BlockPos at) {
        level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4)).forEach(ItemEntity::discard);
    }

    private NativeButcheryProbe() {}
}
