package com.otectus.alexsbutchery.smoketest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.client.pose.PoseProfiles;
import com.otectus.alexsbutchery.client.render.CarcassModels;
import com.otectus.alexsbutchery.client.render.CarcassScene;
import com.otectus.alexsbutchery.client.render.StageTextures;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDefs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exercise the real hanging renderer and the dependency's baked support models, independently of its anchor math. */
final class HangingChecks {
    static void run(Map<String, Object> checks) {
        List<String> failures = new ArrayList<>();
        int rendered = 0, legProfiles = 0;
        List<BlockState> supports = new ArrayList<>();
        for (var block : List.of(ButcheryHooks.hook(), ButcheryHooks.rope())) {
            IntegerProperty variant = (IntegerProperty) block.getStateDefinition().getProperty("blockstate");
            for (int value : variant.getPossibleValues()) for (Direction facing : Direction.Plane.HORIZONTAL)
                supports.add(block.defaultBlockState().setValue(variant, value).setValue(HorizontalDirectionalBlock.FACING, facing));
        }
        for (var def : MobDefs.all()) {
            if (def.floor()) continue;
            var handle = CarcassModels.get(def.entity());
            var shape = handle.shape(null);
            var profile = PoseProfiles.get(def);
            if (!profile.hangingLegs.isEmpty()) {
                legProfiles++;
                for (String name : profile.hangingLegs) if (!shape.parts().containsKey(name)) failures.add(def.id() + ":missing-leg:" + name);
                for (String name : profile.hangingForelegs) if (!shape.parts().containsKey(name)) failures.add(def.id() + ":missing-foreleg:" + name);
            }
            for (String name : profile.hangingRotations.keySet()) if (!shape.parts().containsKey(name)) failures.add(def.id() + ":missing-joint:" + name);
            for (String name : profile.hangingAttachment) if (!shape.parts().containsKey(name)) failures.add(def.id() + ":missing-attachment:" + name);
            for (int stage = 0; stage < 6; stage++) {
                if (stage == 5 && !def.hasSkeleton()) continue;
                Set<Stages.Action> done = EnumSet.noneOf(Stages.Action.class);
                if (stage >= 2 && stage != 5) {
                    if (def.hasHead()) done.add(Stages.Action.HEAD);
                    done.add(Stages.Action.SKIN);
                }
                if (stage >= 3 && stage != 5) done.add(Stages.Action.CUT_1);
                if (stage == 4) done.add(Stages.Action.CUT_2);
                var look = stage == 5 ? StageTextures.Look.BONE : stage >= 2 ? StageTextures.Look.SKINNED
                        : stage == 1 ? StageTextures.Look.DRAINED : StageTextures.Look.FRESH;
                var s = new CarcassScene.Subject(def, handle, profile, new CompoundTag(), look, done);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    BlockState support = supports.get((rendered * 13 + stage) % supports.size());
                    check(s, facing, support, def.id() + ":" + stage + ":" + facing, failures);
                    rendered++;
                }
            }
        }
        // Model swaps and appearance snapshots must not reuse another animal's contact point.
        for (int size = 0; size < 3; size++) {
            var def = MobDefs.byId("catfish");
            CompoundTag data = new CompoundTag();
            data.putInt("CatfishSize", size);
            for (var look : List.of(StageTextures.Look.FRESH, StageTextures.Look.BONE)) for (Direction facing : Direction.Plane.HORIZONTAL) {
                var s = new CarcassScene.Subject(def, CarcassModels.get(def.entity()), PoseProfiles.get(def), data, look, Set.of());
                for (BlockState support : supports) {
                    check(s, facing, support, "catfish-size-" + size + ":" + look + ":" + facing, failures);
                    rendered++;
                }
            }
        }
        // A rendering exception must restore the borrowed hip rotations and every pose-stack frame.
        var def = MobDefs.byId("kangaroo");
        var handle = CarcassModels.get(def.entity());
        var shape = handle.shape(null);
        handle.pose(shape);
        var before = rotations(shape);
        var subject = new CarcassScene.Subject(def, handle, PoseProfiles.get(def), new CompoundTag(), StageTextures.Look.FRESH, Set.of());
        PoseStack pose = new PoseStack();
        boolean threw = false;
        try {
            CarcassScene.renderHanging(subject, Direction.SOUTH, pose, type -> new Samples() {
                @Override public VertexConsumer vertex(double x, double y, double z) { throw new IllegalStateException("hanging-test-interruption"); }
            }, 15728880);
        } catch (IllegalStateException e) { threw = e.getMessage().equals("hanging-test-interruption"); }
        checks.put("hanging_interruption_threw", threw);
        checks.put("hanging_interruption_restored_angles", before.equals(rotations(shape)));
        checks.put("hanging_interruption_restored_stack", pose.clear());
        checks.put("hanging_interrupted_render_restores_pose", threw && before.equals(rotations(shape)) && pose.clear());
        checks.put("hanging_render_cases", rendered);
        checks.put("hanging_leg_profiles", legProfiles);
        checks.put("hanging_failures", failures.toString());
        checks.put("hanging_all_meshes_attached_and_restored", failures.isEmpty());
    }

    private static void check(CarcassScene.Subject s, Direction facing, BlockState support, String label, List<String> failures) {
        var shape = s.handle().shape(s.mobData());
        s.handle().pose(shape);
        var before = rotations(shape);
        Samples body = new Samples(), rope = new Samples();
        PoseStack pose = new PoseStack();
        CarcassScene.renderHanging(s, facing, support, pose,
                type -> type.toString().contains("textures/block/rope.png") ? rope : body, 15728880);
        if (body.points.isEmpty()) failures.add(label + ":empty");
        if (!rope.points.isEmpty()) failures.add(label + ":generated-rope");
        if (distanceToSupport(body, support) > .04F) failures.add(label + ":hook-misses-anatomy:" + support);
        if (!before.equals(rotations(shape))) failures.add(label + ":pose-leak");
        if (!pose.clear()) failures.add(label + ":matrix-leak");
        if (body.points.stream().anyMatch(p -> !p.isFinite())) failures.add(label + ":nonfinite");
    }

    private static List<Float> rotations(CarcassModels.Shape shape) {
        List<Float> result = new ArrayList<>();
        shape.parts().values().forEach(p -> { for (float angle : p.rotation()) result.add(angle); result.add(p.visible() ? 1F : 0F); });
        return result;
    }

    /** Compare real triangles of both meshes; contact may be in the middle of a hook face, away from its corners. */
    private static float distanceToSupport(Samples body, BlockState state) {
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        float distance = Float.MAX_VALUE;
        for (int side = -1; side < 6; side++) {
            for (var quad : model.getQuads(state, side == -1 ? null : Direction.values()[side], RandomSource.create(0))) {
                int[] data = quad.getVertices();
                int stride = data.length / 4;
                Vector3f[] support = new Vector3f[4];
                Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);
                for (int i = 0; i < 4; i++) {
                    support[i] = new Vector3f(Float.intBitsToFloat(data[i * stride]), 1 + Float.intBitsToFloat(data[i * stride + 1]), Float.intBitsToFloat(data[i * stride + 2]));
                    min.min(support[i]); max.max(support[i]);
                }
                for (int j = 0; j + 3 < body.points.size(); j += 4) {
                    Vector3f bmin = new Vector3f(Float.MAX_VALUE), bmax = new Vector3f(-Float.MAX_VALUE);
                    for (int k = 0; k < 4; k++) { bmin.min(body.points.get(j + k)); bmax.max(body.points.get(j + k)); }
                    if (bmax.x < min.x - .04F || bmin.x > max.x + .04F || bmax.y < min.y - .04F
                            || bmin.y > max.y + .04F || bmax.z < min.z - .04F || bmin.z > max.z + .04F) continue;
                    for (int k = 1; k <= 2; k++) for (int l = 1; l <= 2; l++) {
                        Vector3f[] a = {support[0], support[k], support[k + 1]};
                        Vector3f[] b = {body.points.get(j), body.points.get(j + l), body.points.get(j + l + 1)};
                        for (int edge = 0; edge < 3; edge++) {
                            if (intersects(a[edge], a[(edge + 1) % 3], b) || intersects(b[edge], b[(edge + 1) % 3], a)) return 0;
                            distance = Math.min(distance, triangleDistance(a[edge], b[0], b[1], b[2]));
                            distance = Math.min(distance, triangleDistance(b[edge], a[0], a[1], a[2]));
                        }
                        if (distance < .025F) return distance;
                    }
                }
            }
        }
        return distance;
    }

    private static boolean intersects(Vector3f start, Vector3f end, Vector3f[] triangle) {
        Vector3f normal = new Vector3f(triangle[1]).sub(triangle[0]).cross(new Vector3f(triangle[2]).sub(triangle[0]));
        Vector3f delta = new Vector3f(end).sub(start);
        float denominator = normal.dot(delta);
        if (Math.abs(denominator) < .00000001F) return false;
        float t = normal.dot(new Vector3f(triangle[0]).sub(start)) / denominator;
        return t >= 0 && t <= 1 && triangleDistance(new Vector3f(start).add(delta.mul(t)), triangle[0], triangle[1], triangle[2]) < .00001F;
    }

    private static float triangleDistance(Vector3f p, Vector3f a, Vector3f b, Vector3f c) {
        Vector3f ab = new Vector3f(b).sub(a), ac = new Vector3f(c).sub(a), ap = new Vector3f(p).sub(a);
        float aa = ab.dot(ab), bb = ac.dot(ac), cross = ab.dot(ac), denominator = aa * bb - cross * cross;
        if (denominator > .00000001F) {
            float u = (bb * ap.dot(ab) - cross * ap.dot(ac)) / denominator;
            float v = (aa * ap.dot(ac) - cross * ap.dot(ab)) / denominator;
            if (u >= 0 && v >= 0 && u + v <= 1) return new Vector3f(a).add(ab.mul(u)).add(ac.mul(v)).distance(p);
        }
        return Math.min(edgeDistance(p, a, b), Math.min(edgeDistance(p, b, c), edgeDistance(p, c, a)));
    }

    private static float edgeDistance(Vector3f p, Vector3f a, Vector3f b) {
        Vector3f delta = new Vector3f(b).sub(a);
        float t = delta.lengthSquared() < .00000001F ? 0 : new Vector3f(p).sub(a).dot(delta) / delta.lengthSquared();
        return new Vector3f(a).add(delta.mul(Math.max(0, Math.min(1, t)))).distance(p);
    }

    private static class Samples implements VertexConsumer {
        final List<Vector3f> points = new ArrayList<>();
        public VertexConsumer vertex(double x, double y, double z) { points.add(new Vector3f((float) x, (float) y, (float) z)); return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() {}
        public void defaultColor(int r, int g, int b, int a) {}
        public void unsetDefaultColor() {}
    }
    private HangingChecks() {}
}
