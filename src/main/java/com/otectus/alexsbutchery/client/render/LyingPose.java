package com.otectus.alexsbutchery.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.otectus.alexsbutchery.client.pose.PoseProfile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Measures the posed remaining solid anatomy of a lying carcass once per {@link PoseKey}, so the renderer and the
 * bounds export ground it with one explicit translation: its lowest solid point onto the floor.
 */
public final class LyingPose {
    /**
     * The ungrounded pose's lowest solid point and the top of everything drawn, in blocks, after {@code lifts}: for a
     * multipart body, how far each part (0 the head model, then each segment) moves down to rest on the ground itself.
     */
    public record Layout(float floor, float top, float[] lifts) {
        public float height() { return top - floor; }
    }

    // Galleries and variants produce many layouts. Bounded; CarcassModels clears it on reload, logout and world change.
    private static final Map<PoseKey, Layout> CACHE = new LinkedHashMap<>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<PoseKey, Layout> entry) { return size() > 512; }
    };
    private static final Map<PoseProfile, Map<String, HangingPose.Rotation>> JOINTS = new IdentityHashMap<>();

    static void clear() {
        CACHE.clear();
        JOINTS.clear();
    }

    /**
     * {@code draw} poses and draws the body part by part, leaving out the named parts. The profile's
     * {@code lying.ignore} parts (an ear, a fin, a flipper) are left out of the floor measurement only, so the body
     * rests on the ground and they may dip into it; the top is measured with everything.
     */
    static Layout get(CarcassScene.Subject subject, CarcassModels.Shape shape, int parts,
                      BiConsumer<Set<String>, IntFunction<MultiBufferSource>> draw) {
        PoseKey key = PoseKey.of(subject, shape);
        Layout cached = CACHE.get(key);
        if (cached != null) return cached;
        Set<String> ignore = subject.profile().lyingIgnore;
        Layout layout = measure(parts, all -> draw.accept(Set.of(), all));
        if (!ignore.isEmpty()) {
            Layout support = measure(parts, some -> draw.accept(ignore, some));
            layout = new Layout(support.floor(), Math.max(support.floor(), layout.top()), support.lifts());
        }
        CACHE.put(key, layout);
        return layout;
    }

    /** The profile's {@code lying.rotations}: absolute joint angles that lay limbs down instead of propping the body up. */
    static Map<String, HangingPose.Rotation> joints(PoseProfile profile) {
        return JOINTS.computeIfAbsent(profile, p -> {
            Map<String, HangingPose.Rotation> joints = new LinkedHashMap<>();
            p.lyingRotations.forEach((name, degrees) -> joints.put(name, new HangingPose.Rotation((float) Math.toRadians(degrees[0]),
                    (float) Math.toRadians(degrees[1]), (float) Math.toRadians(degrees[2]))));
            return Map.copyOf(joints);
        });
    }

    /** Measures one already-posed mesh. Public so the client smoke test can exercise numerical edge cases directly. */
    public static Layout measure(Consumer<MultiBufferSource> draw) {
        return measure(1, parts -> draw.accept(parts.apply(0)));
    }

    /** Measures an already-posed body drawn part by part; every part that drew anything is lowered onto the floor. */
    static Layout measure(int parts, Consumer<IntFunction<MultiBufferSource>> draw) {
        SolidMesh[] meshes = new SolidMesh[parts];
        for (int i = 0; i < parts; i++) meshes[i] = new SolidMesh();
        draw.accept(part -> new Capture(meshes[part]));
        float floor = Float.POSITIVE_INFINITY;
        for (SolidMesh mesh : meshes) if (mesh.drew()) floor = Math.min(floor, mesh.floor());
        if (!Float.isFinite(floor)) return new Layout(0F, 0F, new float[parts]);
        float[] lifts = new float[parts];
        float top = floor;
        for (int i = 0; i < parts; i++) if (meshes[i].drew()) {
            lifts[i] = floor - meshes[i].floor();
            top = Math.max(top, meshes[i].renderedMaxY + lifts[i]);
        }
        return new Layout(floor, top, lifts);
    }

    private record Capture(SolidMesh mesh) implements MultiBufferSource {
        @Override public VertexConsumer getBuffer(RenderType type) { return mesh; }
    }

    /**
     * Entity model boxes emit six quads (24 vertices). A decorative fur, fin or whisker plane remains coplanar after
     * every pose transform, while a solid box has four non-coplanar corners. Only the latter may support a carcass.
     */
    private static final class SolidMesh implements VertexConsumer {
        private final List<Vector3f> box = new ArrayList<>(24);
        private float solidMinY = Float.POSITIVE_INFINITY;
        private float renderedMinY = Float.POSITIVE_INFINITY;
        private float renderedMaxY = Float.NEGATIVE_INFINITY;

        boolean drew() {
            return Float.isFinite(renderedMinY);
        }

        float floor() {
            // All current donor and generated stage models have solid boxes. The fallback keeps an unusual third-party
            // model visible if it consists solely of planes rather than aborting the block-entity render.
            return Float.isFinite(solidMinY) ? solidMinY : renderedMinY;
        }

        private void finishBox() {
            Vector3f origin = box.get(0);
            Vector3f axis = null;
            float axisLength = 0F;
            float coordinate = 1F;
            for (Vector3f vertex : box) {
                coordinate = Math.max(coordinate, Math.max(Math.abs(vertex.x), Math.max(Math.abs(vertex.y), Math.abs(vertex.z))));
                Vector3f candidate = new Vector3f(vertex).sub(origin);
                if (candidate.lengthSquared() > axisLength) {
                    axis = candidate;
                    axisLength = candidate.lengthSquared();
                }
            }
            Vector3f normal = null;
            float normalLength = 0F;
            if (axis != null) for (int i = 1; i < box.size(); i++) {
                Vector3f candidate = new Vector3f(axis).cross(new Vector3f(box.get(i)).sub(origin));
                if (candidate.lengthSquared() > normalLength) {
                    normal = candidate;
                    normalLength = candidate.lengthSquared();
                }
            }
            if (normal != null && normalLength > 1.0E-16F) {
                normal.div((float) Math.sqrt(normalLength));
                float thickness = 0F;
                for (int i = 1; i < box.size(); i++)
                    thickness = Math.max(thickness, Math.abs(normal.dot(new Vector3f(box.get(i)).sub(origin))));
                // Rotation and translation of a float mesh can move a mathematically coplanar corner by a few ulps.
                // Normalize the plane first, then compare distance with a coordinate-relative tolerance. Current donor
                // anatomy is orders of magnitude thicker than this (generated tissue/bone starts at .12 model pixels).
                float tolerance = Math.max(1.0E-6F, Math.ulp(coordinate) * 32F);
                if (thickness > tolerance) for (Vector3f vertex : box) solidMinY = Math.min(solidMinY, vertex.y);
            }
            box.clear();
        }

        @Override public VertexConsumer vertex(double x, double y, double z) {
            Vector3f vertex = new Vector3f((float) x, (float) y, (float) z);
            renderedMinY = Math.min(renderedMinY, vertex.y);
            renderedMaxY = Math.max(renderedMaxY, vertex.y);
            box.add(vertex);
            if (box.size() == 24) finishBox();
            return this;
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

    private LyingPose() {}
}
