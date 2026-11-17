package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.NativeButchery;
import net.mcreator.butchery.configuration.ButcheryconfigConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Jade guidance for Butchery's own carcasses (review finding AB-04), checked against the installed Butchery: every
 * native carcass or corpse the registry holds is described or deliberately left out, every step the table recommends does
 * what the table says when Butchery's real handlers run it, and bleeding shows as bleeding until Butchery's timer drains it.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeButcheryTests {

    @GameTest(template = "empty")
    public static void everyNativeCarcassIsDescribedOrExcluded(GameTestHelper h) {
        h.assertTrue(NativeButchery.version() != null, "the installed Butchery is one the table was verified with: " + NativeButchery.verifiedVersions());
        Set<ResourceLocation> natives = new TreeSet<>();
        for (Block block : NativeButcheryProbe.candidates()) natives.add(ForgeRegistries.BLOCKS.getKey(block));
        // Every block a recorded step leads to is described too (a drained villager is neither named nor tagged a carcass).
        for (NativeButchery.Descriptor d : NativeButchery.descriptors().values()) for (NativeButchery.Node n : d.nodes()) {
            if (n.to() != null && !n.to().equals("removed")) natives.add(new ResourceLocation(n.to().substring(0, n.to().indexOf('@'))));
        }
        List<String> missing = new ArrayList<>();
        for (ResourceLocation id : natives) {
            if (!NativeButchery.descriptors().containsKey(id) && !NativeButchery.excluded().containsKey(id)) missing.add(id.toString());
        }
        h.assertTrue(missing.isEmpty(), missing.size() + " native carcasses have no guidance and no reason: " + missing);
        List<String> stale = NativeButchery.descriptors().keySet().stream().filter(id -> !ForgeRegistries.BLOCKS.containsKey(id)).map(String::valueOf).toList();
        h.assertTrue(stale.isEmpty(), "the table names blocks this Butchery does not register: " + stale);
        h.assertTrue(natives.size() > 150, "the native set was found (" + natives.size() + ")");
        // Player corpses stay with Butchery 5.3's own Jade line.
        h.assertTrue(NativeButchery.excluded().containsKey(new ResourceLocation("butchery:playercorpse")), "player corpses are left to Butchery");
        h.assertTrue(NativeButchery.of(TestSupport.butcheryBlock("playercorpse")) == null, "and get no lines from this mod");
        h.succeed();
    }

    /** Every tool and action the table names has an English line. */
    @GameTest(template = "empty")
    public static void everyTableLineIsTranslated(GameTestHelper h) {
        Set<String> keys = new TreeSet<>();
        for (NativeButchery.Descriptor d : NativeButchery.descriptors().values()) for (NativeButchery.Node n : d.nodes()) {
            if (n.next() != null) {
                keys.add("jade.alexsbutchery.tool." + n.next().tool());
                keys.add("jade.alexsbutchery.action." + n.next().action());
            }
        }
        keys.addAll(List.of("jade.alexsbutchery.next", "jade.alexsbutchery.progress", "jade.alexsbutchery.hang", "jade.alexsbutchery.tray",
                "jade.alexsbutchery.bleeding_wait", "jade.alexsbutchery.rack.skin", "jade.alexsbutchery.rack.salt", "jade.alexsbutchery.rack.wet",
                "jade.alexsbutchery.rack.curing", "jade.alexsbutchery.rack.curing_wait", "jade.alexsbutchery.rack.done",
                "config.jade.plugin_alexsbutchery.butchery_carcass", "config.jade.plugin_alexsbutchery.butchery_skin_rack"));
        Map<String, String> lang = english();
        List<String> missing = keys.stream().filter(k -> !lang.containsKey(k)).toList();
        h.assertTrue(missing.isEmpty(), "untranslated native guidance: " + missing);
        h.succeed();
    }

    // ---- replaying every recorded step ---------------------------------------------------------------------------

    private record Job(String block, NativeButchery.Node node) {}

    private static final int LANES = 7, SPACING = 9, WAIT = 3, COOLDOWN = 40;

    /**
     * Every node with a tool step, set up as recorded (state, persistent flags, Butchery's hook above), clicked with that
     * tool through the server's use path with Instant Bleeding on, and compared with the node the table says it leads to.
     * Lanes rest between steps so Butchery's queued work (a cat's skinning, the wither crumbling) never reaches another.
     */
    // Its own batch: Instant Bleeding stays on for the whole replay, which no other test may run beside.
    @GameTest(template = "empty", timeoutTicks = 6000, batch = "native_replay")
    public static void everyRecommendedStepDoesWhatTheTableSays(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = TestSupport.advancementPlayer(h);
        Deque<Job> queue = new ArrayDeque<>();
        NativeButchery.descriptors().forEach((id, d) -> {
            for (NativeButchery.Node n : d.nodes()) if (n.next() != null && n.to() != null) queue.add(new Job(id.toString(), n));
        });
        int total = queue.size();
        h.assertTrue(total > 700, "the table's steps were found (" + total + ")");
        BlockPos base = h.absolutePos(BlockPos.ZERO).offset(-24, 48, -24);
        record Lane(BlockPos pos, Job[] job, int[] delay) {}
        List<Lane> lanes = new ArrayList<>();
        for (int x = 0; x < LANES; x++) for (int z = 0; z < LANES; z++)
            lanes.add(new Lane(base.offset(x * SPACING, 0, z * SPACING), new Job[1], new int[]{0}));
        Map<String, String> wrong = new TreeMap<>();
        int[] checked = {0};
        boolean instant = ButcheryconfigConfiguration.INSTANT_BLEED.get();
        ButcheryconfigConfiguration.INSTANT_BLEED.set(true);
        h.onEachTick(() -> {
            boolean busy = false;
            for (Lane lane : lanes) {
                if (lane.delay()[0] > 0) { lane.delay()[0]--; busy = true; continue; }
                Job job = lane.job()[0];
                if (job != null) {
                    String got = observe(level, lane.pos());
                    if (!got.equals(job.node().to())) wrong.put(job.block() + "@" + job.node().state() + job.node().flags() + " " + job.node().next().tool(),
                            "table " + job.node().to() + ", Butchery " + got);
                    checked[0]++;
                    reset(level, lane.pos());
                    lane.job()[0] = null;
                    lane.delay()[0] = COOLDOWN;
                    busy = true;
                    continue;
                }
                Job next = queue.poll();
                if (next == null) continue;
                busy = true;
                lane.job()[0] = next;
                reset(level, lane.pos());
                Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(next.block()));
                IntegerProperty property = NativeButcheryProbe.stateProperty(block);
                BlockState state = property == null ? block.defaultBlockState() : block.defaultBlockState().setValue(property, next.node().state());
                level.setBlock(lane.pos(), state, 3);
                BlockEntity be = level.getBlockEntity(lane.pos());
                if (be != null) {
                    CompoundTag persistent = be.getPersistentData();
                    for (String flag : NativeButchery.descriptors().get(new ResourceLocation(next.block())).watch()) persistent.remove(flag);
                    next.node().flags().forEach(persistent::putDouble);
                }
                ItemStack tool = switch (next.node().next().tool()) {
                    case "skinning_knife" -> TestSupport.butcheryItem("iron_skinning_knife");
                    case "boss_tool" -> TestSupport.butcheryItem("netherite_cleaver");
                    default -> TestSupport.butcheryItem("iron_cleaver");
                };
                player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                player.gameMode.useItemOn(player, level, tool, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(lane.pos()).relative(Direction.UP, .5), Direction.UP, lane.pos(), false));
                lane.delay()[0] = WAIT;
            }
            if (!busy && queue.isEmpty()) {
                ButcheryconfigConfiguration.INSTANT_BLEED.set(instant);
                if (!wrong.isEmpty()) {
                    StringBuilder text = new StringBuilder(wrong.size() + " of " + checked[0] + " steps differ from the table: ");
                    wrong.entrySet().stream().limit(12).forEach(e -> text.append(e.getKey()).append(" -> ").append(e.getValue()).append("; "));
                    h.fail(text.toString());
                    return;
                }
                h.assertTrue(checked[0] == total, "every step was checked, " + checked[0] + " of " + total);
                h.succeed();
            }
        });
    }

    /** The node a lane holds now, in the table's terms: block, state and the block's watched flags, or "removed". */
    static String observe(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (state.isAir() || new ResourceLocation("butchery:metal_tray").equals(id)) return "removed";
        int value = NativeButchery.state(state);
        StringBuilder key = new StringBuilder(id + "@" + (value < 0 ? "-" : String.valueOf(value)) + "{");
        NativeButchery.Descriptor d = NativeButchery.descriptors().get(id);
        BlockEntity be = level.getBlockEntity(pos);
        if (d != null && be != null) {
            CompoundTag flags = NativeButchery.flags(d, be.getPersistentData());
            List<String> parts = new ArrayList<>();
            for (String flag : new TreeSet<>(flags.getAllKeys())) parts.add(flag + "=" + flags.getDouble(flag));
            key.append(String.join(", ", parts));
        }
        return key.append("}").toString();
    }

    private static void reset(ServerLevel level, BlockPos at) {
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(2, 2, 2))) {
            if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3 | 16);
        }
        level.setBlock(at.above(), TestSupport.butcheryBlock("hook").defaultBlockState(), 3);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4)).forEach(ItemEntity::discard);
    }

    // ---- bleeding on Butchery's own timer -------------------------------------------------------------------------

    /**
     * A hung chicken bled with Instant Bleeding off: Butchery only sets {@code isBleeding} and drains it on its own
     * timer (queued work in 5.2, a persistent {@code Timer} its tick counts in 5.3, whose length differs per procedure), so
     * the guidance says "Bleeding..." (no percentage) until the drained chicken asks for its head.
     */
    // Butchery 5.2 drains a chicken after 225 ticks, 5.3 counts a Timer to 900.
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void nativeBleedingIsShownUntilButcheryDrainsIt(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 2, 2);
        h.setBlock(at.above(), TestSupport.butcheryBlock("hook").defaultBlockState());
        Block chicken = TestSupport.butcheryBlock("chicken_carcass");
        h.setBlock(at, chicken.defaultBlockState().setValue(NativeButcheryProbe.stateProperty(chicken), 1));
        ServerPlayer player = TestSupport.advancementPlayer(h);
        boolean instant = ButcheryconfigConfiguration.INSTANT_BLEED.get();
        ButcheryconfigConfiguration.INSTANT_BLEED.set(false);
        try {
            BlockState before = h.getBlockState(at);
            h.assertTrue(keys(NativeButchery.lines(before, NativeButchery.serverData(h.getBlockEntity(at)))).equals(List.of("jade.alexsbutchery.next")),
                    "a hung chicken asks for the cleaver's bleeding cut");
            ItemStack cleaver = TestSupport.butcheryItem("iron_cleaver");
            player.setItemInHand(InteractionHand.MAIN_HAND, cleaver);
            BlockPos abs = h.absolutePos(at);
            player.gameMode.useItemOn(player, h.getLevel(), cleaver, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.SOUTH, .5), Direction.SOUTH, abs, false));
        } finally {
            ButcheryconfigConfiguration.INSTANT_BLEED.set(instant);
        }
        h.assertTrue(h.getBlockEntity(at).getPersistentData().getBoolean("isBleeding"), "Butchery started bleeding it");
        List<Component> bleeding = NativeButchery.lines(h.getBlockState(at), NativeButchery.serverData(h.getBlockEntity(at)));
        h.assertTrue(keys(bleeding).equals(List.of("jade.alexsbutchery.bleeding_wait")), "while it bleeds: only 'Bleeding...', got " + keys(bleeding));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(h.getBlockState(at).is(TestSupport.butcheryBlock("drained_chicken_carcass")), "Butchery drained it"))
                .thenExecute(() -> {
                    List<Component> drained = NativeButchery.lines(h.getBlockState(at), NativeButchery.serverData(h.getBlockEntity(at)));
                    h.assertTrue(keys(drained).equals(List.of("jade.alexsbutchery.next", "jade.alexsbutchery.progress")),
                            "drained, it asks for the next cut with progress: " + keys(drained));
                    h.assertTrue(args(drained.get(0)).contains("jade.alexsbutchery.action.head"), "and that cut takes the head: " + args(drained.get(0)));
                })
                .thenSucceed();
    }

    /** This mod's English lines, as shipped. */
    static Map<String, String> english() {
        try (var in = NativeButcheryTests.class.getResourceAsStream("/assets/alexsbutchery/lang/en_us.json")) {
            if (in == null) throw new IllegalStateException("no en_us.json on the classpath");
            Map<String, String> out = new TreeMap<>();
            com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject()
                    .entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
            return out;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<String> keys(List<Component> lines) {
        return lines.stream().map(c -> c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString()).toList();
    }

    /** The translation keys of a line's arguments. */
    static List<String> args(Component line) {
        List<String> out = new ArrayList<>();
        if (line.getContents() instanceof TranslatableContents t) {
            for (Object arg : t.getArgs()) out.add(arg instanceof Component c && c.getContents() instanceof TranslatableContents a ? a.getKey() : String.valueOf(arg));
        }
        return out;
    }

    private NativeButcheryTests() {}
}
