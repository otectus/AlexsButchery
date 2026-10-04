package com.otectus.alexsbutchery.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-owned selection geometry exported from the posed meshes by the client smoke-test workflow. */
public final class CarcassBounds {
    public static final int SEARCH_RADIUS = 32;
    private static JsonObject definitions;
    private record Key(BlockState state, String variant, BlockState support, double scale) {}
    private static final Map<Key, List<AABB>> BOXES = new LinkedHashMap<>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, List<AABB>> e) { return size() > 512; }
    };
    private static final Map<List<AABB>, VoxelShape> SHAPES = new LinkedHashMap<>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<List<AABB>, VoxelShape> e) { return size() > 64; }
    };

    private static synchronized JsonObject definitions() {
        if (definitions == null) {
            var stream = CarcassBounds.class.getResourceAsStream("/data/alexsbutchery/carcass_bounds.json");
            if (stream == null) return new JsonObject(); // First export in a development checkout.
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                definitions = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read carcass bounds", e); }
        }
        return definitions;
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

    public static String key(BlockState state, CompoundTag data) {
        var block = (AbstractCarcassBlock) state.getBlock();
        return ForgeRegistries.BLOCKS.getKey(block).getPath() + "/" + block.stage(state) + "/" + variant(block.def().id(), data);
    }

    public static List<AABB> boxes(BlockState state, BlockGetter level, BlockPos pos) {
        CompoundTag data = level.getBlockEntity(pos) instanceof CarcassBlockEntity be ? be.mobData() : new CompoundTag();
        return boxes(state, data, level.getBlockState(pos.above()));
    }

    public static synchronized List<AABB> boxes(BlockState state, CompoundTag data, BlockState support) {
        var block = (AbstractCarcassBlock) state.getBlock();
        String scaleTag = scaleTag(block.def().id());
        double value = scaleTag.isEmpty() || !data.contains(scaleTag) ? 1 : data.getFloat(scaleTag);
        double scale = Double.isFinite(value) && value > .05 ? value : 1;
        Key cacheKey = new Key(state, variant(block.def().id(), data), support, scale);
        return BOXES.computeIfAbsent(cacheKey, ignored -> {
            var json = definitions().getAsJsonArray(key(state, data));
            if (json == null) return List.of(new AABB(0, 0, 0, 1, .625, 1));
            List<AABB> boxes = new ArrayList<>();
            Direction facing = state.getValue(AbstractCarcassBlock.FACING);
            Vec3 anchor = block.hanging(state) ? support(support) : new Vec3(.5, 0, .5);
            double angle = Math.toRadians(180 - facing.toYRot()), sin = Math.sin(angle), cos = Math.cos(angle);
            for (var element : json) {
                var a = element.getAsJsonArray();
                AABB b = null;
                for (int i = 0; i < 8; i++) {
                    double x = (a.get((i & 1) == 0 ? 0 : 3).getAsDouble() - .5) * scale;
                    double y = (a.get((i & 2) == 0 ? 1 : 4).getAsDouble() - (block.hanging(state) ? 1 : 0)) * scale;
                    double z = (a.get((i & 4) == 0 ? 2 : 5).getAsDouble() - .5) * scale;
                    Vec3 v = new Vec3(anchor.x + x * cos + z * sin, anchor.y + y, anchor.z + z * cos - x * sin);
                    AABB point = new AABB(v, v);
                    b = b == null ? point : b.minmax(point);
                }
                boxes.add(b);
            }
            return List.copyOf(boxes);
        });
    }

    public static synchronized VoxelShape shape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPES.computeIfAbsent(boxes(state, level, pos), boxes -> {
            VoxelShape result = Shapes.empty();
            for (AABB box : boxes) result = Shapes.or(result, Shapes.create(box));
            return result;
        });
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
