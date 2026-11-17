package com.otectus.alexsbutchery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.alexsbutchery.AlexsButchery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/** Context-specific presentation of the same anatomy and stage decoder as placed and carried blocks. */
public final class CarcassItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static CarcassItemRenderer instance;
    private static final Set<String> FAILED = new HashSet<>();

    private CarcassItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static CarcassItemRenderer get() {
        if (instance == null) instance = new CarcassItemRenderer();
        return instance;
    }

    static void clear() { FAILED.clear(); }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var visual = CarcassVisualState.from(stack);
        if (visual == null) return;
        try { CarcassPresentation.renderItem(visual, context, pose, buffers, light, overlay); }
        catch (RuntimeException e) {
            String key = stack.getItem() + "/" + context;
            if (FAILED.add(key)) AlexsButchery.LOGGER.error("Carcass item render failed for {} (reported once until reload)", key, e);
        }
    }
}
