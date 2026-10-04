package com.otectus.alexsbutchery.mixin;

import com.otectus.alexsbutchery.block.CarcassTargeting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerCarcassUseMixin {
    @Shadow public ServerPlayer player;

    @Redirect(method = "handleUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;canReach(Lnet/minecraft/core/BlockPos;D)Z", remap = false))
    private boolean alexsbutchery$reach(ServerPlayer player, BlockPos pos, double padding) {
        return CarcassTargeting.isCarcass(player.level(), pos) ? CarcassTargeting.canReach(player, pos) : player.canReach(pos, padding);
    }

    // Older Forge 47 builds have only canReach; newer builds add the raw-distance compatibility branch.
    @Redirect(method = "handleUseItemOn", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;canReachRaw(Lnet/minecraft/core/BlockPos;D)Z", remap = false))
    private boolean alexsbutchery$rawReach(ServerPlayer player, BlockPos pos, double padding) {
        return CarcassTargeting.isCarcass(player.level(), pos) ? CarcassTargeting.canReach(player, pos) : player.canReachRaw(pos, padding);
    }

    @Redirect(method = "handleUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 alexsbutchery$surfaceHit(Vec3 hit, Vec3 centre) {
        // Vanilla restricts hit coordinates to one block from the anchor. Only waive that guard for
        // a point independently ray-traced on this carcass's surface, in reach and unobstructed.
        return CarcassTargeting.validHit(player, BlockPos.containing(centre), hit) ? Vec3.ZERO : hit.subtract(centre);
    }
}
