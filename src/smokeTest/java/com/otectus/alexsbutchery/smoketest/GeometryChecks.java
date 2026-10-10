package com.otectus.alexsbutchery.smoketest;

import com.github.alexthe666.alexsmobs.entity.EntityLaviathan;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.client.pose.PoseProfiles;
import com.otectus.alexsbutchery.client.render.CarcassModels;
import com.otectus.alexsbutchery.client.render.CarcassScene;
import com.otectus.alexsbutchery.client.render.LyingPose;
import com.otectus.alexsbutchery.client.render.ModelBounds;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Timer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

final class GeometryChecks {
    static void run(Map<String, Object> checks) {
        List<String> failures = new ArrayList<>();
        List<String> floorFailures = new ArrayList<>();
        List<String> emptyFloorCases = new ArrayList<>();
        Map<String, Double> suspension = new java.util.HashMap<>();
        int cases = 0;
        int floorCases = 0;
        double largestFloorError = 0;
        for (var def : MobDefs.all()) for (var block : BoundsExport.blocks(def)) {
            List<CompoundTag> variants = new ArrayList<>(BoundsExport.variants(def));
            String sizeTag = CarcassBounds.scaleTag(def.id());
            if (!sizeTag.isEmpty()) for (var data : BoundsExport.variants(def)) for (float size : new float[]{.7F, 1.35F}) {
                var scaled = data.copy(); scaled.putFloat(sizeTag, size); variants.add(scaled);
            }
            for (int stage : block.stateProperty().getPossibleValues()) for (var data : variants) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    var support = (cases % 2 == 0 ? ButcheryHooks.hook() : ButcheryHooks.rope()).defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, Direction.from2DDataValue((cases / 3) % 4));
                    var state = block.defaultBlockState().setValue(block.stateProperty(), stage).setValue(AbstractCarcassBlock.FACING, facing);
                    var boxes = CarcassBounds.boxes(state, data, support);
                    String label = CarcassBounds.key(state, data) + "/" + facing + "/" + data;
                    FloorMesh mesh = new FloorMesh(boxes, label, failures);
                    BoundsExport.render(state, data, facing, support, mesh);
                    if (!block.hanging(state)) {
                        floorCases++;
                        // Parts the profile leaves out of ground contact (an ear, a flipper) may dip into the floor:
                        // measure contact on a second render without them, as the solver does.
                        var ignore = PoseProfiles.get(def).lyingIgnore;
                        if (!ignore.isEmpty()) {
                            mesh = new FloorMesh(List.of(new AABB(-1E9, -1E9, -1E9, 1E9, 1E9, 1E9)), label, new ArrayList<>());
                            renderWithout(def, state, data, facing, support, ignore, mesh);
                        }
                        double floor = mesh.solidFloor();
                        double expected = PoseProfiles.get(def).lyingOffset[1];
                        if (!Double.isFinite(floor)) {
                            if (mesh.hasVertices()) floor = mesh.renderedFloor();
                            else emptyFloorCases.add(label);
                        }
                        if (Double.isFinite(floor)) {
                            double error = Math.abs(floor - expected);
                            largestFloorError = Math.max(largestFloorError, error);
                            if (error > .001) floorFailures.add(label + ":ground-contact:" + floor + ":expected:" + expected);
                            double lift = mesh.bodyFloor() - floor;
                            if (Double.isFinite(lift) && facing == Direction.NORTH) suspension.put(CarcassBounds.key(state, data), lift);
                        }
                    }
                    cases++;
                }
            }
        }
        checks.put("geometry_bounds_render_cases", cases);
        checks.put("geometry_bounds_failures", failures.toString());
        checks.put("geometry_bounds_cover_rendered_anatomy", failures.isEmpty());
        checks.put("geometry_lying_floor_cases", floorCases);
        checks.put("geometry_lying_largest_floor_error", largestFloorError);
        checks.put("geometry_lying_floor_failures", floorFailures.toString());
        checks.put("geometry_lying_empty_cases", emptyFloorCases.toString());
        checks.put("geometry_lying_solid_anatomy_grounded", floorFailures.isEmpty());
        // How far the bulk of the body (the largest solid boxes, half the solid volume) sits above its lowest solid
        // point: legs and fins legitimately prop a body up a little, a suspended torso shows here for review.
        var ranked = suspension.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).toList();
        Map<String, Double> worst = new java.util.LinkedHashMap<>();
        for (int i = 0; i < Math.min(25, ranked.size()); i++) worst.put(ranked.get(i).getKey(), Math.round(ranked.get(i).getValue() * 1000) / 1000D);
        checks.put("geometry_lying_body_lift_worst", worst);
        Map<String, Double> named = new java.util.LinkedHashMap<>();
        List<String> suspended = new ArrayList<>();
        suspension.forEach((key, lift) -> {
            String mob = key.substring(key.startsWith("drained_") ? 8 : 0, key.indexOf('/'));
            for (String id : new String[]{"laviathan", "void_worm", "centipede", "farseer"}) if (mob.startsWith(id)) {
                named.put(key, Math.round(lift * 1000) / 1000D);
                if (lift > .4) suspended.add(key + ":" + lift);
            }
        });
        checks.put("geometry_lying_body_lift_reported_mobs", new java.util.TreeMap<>(named));
        checks.put("geometry_lying_reported_mobs_body_on_floor", suspended.isEmpty());
        checks.put("geometry_lying_reported_mobs_suspended", suspended.toString());
        coplanarSupport(checks);
        rugs(checks);
        laviathan(checks);
    }

    private static void rugs(Map<String, Object> checks) {
        List<String> failures = new ArrayList<>();
        Map<String, Double> chinHeights = new java.util.LinkedHashMap<>();
        for (var def : MobDefs.all()) if (def.hasRug()) {
            var handle = CarcassModels.get(def.entity());
            double height = -1;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                ModelBounds chin = new ModelBounds();
                ModelBounds mesh = new ModelBounds() {
                    int vertices;
                    @Override public VertexConsumer vertex(double x, double y, double z) {
                        // The first solid cube is the skull/jaw, not the bison's hanging beard plane.
                        if (vertices++ < 24) chin.vertex(x, y, z);
                        return super.vertex(x, y, z);
                    }
                };
                CarcassScene.renderRugHead(handle, PoseProfiles.get(def), new CompoundTag(), facing, new PoseStack(), type -> mesh, 15728880);
                double y = chin.bounds().minY;
                chinHeights.put(def.id() + "/" + facing, y);
                if (Math.abs(y - .3 / 16) > .07) failures.add(def.id() + ":floating-chin:" + y);
                if (height >= 0 && Math.abs(height - mesh.bounds().getYsize()) > .0001) failures.add(def.id() + ":orientation-height");
                height = mesh.bounds().getYsize();
                if (height < .15) failures.add(def.id() + ":flattened-head");
            }
        }
        checks.put("rug_chin_heights", chinHeights);
        checks.put("rug_orientation_failures", failures.toString());
        checks.put("rug_heads_seated_all_orientations", failures.isEmpty());
    }

    private static void laviathan(Map<String, Object> checks) {
        var def = MobDefs.byId("laviathan");
        var state = ModBlocks.of(def).carcass().get().defaultBlockState();
        var handle = CarcassModels.get(def.entity());
        var shape = handle.shape(null);
        EntityLaviathan dummy = (EntityLaviathan) handle.dummy();
        try {
            var field = java.util.Arrays.stream(Minecraft.class.getDeclaredFields()).filter(f -> f.getType() == Timer.class).findFirst().orElseThrow();
            field.setAccessible(true);
            Timer timer = (Timer) field.get(Minecraft.getInstance());
            float previous = timer.partialTick;
            var raw = new HashSet<Long>();
            var fixed = new HashSet<Long>();
            try {
                for (float frame : new float[]{0F, .25F, .75F, 1F}) {
                    timer.partialTick = frame;
                    // Reproduce the unticked dummy's mismatched interpolation endpoints.
                    dummy.yBodyRot = 90; dummy.yBodyRotO = dummy.yHeadRot = dummy.yHeadRotO = 0;
                    shape.model().setupAnim(dummy, 0, 0, 0, 0, 0);
                    Fingerprint before = new Fingerprint();
                    shape.model().renderToBuffer(new PoseStack(), before, 15728880, 0, 1, 1, 1, 1);
                    raw.add(before.hash);
                    var livingPose = pose(shape);
                    Fingerprint after = new Fingerprint();
                    BoundsExport.render(state, new CompoundTag(), Direction.NORTH, ButcheryHooks.hook().defaultBlockState(), after);
                    fixed.add(after.hash);
                    if (!livingPose.equals(pose(shape))) checks.put("laviathan_living_pose_restored", false);
                }
            } finally { timer.partialTick = previous; }
            checks.put("laviathan_original_jitter_reproduced", raw.size() > 1);
            checks.put("laviathan_frozen_across_frame_fractions", fixed.size() == 1);
            checks.putIfAbsent("laviathan_living_pose_restored", true);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static List<Float> pose(CarcassModels.Shape shape) {
        List<Float> values = new ArrayList<>();
        shape.parts().values().forEach(p -> { for (float f : p.pivot()) values.add(f); for (float f : p.rotation()) values.add(f); });
        return values;
    }

    /** Renders with the named parts hidden on the shared model for this snapshot, restoring them afterwards. */
    private static void renderWithout(com.otectus.alexsbutchery.def.MobDef def, net.minecraft.world.level.block.state.BlockState state,
                                      CompoundTag data, Direction facing, net.minecraft.world.level.block.state.BlockState support,
                                      java.util.Set<String> parts, ModelBounds mesh) {
        var shape = CarcassModels.get(def.entity()).shape(data);
        List<com.otectus.alexsbutchery.client.render.ModelParts.Part> hidden = new ArrayList<>();
        for (String name : parts) {
            var part = shape.parts().get(name);
            if (part != null && part.visible()) { part.setVisible(false); hidden.add(part); }
        }
        try { BoundsExport.render(state, data, facing, support, mesh); }
        finally { hidden.forEach(part -> part.setVisible(true)); }
    }

    /** A tilted decoration below a solid box must never become the floor after float transforms. */
    private static void coplanarSupport(Map<String, Object> checks) {
        List<String> failures = new ArrayList<>();
        int cases = 0;
        for (float scale : new float[]{.35F, 1F, 2.5F}) for (Direction facing : Direction.Plane.HORIZONTAL) {
            PoseStack support = new PoseStack();
            support.mulPose(Axis.YP.rotationDegrees(facing.toYRot()));
            support.scale(scale, scale, scale);
            PoseStack decoration = new PoseStack();
            decoration.mulPose(Axis.YP.rotationDegrees(facing.toYRot()));
            decoration.scale(scale, scale, scale);
            decoration.translate(.7, -1.5, .4);
            decoration.mulPose(Axis.XP.rotationDegrees(30));
            decoration.mulPose(Axis.ZP.rotationDegrees(20));
            float floor = LyingPose.measure(target -> {
                VertexConsumer mesh = target.getBuffer(RenderType.lines());
                emitBox(mesh, decoration, true);
                emitBox(mesh, support, false);
            }).floor();
            if (Math.abs(floor) > .00001) failures.add(scale + "/" + facing + ":" + floor);
            cases++;
        }
        checks.put("geometry_lying_plane_filter_cases", cases);
        checks.put("geometry_lying_plane_filter_failures", failures.toString());
        checks.put("geometry_lying_planes_do_not_support", failures.isEmpty());
    }

    private static void emitBox(VertexConsumer mesh, PoseStack pose, boolean plane) {
        Vector3f[] vertices = new Vector3f[8];
        for (int i = 0; i < vertices.length; i++) {
            float x = (i & 1) == 0 ? -.5F : .5F;
            float y = plane ? 0F : (i & 2) == 0 ? 0F : .35F;
            float z = (i & 4) == 0 ? -.3F : .3F;
            vertices[i] = pose.last().pose().transformPosition(new Vector3f(x, y, z));
        }
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1},
                {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        for (int[] face : faces) for (int corner : face) {
            Vector3f vertex = vertices[corner];
            mesh.vertex(vertex.x, vertex.y, vertex.z).endVertex();
        }
    }

    /** Independently checks the final emitted cuboids; flat fur, whisker and fin planes are not floor supports. */
    private static final class FloorMesh extends ModelBounds {
        private final List<AABB> selection;
        private final String label;
        private final List<String> failures;
        private final List<Vec3> cube = new ArrayList<>(24);
        private boolean outside;
        private double solidFloor = Double.POSITIVE_INFINITY;
        private double renderedFloor = Double.POSITIVE_INFINITY;
        private final List<double[]> solids = new ArrayList<>();

        private FloorMesh(List<AABB> selection, String label, List<String> failures) {
            this.selection = selection;
            this.label = label;
            this.failures = failures;
        }

        double solidFloor() {
            return solidFloor;
        }

        /** The lowest point of the largest solid boxes that together hold half the solid volume. */
        double bodyFloor() {
            double total = 0;
            for (double[] solid : solids) total += solid[0];
            if (total <= 0) return Double.NaN;
            List<double[]> sorted = new ArrayList<>(solids);
            sorted.sort((a, b) -> Double.compare(b[0], a[0]));
            double sum = 0, floor = Double.POSITIVE_INFINITY;
            for (double[] solid : sorted) {
                floor = Math.min(floor, solid[1]);
                if ((sum += solid[0]) >= total / 2) break;
            }
            return floor;
        }

        double renderedFloor() {
            return renderedFloor;
        }

        boolean hasVertices() {
            return Double.isFinite(renderedFloor);
        }

        private void finishCube() {
            Vec3 origin = cube.get(0);
            Vec3 axis = null;
            double axisLength = 0;
            Vec3 normal = null;
            double coordinate = 1;
            for (Vec3 vertex : cube) {
                coordinate = Math.max(coordinate, Math.max(Math.abs(vertex.x), Math.max(Math.abs(vertex.y), Math.abs(vertex.z))));
                Vec3 candidate = vertex.subtract(origin);
                if (candidate.lengthSqr() > axisLength) {
                    axis = candidate;
                    axisLength = candidate.lengthSqr();
                }
            }
            double normalLength = 0;
            if (axis != null) for (int i = 1; i < cube.size(); i++) {
                Vec3 candidate = axis.cross(cube.get(i).subtract(origin));
                if (candidate.lengthSqr() > normalLength) {
                    normal = candidate;
                    normalLength = candidate.lengthSqr();
                }
            }
            if (normal != null && normalLength > 1.0E-24) {
                normal = normal.scale(1 / Math.sqrt(normalLength));
                double thickness = 0;
                for (int i = 1; i < cube.size(); i++)
                    thickness = Math.max(thickness, Math.abs(normal.dot(cube.get(i).subtract(origin))));
                double tolerance = Math.max(1.0E-6, Math.ulp((float) coordinate) * 32);
                if (thickness > tolerance) {
                    double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX, maxX = -minX, maxY = -minX, maxZ = -minX;
                    for (Vec3 vertex : cube) {
                        solidFloor = Math.min(solidFloor, vertex.y);
                        minX = Math.min(minX, vertex.x); minY = Math.min(minY, vertex.y); minZ = Math.min(minZ, vertex.z);
                        maxX = Math.max(maxX, vertex.x); maxY = Math.max(maxY, vertex.y); maxZ = Math.max(maxZ, vertex.z);
                    }
                    solids.add(new double[]{(maxX - minX) * (maxY - minY) * (maxZ - minZ), minY});
                }
            }
            cube.clear();
        }

        @Override public VertexConsumer vertex(double x, double y, double z) {
            renderedFloor = Math.min(renderedFloor, y);
            if (!outside && selection.stream().noneMatch(box -> box.inflate(.0001).contains(x, y, z))) {
                outside = true;
                failures.add(label + ":vertex-outside:" + new Vec3(x, y, z));
            }
            cube.add(new Vec3(x, y, z));
            if (cube.size() == 24) finishCube();
            return super.vertex(x, y, z);
        }
    }

    private static class Fingerprint extends ModelBounds {
        long hash = 1;
        @Override public VertexConsumer vertex(double x, double y, double z) {
            hash = hash * 31 + Math.round(x * 100000); hash = hash * 31 + Math.round(y * 100000); hash = hash * 31 + Math.round(z * 100000);
            return super.vertex(x, y, z);
        }
    }
    private GeometryChecks() {}
}
