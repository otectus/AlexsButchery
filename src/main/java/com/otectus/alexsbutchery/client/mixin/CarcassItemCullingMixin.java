package com.otectus.alexsbutchery.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.alexsbutchery.client.render.CarcassModels;
import com.otectus.alexsbutchery.client.render.CarcassPresentation;
import com.otectus.alexsbutchery.client.render.CarcassVisualState;
import com.otectus.alexsbutchery.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.ForgeHooksClient;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;

/** Presentation-only frustum envelope for enlarged drops; collision, merge and pickup boxes remain vanilla. */
@Mixin(EntityRenderer.class)
public abstract class CarcassItemCullingMixin {
    /** Identity keys match the bounded immutable visual cache and the selected baked-model instance. */
    @Unique private static final Map<CarcassVisualState, Map<BakedModel, AABB>> alexsbutchery$envelopes =
            new IdentityHashMap<>();
    @Unique private static double alexsbutchery$groundMaxSpan = Double.NaN;

    static {
        CarcassModels.addClearListener(alexsbutchery$envelopes::clear);
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void alexsbutchery$itemEnvelope(Entity entity, Frustum frustum, double x, double y, double z,
                                          CallbackInfoReturnable<Boolean> callback) {
        if (!((Object) this instanceof ItemEntityRenderer) || !(entity instanceof ItemEntity item)) return;
        var visual = CarcassVisualState.from(item.getItem());
        if (visual == null) return;
        double groundMaxSpan = ClientConfig.GROUND_ITEM_MAX_SPAN.get();
        if (Double.compare(groundMaxSpan, alexsbutchery$groundMaxSpan) != 0) {
            alexsbutchery$envelopes.clear();
            alexsbutchery$groundMaxSpan = groundMaxSpan;
        }
        AABB relative;
        try {
            BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(
                    item.getItem(), item.level(), null, item.getId());
            Map<BakedModel, AABB> models = alexsbutchery$envelopes.get(visual);
            if (models == null) {
                if (alexsbutchery$envelopes.size() >= 256)
                    alexsbutchery$envelopes.remove(alexsbutchery$envelopes.keySet().iterator().next());
                models = new IdentityHashMap<>();
                alexsbutchery$envelopes.put(visual, models);
            }
            relative = models.get(model);
            if (relative == null) {
                if (models.size() >= 8) models.clear();
                relative = alexsbutchery$envelope(visual, model);
                models.put(model, relative);
            }
        }
        catch (RuntimeException failure) { return; } // The item renderer reports the resource/model failure once.

        double entitySize = entity.getBoundingBox().getSize();
        if (Double.isNaN(entitySize)) entitySize = 1;
        // Include displacement as well as span: a resource model may translate the mesh away from the entity origin.
        double visualSize = relative.minmax(new AABB(0, 0, 0, 0, 0, 0)).getSize();
        if (Double.isNaN(visualSize)) visualSize = 1;
        double size = Math.max(entitySize, visualSize);
        double range = size * 64 * Entity.getViewScale();
        double dx = entity.getX() - x, dy = entity.getY() - y, dz = entity.getZ() - z;
        if (dx * dx + dy * dy + dz * dz >= range * range) {
            callback.setReturnValue(false);
            return;
        }
        if (entity.noCulling) {
            callback.setReturnValue(true);
            return;
        }
        callback.setReturnValue(frustum.isVisible(relative.move(entity.getX(), entity.getY(), entity.getZ())));
    }

    @Unique
    private static AABB alexsbutchery$envelope(CarcassVisualState visual, BakedModel model) {
        AABB local = CarcassPresentation.groundEnvelope(visual);
        PoseStack transform = new PoseStack();
        ForgeHooksClient.handleCameraTransforms(transform, model, ItemDisplayContext.GROUND, false);
        // renderItem cancels ItemRenderer's -0.5 translation, then cancels ItemEntityRenderer's quarter-scale lift.
        transform.translate(0, -.25, 0);
        Matrix4f matrix = transform.last().pose();
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, radius = 0;
        for (int corner = 0; corner < 8; corner++) {
            Vector3f point = matrix.transformPosition(new Vector3f(
                    (float) ((corner & 1) == 0 ? local.minX : local.maxX),
                    (float) ((corner & 2) == 0 ? local.minY : local.maxY),
                    (float) ((corner & 4) == 0 ? local.minZ : local.maxZ)));
            if (!Float.isFinite(point.x) || !Float.isFinite(point.y) || !Float.isFinite(point.z))
                throw new IllegalStateException("Nonfinite carcass item transform");
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
            radius = Math.max(radius, Math.hypot(point.x, point.z));
        }
        ItemTransforms transforms = model.getTransforms();
        float groundScaleY = transforms.getTransform(ItemDisplayContext.GROUND).scale.y();
        if (!Float.isFinite(groundScaleY)) throw new IllegalStateException("Nonfinite carcass ground scale");
        double lift = .25 * groundScaleY;
        // Spin is outside the baked transform. The slack encloses bobbing and every vanilla stack-copy spread mode.
        radius += .5;
        return new AABB(-radius, minY + lift - .5, -radius,
                radius, maxY + lift + .75, radius);
    }
}
