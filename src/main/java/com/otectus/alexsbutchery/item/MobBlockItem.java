package com.otectus.alexsbutchery.item;

import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.butcher.PortableCarcass;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** A carcass, head or trophy item, drawn in hand and in inventories with the mob's own model. */
public class MobBlockItem extends BlockItem {
    private static final ThreadLocal<PlacementCapture> PLACEMENT_CAPTURE = new ThreadLocal<>();

    public MobBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        if (level.getBlockEntity(pos) instanceof CarcassBlockEntity carcass) {
            PlacementCapture capture = PLACEMENT_CAPTURE.get();
            if (capture != null) capture.record(level, pos, carcass);
            return PortableCarcass.apply(stack, carcass);
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    /**
     * Captures the exact block entity created inside Forge's item-placement transaction, before its EntityPlaceEvent
     * callbacks run. Hanging uses the retained instance as rollback ownership; a callback's replacement is never ours.
     */
    public static PlacementCapture capturePlacement(Level level, BlockPos pos) {
        PlacementCapture capture = new PlacementCapture(level, pos, PLACEMENT_CAPTURE.get());
        PLACEMENT_CAPTURE.set(capture);
        return capture;
    }

    public static final class PlacementCapture implements AutoCloseable {
        private final Level level;
        private final BlockPos pos;
        @Nullable private final PlacementCapture previous;
        @Nullable private CarcassBlockEntity placed;

        private void record(Level level, BlockPos pos, CarcassBlockEntity carcass) {
            if (placed == null && this.level == level && this.pos.equals(pos)) placed = carcass;
        }

        @Nullable
        public CarcassBlockEntity placed() {
            return placed;
        }

        @Override
        public void close() {
            if (PLACEMENT_CAPTURE.get() != this) return;
            if (previous == null) PLACEMENT_CAPTURE.remove();
            else PLACEMENT_CAPTURE.set(previous);
        }

        private PlacementCapture(Level level, BlockPos pos, @Nullable PlacementCapture previous) {
            this.level = level;
            this.pos = pos.immutable();
            this.previous = previous;
        }
    }

    @Override
    public void initializeClient(Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        // Only ever called on the client; the holder class is loaded lazily here and nowhere else.
        consumer.accept(com.otectus.alexsbutchery.client.render.MobItemClientExtensions.INSTANCE);
    }
}
