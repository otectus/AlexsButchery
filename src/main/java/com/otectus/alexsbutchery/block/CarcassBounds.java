package com.otectus.alexsbutchery.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-owned selection geometry exported from the posed meshes by the client smoke-test workflow. Each carcass
 * resolves to one immutable {@link Geometry}, shared by every carcass of the same form, stage, facing, size and
 * support: the anatomy boxes picking tests, their envelope for broad rejection, and the shape vanilla draws.
 */
public final class CarcassBounds {
    public static final int SEARCH_RADIUS = 32;
    /**
     * Bump whenever posing or stage geometry changes what the bounds export writes (CarcassScene, LyingPose,
     * HangingPose, StageGeometry, ModelParts), then re-export; the export records it with its other inputs.
     */
    public static final int GEOMETRY_REVISION = 2;
    /** Snapshot sizes are trusted up to this; Alex's Mobs' own scaled creatures stay far below it. */
    private static final double MAX_SCALE = 4;
    private static final CompoundTag NO_DATA = new CompoundTag();
    private static volatile JsonObject definitions;
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /** What the geometry depends on: block state (form, stage, facing), snapshot variant and size, and its anchor. */
    private record Key(BlockState state, String variant, double scale, double anchorX, double anchorY, double anchorZ) {}

    public record Geometry(List<AABB> boxes, AABB envelope, CarcassShape shape) {}

    // Built outside the lock: the result is deterministic, so a rare duplicate build from two threads is only wasted work.
    private static final Map<Key, Geometry> CACHE = new LinkedHashMap<>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Geometry> e) { return size() > 512; }
    };

    private static JsonObject definitions() {
        JsonObject loaded = definitions;
        if (loaded != null) return loaded;
        synchronized (CarcassBounds.class) {
            if (definitions == null) {
                var stream = CarcassBounds.class.getResourceAsStream("/data/alexsbutchery/carcass_bounds.json");
                if (stream == null) {
                    AlexsButchery.LOGGER.error("carcass_bounds.json is missing; every carcass selects as one block");
                    definitions = new JsonObject();
                } else try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonObject read = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonObject inputs = read.getAsJsonObject(INPUTS);
                    JsonObject current = currentInputs();
                    if (inputs == null || !inputs.equals(current)) AlexsButchery.LOGGER.warn(
                            "Carcass selection bounds were exported from {}, running {}; selection may not match the models exactly",
                            inputs, current);
                    definitions = read;
                } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read carcass bounds", e); }
            }
            return definitions;
        }
    }

    /** The key under which the export records what it was generated from. */
    public static final String INPUTS = "_inputs";

    /** What the bundled bounds were exported from, or null for an export that predates the record. */
    public static JsonObject exportInputs() {
        return definitions().getAsJsonObject(INPUTS);
    }

    /**
     * What an export made now would record: the geometry revision, the Alex's Mobs and Citadel versions whose models
     * it measures, and a hash of the bundled pose profiles (read from this mod's own resources on either side).
     */
    public static JsonObject currentInputs() {
        JsonObject inputs = new JsonObject();
        inputs.addProperty("revision", GEOMETRY_REVISION);
        for (String mod : new String[]{"alexsmobs", "citadel"}) inputs.addProperty(mod, net.minecraftforge.fml.ModList.get()
                .getModContainerById(mod).map(c -> c.getModInfo().getVersion().toString()).orElse("absent"));
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            for (var def : com.otectus.alexsbutchery.def.MobDefs.all()) {
                digest.update((def.id() + "\n").getBytes(StandardCharsets.UTF_8));
                try (var profile = CarcassBounds.class.getResourceAsStream("/assets/alexsbutchery/pose_profiles/" + def.id() + ".json")) {
                    if (profile != null) digest.update(profile.readAllBytes());
                }
            }
            inputs.addProperty("pose_profiles_sha256", java.util.HexFormat.of().formatHex(digest.digest()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
            inputs.addProperty("pose_profiles_sha256", "unreadable: " + e);
        }
        return inputs;
    }

    public static String variant(String mob, CompoundTag data) {
        return switch (mob) {
            case "catfish" -> "size" + Math.max(0, Math.min(2, data.getInt("CatfishSize")));
            case "elephant" -> data.getBoolean("Tusked") ? "tusked" : "default";
            case "blobfish" -> data.getBoolean("Depressurized") ? "depressurized" : "default";
            case "bison" -> data.getInt("Age") < 0 ? "baby" : "default";
            case "gorilla" -> data.getBoolean("Silverback") ? "silverback" : "default";
            case "gelada_monkey" -> data.getBoolean("Leader") ? "leader" : "default";
            case "leafcutter_ant" -> data.getBoolean("Queen") ? "queen" : "default";
            case "flutter" -> data.getBoolean("Potted") ? "potted" : "default";
            default -> "default";
        };
    }

    public static String scaleTag(String mob) {
        return switch (mob) {
            case "blobfish" -> "BlobfishScale";
            case "alligator_snapping_turtle" -> "TurtleScale";
            case "triops" -> "TriopsScale";
            case "devils_hole_pupfish" -> "PupfishScale";
            case "crimson_mosquito" -> "MosquitoScale";
            case "enderiophage" -> "PhageScale";
            default -> "";
        };
    }

    /** The snapshot's size factor, 1 when it has none; unusable or implausible values also mean 1. */
    public static double scale(String mob, CompoundTag data) {
        String tag = scaleTag(mob);
        if (tag.isEmpty() || !data.contains(tag)) return 1;
        double value = data.getFloat(tag);
        return Double.isFinite(value) && value > .05 && value <= MAX_SCALE ? value : 1;
    }

    public static String key(BlockState state, CompoundTag data) {
        var block = (AbstractCarcassBlock) state.getBlock();
        return ForgeRegistries.BLOCKS.getKey(block).getPath() + "/" + block.stage(state) + "/" + variant(block.def().id(), data);
    }

    /** Whether this form, stage and snapshot variant has anatomy of its own in the bundled export. */
    public static boolean exported(BlockState state, CompoundTag data) {
        return definitions().has(key(state, data));
    }

    public static List<AABB> boxes(BlockState state, BlockGetter level, BlockPos pos) {
        return geometry(state, level, pos).boxes();
    }

    public static Geometry geometry(BlockState state, BlockGetter level, BlockPos pos) {
        CompoundTag data = level.getBlockEntity(pos) instanceof CarcassBlockEntity be ? be.mobData() : NO_DATA;
        var block = (AbstractCarcassBlock) state.getBlock();
        Vec3 anchor = block.hanging(state) ? support(level.getBlockState(pos.above())) : new Vec3(.5, groundLevel(level, pos), .5);
        return geometry(state, data, anchor);
    }

    /** Detached geometry, on a full block or hanging from {@code support}. */
    public static List<AABB> boxes(BlockState state, CompoundTag data, BlockState support) {
        var block = (AbstractCarcassBlock) state.getBlock();
        return geometry(state, data, block.hanging(state) ? support(support) : new Vec3(.5, 0, .5)).boxes();
    }

    private static Geometry geometry(BlockState state, CompoundTag data, Vec3 anchor) {
        var block = (AbstractCarcassBlock) state.getBlock();
        String variant = variant(block.def().id(), data);
        double scale = scale(block.def().id(), data);
        Key key = new Key(state, variant, scale, anchor.x, anchor.y, anchor.z);
        Geometry cached;
        synchronized (CACHE) { cached = CACHE.get(key); }
        if (cached != null) return cached;
        Geometry built = build(state, variant, scale, anchor);
        synchronized (CACHE) {
            Geometry raced = CACHE.putIfAbsent(key, built);
            return raced != null ? raced : built;
        }
    }

    private static Geometry build(BlockState state, String variant, double scale, Vec3 anchor) {
        var block = (AbstractCarcassBlock) state.getBlock();
        String prefix = ForgeRegistries.BLOCKS.getKey(block).getPath() + "/" + block.stage(state) + "/";
        JsonArray json = definitions().getAsJsonArray(prefix + variant);
        if (json == null) {
            // A variant without an export of its own takes its default form; only a missing form falls back to one block.
            json = definitions().getAsJsonArray(prefix + "default");
            if (REPORTED.add(prefix + variant)) AlexsButchery.LOGGER.warn("No carcass bounds for {}; using {}", prefix + variant,
                    json != null ? prefix + "default" : "a single block");
        }
        List<AABB> boxes = new ArrayList<>();
        if (json == null) boxes.add(new AABB(0, 0, 0, 1, .625, 1));
        else {
            Direction facing = state.getValue(AbstractCarcassBlock.FACING);
            boolean hanging = block.hanging(state);
            for (var element : json) {
                var a = element.getAsJsonArray();
                AABB b = null;
                for (int i = 0; i < 8; i++) {
                    double x = (a.get((i & 1) == 0 ? 0 : 3).getAsDouble() - .5) * scale;
                    double y = (a.get((i & 2) == 0 ? 1 : 4).getAsDouble() - (hanging ? 1 : 0)) * scale;
                    double z = (a.get((i & 4) == 0 ? 2 : 5).getAsDouble() - .5) * scale;
                    // Exact cardinal rotations keep every facing's coordinates on the exported 1/32 grid.
                    Vec3 v = switch (facing) {
                        case SOUTH -> new Vec3(anchor.x - x, anchor.y + y, anchor.z - z);
                        case WEST -> new Vec3(anchor.x + z, anchor.y + y, anchor.z - x);
                        case EAST -> new Vec3(anchor.x - z, anchor.y + y, anchor.z + x);
                        default -> new Vec3(anchor.x + x, anchor.y + y, anchor.z + z);
                    };
                    AABB point = new AABB(v, v);
                    b = b == null ? point : b.minmax(point);
                }
                boxes.add(b);
            }
        }
        AABB envelope = boxes.get(0);
        for (int i = 1; i < boxes.size(); i++) envelope = envelope.minmax(boxes.get(i));
        List<AABB> immutable = List.copyOf(boxes);
        return new Geometry(immutable, envelope, new CarcassShape(immutable, envelope));
    }

    public static VoxelShape shape(BlockState state, BlockGetter level, BlockPos pos) {
        return geometry(state, level, pos).shape();
    }

    /**
     * How far a lying carcass sinks into its anchor block's floor: the top of a partial block it rests on (slab, path,
     * soul sand, carpet, deep snow), less one. Zero on a full block and wherever nothing solid is below; then it keeps
     * its block's height, as any block would. Shared by the renderer and selection.
     */
    public static double groundLevel(BlockGetter level, BlockPos pos) {
        BlockPos below = pos.below();
        VoxelShape support = level.getBlockState(below).getCollisionShape(level, below);
        if (support.isEmpty()) return 0;
        double top = support.max(Direction.Axis.Y);
        return top > 0 && top < 1 ? top - 1 : 0;
    }

    /** The hook bowl or rope knot, in the carcass anchor block's coordinates. Shared with the renderer. */
    public static Vec3 support(BlockState state) {
        int variant = 0;
        for (var property : state.getProperties()) if (property.getName().equals("blockstate"))
            variant = ((Number) state.getValue(property)).intValue();
        boolean rope = state.is(ButcheryHooks.rope());
        double y = rope ? (variant == 0 ? .21875 : 0) : variant == 0 ? .06224 : .08724;
        double z = rope ? 0 : variant == 0 ? .03079 : .04329;
        Direction facing = state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        return new Vec3(.5 - facing.getStepX() * z, 1 + y, .5 - facing.getStepZ() * z);
    }

    private CarcassBounds() {}
}
