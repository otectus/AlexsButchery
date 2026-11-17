package com.otectus.alexsbutchery.client.compat.carryon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.alexsbutchery.client.compat.carryon.CarryOnCarcassRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tschipp.carryon.client.render.CarriedObjectRender;

/** Exact Carry On 2.1.2.7 hooks: one private first-person branch and two calls in its per-player third-person branch. */
@Mixin(value = CarriedObjectRender.class, remap = false)
public abstract class CarriedObjectRenderMixin {
    @Inject(method = "drawFirstPersonBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void alexsbutchery$renderFirstPerson(Player player, MultiBufferSource buffers, PoseStack pose,
                                                       int light, BlockState state, CallbackInfo ci) {
        if (CarryOnCarcassRenderer.renderFirstPerson(player, buffers, pose, light)) ci.cancel();
    }

    @WrapOperation(method = "drawThirdPerson",
            at = @At(value = "INVOKE", target = "Ltschipp/carryon/client/render/CarryRenderHelper;applyBlockTransformations(Lnet/minecraft/world/entity/player/Player;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/level/block/Block;)V", remap = false),
            remap = false)
    private static void alexsbutchery$applyThirdPerson(Player player, float partialTick, PoseStack pose, Block block,
                                                       Operation<Void> original) {
        if (!CarryOnCarcassRenderer.applyThirdPersonTransform(player, partialTick, pose, block)) {
            original.call(player, partialTick, pose, block);
        }
    }

    @WrapOperation(method = "drawThirdPerson",
            at = @At(value = "INVOKE", target = "Ltschipp/carryon/client/render/CarryRenderHelper;renderBakedModel(Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/resources/model/BakedModel;)V", remap = false),
            remap = false)
    private static void alexsbutchery$renderThirdPerson(ItemStack stack, PoseStack pose, MultiBufferSource buffers,
                                                        int light, BakedModel model, Operation<Void> original,
                                                        @Local Player player) {
        if (!CarryOnCarcassRenderer.renderThirdPerson(player, buffers, pose, light)) {
            original.call(stack, pose, buffers, light, model);
        }
    }
}
