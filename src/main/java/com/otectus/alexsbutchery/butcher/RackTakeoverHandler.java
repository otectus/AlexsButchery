package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.SkinRackBlock;
import com.otectus.alexsbutchery.block.entity.SkinRackBlockEntity;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.ItemDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Hanging one of our skins on Butchery's empty skin rack swaps the rack for ours, which cures it. */
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID)
public final class RackTakeoverHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack stack = event.getItemStack();
        if (!isOurSkin(stack)) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState clicked = level.getBlockState(pos);
        if (!clicked.is(ButcheryHooks.skinRack())) return;
        Property<?> stageProperty = clicked.getBlock().getStateDefinition().getProperty("blockstate");
        if (!(stageProperty instanceof IntegerProperty ip) || clicked.getValue(ip) != 0) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide) return;
        BlockState ours = ModBlocks.SKIN_RACK.get().defaultBlockState();
        if (clicked.hasProperty(HorizontalDirectionalBlock.FACING)) ours = ours.setValue(SkinRackBlock.FACING, clicked.getValue(HorizontalDirectionalBlock.FACING));
        level.setBlock(pos, ours, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof SkinRackBlockEntity rack) rack.setSkin(stack);
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        event.getEntity().swing(InteractionHand.MAIN_HAND, true);
        if (!event.getEntity().getAbilities().instabuild) stack.shrink(1);
        // Butchery grants "Step Toward Leather" from its own rack, which is ours now.
        if (event.getEntity() instanceof ServerPlayer player) ButcheryHooks.grantAdvancement(player, "step_toward_leather");
    }

    static boolean isOurSkin(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals(AlexsButchery.MOD_ID)) return false;
        ItemDefs.ItemDef def = ItemDefs.byId(id.getPath());
        return def != null && def.kind() == ItemDefs.Kind.SKIN;
    }

    private RackTakeoverHandler() {}
}
