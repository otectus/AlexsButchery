package com.otectus.alexsbutchery.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.alexsbutchery.AlexsButchery;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What Butchery's own carcasses and corpses need next, for the Jade lines of native blocks. Butchery decides everything
 * in its procedures (block state, and flags in the block entity's persistent data such as {@code isBleeding},
 * {@code isDrained}, {@code pluckedFeathers} or the warden's {@code butcherStage}); it exposes no state machine. The table
 * {@code data/alexsbutchery/native_butchery.json} records it per registered block id: for each reachable state and flag
 * combination, the tool and action that advance it, how many steps are done of how many, and whether the carcass must be
 * hung first. It was recorded by driving Butchery's real handlers ({@code NativeButcheryProbe}) and is checked against
 * them on every GameTest run, for the Butchery versions it names.
 *
 * <p>Nothing here links Butchery classes: blocks are resolved by id. Without a table entry, for an unverified Butchery
 * version, or for a state and flags the table does not know, there is no guidance rather than a guess. Butchery's own
 * persistent flags reach the client only through the server data built here ({@link #serverData}).
 */
public final class NativeButchery {
    /** Server data key for the native flags, namespaced so no other provider's field is mistaken for it. */
    public static final String DATA = AlexsButchery.MOD_ID + ":native";
    private static final String RESOURCE = "/data/alexsbutchery/native_butchery.json";

    /** One step: the tool that does it ({@code cleaver}, {@code skinning_knife}, {@code boss_tool}) and what it does. */
    public record Step(String tool, String action) {}

    /**
     * One reachable condition of a native block: its {@code blockstate} (-1 for blocks without one) and the values of the
     * block's watched persistent flags (a flag missing here is absent), and what it needs: a tool step (with the node it
     * leads to, for the checks, and the steps done of how many), to be hung, Butchery's metal tray, or nothing but time
     * while it bleeds.
     */
    public record Node(int state, Map<String, Double> flags, @Nullable Step next, @Nullable String to, int done, int total,
                       boolean hang, boolean tray, boolean bleeding) {}

    /** A native block's table entry: its family ({@code fresh}, {@code drained}, {@code direct}, {@code staged}), the
     * persistent flags its handlers read, and its reachable nodes. */
    public record Descriptor(ResourceLocation block, String kind, Set<String> watch, List<Node> nodes) {}

    private static volatile Table table;

    private record Table(Map<ResourceLocation, Descriptor> byId, Map<ResourceLocation, String> excluded, List<String> versions,
                         @Nullable String version, Map<Block, Descriptor> byBlock) {}

    private static Table table() {
        Table loaded = table;
        if (loaded != null) return loaded;
        synchronized (NativeButchery.class) {
            if (table == null) table = load(installedVersion());
            return table;
        }
    }

    /** The table read for {@code installed}; also used by the checks to read another version's section. */
    static Table load(String installed) {
        var stream = NativeButchery.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            AlexsButchery.LOGGER.error("{} is missing: Butchery's own carcasses get no Jade guidance", RESOURCE);
            return new Table(Map.of(), Map.of(), List.of(), null, Map.of());
        }
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<String> versions = new ArrayList<>();
            for (JsonElement v : root.getAsJsonArray("butchery_versions")) versions.add(v.getAsString());
            Map<ResourceLocation, String> excluded = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("excluded").entrySet())
                excluded.put(new ResourceLocation(e.getKey()), e.getValue().getAsString());
            // The section of the installed version: its own, or the one it is a patch of (5.3.1 reads 5.3).
            String version = null;
            for (String v : versions) if (installed.equals(v) || installed.startsWith(v + ".")) version = v;
            Map<ResourceLocation, Descriptor> byId = new LinkedHashMap<>();
            if (version != null) {
                Map<String, JsonElement> entries = new LinkedHashMap<>();
                root.getAsJsonObject("blocks").entrySet().forEach(e -> entries.put(e.getKey(), e.getValue()));
                JsonObject overrides = root.getAsJsonObject("versions").getAsJsonObject(version);
                if (overrides != null) overrides.entrySet().forEach(e -> entries.put(e.getKey(), e.getValue()));
                for (Map.Entry<String, JsonElement> e : entries.entrySet()) byId.put(new ResourceLocation(e.getKey()), descriptor(e.getKey(), e.getValue().getAsJsonObject()));
            } else {
                AlexsButchery.LOGGER.warn("Butchery {} is not one of the versions the native carcass guidance was verified with ({}); "
                        + "Jade shows no Alex's Butchery lines for Butchery's own carcasses", installed, versions);
            }
            Map<Block, Descriptor> byBlock = new IdentityHashMap<>();
            byId.forEach((id, d) -> {
                if (ForgeRegistries.BLOCKS.containsKey(id)) byBlock.put(ForgeRegistries.BLOCKS.getValue(id), d);
            });
            return new Table(Collections.unmodifiableMap(byId), Collections.unmodifiableMap(excluded), List.copyOf(versions), version,
                    Collections.unmodifiableMap(byBlock));
        } catch (Exception e) {
            AlexsButchery.LOGGER.error("Cannot read {}: Butchery's own carcasses get no Jade guidance", RESOURCE, e);
            return new Table(Map.of(), Map.of(), List.of(), null, Map.of());
        }
    }

    private static Descriptor descriptor(String id, JsonObject block) {
        Set<String> watch = new LinkedHashSet<>();
        if (block.has("watch")) for (JsonElement f : block.getAsJsonArray("watch")) watch.add(f.getAsString());
        List<Node> nodes = new ArrayList<>();
        for (JsonElement n : block.getAsJsonArray("nodes")) {
            JsonObject node = n.getAsJsonObject();
            Map<String, Double> flags = new LinkedHashMap<>();
            if (node.has("flags")) for (Map.Entry<String, JsonElement> f : node.getAsJsonObject("flags").entrySet()) flags.put(f.getKey(), f.getValue().getAsDouble());
            Step next = node.has("tool") ? new Step(node.get("tool").getAsString(), node.get("action").getAsString()) : null;
            nodes.add(new Node(node.get("state").getAsInt(), Collections.unmodifiableMap(flags), next, node.has("to") ? node.get("to").getAsString() : null,
                    node.has("done") ? node.get("done").getAsInt() : 0, node.has("total") ? node.get("total").getAsInt() : 0,
                    bool(node, "hang"), bool(node, "tray"), bool(node, "bleeding")));
        }
        return new Descriptor(new ResourceLocation(id), block.get("kind").getAsString(), Collections.unmodifiableSet(watch), List.copyOf(nodes));
    }

    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && o.get(key).getAsBoolean();
    }

    private static String installedVersion() {
        return ModList.get().getModContainerById("butchery").map(c -> c.getModInfo().getVersion().toString()).orElse("absent");
    }

    /** The table entry of a native block, or null (not Butchery's, not a carcass, excluded, or an unverified version). */
    @Nullable
    public static Descriptor of(Block block) {
        return table().byBlock().get(block);
    }

    /** Every block id the table describes for the installed Butchery, for coverage checks. */
    public static Map<ResourceLocation, Descriptor> descriptors() {
        return table().byId();
    }

    /** Block ids deliberately left without guidance, with the reason (Butchery's own player-corpse line). */
    public static Map<ResourceLocation, String> excluded() {
        return table().excluded();
    }

    public static List<String> verifiedVersions() {
        return table().versions();
    }

    /** The table section in use, or null when the installed Butchery is not a verified version (no native guidance). */
    @Nullable
    public static String version() {
        return table().version();
    }

    /** The {@code blockstate} value of a native block, -1 for blocks without one. */
    public static int state(BlockState state) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty("blockstate");
        return property instanceof IntegerProperty ip ? state.getValue(ip) : -1;
    }

    /**
     * What the server sends for a native block entity the player looks at: its watched persistent flags, read here because
     * the client is not sent Butchery's persistent data. Null for anything without a table entry, checked before any data
     * is touched.
     */
    @Nullable
    public static CompoundTag serverData(@Nullable BlockEntity be) {
        if (be == null) return null;
        Descriptor d = of(be.getBlockState().getBlock());
        if (d == null) return null;
        return flags(d, be.getPersistentData());
    }

    /** The watched flags of a block entity's persistent data, numbers only (Butchery stores booleans and doubles). */
    public static CompoundTag flags(Descriptor d, CompoundTag persistent) {
        CompoundTag out = new CompoundTag();
        for (String flag : d.watch()) {
            if (persistent.get(flag) instanceof NumericTag n) out.putDouble(flag, n.getAsDouble());
        }
        return out;
    }

    /**
     * The node for this state and these flags: same state and exactly the same watched flags (a flag Butchery has not set
     * counts as absent; {@code false} written as 0 counts as written). Null for a condition the table does not know.
     */
    @Nullable
    public static Node node(Descriptor d, int state, CompoundTag flags) {
        for (Node node : d.nodes()) {
            if (node.state() != state) continue;
            boolean same = true;
            for (String flag : d.watch()) {
                Double want = node.flags().get(flag);
                boolean has = flags.contains(flag, Tag.TAG_ANY_NUMERIC);
                if (want == null ? has : !has || flags.getDouble(flag) != want) { same = false; break; }
            }
            if (same) return node;
        }
        return null;
    }

    /**
     * The lines Jade shows for a native block, from its state and the flags the server sent: "Bleeding..." where Butchery
     * keeps only a flag (no invented percentage), "Hang it on a hook", "Put it on a metal tray", or the next tool and action
     * with the steps done. Empty without flags, a table entry or a matching node.
     */
    public static List<Component> lines(BlockState state, @Nullable CompoundTag flags) {
        Descriptor d = of(state.getBlock());
        if (d == null || flags == null) return List.of();
        Node node = node(d, state(state), flags);
        if (node == null) return List.of();
        List<Component> out = new ArrayList<>();
        if (node.bleeding()) out.add(line("bleeding_wait").withStyle(ChatFormatting.DARK_RED));
        else if (node.hang()) out.add(line("hang").withStyle(ChatFormatting.GRAY));
        else if (node.tray()) out.add(line("tray").withStyle(ChatFormatting.GRAY));
        else if (node.next() != null) {
            out.add(line("next", line("tool." + node.next().tool()).withStyle(ChatFormatting.GOLD), line("action." + node.next().action())));
            if (node.total() > 0) out.add(line("progress", node.done(), node.total()).withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    // ---- Butchery's skin rack -------------------------------------------------------------------------------------

    /** Server data key for the native rack's curing data. */
    public static final String RACK_DATA = AlexsButchery.MOD_ID + ":native_rack";
    private static final ResourceLocation SKIN_RACK = new ResourceLocation("butchery", "skin_rack");

    /**
     * What Butchery's rack states mean in the installed version ({@code skin_rack} in the table): the raw skin each state
     * holds (an empty hand gives that skin back), the salted, wet and cured states, and the persistent counter Butchery 5.3
     * cures with (5.2 cures on queued work it does not save, so it has none).
     */
    public record Rack(Map<Integer, ResourceLocation> skins, int salted, int wet, int cured, @Nullable String timer) {}

    @Nullable
    public static Rack rack() {
        return RackTable.RACK;
    }

    private static final class RackTable {
        @Nullable static final Rack RACK = load(version());

        @Nullable
        private static Rack load(@Nullable String version) {
            if (version == null) return null;
            var stream = NativeButchery.class.getResourceAsStream(RESOURCE);
            if (stream == null) return null;
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject racks = root.getAsJsonObject("skin_rack");
                JsonObject rack = racks == null ? null : racks.getAsJsonObject(version);
                if (rack == null) return null;
                Map<Integer, ResourceLocation> skins = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : rack.getAsJsonObject("skins").entrySet())
                    skins.put(Integer.parseInt(e.getKey()), new ResourceLocation(e.getValue().getAsString()));
                return new Rack(Collections.unmodifiableMap(skins), rack.get("salted").getAsInt(), rack.get("wet").getAsInt(),
                        rack.get("cured").getAsInt(), rack.has("timer") ? rack.get("timer").getAsString() : null);
            } catch (Exception e) {
                AlexsButchery.LOGGER.error("Cannot read the skin rack section of {}", RESOURCE, e);
                return null;
            }
        }
    }

    /** Whether this is Butchery's own skin rack, with a known meaning for its states. */
    public static boolean isRack(BlockState state) {
        if (rack() == null) return false;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return SKIN_RACK.equals(id);
    }

    /** The rack's curing counter and its target, when Butchery keeps them (5.3); null for anything but the rack. */
    @Nullable
    public static CompoundTag rackServerData(@Nullable BlockEntity be) {
        if (be == null || !isRack(be.getBlockState())) return null;
        Rack rack = rack();
        CompoundTag out = new CompoundTag();
        if (rack.timer() != null && be.getPersistentData().get(rack.timer()) instanceof NumericTag n) {
            double target = ButcheryHooks.skinDryingTime();
            out.putDouble("Timer", n.getAsDouble());
            if (target > 0) out.putDouble("Target", target);
        }
        return out;
    }

    /**
     * The rack's lines: the skin on it and the next step (salt, then a wet sponge or rag), "Curing" with a percentage only
     * where Butchery counts the time (5.3), and the cured leather. Nothing for the empty rack or a state the table does
     * not name; the numeric state is never shown as progress.
     */
    public static List<Component> rackLines(BlockState state, @Nullable CompoundTag data) {
        Rack rack = rack();
        if (rack == null || !isRack(state)) return List.of();
        int value = state(state);
        List<Component> out = new ArrayList<>();
        ResourceLocation skin = rack.skins().get(value);
        if (skin != null) {
            var item = ForgeRegistries.ITEMS.getValue(skin);
            if (item != null) out.add(line("rack.skin", item.getDescription()).withStyle(ChatFormatting.GOLD));
            out.add(line("rack.salt").withStyle(ChatFormatting.GRAY));
        } else if (value == rack.salted()) {
            out.add(line("rack.wet").withStyle(ChatFormatting.AQUA));
        } else if (value == rack.wet()) {
            if (data != null && data.contains("Timer", Tag.TAG_ANY_NUMERIC) && data.contains("Target", Tag.TAG_ANY_NUMERIC) && data.getDouble("Target") > 0)
                out.add(line("rack.curing", Math.min(100, (int) Math.round(data.getDouble("Timer") * 100 / data.getDouble("Target")))).withStyle(ChatFormatting.AQUA));
            else out.add(line("rack.curing_wait").withStyle(ChatFormatting.AQUA));
        } else if (value == rack.cured()) {
            out.add(line("rack.done").withStyle(ChatFormatting.GREEN));
        }
        return out;
    }

    private static MutableComponent line(String key, Object... args) {
        return Component.translatable("jade.alexsbutchery." + key, args);
    }

    private NativeButchery() {}
}
