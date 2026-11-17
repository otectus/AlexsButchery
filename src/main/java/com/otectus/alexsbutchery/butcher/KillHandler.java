package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.advancement.ButcheringTrigger;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.compat.CompatLog;
import com.otectus.alexsbutchery.compat.CompatState;
import com.otectus.alexsbutchery.compat.MobSnapshot;
import com.otectus.alexsbutchery.config.ServerConfig;
import com.otectus.alexsbutchery.def.DeathSpawnMode;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModItems;
import com.otectus.alexsbutchery.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns a qualifying kill into a carcass. The rules are Butchery's ({@code CowcarcassdropProcedure}): an adult,
 * a killer entity, and with cleaver-only kills on, a cleaver or Butcher's Touch in the main hand, with Farmer's
 * Delight's knives subject to Butchery's own option for them. Unlike Butchery, which deletes vanilla drops one tick
 * later with a command, the drops the carcass yields are removed from the event itself; everything else the mob
 * drops is kept. Nothing changes unless the carcass is actually created.
 *
 * <p>Runs in the event's last phase ({@link EventPriority#LOWEST}): other mods' additions at the usual priority, such as
 * Alex's Mobs Delight re-rolling the death table for its Dimensional Slicer, are in the drop list by then, so an
 * ordinary meat drop the carcass stands for cannot come back after the filter. What is removed is per species
 * ({@link CompatState#suppression}): this creature's own replaced parts that are still in
 * {@code #alexsbutchery:replaced_by_carcass}, the death drops every installed cooking addon's profile declares for the
 * same product (raw, and cooked by a burning death), and items a pack added to the tag for every carcass kill. Stacks
 * carrying item data are never removed, so data-bearing rewards (Alex's Mobs Delight's effect-holding seagulls) and
 * named or enchanted loot survive; items dropped earlier by other mechanics are not touched.
 */
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID)
public final class KillHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
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
        DeathSpawnMode deathMode = CarcassPolicy.current().deathSpawnMode(def);
        // A refused block conversion leaves every ordinary death drop and awards no carcass progress.
        if (deathMode == DeathSpawnMode.PLACED_BLOCK
                && !CarcassPlacement.placeAtDeath(level, victim, killer, def, mobData).succeeded()) return;
        if (ServerConfig.REPLACE_MOB_DROPS.get()) removeRepresented(event, def, level);
        if (killer instanceof ServerPlayer player) {
            // Butchery grants "The Chosen One" only for Animal and Monster kills with its own cleavers.
            ButcheryHooks.grantAdvancement(player, "firstcarcass");
            ButcheringTrigger.INSTANCE.trigger(player, def, ButcheringTrigger.Action.KILL);
        }
        if (deathMode == DeathSpawnMode.ITEM) {
            ItemStack stack = carcassStack(def, mobData);
            ItemEntity item = new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(), stack);
            item.setDefaultPickUpDelay();
            event.getDrops().add(item);
        }
    }

    /** Removes the death drops the carcass stands for (see the class comment); returns what was removed. */
    static List<ItemStack> removeRepresented(LivingDropsEvent event, MobDef def, ServerLevel level) {
        CompatState state = CompatState.get(level.getServer());
        Set<Item> species = state != null ? state.suppression(def.entity()) : Set.of();
        Set<Item> global = state != null ? state.packGlobalRemovals() : Set.of();
        List<ItemStack> removed = new ArrayList<>();
        event.getDrops().removeIf(drop -> {
            ItemStack stack = drop.getItem();
            if (stack.hasTag()) return false;
            // Without rules (never on a running server) fall back to the tag alone, as before profiles existed.
            boolean represented = state == null ? stack.is(ModTags.Items.REPLACED_BY_CARCASS) : species.contains(stack.getItem()) || global.contains(stack.getItem());
            if (represented) removed.add(stack);
            return represented;
        });
        if (!removed.isEmpty() && ServerConfig.DEBUG_LOGGING.get()) CompatLog.debug("carcass kill of " + def.id() + " removed " + removed);
        return removed;
    }

    /** A fresh carcass item carrying the mob snapshot in its block entity tag. */
    public static ItemStack carcassStack(MobDef def, CompoundTag mobData) {
        ItemStack stack = new ItemStack(ModItems.of(def).carcass().get());
        PortableCarcass.writeMobData(stack, mobData);
        return stack;
    }

    /**
     * The lowest space straight down the mob's own loaded column that a falling block could be placed in: through air,
     * plants, a single snow layer, fire and fluids, never through anything else. A mob standing in a partial block (slab,
     * path, deep snow, carpet) rests on top of it. Null when nothing below can hold it.
     */
    @Nullable
    public static BlockPos restingPlace(ServerLevel level, BlockPos start) {
        return CarcassPlacement.restingPlace(level, start);
    }

    private KillHandler() {}
}
