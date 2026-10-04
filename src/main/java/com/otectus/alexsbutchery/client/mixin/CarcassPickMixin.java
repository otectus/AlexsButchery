package com.otectus.alexsbutchery.client.mixin;

import com.otectus.alexsbutchery.block.CarcassTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extend candidate discovery, not player reach. Vanilla still handles the selected block. */
@Mixin(GameRenderer.class)
abstract class CarcassPickMixin {
    @Inject(method = "pick", at = @At("RETURN"))
    private void alexsbutchery$pick(float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getCameraEntity() != mc.player) return;
        var eye = mc.player.getEyePosition(partialTick);
        var end = eye.add(mc.player.getViewVector(partialTick).scale(mc.player.getBlockReach()));
        var hit = CarcassTargeting.pick(mc.level, eye, end, mc.player);
        if (hit != null && (mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS
                || eye.distanceToSqr(hit.getLocation()) <= eye.distanceToSqr(mc.hitResult.getLocation()) + 1E-7)) {
            mc.hitResult = hit;
            mc.crosshairPickEntity = null;
        }
    }
}
