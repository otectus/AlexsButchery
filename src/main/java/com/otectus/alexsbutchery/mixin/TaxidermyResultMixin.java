package com.otectus.alexsbutchery.mixin;

import com.otectus.alexsbutchery.item.TrophyData;
import net.mcreator.butchery.procedures.TaxidermytableOnTickUpdateProcedure;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Butchery's taxidermy table recomputes its output every tick ({@code TaxidermytableOnTickUpdateProcedure}): the first
 * recipe whose three ingredients match slots 0-2 gives {@code getResultItem(null).copy()}, written once into slot 3 by
 * the lambda {@code lambda$execute$0}. Taking it out of slot 3 runs {@code TaxidermyTakenfromslotProcedure}, which uses
 * up one of each input. The recipe's static result knows nothing of the head being mounted, so the write is decorated
 * here, in the same tick: when the result is one of this mod's mounts and slot 1 holds the same mob's head, the result
 * takes a copy of the head's appearance. Anything else (Butchery's own trophies, other recipes, an empty result) is
 * written unchanged. Identical in Butchery 5.2 and 5.3 (one call, same lambda); a later Butchery without this seam loads
 * without the decoration and {@code TaxidermyBridge} logs it.
 */
@Mixin(value = TaxidermytableOnTickUpdateProcedure.class, remap = false)
abstract class TaxidermyResultMixin {
    @Redirect(method = "lambda$execute$0", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/items/IItemHandlerModifiable;setStackInSlot(ILnet/minecraft/world/item/ItemStack;)V"))
    private static void alexsbutchery$keepHeadAppearance(IItemHandlerModifiable handler, int slot, ItemStack result) {
        handler.setStackInSlot(slot, slot == 3 ? TrophyData.taxidermyResult(result, handler.getStackInSlot(1)) : result);
    }
}
