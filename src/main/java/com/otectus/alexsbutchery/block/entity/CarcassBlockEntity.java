package com.otectus.alexsbutchery.block.entity;

import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.block.CarcassIndex;
import com.otectus.alexsbutchery.block.MobBlock;
import com.otectus.alexsbutchery.butcher.Acid;
import com.otectus.alexsbutchery.butcher.Bleeding;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * State shared by every mob block: a snapshot of the killed mob (variant, tusks, size) so the renderer shows the
 * right look, and, for fresh carcasses, the bleeding progress. Bleeding is ticked here rather than through
 * Butchery's work queue, so it survives saving and reloading.
 */
public class CarcassBlockEntity extends BlockEntity {
    public static final String MOB_DATA = "MobData";

    private CompoundTag mobData = new CompoundTag();
    private boolean bleeding;
    private int bleedTicks;
    private int fills;
    /** Ticks left until acid has dissolved the carcass into its skeleton; 0 when no acid was poured. */
    private int acidTicks;

    public CarcassBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CARCASS.get(), pos, state);
    }

    /** Joins the level's carcass index wherever the block entity enters a chunk, on both sides. */
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        CarcassIndex.add(level, worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) CarcassIndex.remove(level, worldPosition);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CarcassBlockEntity carcass) {
        if (carcass.acidTicks > 0) Acid.tick(level, pos, state, carcass);
        else if (carcass.bleeding) Bleeding.tick(level, pos, state, carcass);
    }

    public MobDef def() {
        return ((MobBlock) getBlockState().getBlock()).def();
    }

    public CompoundTag mobData() {
        return mobData;
    }

    public void setMobData(@Nullable CompoundTag tag) {
        this.mobData = tag == null ? new CompoundTag() : tag.copy();
        sync();
    }

    public boolean isBleeding() {
        return bleeding;
    }

    public int bleedTicks() {
        return bleedTicks;
    }

    public int fills() {
        return fills;
    }

    public void startBleeding() {
        bleeding = true;
        bleedTicks = 0;
        fills = 0;
        // Butchery's own hint overlays read these flags from the block entity's persistent data.
        getPersistentData().putBoolean("isBleeding", true);
        sync();
    }

    /** Advances the bleed clock by one tick; returns true when a fill is due. */
    public boolean tickBleed(int interval) {
        bleedTicks++;
        setChanged();
        return interval > 0 && bleedTicks % interval == 0;
    }

    public boolean isDissolving() {
        return acidTicks > 0;
    }

    public int acidTicks() {
        return acidTicks;
    }

    public void startDissolving(int ticks) {
        acidTicks = ticks;
        sync();
    }

    /** Counts the acid down by one tick; returns true when the carcass has dissolved. */
    public boolean tickDissolve() {
        acidTicks--;
        setChanged();
        return acidTicks <= 0;
    }

    public void countFill() {
        fills++;
        setChanged();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!mobData.isEmpty()) tag.put(MOB_DATA, mobData.copy());
        if (bleeding) {
            tag.putBoolean("Bleeding", true);
            tag.putInt("BleedTicks", bleedTicks);
            tag.putInt("Fills", fills);
        }
        if (acidTicks > 0) tag.putInt("AcidTicks", acidTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mobData = tag.contains(MOB_DATA) ? tag.getCompound(MOB_DATA).copy() : new CompoundTag();
        bleeding = tag.getBoolean("Bleeding");
        bleedTicks = tag.getInt("BleedTicks");
        fills = tag.getInt("Fills");
        acidTicks = tag.getInt("AcidTicks");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Frustum culling for the renderer: the anatomy's own envelope (which the geometry review checks contains every
     * rendered vertex) plus a block of slack, so a visible tail is drawn while its anchor is off screen.
     */
    @Override
    public AABB getRenderBoundingBox() {
        if (level != null && getBlockState().getBlock() instanceof AbstractCarcassBlock)
            return CarcassBounds.geometry(getBlockState(), level, worldPosition).envelope().move(worldPosition).inflate(1);
        // Heads, trophies and rugs: a whale's or Void Worm's head reaches well past its block.
        boolean big = getBlockState().getBlock() instanceof MobBlock mob && mob.def().floor();
        return big ? new AABB(worldPosition).inflate(12, 8, 12) : new AABB(worldPosition).inflate(5, 7, 5);
    }
}
