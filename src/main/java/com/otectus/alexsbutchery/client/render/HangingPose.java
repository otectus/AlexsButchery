package com.otectus.alexsbutchery.client.render;

import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.client.pose.PoseProfile;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Seats solid anatomy directly on the support and balances the remaining mesh beneath it. */
final class HangingPose {
    private record Key(Object model, Object profile, ResourceLocation texture, float scale, CompoundTag data,
                       StageTextures.Look look, Set<Stages.Action> done) {}
    record Rotation(float x, float y, float z) {
        static Rotation of(AdvancedModelBox box) { return new Rotation(box.rotateAngleX, box.rotateAngleY, box.rotateAngleZ); }
        void apply(AdvancedModelBox box) { box.rotateAngleX = x; box.rotateAngleY = y; box.rotateAngleZ = z; }
    }
    record Layout(Map<String, Rotation> joints, Vector3f anchor, Quaternionf balance) {}
    // Snapshots may be numerous in a gallery. Bound the cache and clear it on resource/world reload.
    private static final Map<Key, Layout> CACHE = new LinkedHashMap<>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Layout> e) { return size() > 512; }
    };
    static void clear() { CACHE.clear(); }

    static Layout get(CarcassScene.Subject s, CarcassModels.Shape shape,
                      BiConsumer<Map<String, Rotation>, MultiBufferSource> draw) {
        Key key = new Key(shape.model(), s.profile(), shape.texture(), shape.scale(), s.mobData().copy(), s.look(), Set.copyOf(s.done()));
        Layout cached = CACHE.get(key);
        if (cached != null) return cached;
        s.handle().pose(shape);
        Map<String, Rotation> legs = joints(shape, s.profile());
        Mesh mesh = new Mesh();
        draw.accept(legs, new Capture(mesh));
        Layout result = mesh.seat(legs);
        CACHE.put(key, result);
        return result;
    }

    private static Map<String, Rotation> joints(CarcassModels.Shape shape, PoseProfile profile) {
        Map<String, Rotation> result = new LinkedHashMap<>();
        Map<AdvancedModelBox, Rotation> saved = new IdentityHashMap<>();
        try {
            profile.hangingRotations.forEach((name, degrees) -> {
                var part = shape.parts().get(name);
                if (part != null && part.raw() instanceof AdvancedModelBox box) {
                    saved.put(box, Rotation.of(box));
                    Rotation rotation = new Rotation((float) Math.toRadians(degrees[0]),
                            (float) Math.toRadians(degrees[1]), (float) Math.toRadians(degrees[2]));
                    rotation.apply(box);
                    result.put(name, rotation);
                }
            });
            result.putAll(converge(shape, profile));
            Vector3f down = new Vector3f(0, -1, 0)
                    .rotateX((float) Math.toRadians(-profile.hangingPitch))
                    .rotateZ((float) Math.toRadians(-profile.hangingFlip)).mul(-1, -1, 1);
            Set<AdvancedModelBox> tails = Collections.newSetFromMap(new IdentityHashMap<>());
            if (!profile.hangingLegs.isEmpty()) shape.parts().forEach((name, part) -> {
                if (name.toLowerCase(Locale.ROOT).startsWith("tail") && part.raw() instanceof AdvancedModelBox box) tails.add(box);
            });
            shape.parts().forEach((name, part) -> {
                if (!(part.raw() instanceof AdvancedModelBox box) || profile.hangingRotations.containsKey(name)) return;
                boolean tail = tails.contains(box);
                for (AdvancedModelBox parent = box.getParent(); parent != null; parent = parent.getParent())
                    if (tails.contains(parent)) tail = false;
                if (!tail && !profile.hangingForelegs.contains(name)) return;
                Vector3f pivot = matrix(box).last().pose().transformPosition(new Vector3f());
                Vector3f direction = new Vector3f(foot(box).centre).sub(pivot);
                if (direction.lengthSquared() < .000001F) return;
                Matrix4f parent = box.getParent() == null ? new Matrix4f() : new Matrix4f(matrix(box.getParent()).last().pose());
                parent.invert();
                Vector3f current = parent.transformDirection(direction).normalize();
                Vector3f target = parent.transformDirection(new Vector3f(down)).normalize();
                Quaternionf rotation = new Quaternionf().rotationTo(current, target)
                        .mul(new Quaternionf().rotationZYX(box.rotateAngleZ, box.rotateAngleY, box.rotateAngleX));
                Vector3f angles = rotation.getEulerAnglesZYX(new Vector3f());
                result.put(name, new Rotation(angles.x, angles.y, angles.z));
            });
        } finally { saved.forEach((box, rotation) -> rotation.apply(box)); }
        return result;
    }

    /** Rotate at the hips, preserving joint positions and bone lengths; never translate legs off the pelvis. */
    private static Map<String, Rotation> converge(CarcassModels.Shape shape, PoseProfile profile) {
        Map<String, Rotation> angles = new LinkedHashMap<>();
        if (profile.hangingLegs.size() != 2) return angles;
        List<AdvancedModelBox> legs = new ArrayList<>();
        for (String name : profile.hangingLegs) {
            var part = shape.parts().get(name);
            if (part == null || !(part.raw() instanceof AdvancedModelBox box)) return angles;
            legs.add(box);
        }
        Map<AdvancedModelBox, Rotation> saved = new IdentityHashMap<>();
        try {
            for (var leg : legs) {
                saved.put(leg, Rotation.of(leg));
                leg.rotateAngleX += (float) Math.toRadians(profile.hangingLegPitch);
            }
            float centre = (foot(legs.get(0)).centre.x + foot(legs.get(1)).centre.x) * .5F;
            boolean turnY = Math.abs(profile.hangingLegPitch) > 45;
            int index = 0;
            for (String name : profile.hangingLegs) {
                AdvancedModelBox leg = legs.get(index++);
                float original = turnY ? leg.rotateAngleY : leg.rotateAngleZ;
                Foot foot = foot(leg);
                Vector3f end = foot.centre;
                // Bring the hocks together without collapsing their original joint lengths.
                float target = centre + Math.copySign(Math.min(Math.abs(end.x - centre), foot.halfWidth + .005F), end.x - centre);
                float best = original, error = Math.abs(end.x - target);
                for (int degrees = -65; degrees <= 65; degrees++) {
                    float angle = original + (float) Math.toRadians(degrees);
                    if (turnY) leg.rotateAngleY = angle; else leg.rotateAngleZ = angle;
                    float distance = Math.abs(foot(leg).centre.x - target);
                    if (distance < error) { error = distance; best = angle; }
                }
                if (turnY) leg.rotateAngleY = best; else leg.rotateAngleZ = best;
                angles.put(name, Rotation.of(leg));
            }
        } finally { saved.forEach((box, rotation) -> rotation.apply(box)); }
        return angles;
    }

    /** Distal end of the farthest solid box in a leg's hierarchy, in the model's frame (blocks). */
    private record Foot(Vector3f centre, float halfWidth) {}
    private static Foot foot(AdvancedModelBox leg) {
        Vector3f pivot = matrix(leg).last().pose().transformPosition(new Vector3f());
        return distal(leg, pivot, new Foot(new Vector3f(pivot), 0));
    }

    private static Foot distal(AdvancedModelBox box, Vector3f pivot, Foot end) {
        var matrix = matrix(box).last().pose();
        for (var cube : box.cubeList) {
            if (cube.posX1 == cube.posX2 || cube.posY1 == cube.posY2 || cube.posZ1 == cube.posZ2) continue;
            Vector3f local = new Vector3f((cube.posX1 + cube.posX2) / 32F,
                    (cube.posY1 + cube.posY2) / 32F, (cube.posZ1 + cube.posZ2) / 32F);
            float w = cube.posX2 - cube.posX1, h = cube.posY2 - cube.posY1, d = cube.posZ2 - cube.posZ1;
            Vector3f offset = h >= w && h >= d ? new Vector3f(0, h / 32F, 0)
                    : d >= w ? new Vector3f(0, 0, d / 32F) : new Vector3f(w / 32F, 0, 0);
            // Aim with the distal end, not the shaft's centre (which would cross the feet past each other).
            Vector3f a = matrix.transformPosition(new Vector3f(local).add(offset));
            Vector3f b = matrix.transformPosition(new Vector3f(local).sub(offset));
            Vector3f c = a.distanceSquared(pivot) > b.distanceSquared(pivot) ? a : b;
            if (c.distanceSquared(pivot) <= end.centre.distanceSquared(pivot)) continue;
            float halfWidth = 0;
            for (int i = 0; i < 8; i++) {
                Vector3f v = matrix.transformPosition(new Vector3f((i % 2 == 0 ? cube.posX1 : cube.posX2) / 16F,
                        ((i & 2) == 0 ? cube.posY1 : cube.posY2) / 16F, ((i & 4) == 0 ? cube.posZ1 : cube.posZ2) / 16F));
                halfWidth = Math.max(halfWidth, Math.abs(v.x - c.x));
            }
            end = new Foot(c, halfWidth);
        }
        if (box.childModels != null) for (var child : box.childModels)
            if (child instanceof AdvancedModelBox b && b.showModel) end = distal(b, pivot, end);
        return end;
    }

    private static PoseStack matrix(AdvancedModelBox box) {
        PoseStack pose;
        if (box.getParent() == null) pose = new PoseStack();
        else {
            AdvancedModelBox parent = box.getParent();
            pose = matrix(parent);
            if (!parent.scaleChildren) pose.scale(1 / parent.scaleX, 1 / parent.scaleY, 1 / parent.scaleZ);
        }
        box.translateAndRotate(pose);
        return pose;
    }

    private record Capture(Mesh mesh) implements MultiBufferSource {
        @Override public VertexConsumer getBuffer(RenderType type) { return mesh; }
    }

    /** Prefer the hocks, mantle, tail stock or shell over decorative fins, wings and antennae. */
    static void captureAnchor(CarcassModels.Shape shape, PoseProfile profile, PoseStack pose, MultiBufferSource buffers) {
        if (!(buffers instanceof Capture capture)) return;
        List<Bounds> bounds = new ArrayList<>();
        for (String name : profile.hangingLegs) {
            var part = shape.parts().get(name);
            if (part != null && part.raw() instanceof AdvancedModelBox box && visible(box))
                bounds(box, pose, bounds, true);
        }
        if (bounds.isEmpty()) for (String name : profile.hangingAttachment) {
            var part = shape.parts().get(name);
            if (part == null || !(part.raw() instanceof AdvancedModelBox box) || !visible(box)) continue;
            bounds(box, pose, bounds, false);
            if (!bounds.isEmpty()) break;
        }
        // Multipart bodies are captured in order; their tail is the uppermost remaining section.
        capture.mesh.anatomy.addAll(bounds);
    }

    private static boolean visible(AdvancedModelBox box) {
        for (AdvancedModelBox b = box; b != null; b = b.getParent()) if (!b.showModel) return false;
        return true;
    }

    private static void bounds(AdvancedModelBox box, PoseStack pose, List<Bounds> bounds, boolean children) {
        Matrix4f transform = new Matrix4f(pose.last().pose()).mul(matrix(box).last().pose());
        for (var cube : box.cubeList) {
            if (cube.posX1 == cube.posX2 || cube.posY1 == cube.posY2 || cube.posZ1 == cube.posZ2) continue;
            Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);
            for (int i = 0; i < 8; i++) {
                Vector3f v = transform.transformPosition(new Vector3f((i % 2 == 0 ? cube.posX1 : cube.posX2) / 16F,
                        ((i & 2) == 0 ? cube.posY1 : cube.posY2) / 16F, ((i & 4) == 0 ? cube.posZ1 : cube.posZ2) / 16F));
                min.min(v); max.max(v);
            }
            bounds.add(new Bounds(min.sub(.002F, .002F, .002F), max.add(.002F, .002F, .002F)));
        }
        if (children && box.childModels != null) for (var child : box.childModels)
            if (child instanceof AdvancedModelBox b && b.showModel) bounds(b, pose, bounds, true);
    }

    private record Bounds(Vector3f min, Vector3f max) {
        boolean contains(Vector3f v) {
            return v.x >= min.x && v.x <= max.x && v.y >= min.y && v.y <= max.y && v.z >= min.z && v.z <= max.z;
        }
    }

    /** Butchery 5.2's hook bowl, after its -22.5-degree model rotation, or the occupied rope's bottom knot. */
    static Vector3f support(BlockState state) {
        int variant = 0;
        for (var property : state.getProperties()) if (property.getName().equals("blockstate"))
            variant = ((Number) state.getValue(property)).intValue();
        boolean rope = state.is(ButcheryHooks.rope());
        float y = rope ? (variant == 0 ? .21875F : 0F) : variant == 0 ? .06224F : .08724F;
        float z = rope ? 0 : variant == 0 ? .03079F : .04329F;
        Direction facing = state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        Vector3f point = new Vector3f(0, y, z).rotateY((float) Math.toRadians(180 - facing.toYRot()));
        return point.add(.5F, 1, .5F);
    }

    /** Use points on emitted surfaces, never the empty centre between two legs or inside a rib cage. */
    private static final class Mesh implements VertexConsumer {
        private final List<Vector3f> vertices = new ArrayList<>();
        private final List<Bounds> anatomy = new ArrayList<>();
        private boolean atAttachment(Vector3f v) {
            return anatomy.isEmpty() || anatomy.stream().anyMatch(b -> b.contains(v));
        }

        Layout seat(Map<String, Rotation> joints) {
            if (vertices.isEmpty()) throw new IllegalStateException("Empty hanging carcass mesh");
            Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);
            for (var v : vertices) if (atAttachment(v)) { min.min(v); max.max(v); }
            if (min.y > max.y) {
                // A late cut can remove every preferred part; re-seat the actual remaining tissue or bone.
                anatomy.clear();
                for (var v : vertices) { min.min(v); max.max(v); }
            }
            float y = max.y - Math.min(.065F, (max.y - min.y) * .12F);
            Vector3f target = new Vector3f((min.x + max.x) * .5F, y, (min.z + max.z) * .5F);
            Vector3f anchor = null;
            float best = Float.MAX_VALUE;
            for (int i = 0; i + 3 < vertices.size(); i += 4) {
                List<Vector3f> slice = new ArrayList<>(4);
                for (int j = 0; j < 4; j++) {
                    Vector3f a = vertices.get(i + j), b = vertices.get(i + (j + 1) % 4);
                    if (!atAttachment(a) || !atAttachment(b) || Math.abs(a.y - b.y) < .000001F) continue;
                    float t = (y - a.y) / (b.y - a.y);
                    if (t >= 0 && t <= 1) slice.add(new Vector3f(a).lerp(b, t));
                }
                for (int j = 0; j + 1 < slice.size(); j += 2) {
                    Vector3f a = slice.get(j), delta = new Vector3f(slice.get(j + 1)).sub(a);
                    float t = delta.lengthSquared() < .000001F ? 0 : new Vector3f(target).sub(a).dot(delta) / delta.lengthSquared();
                    Vector3f candidate = new Vector3f(a).add(delta.mul(Math.max(0, Math.min(1, t))));
                    float score = candidate.distanceSquared(target);
                    if (score < best) { anchor = candidate; best = score; }
                }
            }
            if (anchor == null) {
                target.y = max.y;
                for (var v : vertices) if (atAttachment(v) && v.y >= max.y - .001F && v.distanceSquared(target) < best) {
                    anchor = new Vector3f(v); best = v.distanceSquared(target);
                }
            }
            if (anchor == null) throw new IllegalStateException("No solid hook attachment");
            // Area weighting avoids bias toward the more detailed head, toes, or a subdivided tentacle.
            Vector3f centre = new Vector3f();
            float area = 0;
            for (int i = 0; i + 3 < vertices.size(); i += 4) {
                Vector3f a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2), d = vertices.get(i + 3);
                float weight = new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a)).length();
                centre.add(new Vector3f(a).add(b).add(c).add(d).mul(weight * .25F));
                area += weight;
            }
            Quaternionf balance = new Quaternionf();
            if (area > .000001F) {
                Vector3f up = new Vector3f(anchor).sub(centre.div(area));
                if (up.lengthSquared() > .000001F) balance.rotationTo(up.normalize(), new Vector3f(0, 1, 0));
            }
            return new Layout(Map.copyOf(joints), anchor, balance);
        }
        public VertexConsumer vertex(double x, double y, double z) { vertices.add(new Vector3f((float) x, (float) y, (float) z)); return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() {}
        public void defaultColor(int r, int g, int b, int a) {}
        public void unsetDefaultColor() {}
    }
    private HangingPose() {}
}
