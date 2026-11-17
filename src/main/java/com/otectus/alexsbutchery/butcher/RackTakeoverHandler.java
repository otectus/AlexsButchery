package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.SkinRackBlock;
import com.otectus.alexsbutchery.block.entity.SkinRackBlockEntity;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.ItemDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Hanging one of our skins on Butchery's empty skin rack swaps the rack for ours, which cures it.
 *
 * <p>The swap replaces a block, so it is guarded like a placement: never for spectators, in Adventure mode only with the
 * skin's {@code CanPlaceOn} naming the rack, not under spawn protection or past the world border, and Forge's block place
 * event lets protection mods refuse it. The original rack, block entity included, is restored whenever the swap is
 * refused or does not produce our rack and its block entity; only then is the skin taken, the sound played and
 * Butchery's advancement granted.
 */
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID)
public final class RackTakeoverHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getUseItem() == Event.Result.DENY) return;
        ItemStack stack = event.getItemStack();
        if (!isOurSkin(stack)) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState clicked = level.getBlockState(pos);
        if (!clicked.is(ButcheryHooks.skinRack())) return;
        Property<?> stageProperty = clicked.getBlock().getStateDefinition().getProperty("blockstate");
        if (!(stageProperty instanceof IntegerProperty ip) || clicked.getValue(ip) != 0) return;
        Player player = event.getEntity();
        if (player.isSpectator()) return;

        event.setCanceled(true);
        if (!mayTakeOver(player, level, pos, stack)) {
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (level.isClientSide) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        event.setCancellationResult(takeOver(player, level, pos, clicked, stack));
    }

    static boolean mayTakeOver(Player player, Level level, BlockPos pos, ItemStack stack) {
        if (player.isSpectator()) return false;
        if (!player.getAbilities().mayBuild && !stack.hasAdventureModePlaceTagForBlock(
                level.registryAccess().registryOrThrow(Registries.BLOCK), new BlockInWorld(level, pos, false))) return false;
        return level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(player, pos);
    }

    private static InteractionResult takeOver(Player player, Level level, BlockPos pos, BlockState clicked, ItemStack stack) {
        BlockSnapshot original = BlockSnapshot.create(level.dimension(), level, pos);
        BlockState ours = ModBlocks.SKIN_RACK.get().defaultBlockState();
        if (clicked.hasProperty(HorizontalDirectionalBlock.FACING)) ours = ours.setValue(SkinRackBlock.FACING, clicked.getValue(HorizontalDirectionalBlock.FACING));
        boolean placed = level.setBlock(pos, ours, Block.UPDATE_ALL);
        if (!placed || !(level.getBlockEntity(pos) instanceof SkinRackBlockEntity rack)) {
            if (placed) original.restore(true, false);
            return InteractionResult.FAIL;
        }
        rack.setSkin(stack);
        if (ForgeEventFactory.onBlockPlace(player, original, Direction.UP)) {
            restore(level, original);
            return InteractionResult.FAIL;
        }
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        player.swing(InteractionHand.MAIN_HAND, true);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        // Butchery grants "Step Toward Leather" from its own rack, which is ours now.
        if (player instanceof ServerPlayer sp) ButcheryHooks.grantAdvancement(sp, "step_toward_leather");
        return InteractionResult.SUCCESS;
    }

    /** Puts the original rack back as Forge does for a refused placement; ours leaves without dropping anything. */
    private static void restore(Level level, BlockSnapshot original) {
        level.restoringBlockSnapshots = true;
        try {
            original.restore(true, false);
        } finally {
            level.restoringBlockSnapshots = false;
        }
    }

    static boolean isOurSkin(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals(AlexsButchery.MOD_ID)) return false;
        ItemDefs.ItemDef def = ItemDefs.byId(id.getPath());
        return def != null && def.kind() == ItemDefs.Kind.SKIN;
    }

    private RackTakeoverHandler() {}
}
