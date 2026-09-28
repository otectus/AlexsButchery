package com.otectus.alexsbutchery.client.render;

import com.github.alexthe666.citadel.client.model.AdvancedEntityModel;
import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.TabulaModelRenderUtils;
import com.github.alexthe666.citadel.client.model.basic.BasicModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Finds the named parts of an entity model so stages can hide them (head removed, limbs cut) or draw one alone (a
 * trophy head). Alex's Mobs models are Citadel {@link AdvancedEntityModel}s whose parts are public fields with
 * descriptive names; vanilla-style models expose {@link ModelPart} fields. Both are wrapped behind one interface.
 */
public final class ModelParts {

    /** A model part that can be shown, hidden or drawn on its own. */
    public interface Part {
        boolean visible();

        void setVisible(boolean visible);

        /** Draws this part and its children with its own rotation and pivot, nothing of its parents. */
        void render(PoseStack pose, VertexConsumer consumer, int light, int overlay);

        /** Pivot in model pixels, relative to the parent part. */
        float[] pivot();

        /** The wrapped model object, for identity lookups. */
        Object raw();

        /** Rotation angles in radians (applied Z, then Y, then X, as the part renders). */
        float[] rotation();

        /**
         * The box of this part's geometry and its visible children's, in the part's own frame, in pixels:
         * {@code {minX, minY, minZ, maxX, maxY, maxZ}}; children's own rotations are ignored. Null when unknown.
         */
        @Nullable
        default float[] bounds() {
            return null;
        }
    }

    private record BasicPart(BasicModelPart part) implements Part {
        @Override
        public boolean visible() {
            return part.showModel;
        }

        @Override
        public void setVisible(boolean visible) {
            part.showModel = visible;
        }

        @Override
        public void render(PoseStack pose, VertexConsumer consumer, int light, int overlay) {
            part.render(pose, consumer, light, overlay);
        }

        @Override
        public float[] pivot() {
            return new float[]{part.rotationPointX, part.rotationPointY, part.rotationPointZ};
        }

        @Override
        public Object raw() {
            return part;
        }

        @Override
        public float[] rotation() {
            return new float[]{part.rotateAngleX, part.rotateAngleY, part.rotateAngleZ};
        }

        @Nullable
        @Override
        public float[] bounds() {
            if (!(part instanceof AdvancedModelBox box)) return null;
            float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            collectBounds(box, 0F, 0F, 0F, b, 0);
            return b[0] > b[3] ? null : b;
        }

        private static void collectBounds(AdvancedModelBox box, float ox, float oy, float oz, float[] b, int depth) {
            if (box.cubeList != null) {
                for (TabulaModelRenderUtils.ModelBox cube : box.cubeList) {
                    b[0] = Math.min(b[0], ox + Math.min(cube.posX1, cube.posX2));
                    b[1] = Math.min(b[1], oy + Math.min(cube.posY1, cube.posY2));
                    b[2] = Math.min(b[2], oz + Math.min(cube.posZ1, cube.posZ2));
                    b[3] = Math.max(b[3], ox + Math.max(cube.posX1, cube.posX2));
                    b[4] = Math.max(b[4], oy + Math.max(cube.posY1, cube.posY2));
                    b[5] = Math.max(b[5], oz + Math.max(cube.posZ1, cube.posZ2));
                }
            }
            if (box.childModels == null || depth > 16) return;
            for (BasicModelPart child : box.childModels) {
                if (child instanceof AdvancedModelBox c && c.showModel) {
                    collectBounds(c, ox + c.rotationPointX, oy + c.rotationPointY, oz + c.rotationPointZ, b, depth + 1);
                }
            }
        }
    }

    private record VanillaPart(ModelPart part) implements Part {
        @Override
        public boolean visible() {
            return part.visible;
        }

        @Override
        public void setVisible(boolean visible) {
            part.visible = visible;
        }

        @Override
        public void render(PoseStack pose, VertexConsumer consumer, int light, int overlay) {
            part.render(pose, consumer, light, overlay);
        }

        @Override
        public float[] pivot() {
            return new float[]{part.x, part.y, part.z};
        }

        @Override
        public Object raw() {
            return part;
        }

        @Override
        public float[] rotation() {
            return new float[]{part.xRot, part.yRot, part.zRot};
        }
    }

    public static Map<String, Part> collect(EntityModel<?> model) {
        Map<String, Part> parts = new LinkedHashMap<>();
        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                Class<?> type = field.getType();
                boolean basic = BasicModelPart.class.isAssignableFrom(type);
                boolean vanilla = ModelPart.class.isAssignableFrom(type);
                if (!basic && !vanilla) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (value instanceof BasicModelPart bp) parts.putIfAbsent(field.getName(), new BasicPart(bp));
                    else if (value instanceof ModelPart mp) parts.putIfAbsent(field.getName(), new VanillaPart(mp));
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Inaccessible field: that part simply cannot be hidden.
                }
            }
        }
        if (model instanceof AdvancedEntityModel<?> advanced) {
            for (AdvancedModelBox box : advanced.getAllParts()) {
                if (box != null && box.boxName != null) parts.putIfAbsent(box.boxName, new BasicPart(box));
            }
        }
        return parts;
    }

    /**
     * Where a part drawn on its own puts the middle of its geometry, in model pixels, and how big that geometry is:
     * {@code {cx, cy, cz, sizeX, sizeY, sizeZ}}. The part renders from its own pivot with its own rotation, so the
     * middle is the pivot plus the rotated centre of its box. Falls back to the bare pivot and a 8-pixel cube.
     */
    public static float[] centre(Part part) {
        float[] pivot = part.pivot();
        float[] b = part.bounds();
        if (b == null) return new float[]{pivot[0], pivot[1], pivot[2], 8F, 8F, 8F};
        float[] r = part.rotation();
        Vector3f c = new Vector3f((b[0] + b[3]) * 0.5F, (b[1] + b[4]) * 0.5F, (b[2] + b[5]) * 0.5F);
        // Rendering applies Z, then Y, then X to the pose, so a point is turned by X first.
        c.rotateX(r[0]).rotateY(r[1]).rotateZ(r[2]);
        return new float[]{pivot[0] + c.x, pivot[1] + c.y, pivot[2] + c.z, b[3] - b[0], b[4] - b[1], b[5] - b[2]};
    }

    private ModelParts() {}
}
