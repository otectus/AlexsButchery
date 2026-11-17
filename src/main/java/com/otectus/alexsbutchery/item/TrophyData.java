package com.otectus.alexsbutchery.item;

import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.registry.ModBlockEntities;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A head's appearance (the mob snapshot, {@code BlockEntityTag.MobData}) carried into what is made from it: the trophy
 * mounted at Butchery's taxidermy table and the pelt rug. Only that compound moves, as a copy; a head without one
 * (harvested before heads kept it) simply makes a trophy with the default look, and nothing is invented for it.
 */
public final class TrophyData {

    /** The appearance a mob item carries, or null; the stack's own compound, so copy before keeping it. */
    @Nullable
    public static CompoundTag appearance(ItemStack stack) {
        CompoundTag blockEntity = BlockItem.getBlockEntityData(stack);
        return blockEntity != null && blockEntity.contains(CarcassBlockEntity.MOB_DATA, Tag.TAG_COMPOUND)
                ? blockEntity.getCompound(CarcassBlockEntity.MOB_DATA) : null;
    }

    /**
     * {@code result} with the appearance of {@code head} when the result is this mod's {@code kind} item (mount or rug) of
     * the same mob and the input is that mob's head; otherwise {@code result} unchanged, so other recipes, Butchery's own
     * trophies and empty results pass through untouched. Count and any other data of the result are kept.
     */
    public static ItemStack fromHead(ItemStack result, ItemStack head, ModItems.Kind kind) {
        if (result.isEmpty() || head.isEmpty()) return result;
        ModItems.Owner made = ModItems.owner(result.getItem());
        ModItems.Owner from = ModItems.owner(head.getItem());
        if (made == null || from == null || made.kind() != kind || from.kind() != ModItems.Kind.HEAD || made.def() != from.def()) return result;
        CompoundTag look = appearance(head);
        if (look == null || look.isEmpty()) return result;
        ItemStack decorated = result.copy();
        CompoundTag existing = BlockItem.getBlockEntityData(decorated);
        CompoundTag blockEntity = existing == null ? new CompoundTag() : existing.copy();
        blockEntity.remove("id");
        blockEntity.put(CarcassBlockEntity.MOB_DATA, look.copy());
        BlockItem.setBlockEntityData(decorated, ModBlockEntities.CARCASS.get(), blockEntity);
        return decorated;
    }

    /** The output Butchery's taxidermy table computed, with the appearance of the head in its input slot 1. */
    public static ItemStack taxidermyResult(ItemStack result, ItemStack headSlot) {
        return fromHead(result, headSlot, ModItems.Kind.MOUNT);
    }

    private TrophyData() {}
}
