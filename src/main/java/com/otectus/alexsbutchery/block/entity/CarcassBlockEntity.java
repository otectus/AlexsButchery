package com.otectus.alexsbutchery.block.entity;

import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.block.CarcassIndex;
import com.otectus.alexsbutchery.block.MobBlock;
import com.otectus.alexsbutchery.butcher.Acid;
import com.otectus.alexsbutchery.butcher.Bleeding;
import com.otectus.alexsbutchery.butcher.PortableCarcass;
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
    public static final String MOB_DATA = PortableCarcass.MOB_DATA;

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

    /** Acid takes precedence: the portable decoder never permits a carcass to bleed and dissolve at once. */
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
        if (bleeding) return;
        bleeding = true;
        bleedTicks = 0;
        fills = 0;
        syncFlags();
        sync();
    }

    /** Fills still owed by this carcass's blood class: what timed or instant bleeding may still emit. */
    public int remainingFills() {
        return Math.max(0, def().blood().fills - fills);
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
        PortableCarcass.save(this, tag);
    }

    /**
     * Also how a carried carcass resumes: its item keeps these fields ({@code ModBlockLoot}), and placing or hanging it
     * loads them here, so a carcass picked up mid-way never starts a fresh, fully paid process again.
     */
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        restorePortable(PortableCarcass.decode(tag, getBlockState(), def()), false);
    }

    /**
     * Rejects process state this block cannot have, wherever it came from (old saves, edited or carried items): only a
     * fresh carcass of a bleeding mob bleeds, and never past its blood class; only a form acid can dissolve keeps an
     * acid clock. A save holding both keeps the acid (which already won every tick) and drops the bleeding, so no blood
     * is owed any more. All counters are bounded to what one carcass can legitimately produce.
     */
    public void restorePortable(PortableCarcass.Payload payload) {
        restorePortable(payload, true);
    }

    private void restorePortable(PortableCarcass.Payload payload, boolean notify) {
        mobData = payload.mobData().copy();
        bleeding = payload.bleeding();
        bleedTicks = payload.bleedTicks();
        fills = payload.fills();
        acidTicks = payload.acidTicks();
        syncFlags();
        if (notify) sync();
    }

    /** Butchery's own hint overlays read {@code isBleeding} from the block entity's persistent data. */
    private void syncFlags() {
        if (bleeding) getPersistentData().putBoolean("isBleeding", true);
        else getPersistentData().remove("isBleeding");
        if (getBlockState().getBlock() instanceof com.otectus.alexsbutchery.block.DrainedCarcassBlock)
            getPersistentData().putBoolean("isDrained", true);
        else getPersistentData().remove("isDrained");
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
     * rendered vertex) plus a block of slack, so a visible tail or a whale's jaw is drawn while its anchor is off screen.
     * Carcasses, skeletons, heads and trophies have exported anatomy; a rug's head lies on its own pelt.
     */
    @Override
    public AABB getRenderBoundingBox() {
        if (level != null && CarcassBounds.selectable(getBlockState().getBlock()))
            return CarcassBounds.geometry(getBlockState(), level, worldPosition).envelope().move(worldPosition).inflate(1);
        return new AABB(worldPosition).inflate(2, 2, 2);
    }
}
