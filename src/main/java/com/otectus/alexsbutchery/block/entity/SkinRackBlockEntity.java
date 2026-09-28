package com.otectus.alexsbutchery.block.entity;

import com.otectus.alexsbutchery.block.SkinRackBlock;
import com.otectus.alexsbutchery.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The skin on the taken-over rack, how far it has been cured, and the curing clock. */
public class SkinRackBlockEntity extends BlockEntity {
    private ItemStack skin = ItemStack.EMPTY;
    private int stage = SkinRackBlock.STAGE_HUNG;
    private int cureTicks;

    public SkinRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKIN_RACK.get(), pos, state);
    }

    public ItemStack skin() {
        return skin;
    }

    public int stage() {
        return stage;
    }

    public int cureTicks() {
        return cureTicks;
    }

    public void setSkin(ItemStack skin) {
        this.skin = skin.copy();
        this.skin.setCount(1);
        sync();
    }

    public void setStage(int stage) {
        this.stage = stage;
        sync();
    }

    public void startCuring(int ticks) {
        this.stage = SkinRackBlock.STAGE_CURING;
        this.cureTicks = ticks;
        sync();
    }

    public void serverTick() {
        if (stage != SkinRackBlock.STAGE_CURING) return;
        if (--cureTicks <= 0) setStage(SkinRackBlock.STAGE_DONE);
        else setChanged();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!skin.isEmpty()) tag.put("Skin", skin.save(new CompoundTag()));
        tag.putInt("Stage", stage);
        tag.putInt("CureTicks", cureTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        skin = tag.contains("Skin") ? ItemStack.of(tag.getCompound("Skin")) : ItemStack.EMPTY;
        stage = tag.getInt("Stage");
        cureTicks = tag.getInt("CureTicks");
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
}
