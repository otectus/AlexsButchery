package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.def.BloodClass;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.registry.ModBlockEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The owned, bounded data allowed to travel with a whole carcass item. Missing process fields are a legacy unprocessed
 * carcass. Coordinates, UUIDs, inventories, ForgeData and other block-entity data are deliberately not portable.
 */
public final class PortableCarcass {
    public static final String MOB_DATA = "MobData";
    public static final String BLEEDING = "Bleeding";
    public static final String BLEED_TICKS = "BleedTicks";
    public static final String FILLS = "Fills";
    public static final String ACID_TICKS = "AcidTicks";
    public static final List<String> PROCESS_FIELDS = List.of(BLEEDING, BLEED_TICKS, FILLS, ACID_TICKS);

    public record Payload(CompoundTag mobData, boolean bleeding, int bleedTicks, int fills, int acidTicks) {
        public Payload {
            mobData = mobData.copy();
        }
    }

    public static Payload decode(@Nullable CompoundTag source, BlockState state, MobDef def) {
        CompoundTag tag = source == null ? new CompoundTag() : source;
        CompoundTag mobData = tag.contains(MOB_DATA, Tag.TAG_COMPOUND) ? tag.getCompound(MOB_DATA).copy() : new CompoundTag();

        int acidTicks = numeric(tag, ACID_TICKS) ? Mth.clamp(tag.getInt(ACID_TICKS), 0, Acid.DISSOLVE_TICKS) : 0;
        if (!(state.getBlock() instanceof AbstractCarcassBlock block) || !Acid.canDissolve(block, state)) acidTicks = 0;

        int rawFills = numeric(tag, FILLS) ? tag.getInt(FILLS) : 0;
        int rawBleedTicks = numeric(tag, BLEED_TICKS) ? tag.getInt(BLEED_TICKS) : 0;
        boolean canBleed = state.getBlock() instanceof CarcassBlock && def.bleeds();
        // If a damaged payload lost its flag but retained paid fills or clock progress, resume conservatively rather
        // than treating it as a fresh carcass that can pay the same blood again. Truly legacy no-field items stay fresh.
        boolean bleeding = acidTicks == 0 && canBleed && (tag.getBoolean(BLEEDING) || rawFills > 0 || rawBleedTicks > 0);
        int fills = 0;
        int bleedTicks = 0;
        if (bleeding) {
            BloodClass blood = def.blood();
            fills = Mth.clamp(rawFills, 0, blood.fills);
            bleedTicks = Mth.clamp(rawBleedTicks, 0, blood.totalTicks());
        }
        return new Payload(mobData, bleeding, bleedTicks, fills, acidTicks);
    }

    public static void save(CarcassBlockEntity carcass, CompoundTag out) {
        if (!carcass.mobData().isEmpty()) out.put(MOB_DATA, carcass.mobData().copy());
        if (carcass.isBleeding()) {
            out.putBoolean(BLEEDING, true);
            out.putInt(BLEED_TICKS, carcass.bleedTicks());
            out.putInt(FILLS, carcass.fills());
        }
        if (carcass.acidTicks() > 0) out.putInt(ACID_TICKS, carcass.acidTicks());
    }

    /** Applies only the owned payload during ordinary item placement and transactional hanging. */
    public static boolean apply(ItemStack stack, CarcassBlockEntity carcass) {
        CompoundTag tag = BlockItem.getBlockEntityData(stack);
        if (tag == null) return false;
        carcass.restorePortable(decode(tag, carcass.getBlockState(), carcass.def()));
        return true;
    }

    /** Confirms that ordinary placement installed the complete owned payload before its caller commits other effects. */
    public static boolean matches(ItemStack stack, CarcassBlockEntity carcass) {
        Payload expected = decode(BlockItem.getBlockEntityData(stack), carcass.getBlockState(), carcass.def());
        return expected.mobData().equals(carcass.mobData()) && expected.bleeding() == carcass.isBleeding()
                && expected.bleedTicks() == carcass.bleedTicks() && expected.fills() == carcass.fills()
                && expected.acidTicks() == carcass.acidTicks();
    }

    public static void writeMobData(ItemStack stack, CompoundTag mobData) {
        if (mobData.isEmpty()) return;
        CompoundTag portable = new CompoundTag();
        portable.put(MOB_DATA, mobData.copy());
        BlockItem.setBlockEntityData(stack, ModBlockEntities.CARCASS.get(), portable);
    }

    private static boolean numeric(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC);
    }

    private PortableCarcass() {}
}
