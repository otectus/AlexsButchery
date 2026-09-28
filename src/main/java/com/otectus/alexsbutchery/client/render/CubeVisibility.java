package com.otectus.alexsbutchery.client.render;

import com.github.alexthe666.citadel.client.model.TabulaModelRenderUtils.ModelBox;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.FastColor;

import java.lang.reflect.Field;

/** Citadel hides alternate tusks/antlers with entirely transparent UV regions, not showModel flags. */
final class CubeVisibility {
    // Citadel exposes ModelBox.quads but its nested quad/vertex types are package-private.
    private static final Class<?> QUAD_TYPE = field(ModelBox.class.getDeclaredFields(), "quads").getType().getComponentType();
    private static final Field QUAD_VERTICES = accessibleField(QUAD_TYPE, "vertexPositions");
    private static final Field U = accessibleField(QUAD_VERTICES.getType().getComponentType(), "textureU");
    private static final Field V = accessibleField(QUAD_VERTICES.getType().getComponentType(), "textureV");

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
