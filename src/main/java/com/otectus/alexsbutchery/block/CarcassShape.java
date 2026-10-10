package com.otectus.alexsbutchery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The anatomy as vanilla sees it. Its voxel grid is only the enclosing box, built in constant time; ray clipping, the
 * box list and the outline come from the exported anatomy boxes themselves, so targeting, outline drawing and break
 * particles never traverse a voxel union (hundreds of thousands of cells for a coiled Void Worm).
 */
public final class CarcassShape extends ArrayVoxelShape {
    private static final Direction.Axis[] AXES = Direction.Axis.values();
    private final List<AABB> boxes;
    private volatile double[] edges;

    public CarcassShape(List<AABB> boxes, AABB envelope) {
        super(cell(), new double[]{envelope.minX, envelope.maxX}, new double[]{envelope.minY, envelope.maxY},
                new double[]{envelope.minZ, envelope.maxZ});
        if (boxes.isEmpty()) throw new IllegalArgumentException("Carcass anatomy must contain at least one box");
        this.boxes = List.copyOf(boxes);
    }

    private static BitSetDiscreteVoxelShape cell() {
        var cell = new BitSetDiscreteVoxelShape(1, 1, 1);
        cell.fill(0, 0, 0);
        return cell;
    }

    public List<AABB> boxes() {
        return boxes;
    }

    @Override
    public List<AABB> toAabbs() {
        return boxes;
    }

    @Override
    public void forAllBoxes(Shapes.DoubleLineConsumer consumer) {
        for (AABB b : boxes) consumer.consume(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    @Override
    public VoxelShape optimize() {
        return this;
    }

    @Override
    public VoxelShape move(double x, double y, double z) {
        List<AABB> moved = new ArrayList<>(boxes.size());
        for (AABB b : boxes) moved.add(b.move(x, y, z));
        return new CarcassShape(moved, bounds().move(x, y, z));
    }

    /** The outline, measured once per geometry: every box edge minus the stretches buried inside another box. */
    @Override
    public void forAllEdges(Shapes.DoubleLineConsumer consumer) {
        double[] lines = edges;
        if (lines == null) lines = cacheEdges();
        for (int i = 0; i < lines.length; i += 6)
            consumer.consume(lines[i], lines[i + 1], lines[i + 2], lines[i + 3], lines[i + 4], lines[i + 5]);
    }

    public int edgeCount() {
        double[] lines = edges;
        return (lines == null ? cacheEdges() : lines).length / 6;
    }

    private synchronized double[] cacheEdges() {
        if (edges == null) edges = outline(boxes);
        return edges;
    }

    /**
     * The visible edges of a union of boxes without building the union: each of a box's twelve edges, less the open
     * stretches that run through another box's interior. Edges on the surface stay, so the silhouette and every
     * convex crease are drawn; concave creases where two boxes cross are left out. Quadratic in the box count.
     */
    static double[] outline(List<AABB> boxes) {
        int n = boxes.size();
        // An edge splits into several pieces where boxes cross it part way, so the buffer grows as needed.
        double[][] out = {new double[n * 12 * 6]};
        int size = 0;
        double[] cuts = new double[2 * n];
        for (int i = 0; i < n; i++) {
            AABB b = boxes.get(i);
            for (Direction.Axis axis : AXES) {
                Direction.Axis a = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
                Direction.Axis c = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
                double from = b.min(axis), to = b.max(axis);
                for (int corner = 0; corner < 4; corner++) {
                    double u = (corner & 1) == 0 ? b.min(a) : b.max(a);
                    double v = (corner & 2) == 0 ? b.min(c) : b.max(c);
                    int cutCount = 0;
                    for (int j = 0; j < n; j++) {
                        if (j == i) continue;
                        AABB o = boxes.get(j);
                        if (!(o.min(a) < u && u < o.max(a) && o.min(c) < v && v < o.max(c))) continue;
                        double lo = Math.max(from, o.min(axis)), hi = Math.min(to, o.max(axis));
                        if (lo < hi) { cuts[cutCount++] = lo; cuts[cutCount++] = hi; }
                    }
                    double start = from;
                    if (cutCount > 0) {
                        sortIntervals(cuts, cutCount);
                        for (int k = 0; k < cutCount; k += 2) {
                            if (cuts[k] > start) size = emit(out, size, axis, a, c, start, cuts[k], u, v);
                            start = Math.max(start, cuts[k + 1]);
                        }
                    }
                    if (to > start) size = emit(out, size, axis, a, c, start, to, u, v);
                }
            }
        }
        return Arrays.copyOf(out[0], size);
    }

    private static int emit(double[][] buffer, int size, Direction.Axis axis, Direction.Axis a, Direction.Axis c,
                            double from, double to, double u, double v) {
        if (size + 6 > buffer[0].length) buffer[0] = Arrays.copyOf(buffer[0], buffer[0].length * 2);
        double[] out = buffer[0];
        for (int end = 0; end < 2; end++) {
            double along = end == 0 ? from : to;
            out[size + end * 3 + axis.ordinal()] = along;
            out[size + end * 3 + a.ordinal()] = u;
            out[size + end * 3 + c.ordinal()] = v;
        }
        return size + 6;
    }

    /** Sorts {@code count / 2} (lo, hi) pairs by lo; insertion sort, as an edge rarely passes through many boxes. */
    private static void sortIntervals(double[] cuts, int count) {
        for (int i = 2; i < count; i += 2) {
            double lo = cuts[i], hi = cuts[i + 1];
            int j = i - 2;
            while (j >= 0 && cuts[j] > lo) {
                cuts[j + 2] = cuts[j];
                cuts[j + 3] = cuts[j + 1];
                j -= 2;
            }
            cuts[j + 2] = lo;
            cuts[j + 3] = hi;
        }
    }

    @Nullable
    @Override
    public BlockHitResult clip(Vec3 eye, Vec3 end, BlockPos pos) {
        return clip(boxes, eye, end, pos);
    }

    /** The nearest hit on the boxes, with vanilla's inside-hit convention; only the winning hit is allocated. */
    @Nullable
    public static BlockHitResult clip(List<AABB> boxes, Vec3 eye, Vec3 end, BlockPos pos) {
        Hit hit = new Hit();
        if (!nearest(boxes, eye, end, pos, hit)) return null;
        return hit.result(eye, end, pos);
    }

    /** A ray's nearest entry into one carcass: parametric distance along eye→end, entry face, inside-start flag. */
    public static final class Hit {
        public double t;
        public Direction face;
        public boolean inside;

        public BlockHitResult result(Vec3 eye, Vec3 end, BlockPos pos) {
            Vec3 at = eye.add((end.x - eye.x) * t, (end.y - eye.y) * t, (end.z - eye.z) * t);
            return new BlockHitResult(at, face, pos, inside);
        }
    }

    /**
     * Vanilla's {@link VoxelShape#clip} semantics against loose boxes in the anchor's coordinates, without allocating:
     * a ray whose probe point (1/1000 along it) starts inside a box hits there, facing back along the ray; otherwise the
     * nearest box face the ray enters within its length. Returns false on a miss or a degenerate ray.
     */
    public static boolean nearest(List<AABB> boxes, Vec3 eye, Vec3 end, BlockPos pos, Hit out) {
        double dx = end.x - eye.x, dy = end.y - eye.y, dz = end.z - eye.z;
        if (dx * dx + dy * dy + dz * dz < 1.0E-7) return false;
        double ox = eye.x - pos.getX(), oy = eye.y - pos.getY(), oz = eye.z - pos.getZ();
        double px = ox + dx * .001, py = oy + dy * .001, pz = oz + dz * .001;
        for (AABB b : boxes) if (b.contains(px, py, pz)) {
            out.t = .001;
            out.face = Direction.getNearest(dx, dy, dz).getOpposite();
            out.inside = true;
            return true;
        }
        double best = 1;
        Direction face = null;
        for (AABB b : boxes) {
            if (dx > 1.0E-7) { double t = (b.minX - ox) / dx; if (t > 0 && t < best && inside(oy + t * dy, b.minY, b.maxY) && inside(oz + t * dz, b.minZ, b.maxZ)) { best = t; face = Direction.WEST; } }
            else if (dx < -1.0E-7) { double t = (b.maxX - ox) / dx; if (t > 0 && t < best && inside(oy + t * dy, b.minY, b.maxY) && inside(oz + t * dz, b.minZ, b.maxZ)) { best = t; face = Direction.EAST; } }
            if (dy > 1.0E-7) { double t = (b.minY - oy) / dy; if (t > 0 && t < best && inside(oz + t * dz, b.minZ, b.maxZ) && inside(ox + t * dx, b.minX, b.maxX)) { best = t; face = Direction.DOWN; } }
            else if (dy < -1.0E-7) { double t = (b.maxY - oy) / dy; if (t > 0 && t < best && inside(oz + t * dz, b.minZ, b.maxZ) && inside(ox + t * dx, b.minX, b.maxX)) { best = t; face = Direction.UP; } }
            if (dz > 1.0E-7) { double t = (b.minZ - oz) / dz; if (t > 0 && t < best && inside(ox + t * dx, b.minX, b.maxX) && inside(oy + t * dy, b.minY, b.maxY)) { best = t; face = Direction.NORTH; } }
            else if (dz < -1.0E-7) { double t = (b.maxZ - oz) / dz; if (t > 0 && t < best && inside(ox + t * dx, b.minX, b.maxX) && inside(oy + t * dy, b.minY, b.maxY)) { best = t; face = Direction.SOUTH; } }
        }
        if (face == null) return false;
        out.t = best;
        out.face = face;
        out.inside = false;
        return true;
    }

    private static boolean inside(double value, double min, double max) {
        return min - 1.0E-7 < value && value < max + 1.0E-7;
    }

    /**
     * Where a ray first meets {@code box} (anchor coordinates) as a fraction of its length, 0 if it starts inside, or
     * -1 if it misses: the broad phase that rejects a carcass before its anatomy is tested.
     */
    public static double entry(AABB box, Vec3 eye, Vec3 end, BlockPos pos) {
        // Padded past the fine test's 1E-7 tolerance, so the broad phase never rejects a ray the anatomy would accept.
        double pad = 1.0E-6, near = 0, far = 1;
        for (Direction.Axis axis : AXES) {
            double origin = eye.get(axis) - pos.get(axis), delta = end.get(axis) - eye.get(axis);
            double min = box.min(axis) - pad, max = box.max(axis) + pad;
            if (Math.abs(delta) < 1.0E-12) {
                if (origin < min || origin > max) return -1;
                continue;
            }
            double t0 = (min - origin) / delta, t1 = (max - origin) / delta;
            near = Math.max(near, Math.min(t0, t1));
            far = Math.min(far, Math.max(t0, t1));
            if (near > far) return -1;
        }
        return near;
    }
}
