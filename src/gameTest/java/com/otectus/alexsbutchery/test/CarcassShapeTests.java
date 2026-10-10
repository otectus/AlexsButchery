package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.CarcassShape;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The carcass shape against vanilla's own union of the same boxes: identical ray hits, an outline on its surface. */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CarcassShapeTests {
    // Overlapping anatomy, a disconnected segment, flush faces, and open space inside the enclosing bounds.
    private static final List<AABB> BOXES = List.of(new AABB(-3, -.25, -1, 2, .5, 1), new AABB(1, 0, -.5, 3, 2, .5),
            new AABB(-1, 1, 2, 1, 1.5, 3), new AABB(2, -.25, -1, 2.5, .5, 1));

    @GameTest(template = "empty")
    public static void rayHitsMatchVanillaUnion(GameTestHelper h) {
        VoxelShape vanilla = union(BOXES);
        var shape = shape(BOXES);
        h.assertTrue(vanilla.bounds().equals(shape.bounds()), "bounds are the union's bounds");
        h.assertTrue(shape.toAabbs().equals(BOXES), "the boxes are the anatomy itself");
        var offset = new BlockPos(37, -12, 51);
        var random = new Random(743021);
        for (int i = 0; i < 2000; i++) {
            Vec3 eye = new Vec3(random.nextDouble() * 10 - 5, random.nextDouble() * 6 - 2, random.nextDouble() * 8 - 3)
                    .add(Vec3.atLowerCornerOf(offset));
            Vec3 end = i % 7 == 0 ? eye.add(random.nextInt(3) - 1, 0, 0) // axis-parallel rays
                    : new Vec3(random.nextDouble() * 8 - 4, random.nextDouble() * 5 - 2, random.nextDouble() * 8 - 3).add(Vec3.atLowerCornerOf(offset));
            h.assertTrue(sameHit(vanilla.clip(eye, end, offset), shape.clip(eye, end, offset)), "ray parity " + i);
        }
        // Rays along faces and edges, from inside, and starting exactly on a surface.
        for (Vec3[] ray : new Vec3[][]{{new Vec3(-4, .5, 0), new Vec3(4, .5, 0)}, {new Vec3(0, -.25, -3), new Vec3(0, -.25, 3)},
                {new Vec3(2, 1, 0), new Vec3(2, 1, 4)}, {new Vec3(0, 0, 0), new Vec3(4, 0, 0)}, {new Vec3(2, .5, 1), new Vec3(-4, .5, 1)}}) {
            Vec3 eye = ray[0].add(Vec3.atLowerCornerOf(offset)), end = ray[1].add(Vec3.atLowerCornerOf(offset));
            h.assertTrue(sameHit(vanilla.clip(eye, end, offset), shape.clip(eye, end, offset)), "boundary ray " + ray[0] + " -> " + ray[1]);
        }
        Vec3 inside = new Vec3(0, 0, 0).add(Vec3.atLowerCornerOf(offset));
        h.assertTrue(shape.clip(inside, inside.add(4, 0, 0), offset).isInside(), "inside hit preserves vanilla convention");
        h.assertTrue(shape.clip(inside, inside, offset) == null, "zero-length rays miss");
        h.assertTrue(shape.clip(new Vec3(-4, .75, 2.5), new Vec3(4, .75, 2.5), BlockPos.ZERO) == null,
                "empty space in enclosing bounds remains untargetable");
        var moved = shape.move(1, 2, 3);
        h.assertTrue(moved.toAabbs().equals(BOXES.stream().map(b -> b.move(1, 2, 3)).toList()), "moving keeps the anatomy");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void outlineLiesOnTheUnionSurface(GameTestHelper h) {
        var shape = shape(BOXES);
        List<double[]> segments = new ArrayList<>();
        shape.forAllEdges((a, b, c, d, e, f) -> segments.add(new double[]{a, b, c, d, e, f}));
        List<double[]> again = new ArrayList<>();
        shape.forAllEdges((a, b, c, d, e, f) -> again.add(new double[]{a, b, c, d, e, f}));
        h.assertTrue(segments.size() == again.size() && segments.size() == shape.edgeCount(), "the cached outline is stable");
        h.assertTrue(segments.size() <= 12 * BOXES.size() * 2, "bounded by the box edges");
        for (double[] s : segments) {
            int axes = (s[0] != s[3] ? 1 : 0) + (s[1] != s[4] ? 1 : 0) + (s[2] != s[5] ? 1 : 0);
            h.assertTrue(axes == 1, "axis-aligned, non-degenerate segment");
            for (double t : new double[]{.25, .5, .75}) {
                Vec3 p = new Vec3(s[0] + (s[3] - s[0]) * t, s[1] + (s[4] - s[1]) * t, s[2] + (s[5] - s[2]) * t);
                h.assertTrue(BOXES.stream().anyMatch(b -> closedContains(b, p)), "outline point on the anatomy " + p);
                h.assertTrue(BOXES.stream().noneMatch(b -> openContains(b, p)), "outline point not buried inside " + p);
            }
        }
        // Every exposed corner of the anatomy is drawn.
        for (AABB b : BOXES) for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
            if (BOXES.stream().anyMatch(o -> openContains(o, corner))) continue;
            h.assertTrue(segments.stream().anyMatch(s -> onSegment(s, corner)), "exposed corner " + corner + " is outlined");
        }
        h.succeed();
    }

    private static CarcassShape shape(List<AABB> boxes) {
        AABB envelope = boxes.get(0);
        for (AABB b : boxes) envelope = envelope.minmax(b);
        return new CarcassShape(boxes, envelope);
    }

    private static VoxelShape union(List<AABB> boxes) {
        VoxelShape vanilla = Shapes.empty();
        for (var box : boxes) vanilla = Shapes.or(vanilla, Shapes.create(box));
        return vanilla;
    }

    private static boolean closedContains(AABB b, Vec3 p) {
        return p.x >= b.minX - 1E-9 && p.x <= b.maxX + 1E-9 && p.y >= b.minY - 1E-9 && p.y <= b.maxY + 1E-9
                && p.z >= b.minZ - 1E-9 && p.z <= b.maxZ + 1E-9;
    }

    private static boolean openContains(AABB b, Vec3 p) {
        return p.x > b.minX + 1E-9 && p.x < b.maxX - 1E-9 && p.y > b.minY + 1E-9 && p.y < b.maxY - 1E-9
                && p.z > b.minZ + 1E-9 && p.z < b.maxZ - 1E-9;
    }

    private static boolean onSegment(double[] s, Vec3 p) {
        return p.x >= Math.min(s[0], s[3]) - 1E-9 && p.x <= Math.max(s[0], s[3]) + 1E-9
                && p.y >= Math.min(s[1], s[4]) - 1E-9 && p.y <= Math.max(s[1], s[4]) + 1E-9
                && p.z >= Math.min(s[2], s[5]) - 1E-9 && p.z <= Math.max(s[2], s[5]) + 1E-9;
    }

    private static boolean sameHit(BlockHitResult a, BlockHitResult b) {
        return a == null ? b == null : b != null && a.getLocation().distanceToSqr(b.getLocation()) < 1E-12
                && a.getDirection() == b.getDirection() && a.isInside() == b.isInside() && a.getBlockPos().equals(b.getBlockPos());
    }
}
