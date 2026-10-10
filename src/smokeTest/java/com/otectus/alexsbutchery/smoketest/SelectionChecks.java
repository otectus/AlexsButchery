package com.otectus.alexsbutchery.smoketest;

import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.block.CarcassShape;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outline cost and correctness on the real exported anatomy, against vanilla's union of the same boxes (what 0.1.1
 * drew and re-traversed every frame), independently of GPU, vsync and frame caps. Every outline segment must lie on
 * the union's surface; the share of the union's crease length it draws is reported.
 */
final class SelectionChecks {
    private static volatile double consumed;

    static void run(Map<String, Object> checks) {
        Map<String, Object> timings = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        double legacyTotal = 0, cachedTotal = 0, worstCold = 0;
        String[][] cases = {{"laviathan", "carcass", "0"}, {"void_worm", "carcass", "0"}, {"centipede", "carcass", "0"},
                {"farseer", "carcass", "0"}, {"void_worm", "carcass", "1"}, {"anaconda", "skeleton", "1"},
                {"cachalot_whale", "skeleton", "1"}, {"laviathan", "skeleton", "1"}};
        for (String[] c : cases) {
            var entry = ModBlocks.of(MobDefs.byId(c[0]));
            var block = (AbstractCarcassBlock) (c[1].equals("skeleton") ? entry.skeleton().get() : entry.carcass().get());
            var state = block.defaultBlockState().setValue(block.stateProperty(), Integer.parseInt(c[2])).setValue(AbstractCarcassBlock.FACING, Direction.EAST);
            var boxes = CarcassBounds.boxes(state, new CompoundTag(), Blocks.AIR.defaultBlockState());
            AABB envelope = boxes.stream().reduce(AABB::minmax).orElseThrow();
            long start = System.nanoTime();
            var shape = new CarcassShape(boxes, envelope);
            int edges = shape.edgeCount();
            double coldMs = (System.nanoTime() - start) / 1E6;
            worstCold = Math.max(worstCold, coldMs);
            start = System.nanoTime();
            VoxelShape legacy = Shapes.empty();
            for (var box : boxes) legacy = Shapes.or(legacy, Shapes.create(box));
            double legacyBuildMs = (System.nanoTime() - start) / 1E6;
            List<double[]> ours = segments(shape), union = segments(legacy);
            for (double[] s : ours) {
                Vec3 mid = new Vec3((s[0] + s[3]) / 2, (s[1] + s[4]) / 2, (s[2] + s[5]) / 2);
                if (boxes.stream().noneMatch(b -> closed(b, mid)) || boxes.stream().anyMatch(b -> open(b, mid))) {
                    failures.add(String.join("/", c) + ":off-surface:" + mid);
                    break;
                }
            }
            double drawn = 0, total = 0;
            for (double[] s : union) {
                double length = Math.abs(s[3] - s[0]) + Math.abs(s[4] - s[1]) + Math.abs(s[5] - s[2]);
                total += length;
                Vec3 mid = new Vec3((s[0] + s[3]) / 2, (s[1] + s[4]) / 2, (s[2] + s[5]) / 2);
                if (ours.stream().anyMatch(o -> on(o, mid))) drawn += length;
            }
            double legacyFrame = measure(legacy, 4), cachedFrame = measure(shape, 2000);
            legacyTotal += legacyFrame;
            cachedTotal += cachedFrame;
            timings.put(String.join("/", c), Map.of("boxes", boxes.size(), "edges", edges, "union_edges", union.size(),
                    "union_crease_length_drawn", total == 0 ? 1 : drawn / total, "cold_build_and_outline_ms", coldMs,
                    "legacy_union_build_ms", legacyBuildMs, "legacy_outline_per_frame_ms", legacyFrame, "outline_per_frame_ms", cachedFrame));
        }
        checks.put("selection_outline_timings", timings);
        checks.put("selection_outline_failures", failures.toString());
        checks.put("selection_outline_on_anatomy_surface", failures.isEmpty());
        checks.put("selection_outline_worst_cold_ms", worstCold);
        // First use in a session includes JIT warm-up; it must still fit in one 60 FPS frame.
        checks.put("selection_outline_cold_under_one_frame", worstCold < 16.7);
        checks.put("selection_outline_at_least_20x_faster_per_frame", cachedTotal * 20 < legacyTotal);
    }

    private static List<double[]> segments(VoxelShape shape) {
        List<double[]> result = new ArrayList<>();
        shape.forAllEdges((a, b, c, d, e, f) -> result.add(new double[]{a, b, c, d, e, f}));
        return result;
    }

    private static boolean closed(AABB b, Vec3 p) {
        return p.x >= b.minX - 1E-9 && p.x <= b.maxX + 1E-9 && p.y >= b.minY - 1E-9 && p.y <= b.maxY + 1E-9 && p.z >= b.minZ - 1E-9 && p.z <= b.maxZ + 1E-9;
    }

    private static boolean open(AABB b, Vec3 p) {
        return p.x > b.minX + 1E-9 && p.x < b.maxX - 1E-9 && p.y > b.minY + 1E-9 && p.y < b.maxY - 1E-9 && p.z > b.minZ + 1E-9 && p.z < b.maxZ - 1E-9;
    }

    private static boolean on(double[] s, Vec3 p) {
        return p.x >= Math.min(s[0], s[3]) - 1E-9 && p.x <= Math.max(s[0], s[3]) + 1E-9 && p.y >= Math.min(s[1], s[4]) - 1E-9
                && p.y <= Math.max(s[1], s[4]) + 1E-9 && p.z >= Math.min(s[2], s[5]) - 1E-9 && p.z <= Math.max(s[2], s[5]) + 1E-9;
    }

    private static double measure(VoxelShape shape, int repeats) {
        double[] checksum = {0};
        // Read every coordinate so the JIT cannot replace the traversal with an edge-count increment.
        Shapes.DoubleLineConsumer consumer = (a, b, c, d, e, f) -> checksum[0] += a + b * .5 + c * .25 + d * .125 + e * .0625 + f * .03125;
        shape.forAllEdges(consumer);
        long start = System.nanoTime();
        for (int i = 0; i < repeats; i++) shape.forAllEdges(consumer);
        long duration = System.nanoTime() - start;
        consumed = checksum[0];
        return duration / 1E6 / repeats;
    }

    private SelectionChecks() {}
}
