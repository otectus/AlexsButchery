package com.otectus.alexsbutchery.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** A carcass, head or trophy item, drawn in hand and in inventories with the mob's own model. */
public class MobBlockItem extends BlockItem {

    public MobBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        // Only ever called on the client; the holder class is loaded lazily here and nowhere else.
        consumer.accept(com.otectus.alexsbutchery.client.render.MobItemClientExtensions.INSTANCE);
    }
}
