package com.otectus.alexsbutchery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.alexsbutchery.block.SkinRackBlock;
import com.otectus.alexsbutchery.block.entity.SkinRackBlockEntity;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Draws our rack with Butchery's own rack models: the empty frame with the skin item hung in it, then Butchery's
 * generic salted, wet and leather states as the cure progresses.
 */
public class SkinRackRenderer implements BlockEntityRenderer<SkinRackBlockEntity> {

    public SkinRackRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SkinRackBlockEntity rack, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = rack.getBlockState().getValue(SkinRackBlock.FACING);
        int butcheryState = switch (rack.stage()) {
            case SkinRackBlock.STAGE_SALTED -> 26;
            case SkinRackBlock.STAGE_CURING -> 27;
            case SkinRackBlock.STAGE_DONE -> 30;
            default -> 0;
        };
        BlockState frame = ButcheryHooks.skinRack().defaultBlockState();
        if (frame.hasProperty(HorizontalDirectionalBlock.FACING)) frame = frame.setValue(HorizontalDirectionalBlock.FACING, facing);
        Property<?> stageProperty = frame.getBlock().getStateDefinition().getProperty("blockstate");
        if (stageProperty instanceof IntegerProperty ip && ip.getPossibleValues().contains(butcheryState)) frame = frame.setValue(ip, butcheryState);
        pose.pushPose();
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(frame, pose, buffers, light, overlay);
        pose.popPose();

        if (rack.stage() == SkinRackBlock.STAGE_HUNG && !rack.skin().isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            pose.scale(0.7F, 0.7F, 0.7F);
            Minecraft.getInstance().getItemRenderer().renderStatic(rack.skin(), ItemDisplayContext.FIXED, light, overlay, pose, buffers,
                    rack.getLevel(), (int) rack.getBlockPos().asLong());
            pose.popPose();
        }
    }
}
