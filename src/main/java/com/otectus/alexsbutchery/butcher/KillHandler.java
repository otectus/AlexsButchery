package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.advancement.ButcheringTrigger;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.compat.MobSnapshot;
import com.otectus.alexsbutchery.config.ServerConfig;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlockEntities;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import com.otectus.alexsbutchery.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a qualifying kill into a carcass. The rules are Butchery's ({@code CowcarcassdropProcedure}): an adult,
 * a killer entity, and with cleaver-only kills on, a cleaver or Butcher's Touch in the main hand, with Farmer's
 * Delight's knives subject to Butchery's own option for them. Unlike Butchery, which deletes vanilla drops one tick
 * later with a command, the drops the carcass yields are removed from the event itself; everything else the mob
 * drops is kept. Nothing changes unless the carcass is actually created.
 */
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID)
public final class KillHandler {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        MobDef def = MobDefs.byEntity(victim.getType());
        if (def == null || victim.isBaby()) return;
        if (ServerConfig.isDisabled(def) || OfficialAddonGuard.covers(def)) return;
        if (!ServerConfig.BUTCHER_TAMED_MOBS.get() && victim instanceof TamableAnimal tamable && tamable.isTame()) return;
        Entity killer = event.getSource().getEntity();
        if (killer == null) return;
        ItemStack weapon = killer instanceof LivingEntity living ? living.getMainHandItem() : ItemStack.EMPTY;
        if (!ButcheryHooks.allowsCarcassKill(weapon)) return;
        if (def.bossTool() && !weapon.is(ModTags.Items.BOSS_TOOL)) return;

        CompoundTag mobData = MobSnapshot.capture(victim);
        // A floor carcass that cannot be placed (nothing below but the void) leaves the ordinary drops alone.
        if (def.floor() && !placeFloorCarcass(level, victim, def, mobData)) return;
        if (ServerConfig.REPLACE_MOB_DROPS.get()) {
            event.getDrops().removeIf(drop -> drop.getItem().is(ModTags.Items.REPLACED_BY_CARCASS));
        }
        if (killer instanceof ServerPlayer player) {
            // Butchery grants "The Chosen One" only for Animal and Monster kills with its own cleavers.
            ButcheryHooks.grantAdvancement(player, "firstcarcass");
            ButcheringTrigger.INSTANCE.trigger(player, def, ButcheringTrigger.Action.KILL);
        }
        if (!def.floor()) {
            ItemStack stack = carcassStack(def, mobData);
            ItemEntity item = new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(), stack);
            item.setDefaultPickUpDelay();
            event.getDrops().add(item);
        }
    }

    /** A fresh carcass item carrying the mob snapshot in its block entity tag. */
    public static ItemStack carcassStack(MobDef def, CompoundTag mobData) {
        ItemStack stack = new ItemStack(ModItems.of(def).carcass().get());
        if (!mobData.isEmpty()) {
            CompoundTag tag = new CompoundTag();
            tag.put(CarcassBlockEntity.MOB_DATA, mobData);
            BlockItem.setBlockEntityData(stack, ModBlockEntities.CARCASS.get(), tag);
        }
        return stack;
    }

    /**
     * Floor carcasses are too big to carry: the block comes to rest where the mob died, like Butchery's warden. It is
     * placed directly where a falling block would land, so the block the mob stood in (a slab, path or carpet) is never
     * replaced and a landing on one never loses the carcass.
     */
    private static boolean placeFloorCarcass(ServerLevel level, LivingEntity victim, MobDef def, CompoundTag mobData) {
        BlockPos pos = restingPlace(level, victim.blockPosition());
        if (pos == null) return false;
        Direction facing = victim.getDirection().getOpposite();
        BlockState state = ModBlocks.of(def).carcass().get().defaultBlockState().setValue(AbstractCarcassBlock.FACING, facing);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) return false;
        if (!mobData.isEmpty() && level.getBlockEntity(pos) instanceof CarcassBlockEntity carcass) carcass.setMobData(mobData);
        return true;
    }

    /**
     * The lowest space straight down the mob's own loaded column that a falling block could be placed in: through air,
     * plants, a single snow layer, fire and fluids, never through anything else. A mob standing in a partial block (slab,
     * path, deep snow, carpet) rests on top of it. Null when nothing below can hold it.
     */
    @Nullable
    public static BlockPos restingPlace(ServerLevel level, BlockPos start) {
        BlockPos pos = start;
        if (!replaceable(level, pos)) {
            pos = pos.above();
            if (!replaceable(level, pos)) return null;
        }
        while (pos.getY() > level.getMinBuildHeight()) {
            BlockPos below = pos.below();
            if (!replaceable(level, below)) return level.isOutsideBuildHeight(pos) ? null : pos;
            pos = below;
        }
        return null;
    }

    private static boolean replaceable(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return pos.getY() >= level.getMaxBuildHeight();
        return level.getBlockState(pos).canBeReplaced(new DirectionalPlaceContext(level, pos, Direction.DOWN, ItemStack.EMPTY, Direction.UP));
    }

    private KillHandler() {}
}
