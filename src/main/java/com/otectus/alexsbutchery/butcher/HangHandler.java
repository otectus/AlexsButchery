package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.advancement.ButcheringTrigger;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hanging a carcass on Butchery's hook or rope, as Butchery's {@code Place<mob>carcassProcedure} does: the block
 * below must be air, the carcass appears there hanging ({@code blockstate 1}), a rope advances one state (3 and 7
 * are full), and the rope sound plays. Fresh and drained carcasses both hang, and so do skeletons.
 */
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID)
public final class HangHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack stack = event.getItemStack();
        ModItems.Owner owner = ModItems.owner(stack.getItem());
        if (owner == null || (owner.kind() != ModItems.Kind.CARCASS && owner.kind() != ModItems.Kind.DRAINED
                && owner.kind() != ModItems.Kind.SKELETON)) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState clicked = level.getBlockState(pos);
        boolean hook = clicked.is(ButcheryHooks.hook());
        boolean rope = clicked.is(ButcheryHooks.rope());
        if (!hook && !rope) return;
        Player player = event.getEntity();
        MobDef def = owner.def();
        if (def.floor()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.alexsbutchery.too_heavy_to_hang"), true);
            return;
        }
        int ropeState = rope ? intState(clicked) : -1;
        if (rope && (ropeState == 3 || ropeState == 7 || ropeState < 0)) return;
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isAir()) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide) return;

        Block block = switch (owner.kind()) {
            case CARCASS -> ModBlocks.of(def).carcass().get();
            case DRAINED -> ModBlocks.of(def).drained().get();
            default -> ModBlocks.of(def).skeleton().get();
        };
        AbstractCarcassBlock carcassBlock = (AbstractCarcassBlock) block;
        BlockState placed = block.defaultBlockState()
                .setValue(AbstractCarcassBlock.FACING, player.getDirection().getOpposite())
                .setValue(carcassBlock.stateProperty(), 1);
        level.setBlock(below, placed, Block.UPDATE_ALL);
        BlockItem.updateCustomBlockEntityTag(level, player, below, stack);
        if (rope) advanceRope(level, pos, clicked, ropeState);
        SoundEvent sound = ButcheryHooks.ropeSound();
        if (sound != null) level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.5F, 1.0F);
        player.swing(InteractionHand.MAIN_HAND, true);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (player instanceof ServerPlayer sp && owner.kind() != ModItems.Kind.SKELETON) {
            // Butchery's "Hanging Around" only knows its own carcass items; the rest of its tree follows from here.
            ButcheryHooks.grantAdvancement(sp, "hangingaround");
            ButcheringTrigger.INSTANCE.trigger(sp, def, ButcheringTrigger.Action.HANG);
        }
    }

    private static int intState(BlockState state) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty("blockstate");
        return property instanceof IntegerProperty ip ? state.getValue(ip) : -1;
    }

    /** Butchery's rope steps 0-1-2-3 and 4-5-6-7 as carcasses are hung. */
    private static void advanceRope(Level level, BlockPos pos, BlockState state, int current) {
        if (current == 3 || current == 7) return;
        Property<?> property = state.getBlock().getStateDefinition().getProperty("blockstate");
        if (property instanceof IntegerProperty ip && ip.getPossibleValues().contains(current + 1)) {
            level.setBlock(pos, state.setValue(ip, current + 1), Block.UPDATE_ALL);
        }
    }

    private HangHandler() {}
}
