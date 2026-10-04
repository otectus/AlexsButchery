package com.otectus.alexsbutchery.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Measures the vertices the renderer actually emits, including child rotations and model scale. */
public class ModelBounds implements VertexConsumer {
    private AABB bounds;
    private AABB cube;
    private int count;
    private final List<AABB> cubes = new ArrayList<>();

    public AABB bounds() { return bounds == null ? new AABB(0, 0, 0, 0, 0, 0) : bounds; }

    /** Citadel and our stage meshes emit six quads per box. Keep separate boxes for necks, limbs and tails. */
    public List<AABB> cubes() {
        List<AABB> result = new ArrayList<>(cubes);
        if (cube != null) result.add(cube);
        return result;
    }

    @Override public VertexConsumer vertex(double x, double y, double z) {
        AABB point = new AABB(x, y, z, x, y, z);
        bounds = bounds == null ? point : bounds.minmax(point);
        cube = cube == null ? point : cube.minmax(point);
        if (++count % 24 == 0) { cubes.add(cube); cube = null; }
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
