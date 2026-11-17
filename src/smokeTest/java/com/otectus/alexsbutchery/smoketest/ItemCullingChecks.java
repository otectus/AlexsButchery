package com.otectus.alexsbutchery.smoketest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.alexsbutchery.client.render.CarcassVisualState;
import com.otectus.alexsbutchery.client.render.ModelBounds;
import com.otectus.alexsbutchery.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.model.BakedModelWrapper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;

/** Compare the installed culling hook with actual emitted item vertices, including a resource-model transform. */
final class ItemCullingChecks {
    static void run(Map<String, Object> checks) {
        Minecraft mc = Minecraft.getInstance();
        double originalCap = ClientConfig.GROUND_ITEM_MAX_SPAN.get();
        try {
            ClientConfig.GROUND_ITEM_MAX_SPAN.set(0D);
            var stack = ItemPresentationChecks.stack("elephant", new CompoundTag());
            var item = new ItemEntity(mc.level, 0, 0, 0, stack);
            var collision = item.getBoundingBox();
            var renderer = mc.getEntityRenderDispatcher().getRenderer(item);
            CapturingFrustum frustum = new CapturingFrustum();
            checks.put("item_culling_uses_visual_distance", !item.shouldRenderAtSqrDistance(100 * 100)
                    && renderer.shouldRender(item, frustum, 100, 0, 0));
            ModelBounds mesh = new ModelBounds();
            renderer.render(item, 0, .5F, new PoseStack(), type -> mesh, 15728880);
            AABB full = frustum.captured;
            checks.put("item_culling_encloses_actual_drop", encloses(full, mesh.bounds()));
            checks.put("item_culling_preserves_collision", collision.equals(item.getBoundingBox()));
            ClientConfig.GROUND_ITEM_MAX_SPAN.set(1D);
            renderer.shouldRender(item, frustum, 0, 0, 0);
            checks.put("item_culling_refreshes_live_size_cap", frustum.captured.getSize() < full.getSize());
            ClientConfig.GROUND_ITEM_MAX_SPAN.set(0D);

            ItemTransform ground = new ItemTransform(new Vector3f(27, 20, 18), new Vector3f(4, 1, -2),
                    new Vector3f(2.5F, .7F, 1.8F));
            ItemTransform none = ItemTransform.NO_TRANSFORM;
            ItemTransforms transforms = new ItemTransforms(none, none, none, none, none, none, ground, none);
            BakedModel original = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
            BakedModel custom = new BakedModelWrapper<>(original) {
                @Override public ItemTransforms getTransforms() { return transforms; }
                @Override public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean left) {
                    transforms.getTransform(context).apply(left, pose);
                    return this;
                }
            };
            var method = EntityRenderer.class.getDeclaredMethod("alexsbutchery$envelope", CarcassVisualState.class, BakedModel.class);
            method.setAccessible(true);
            AABB envelope = (AABB) method.invoke(null, CarcassVisualState.from(stack), custom);
            boolean contained = true;
            for (int yaw = 0; yaw < 360; yaw += 45) {
                ModelBounds transformed = new ModelBounds();
                PoseStack pose = new PoseStack();
                pose.translate(0, .25 * ground.scale.y + .2, 0);
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                mc.getItemRenderer().render(stack, ItemDisplayContext.GROUND, false, pose, type -> transformed,
                        15728880, 0, custom);
                contained &= encloses(envelope, transformed.bounds());
            }
            checks.put("item_culling_encloses_resource_transform_and_spin", contained);
        } catch (ReflectiveOperationException | RuntimeException error) {
            checks.put("item_culling_exception", error.toString());
            checks.put("item_culling_checks_passed", false);
        } finally { ClientConfig.GROUND_ITEM_MAX_SPAN.set(originalCap); }
    }

    private static boolean encloses(AABB outer, AABB inner) {
        return outer != null && outer.inflate(.002).contains(inner.minX, inner.minY, inner.minZ)
                && outer.inflate(.002).contains(inner.maxX, inner.maxY, inner.maxZ);
    }

    private static final class CapturingFrustum extends Frustum {
        AABB captured;
        CapturingFrustum() { super(new Matrix4f(), new Matrix4f()); }
        @Override public boolean isVisible(AABB bounds) { captured = bounds; return true; }
    }
    private ItemCullingChecks() {}
}
