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
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the named parts of an entity model so stages can hide them (head removed, limbs cut) or draw one alone (a
 * trophy head). Alex's Mobs models are Citadel {@link AdvancedEntityModel}s whose parts are public fields with
 * descriptive names; vanilla-style models expose {@link ModelPart} fields. Both are wrapped behind one interface.
 */
public final class ModelParts {

    /** Borrowed living models must be restored even when a render buffer throws halfway through. */
    public static Runnable savePose(CarcassModels.Shape shape) {
        List<Runnable> restore = new ArrayList<>();
        for (Part part : shape.parts().values()) {
            if (part.raw() instanceof AdvancedModelBox box) {
                var angles = box.getModelAngleCopy();
                float x = box.scaleX, y = box.scaleY, z = box.scaleZ;
                boolean visible = box.showModel, children = box.scaleChildren;
                restore.add(() -> {
                    box.copyModelAngles(angles);
                    box.setScale(x, y, z);
                    box.showModel = visible;
                    box.scaleChildren = children;
                });
            } else if (part.raw() instanceof ModelPart box) {
                var pose = box.storePose();
                boolean visible = box.visible;
                restore.add(() -> { box.loadPose(pose); box.visible = visible; });
            }
        }
        var model = shape.model();
        boolean young = model.young, riding = model.riding;
        float attack = model.attackTime;
        return () -> {
            restore.forEach(Runnable::run);
            model.young = young; model.riding = riding; model.attackTime = attack;
        };
    }

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
     * measurement includes pivots, rotations and scales throughout its rendered child hierarchy.
     */
    public static float[] centre(Part part) {
        ModelBounds mesh = new ModelBounds();
        part.render(new PoseStack(), mesh, 15728880, 0);
        var b = mesh.bounds();
        return new float[]{(float) (b.minX + b.maxX) * 8, (float) (b.minY + b.maxY) * 8,
                (float) (b.minZ + b.maxZ) * 8, (float) b.getXsize() * 16,
                (float) b.getYsize() * 16, (float) b.getZsize() * 16};
    }

    /** Solid anatomy for seating a rug head: a beard/whisker plane must not hold the skull in the air. */
    public static void solidBounds(AdvancedModelBox box, PoseStack pose, ModelBounds mesh) {
        if (!box.showModel) return;
        pose.pushPose();
        try {
            box.translateAndRotate(pose);
            for (var cube : box.cubeList) {
                if (cube.posX1 == cube.posX2 || cube.posY1 == cube.posY2 || cube.posZ1 == cube.posZ2) continue;
                for (int i = 0; i < 8; i++) {
                    Vector3f v = pose.last().pose().transformPosition(new Vector3f(
                            (i % 2 == 0 ? cube.posX1 : cube.posX2) / 16F,
                            ((i & 2) == 0 ? cube.posY1 : cube.posY2) / 16F,
                            ((i & 4) == 0 ? cube.posZ1 : cube.posZ2) / 16F));
                    mesh.vertex(v.x, v.y, v.z);
                }
            }
            if (!box.scaleChildren) pose.scale(1 / box.scaleX, 1 / box.scaleY, 1 / box.scaleZ);
            for (var child : box.childModels) if (child instanceof AdvancedModelBox advanced) solidBounds(advanced, pose, mesh);
        } finally { pose.popPose(); }
    }

    private ModelParts() {}
}
