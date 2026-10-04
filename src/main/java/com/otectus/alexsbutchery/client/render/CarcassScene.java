package com.otectus.alexsbutchery.client.render;

import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.client.pose.PoseProfile;
import com.otectus.alexsbutchery.client.pose.SegmentChain;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The drawing routines shared by the block entity renderer and the item renderer. Callers position the block space
 * (0..1 on each axis); these place the mob's model in it according to its pose profile, hide the parts the reached
 * stages removed, trail a multipart mob's body segments behind its head, and restore the shared models afterwards.
 */
public final class CarcassScene {

    /** One mob block to draw: whose model, its profile and snapshot, the look and the stages already done. */
    public record Subject(MobDef def, CarcassModels.Handle handle, PoseProfile profile, CompoundTag mobData,
                          StageTextures.Look look, Set<Stages.Action> done) {}

    /** A carcass lying on its side (or on its belly, roll 0), centred on the block. */
    public static void renderLying(Subject s, Direction facing, PoseStack pose, MultiBufferSource buffers, int light) {
        CarcassModels.Shape shape = s.handle().shape(s.mobData());
        PoseProfile p = s.profile();
        float size = p.scale * shape.scale();
        float width = shape.width() * size;
        float height = shape.height() * size;
        double roll = Math.toRadians(p.lyingRoll);
        List<SegmentChain.Placement> chain = chain(s, false);
        float[] centre = centre(chain, p.scale);
        pose.pushPose();
        // On its side the body's half width is off the ground and its height lies across the block; on its belly neither.
        pose.translate(0.5 + p.lyingOffset[0], Math.abs(Math.sin(roll)) * width * 0.5 + p.lyingOffset[1], 0.5 + p.lyingOffset[2]);
        pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot() + p.lyingYaw));
        pose.translate(Math.sin(roll) * height * 0.5, 0, 0);
        pose.mulPose(Axis.XP.rotationDegrees(p.lyingPitch));
        pose.mulPose(Axis.ZP.rotationDegrees(p.lyingRoll));
        pose.translate(-centre[0], 0, -centre[1]);
        drawCreature(s, shape, size, chain, Map.of(), pose, buffers, light);
        pose.popPose();
    }

    /** Hanging geometry seats a real hock, tail or body surface directly on the support above. */
    public static void renderHanging(Subject s, Direction facing, PoseStack pose, MultiBufferSource buffers, int light) {
        renderHanging(s, facing, ButcheryHooks.hook().defaultBlockState(), pose, buffers, light);
    }

    public static void renderHanging(Subject s, Direction facing, BlockState support, PoseStack pose, MultiBufferSource buffers, int light) {
        CarcassModels.Shape shape = s.handle().shape(s.mobData());
        PoseProfile p = s.profile();
        float size = p.scale * shape.scale();
        List<SegmentChain.Placement> chain = chain(s, true);
        HangingPose.Layout attachment = HangingPose.get(s, shape, (legs, target) -> {
            // Solve contact in model units: absolute sampling tolerances must not pick a different
            // attachment/balance when a snapshot changes only the creature's uniform size.
            PoseStack normalized = new PoseStack();
            normalized.scale(1 / size, 1 / size, 1 / size);
            drawHangingBody(s, shape, size, chain, legs, normalized, target, light);
        });
        Vector3f hook = HangingPose.support(support);
        pose.pushPose();
        try {
            pose.translate(hook.x, hook.y, hook.z);
            pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
            pose.mulPose(attachment.balance());
            Vector3f anchor = attachment.anchor();
            pose.translate(-anchor.x * size, -anchor.y * size, -anchor.z * size);
            drawHangingBody(s, shape, size, chain, attachment.joints(), pose, buffers, light);
        } finally { pose.popPose(); }
    }

    private static void drawHangingBody(Subject s, CarcassModels.Shape shape, float size, List<SegmentChain.Placement> chain,
                                        Map<String, HangingPose.Rotation> legs, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        try {
            if (s.profile().segments != null) pose.mulPose(Axis.XP.rotationDegrees(-90F));
            else {
                pose.mulPose(Axis.XP.rotationDegrees(s.profile().hangingPitch));
                pose.mulPose(Axis.ZP.rotationDegrees(s.profile().hangingFlip));
            }
            drawCreature(s, shape, size, chain, legs, pose, buffers, light);
        } finally { pose.popPose(); }
    }

    /** How far the lying body extends from its centre, in blocks, for fitting it into an item slot. */
    public static float extent(Subject s) {
        CarcassModels.Shape shape = s.handle().shape(s.mobData());
        float size = s.profile().scale * shape.scale();
        float extent = Math.max(shape.width(), shape.height()) * size * 0.5F;
        List<SegmentChain.Placement> chain = chain(s, false);
        float[] centre = centre(chain, s.profile().scale);
        extent = Math.max(extent, (float) Math.hypot(centre[0], centre[1]) + extent);
        for (SegmentChain.Placement placement : chain) {
            float dx = placement.x() * s.profile().scale - centre[0];
            float dz = placement.z() * s.profile().scale - centre[1];
            extent = Math.max(extent, (float) Math.hypot(dx, dz) + placement.segment().spacing() * s.profile().scale * 0.5F);
        }
        return extent;
    }

    private static List<SegmentChain.Placement> chain(Subject s, boolean hanging) {
        SegmentChain segments = s.profile().segments;
        if (segments == null) return List.of();
        int cuts = (s.done().contains(Stages.Action.CUT_1) ? 1 : 0) + (s.done().contains(Stages.Action.CUT_2) ? 1 : 0);
        return segments.layout(hanging, hanging ? segments.hangingSpacing : 1F, segments.remaining(cuts));
    }

    /** The middle of the head and its segments, so a coiled body is centred on its block rather than its head. */
    private static float[] centre(List<SegmentChain.Placement> chain, float scale) {
        if (chain.isEmpty()) return new float[]{0F, 0F};
        float x = 0F;
        float z = 0F;
        for (SegmentChain.Placement placement : chain) {
            x += placement.x();
            z += placement.z();
        }
        int n = chain.size() + 1;
        return new float[]{x / n * scale, z / n * scale};
    }

    private static void drawCreature(Subject s, CarcassModels.Shape shape, float size, List<SegmentChain.Placement> chain, Map<String, HangingPose.Rotation> legs,
                                     PoseStack pose, MultiBufferSource buffers, int light) {
        PoseProfile p = s.profile();
        boolean dropHead = p.segments != null && p.segments.dropHeadModel && s.done().contains(Stages.Action.HEAD);
        if (!dropHead) {
            pose.pushPose();
            try {
                pose.scale(-size, -size, size);
                pose.translate(0, -1.501F, 0);
                drawModel(s.handle(), shape, StageTextures.get(shape.texture(), s.look()), p, s.done(), true, s.look(), s.def().hasSkeleton(), legs, pose, buffers, light);
            } finally { pose.popPose(); }
        }
        if (chain.isEmpty()) return;
        CompoundTag shared = new CompoundTag();
        for (String key : p.segments.copy) {
            if (s.mobData().contains(key)) shared.put(key, s.mobData().get(key).copy());
        }
        for (SegmentChain.Placement placement : chain) {
            CarcassModels.Handle segment = CarcassModels.get(placement.segment().entity());
            if (segment == null) continue;
            CompoundTag data = placement.segment().data();
            if (!shared.isEmpty()) data = data.copy().merge(shared);
            CarcassModels.Shape part = segment.shape(data);
            float partSize = p.scale * part.scale();
            ResourceLocation texture = p.segments.headTexture ? shape.texture() : part.texture();
            pose.pushPose();
            try {
                pose.translate(placement.x() * p.scale, 0, placement.z() * p.scale);
                pose.mulPose(Axis.YP.rotationDegrees(placement.yaw()));
                pose.scale(-partSize, -partSize, partSize);
                pose.translate(0, -1.501F, 0);
                drawModel(segment, part, StageTextures.get(texture, s.look()), p, s.done(), false, s.look(), s.def().hasSkeleton(), Map.of(), pose, buffers, light);
            } finally { pose.popPose(); }
        }
    }


    /** A severed head resting on the block, or on Butchery's plaque when {@code mount}. */
    public static void renderHead(CarcassModels.Handle handle, MobDef def, PoseProfile profile, CompoundTag mobData, boolean mount,
                                  Direction facing, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (mount) {
            BlockState plaque = ButcheryHooks.emptyMount(def.mount().butcheryMount).defaultBlockState();
            if (plaque.hasProperty(HorizontalDirectionalBlock.FACING)) plaque = plaque.setValue(HorizontalDirectionalBlock.FACING, facing);
            pose.pushPose();
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(plaque, pose, buffers, light, overlay);
            pose.popPose();
        }
        float[] size = headSize(handle, profile, mobData);
        pose.pushPose();
        if (mount) {
            // The back of the head against the plaque's face, a quarter block from the wall.
            double out = size[2] * 0.5 - 0.25;
            pose.translate(0.5 + facing.getStepX() * out + profile.headOffset[0], 0.5 + profile.headOffset[1],
                    0.5 + facing.getStepZ() * out + profile.headOffset[2]);
        } else {
            pose.translate(0.5 + profile.headOffset[0], size[1] * 0.5 + profile.headOffset[1], 0.5 + profile.headOffset[2]);
        }
        pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
        drawHead(handle, profile, mobData, StageTextures.Look.FRESH, pose, buffers, light, 1F);
        pose.popPose();
    }

    /** The head of a pelt rug: at the rug's front edge, facing out, chin toward the floor. */
    public static void renderRugHead(CarcassModels.Handle handle, PoseProfile profile, CompoundTag mobData, Direction facing,
                                     PoseStack pose, MultiBufferSource buffers, int light) {
        CarcassModels.Shape shape = handle.shape(mobData);
        ModelParts.Part head = headPart(shape, profile);
        if (head == null) return;
        Runnable restore = ModelParts.savePose(shape);
        PoseStack.Pose frame = pose.last();
        try {
            handle.pose(shape);
            float[] centre = ModelParts.centre(head);
            PoseStack local = new PoseStack();
            local.mulPose(Axis.XP.rotationDegrees(-profile.rugHeadPitch));
            float scale = profile.headScale * profile.rugHeadScale * shape.scale();
            local.scale(-scale, -scale, scale);
            local.translate(-centre[0] / 16F, -centre[1] / 16F, -centre[2] / 16F);
            ModelBounds mesh = new ModelBounds();
            if (head.raw() instanceof AdvancedModelBox box) ModelParts.solidBounds(box, local, mesh);
            else head.render(local, mesh, light, OverlayTexture.NO_OVERLAY);
            var bounds = mesh.bounds();
            pose.pushPose();
            pose.translate(.5, .3 / 16, .5);
            pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
            // Seat the pitched solid chin on the carpet and overlap its rear with the authored neck.
            // Offsets are local to the rug, so all four placements connect identically.
            pose.translate(profile.headOffset[0], -bounds.minY + profile.rugHeadLift + profile.headOffset[1],
                    -.30 - bounds.maxZ + profile.headOffset[2]);
            pose.mulPose(Axis.XP.rotationDegrees(-profile.rugHeadPitch));
            drawHead(handle, profile, mobData, StageTextures.Look.FRESH, pose, buffers, light, profile.rugHeadScale);
        } finally {
            while (pose.last() != frame) pose.popPose();
            restore.run();
        }
    }

    /** The head's size in blocks, as drawn: {@code {width, height, depth}}. */
    private static float[] headSize(CarcassModels.Handle handle, PoseProfile profile, CompoundTag mobData) {
        CarcassModels.Shape shape = handle.shape(mobData);
        ModelParts.Part head = headPart(shape, profile);
        if (head == null) return new float[]{0.5F, 0.5F, 0.5F};
        Runnable restore = ModelParts.savePose(shape);
        try {
            handle.pose(shape);
            float[] centre = ModelParts.centre(head);
            float scale = profile.headScale * shape.scale() / 16F;
            return new float[]{centre[3] * scale, centre[4] * scale, centre[5] * scale};
        } finally { restore.run(); }
    }

    /** The head part alone, the middle of its geometry at the current origin. */
    private static void drawHead(CarcassModels.Handle handle, PoseProfile profile, CompoundTag mobData, StageTextures.Look look,
                                 PoseStack pose, MultiBufferSource buffers, int light, float multiplier) {
        CarcassModels.Shape shape = handle.shape(mobData);
        ModelParts.Part head = headPart(shape, profile);
        if (head == null) return;
        Runnable restore = ModelParts.savePose(shape);
        PoseStack.Pose frame = pose.last();
        try {
            handle.pose(shape);
            float[] centre = ModelParts.centre(head);
            float scale = profile.headScale * shape.scale() * multiplier;
            pose.pushPose();
            pose.scale(-scale, -scale, scale);
            pose.translate(-centre[0] / 16F, -centre[1] / 16F, -centre[2] / 16F);
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(StageTextures.get(shape.texture(), look)));
            head.render(pose, consumer, light, OverlayTexture.NO_OVERLAY);
        } finally {
            while (pose.last() != frame) pose.popPose();
            restore.run();
        }
    }

    @Nullable
    static ModelParts.Part headPart(CarcassModels.Shape shape, PoseProfile profile) {
        ModelParts.Part head = shape.parts().get(profile.headRoot);
        if (head != null) return head;
        for (var e : shape.parts().entrySet()) {
            if (profile.isHead(e.getKey())) return e.getValue();
        }
        return null;
    }

    private static void drawModel(CarcassModels.Handle handle, CarcassModels.Shape shape, ResourceLocation texture, PoseProfile profile,
                                  Set<Stages.Action> done, boolean headModel, StageTextures.Look look, boolean hasSkeleton, Map<String, HangingPose.Rotation> legs,
                                  PoseStack pose, MultiBufferSource buffers, int light) {
        Runnable restore = ModelParts.savePose(shape);
        handle.pose(shape);
        Map<AdvancedModelBox, HangingPose.Rotation> savedAngles = new IdentityHashMap<>();
        legs.forEach((name, angle) -> {
            var part = shape.parts().get(name);
            if (part != null && part.raw() instanceof AdvancedModelBox box) {
                savedAngles.put(box, HangingPose.Rotation.of(box));
                angle.apply(box);
            }
        });
        List<ModelParts.Part> hidden = new ArrayList<>();
        boolean head = headModel && done.contains(Stages.Action.HEAD);
        boolean cut1 = done.contains(Stages.Action.CUT_1);
        boolean cut2 = done.contains(Stages.Action.CUT_2);
        shape.parts().forEach((name, part) -> {
            if (!part.visible()) return;
            if (profile.isAlwaysHidden(name) || head && profile.isHead(name) || cut1 && profile.isCut1(name) || cut2 && profile.isCut2(name)) {
                part.setVisible(false);
                hidden.add(part);
            }
        });
        PoseStack.Pose frame = pose.last();
        try {
            HangingPose.captureAnchor(shape, profile, pose, buffers);
            if (!profile.skeletal && (look == StageTextures.Look.BONE || look == StageTextures.Look.SKINNED || cut1 || cut2)) {
                StageGeometry.render(shape, profile, headModel, look, done, hasSkeleton, pose, buffers, light);
            } else {
                VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
                shape.model().renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
            }
        } finally {
            // Citadel parts push their own frames without finally blocks; also unwind those if a buffer fails.
            while (pose.last() != frame) pose.popPose();
            // The model is the live one the mob's own renderer uses; never leave parts hidden or posed for hanging.
            hidden.forEach(part -> part.setVisible(true));
            savedAngles.forEach((box, angle) -> angle.apply(box));
            restore.run();
        }
    }

    private CarcassScene() {}
}
