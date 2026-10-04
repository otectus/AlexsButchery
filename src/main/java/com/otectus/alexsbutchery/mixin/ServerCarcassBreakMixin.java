package com.otectus.alexsbutchery.mixin;

import com.otectus.alexsbutchery.block.CarcassTargeting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve vanilla mining time, tools, Forge protection events, drops and block-change acknowledgements. */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerCarcassBreakMixin {
    @Redirect(method = "handleBlockBreakAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;canReach(Lnet/minecraft/core/BlockPos;D)Z", remap = false))
    private boolean alexsbutchery$reach(ServerPlayer player, BlockPos pos, double padding) {
        return CarcassTargeting.isCarcass(player.level(), pos) ? CarcassTargeting.canReach(player, pos) : player.canReach(pos, padding);
    }

    @Redirect(method = "handleBlockBreakAction", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;canReachRaw(Lnet/minecraft/core/BlockPos;D)Z", remap = false))
    private boolean alexsbutchery$rawReach(ServerPlayer player, BlockPos pos, double padding) {
        return CarcassTargeting.isCarcass(player.level(), pos) ? CarcassTargeting.canReach(player, pos) : player.canReachRaw(pos, padding);
    }
}
