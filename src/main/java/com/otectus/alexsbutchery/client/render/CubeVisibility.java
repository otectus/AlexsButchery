package com.otectus.alexsbutchery.client.render;

import com.github.alexthe666.citadel.client.model.TabulaModelRenderUtils.ModelBox;
import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.mojang.blaze3d.platform.NativeImage;
import com.otectus.alexsbutchery.AlexsButchery;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.model.EntityModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Citadel hides alternate tusks/antlers with entirely transparent UV regions, not showModel flags. */
final class CubeVisibility {
    private record Key(EntityModel<?> model, ResourceLocation texture) {}
    private static final Map<Key, Map<AdvancedModelBox, ObjectList<ModelBox>>> CACHE = new LinkedHashMap<>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Map<AdvancedModelBox, ObjectList<ModelBox>>> entry) {
            return size() > 512;
        }
    };

    static void clear() { CACHE.clear(); }

    /**
     * Draw only donor cubes whose texture has visible pixels. Ground contact, hanging balance and the bounds
     * exporter use this same draw path. Borrowed lists are always restored, including when a measurement throws.
     */
    static void render(CarcassModels.Shape shape, Runnable draw) {
        var visible = CACHE.computeIfAbsent(new Key(shape.model(), shape.texture()), key -> {
            Map<AdvancedModelBox, ObjectList<ModelBox>> filtered = new IdentityHashMap<>();
            try (NativeImage pixels = StageTextures.read(shape.texture())) {
                for (var part : shape.parts().values()) if (part.raw() instanceof AdvancedModelBox box) {
                    ObjectList<ModelBox> cubes = new ObjectArrayList<>();
                    for (ModelBox cube : box.cubeList) if (visible(cube, pixels)) cubes.add(cube);
                    if (cubes.size() != box.cubeList.size()) filtered.put(box, cubes);
                }
            } catch (IOException e) {
                AlexsButchery.LOGGER.warn("Cannot inspect carcass texture {}; leaving donor cubes intact", shape.texture(), e);
                return Map.of();
            }
            return filtered;
        });
        if (visible.isEmpty()) {
            draw.run();
            return;
        }
        Map<AdvancedModelBox, ObjectList<ModelBox>> saved = new IdentityHashMap<>();
        visible.forEach((box, cubes) -> saved.put(box, box.cubeList));
        try {
            visible.forEach((box, cubes) -> box.cubeList = cubes);
            draw.run();
        } finally { saved.forEach((box, cubes) -> box.cubeList = cubes); }
    }

    // Citadel exposes ModelBox.quads but its nested quad/vertex types are package-private.
    private static final Class<?> QUAD_TYPE = field(ModelBox.class.getDeclaredFields(), "quads").getType().getComponentType();
    private static final Field QUAD_VERTICES = accessibleField(QUAD_TYPE, "vertexPositions");
    private static final Field U = accessibleField(QUAD_VERTICES.getType().getComponentType(), "textureU");
    private static final Field V = accessibleField(QUAD_VERTICES.getType().getComponentType(), "textureV");
    private static final Field POSITION = accessibleField(QUAD_VERTICES.getType().getComponentType(), "position");

    /** Actual corners include the donor's cube inflation; posX1..posZ2 describe the uninflated box. */
    static void measure(ModelBox cube, com.mojang.blaze3d.vertex.PoseStack pose, ModelBounds mesh) {
        try {
            for (Object quad : cube.quads) for (Object vertex : (Object[]) QUAD_VERTICES.get(quad)) {
                var point = new org.joml.Vector3f((org.joml.Vector3f) POSITION.get(vertex)).div(16F);
                pose.last().pose().transformPosition(point);
                mesh.vertex(point.x, point.y, point.z);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read Citadel vertices", e);
        }
    }

    private static Field field(Field[] fields, String name) {
        for (Field f : fields) if (f.getName().equals(name)) return f;
        throw new IllegalStateException("Missing Citadel field " + name);
    }

    private static Field accessibleField(Class<?> type, String name) {
        Field f = field(type.getDeclaredFields(), name);
        f.setAccessible(true);
        return f;
    }

    static boolean visible(ModelBox cube, NativeImage pixels) {
        try {
            for (Object quad : cube.quads) {
                float minU = Float.POSITIVE_INFINITY, minV = minU, maxU = Float.NEGATIVE_INFINITY, maxV = maxU;
                for (Object vertex : (Object[]) QUAD_VERTICES.get(quad)) {
                    float u = U.getFloat(vertex), v = V.getFloat(vertex);
                    minU = Math.min(minU, u); maxU = Math.max(maxU, u);
                    minV = Math.min(minV, v); maxV = Math.max(maxV, v);
                }
                int x0 = (int) Math.floor(minU * pixels.getWidth()), x1 = (int) Math.ceil(maxU * pixels.getWidth());
                int y0 = (int) Math.floor(minV * pixels.getHeight()), y1 = (int) Math.ceil(maxV * pixels.getHeight());
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                    if (FastColor.ABGR32.alpha(pixels.getPixelRGBA(Math.floorMod(x, pixels.getWidth()), Math.floorMod(y, pixels.getHeight()))) > 25) return true;
                }
            }
            return false;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read Citadel UVs", e);
        }
    }
    private CubeVisibility() {}
}
