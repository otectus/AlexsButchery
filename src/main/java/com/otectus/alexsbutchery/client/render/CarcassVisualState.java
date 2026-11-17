package com.otectus.alexsbutchery.client.render;

import com.otectus.alexsbutchery.block.*;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.client.pose.PoseProfiles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** An owned appearance snapshot and actual stage, independent of world, support and display pose. */
public final class CarcassVisualState {
    public enum Form { BODY, HEAD, MOUNT, RUG }
    private static final java.util.List<CarcassVisualState> CACHE = new java.util.ArrayList<>();
    static void clear() { CACHE.clear(); }
    private final BlockState state;
    private final CompoundTag appearance;
    private final Form form;
    private final StageTextures.Look look;
    private final Set<Stages.Action> done;

    private CarcassVisualState(BlockState state, CompoundTag appearance) {
        this.state = state;
        this.appearance = appearance.copy();
        var block = state.getBlock();
        var def = ((MobBlock) block).def();
        form = block instanceof HeadMountBlock ? Form.MOUNT : block instanceof HeadBlock ? Form.HEAD
                : block instanceof RugBlock ? Form.RUG : Form.BODY;
        done = block instanceof DrainedCarcassBlock drained ? Set.copyOf(Stages.done(def, false, drained.stage(state)))
                : block instanceof StagedCarcassBlock staged ? Set.copyOf(Stages.done(def, true, staged.stage(state))) : Set.of();
        look = block instanceof SkeletonBlock ? StageTextures.Look.BONE : block instanceof DrainedCarcassBlock
                ? done.contains(Stages.Action.SKIN) ? StageTextures.Look.SKINNED : StageTextures.Look.DRAINED : StageTextures.Look.FRESH;
    }

    /** Reads only owned appearance; never edits the carrier's tile, creates a BE, or touches a level. */
    @Nullable public static CarcassVisualState from(BlockState state, @Nullable CompoundTag tile) {
        return fromAppearance(state, tile == null ? EMPTY : tile.getCompound(CarcassBlockEntity.MOB_DATA));
    }

    private static final CompoundTag EMPTY = new CompoundTag();

    @Nullable public static CarcassVisualState fromAppearance(BlockState state, CompoundTag appearance) {
        if (!(state.getBlock() instanceof MobBlock)) return null;
        for (var cached : CACHE) if (cached.state == state && cached.appearance.equals(appearance)) return cached;
        var result = new CarcassVisualState(state, appearance);
        if (CACHE.size() >= 256) CACHE.remove(0);
        CACHE.add(result);
        return result;
    }

    @Nullable public static CarcassVisualState from(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof MobBlock)) return null;
        BlockState state = item.getBlock().defaultBlockState();
        CompoundTag properties = stack.getTagElement("BlockStateTag");
        if (properties != null) for (String name : properties.getAllKeys()) {
            var property = state.getBlock().getStateDefinition().getProperty(name);
            if (property != null) state = property(state, property, properties.getString(name));
        }
        return from(state, BlockItem.getBlockEntityData(stack));
    }

    private static <T extends Comparable<T>> BlockState property(BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    public BlockState state() { return state; }
    public Form form() { return form; }
    public StageTextures.Look look() { return look; }
    public Set<Stages.Action> done() { return done; }
    public CompoundTag appearance() { return appearance.copy(); }

    @Nullable public CarcassScene.Subject subject() {
        var def = ((MobBlock) state.getBlock()).def();
        var handle = CarcassModels.get(def.entity());
        return handle == null ? null : new CarcassScene.Subject(def, handle, PoseProfiles.get(def), appearance, look, done);
    }
}
