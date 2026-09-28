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
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Turns a qualifying kill into a carcass. The rules are Butchery's ({@code CowcarcassdropProcedure}): an adult,
 * a killer entity, and with cleaver-only kills on, a cleaver or Butcher's Touch in the main hand. Unlike Butchery,
 * which deletes vanilla drops one tick later with a command, the drops the carcass yields are removed from the
 * event itself; everything else the mob drops is kept.
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
        if (!ButcheryHooks.weaponButchers(weapon)) return;
        if (def.bossTool() && !weapon.is(ModTags.Items.BOSS_TOOL)) return;

        if (ServerConfig.REPLACE_MOB_DROPS.get()) {
            event.getDrops().removeIf(drop -> drop.getItem().is(ModTags.Items.REPLACED_BY_CARCASS));
        }
        CompoundTag mobData = MobSnapshot.capture(victim);
        if (killer instanceof ServerPlayer player) {
            // Butchery grants "The Chosen One" only for Animal and Monster kills with its own cleavers.
            ButcheryHooks.grantAdvancement(player, "firstcarcass");
            ButcheringTrigger.INSTANCE.trigger(player, def, ButcheringTrigger.Action.KILL);
        }
        if (def.floor()) {
            placeFloorCarcass(level, victim, def, mobData);
        } else {
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

    /** Floor carcasses are too big to carry: the block falls into place where the mob died, like Butchery's warden. */
    private static void placeFloorCarcass(ServerLevel level, LivingEntity victim, MobDef def, CompoundTag mobData) {
        BlockPos pos = victim.blockPosition();
        Direction facing = victim.getDirection().getOpposite();
        BlockState state = ModBlocks.of(def).carcass().get().defaultBlockState().setValue(AbstractCarcassBlock.FACING, facing);
        FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
        if (!mobData.isEmpty()) {
            CompoundTag tag = new CompoundTag();
            tag.put(CarcassBlockEntity.MOB_DATA, mobData);
            falling.blockData = tag;
        }
        falling.dropItem = false;
    }

    private KillHandler() {}
}
