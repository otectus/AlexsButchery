package com.otectus.alexsbutchery.client.render;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Hands mob items to the model-based item renderer. */
public final class MobItemClientExtensions implements IClientItemExtensions {
    public static final MobItemClientExtensions INSTANCE = new MobItemClientExtensions();

    private MobItemClientExtensions() {}

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return CarcassItemRenderer.get();
    }
}
