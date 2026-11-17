package com.otectus.alexsbutchery.smoketest;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.alexsbutchery.client.render.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Fixed camera/scale contact sheet. Legacy row recreates the reviewed pre-fix transforms, not server anatomy. */
final class ItemReviewScreen extends Screen {
    private final ItemDisplayContext context;
    private final boolean legacy;
    private static final String[] SPECIES = {"moose", "elephant", "jerboa", "catfish", "terrapin", "cachalot_whale", "anaconda", "farseer"};

    ItemReviewScreen(ItemDisplayContext context, boolean legacy) {
        super(Component.literal("Carcass item presentation"));
        this.context = context;
        this.legacy = legacy;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff26303b);
        graphics.drawString(font, (legacy ? "Before (source reconstruction)" : "After") + " / " + context, 10, 8, 0xffffff);
        double scale = context == ItemDisplayContext.GUI || context == ItemDisplayContext.FIXED || context == ItemDisplayContext.HEAD ? 72 : 14;
        int cellW = width / 4, cellH = (height - 32) / 2;
        for (int i = 0; i < SPECIES.length; i++) {
            int x = i % 4 * cellW, y = 30 + i / 4 * cellH;
            graphics.fill(x + 3, y, x + cellW - 3, y + cellH - 3, 0xff18212a);
            graphics.drawString(font, SPECIES[i], x + 8, y + 5, 0xffffff);
            var stack = ItemPresentationChecks.stack(SPECIES[i], new CompoundTag());
            PoseStack pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(x + cellW / 2D, y + cellH * .65, 150);
                pose.scale((float) scale, (float) -scale, (float) scale);
                var buffers = minecraft.renderBuffers().bufferSource();
                if (legacy) legacy(stack, context, pose, buffers);
                else minecraft.getItemRenderer().renderStatic(stack, context, LightTexture.FULL_BRIGHT, 0, pose, buffers, minecraft.level, 0);
                buffers.endBatch();
            } finally { pose.popPose(); }
        }
        RenderSystem.enableDepthTest();
    }

    static void legacy(ItemStack stack, ItemDisplayContext context, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers) {
        // Kept in test code only. Exactly the body fit and JSON transformations captured before this task.
        if (context == ItemDisplayContext.GUI) { pose.mulPose(Axis.XP.rotationDegrees(30)); pose.mulPose(Axis.YP.rotationDegrees(225)); pose.scale(.625F, .625F, .625F); }
        if (context == ItemDisplayContext.GROUND) { pose.translate(0, 3D / 16, 0); pose.scale(.25F, .25F, .25F); }
        if (context == ItemDisplayContext.FIXED) pose.scale(.5F, .5F, .5F);
        if (context.firstPerson()) { pose.mulPose(Axis.YP.rotationDegrees(context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 225 : 45)); pose.scale(.4F, .4F, .4F); }
        if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            pose.translate(0, 2.5 / 16, 0); pose.mulPose(Axis.XP.rotationDegrees(75)); pose.mulPose(Axis.YP.rotationDegrees(45)); pose.scale(.375F, .375F, .375F);
        }
        var subject = CarcassVisualState.from(stack).subject();
        float fit = .425F / Math.max(.3F, CarcassScene.extent(subject));
        pose.scale(fit, fit, fit);
        pose.translate(-.5, -CarcassScene.lyingHeight(subject) * .5, -.5);
        CarcassScene.renderLying(subject, Direction.SOUTH, pose, buffers, 15728880);
    }

    @Override public boolean isPauseScreen() { return false; }
}
