package com.otectus.alexsbutchery.smoketest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.block.HeadBlock;
import com.otectus.alexsbutchery.block.HeadMountBlock;
import com.otectus.alexsbutchery.client.pose.PoseProfiles;
import com.otectus.alexsbutchery.client.render.CarcassModels;
import com.otectus.alexsbutchery.client.render.CarcassScene;
import com.otectus.alexsbutchery.def.MobDefs;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Independent final-vertex checks for heads, their solid contact anatomy, and rendered trophies. */
final class HeadContactChecks {
    private static final double EPSILON = .001;

    static void run(Map<String, Object> checks) {
        List<String> mountFailures = new ArrayList<>(), floorFailures = new ArrayList<>();
        int mounts = 0, ground = 0;
        for (var def : MobDefs.all()) for (HeadBlock block : BoundsExport.heads(def)) for (CompoundTag data : variants(def))
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockState state = block.defaultBlockState().setValue(HeadBlock.FACING, facing);
                String label = CarcassBounds.key(state, data) + "/" + facing + "/" + data;
                Cubes head = renderHead(state, data, facing);
                if (block instanceof HeadMountBlock) { mounts++; checkMount(def, state, data, facing, label, head, mountFailures); }
                else {
                    ground++;
                    AABB solid = head.solidBounds();
                    if (solid == null) floorFailures.add(label + ":no-solid-head");
                    else if (Math.abs(solid.minY) > EPSILON) floorFailures.add(label + ":solid-floor:" + solid.minY);
                }
            }
        checks.put("head_contact_mount_cases", mounts);
        checks.put("head_contact_mount_failures", mountFailures.toString());
        checks.put("head_mounts_seated_on_actual_wood", mountFailures.isEmpty());
        checks.put("head_contact_ground_cases", ground);
        checks.put("head_contact_ground_failures", floorFailures.toString());
        checks.put("ground_heads_rest_on_solid_anatomy", floorFailures.isEmpty());
    }

    private static List<CompoundTag> variants(com.otectus.alexsbutchery.def.MobDef def) {
        List<CompoundTag> result = new ArrayList<>(BoundsExport.variants(def));
        String sizeTag = CarcassBounds.scaleTag(def.id());
        if (!sizeTag.isEmpty()) for (CompoundTag data : BoundsExport.variants(def)) for (float size : new float[]{.7F, 1.35F}) {
            CompoundTag scaled = data.copy(); scaled.putFloat(sizeTag, size); result.add(scaled);
        }
        return result;
    }

    private static void checkMount(com.otectus.alexsbutchery.def.MobDef def, BlockState state, CompoundTag data, Direction facing,
                                   String label, Cubes head, List<String> failures) {
        List<AABB> selection = CarcassBounds.boxes(state, data, Blocks.AIR.defaultBlockState());
        Cubes plaque = renderPlaque(def, facing, selection, label, failures);
        AABB board = plaque.broadBoard(facing);
        if (board == null) { failures.add(label + ":no-solid-plaque"); return; }
        AABB skull = skull(def, data, head);
        if (skull == null) { failures.add(label + ":no-solid-skull"); return; }
        double transverse = transverse(skull.getCenter(), facing);
        double boardTransverse = transverse(board.getCenter(), facing);
        if (Math.abs(transverse - boardTransverse) > EPSILON || Math.abs(skull.getCenter().y - .5) > EPSILON)
            failures.add(label + ":skull-not-centred-on-board:" + skull.getCenter() + ":" + board.getCenter());
        double wood = wood(board, facing);
        if (Math.abs(rear(skull, facing) - wood) > EPSILON)
            failures.add(label + ":skull-not-on-wood:" + rear(skull, facing) + ":expected:" + wood);
        if (!HeadMountBlock.plaque(def.mount(), facing).bounds().contains(board.getCenter()))
            failures.add(label + ":board-outside-plaque-envelope:" + board);
    }

    /** First emitted solid cube is the authored skull/neck; an empty/plane root (Cosmaw) uses all solid descendants. */
    private static AABB skull(com.otectus.alexsbutchery.def.MobDef def, CompoundTag data, Cubes head) {
        var shape = CarcassModels.get(def.entity()).shape(data);
        var profile = PoseProfiles.get(def);
        var part = shape.parts().get(profile.headRoot);
        if (part == null) for (var entry : shape.parts().entrySet()) if (profile.isHead(entry.getKey())) { part = entry.getValue(); break; }
        boolean rootSolid = part != null && part.raw() instanceof com.github.alexthe666.citadel.client.model.AdvancedModelBox box
                && box.cubeList.stream().anyMatch(c -> c.posX1 != c.posX2 && c.posY1 != c.posY2 && c.posZ1 != c.posZ2);
        return rootSolid ? head.firstSolid() : head.solidBounds();
    }

    private static Cubes renderHead(BlockState state, CompoundTag data, Direction facing) {
        Cubes mesh = new Cubes(); BoundsExport.renderHead(state, data, facing, mesh); return mesh;
    }

    private static Cubes renderPlaque(com.otectus.alexsbutchery.def.MobDef def, Direction facing, List<AABB> selection,
                                      String label, List<String> failures) {
        Cubes mesh = new Cubes() {
            @Override void point(double x, double y, double z) {
                if (!HeadMountBlock.plaque(def.mount(), facing).bounds().inflate(EPSILON).contains(x, y, z))
                    failures.add(label + ":plaque-outside-collision:" + x + "," + y + "," + z);
                if (selection.stream().noneMatch(box -> box.inflate(EPSILON).contains(x, y, z)))
                    failures.add(label + ":plaque-outside-export:" + x + "," + y + "," + z);
            }
        };
        CarcassScene.renderPlaque(def, facing, new PoseStack(), type -> mesh, 15728880, 0);
        return mesh;
    }

    private static double transverse(Vec3 point, Direction facing) { return facing.getAxis() == Direction.Axis.Z ? point.x : point.z; }
    private static double rear(AABB box, Direction facing) { return switch (facing) { case NORTH -> box.maxZ; case SOUTH -> box.minZ; case EAST -> box.minX; case WEST -> box.maxX; default -> throw new IllegalArgumentException(); }; }
    /** The board face toward the trophy, from the broad native-board cuboid rather than the collision shape. */
    private static double wood(AABB board, Direction facing) { return switch (facing) { case NORTH -> board.minZ; case SOUTH -> board.maxZ; case EAST -> board.maxX; case WEST -> board.minX; default -> throw new IllegalArgumentException(); }; }

    /** Groups Citadel's six quads per cube and classifies solids from emitted 3-D volume, not rotated AABBs. */
    private static class Cubes implements VertexConsumer {
        private final List<List<Vec3>> cubes = new ArrayList<>();
        private List<Vec3> cube = new ArrayList<>(24);
        @Override public VertexConsumer vertex(double x, double y, double z) {
            point(x, y, z); cube.add(new Vec3(x, y, z));
            if (cube.size() == 24) { cubes.add(cube); cube = new ArrayList<>(24); }
            return this;
        }
        void point(double x, double y, double z) {}
        AABB firstSolid() { for (List<Vec3> cube : cubes) if (solid(cube)) return bounds(cube); return null; }
        AABB solidBounds() {
            AABB result = null;
            for (List<Vec3> cube : cubes) if (solid(cube)) result = result == null ? bounds(cube) : result.minmax(bounds(cube));
            return result;
        }
        AABB broadBoard(Direction facing) {
            // Baked block quads are grouped by face direction, not by cuboid. Find the broad wood quad
            // behind the skull; its geometric centre need not be the trophy's authored height of 1/2.
            AABB result = null; double area = -1;
            for (List<Vec3> cube : cubes) for (int q = 0; q < cube.size(); q += 4) {
                AABB box = bounds(cube.subList(q, q + 4));
                boolean northSouth = facing.getAxis() == Direction.Axis.Z;
                double depth = northSouth ? box.getZsize() : box.getXsize();
                double low = northSouth ? box.minX : box.minZ, high = northSouth ? box.maxX : box.maxZ;
                if (depth > EPSILON || low > .5 || high < .5 || box.minY > .5 || box.maxY < .5) continue;
                double next = (high - low) * box.getYsize();
                boolean forward = result != null && (wood(box, facing) - wood(result, facing))
                        * (facing == Direction.NORTH || facing == Direction.WEST ? -1 : 1) > 0;
                if (next > area + EPSILON || Math.abs(next - area) < EPSILON && forward) { area = next; result = box; }
            }
            return result;
        }
        private static boolean solid(List<Vec3> vertices) {
            List<Vec3> unique = vertices.stream().distinct().toList();
            Vec3 origin = unique.get(0);
            AABB box = bounds(unique);
            double span = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
            double tolerance = Math.max(1.0E-14, span * span * span * 1.0E-6);
            for (Vec3 a : unique) for (Vec3 b : unique) {
                Vec3 normal = a.subtract(origin).cross(b.subtract(origin));
                for (Vec3 c : unique) if (Math.abs(normal.dot(c.subtract(origin))) > tolerance) return true;
            }
            return false;
        }
        private static AABB bounds(List<Vec3> vertices) {
            AABB result = new AABB(vertices.get(0), vertices.get(0));
            for (int i = 1; i < vertices.size(); i++) result = result.minmax(new AABB(vertices.get(i), vertices.get(i)));
            return result;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    }

    private HeadContactChecks() {}
}
