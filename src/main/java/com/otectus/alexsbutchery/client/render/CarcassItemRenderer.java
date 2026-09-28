package com.otectus.alexsbutchery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.alexsbutchery.block.DrainedCarcassBlock;
import com.otectus.alexsbutchery.block.HeadBlock;
import com.otectus.alexsbutchery.block.HeadMountBlock;
import com.otectus.alexsbutchery.block.MobBlock;
import com.otectus.alexsbutchery.block.SkeletonBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.client.pose.PoseProfile;
import com.otectus.alexsbutchery.client.pose.PoseProfiles;
import com.otectus.alexsbutchery.def.MobDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Set;

/**
 * Item form of the mob blocks: the same models as in the world, scaled to fit an item slot, with the mob look the
 * stack's block entity tag carries. Drained items show the drained texture, skeletons the bone one, heads the head
 * alone; a multipart mob's coiled body is fitted whole.
 */
public final class CarcassItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static CarcassItemRenderer instance;

    private CarcassItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static CarcassItemRenderer get() {
        if (instance == null) instance = new CarcassItemRenderer();
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof MobBlock mobBlock)) return;
        Block block = blockItem.getBlock();
        MobDef def = mobBlock.def();
        CarcassModels.Handle handle = CarcassModels.get(def.entity());
        if (handle == null) return;
        CompoundTag tag = BlockItem.getBlockEntityData(stack);
        CompoundTag mobData = tag != null && tag.contains(CarcassBlockEntity.MOB_DATA) ? tag.getCompound(CarcassBlockEntity.MOB_DATA) : new CompoundTag();
        PoseProfile profile = PoseProfiles.get(def);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (block instanceof HeadBlock) {
            boolean mount = block instanceof HeadMountBlock;
            CarcassModels.Shape shape = handle.shape(mobData);
            float fit = 0.9F / Math.max(0.5F, Math.min(shape.width(), shape.height()) * shape.scale() * 0.8F);
            pose.scale(fit, fit, fit);
            pose.translate(-0.5, mount ? -0.5 : -0.25, -0.5);
            CarcassScene.renderHead(handle, def, profile, mobData, mount, Direction.SOUTH, pose, buffers, light, overlay);
        } else {
            StageTextures.Look look = block instanceof SkeletonBlock ? StageTextures.Look.BONE
                    : block instanceof DrainedCarcassBlock ? StageTextures.Look.DRAINED : StageTextures.Look.FRESH;
            CarcassScene.Subject subject = new CarcassScene.Subject(def, handle, profile, mobData, look, Set.of());
            CarcassModels.Shape shape = handle.shape(mobData);
            float fit = 0.85F / Math.max(0.3F, CarcassScene.extent(subject));
            pose.scale(fit * 0.5F, fit * 0.5F, fit * 0.5F);
            // The body's vertical middle: half its width on its side, half its height on its belly.
            double roll = Math.toRadians(profile.lyingRoll);
            float size = shape.scale() * profile.scale;
            float lift = (float) (Math.abs(Math.sin(roll)) * shape.width() + Math.abs(Math.cos(roll)) * shape.height()) * size * 0.5F;
            pose.translate(-0.5, -lift, -0.5);
            CarcassScene.renderLying(subject, Direction.SOUTH, pose, buffers, light);
        }
        pose.popPose();
    }
}
