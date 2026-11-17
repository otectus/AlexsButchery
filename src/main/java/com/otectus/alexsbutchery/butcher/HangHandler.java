package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.advancement.ButcheringTrigger;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import com.otectus.alexsbutchery.item.MobBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hanging a carcass on Butchery's hook or rope, as Butchery's {@code Place<mob>carcassProcedure} does: the block
 * below must be air, the carcass appears there hanging ({@code blockstate 1}), a rope advances one state (3 and 7
 * are full), and the rope sound plays. Fresh and drained carcasses both hang, and so do skeletons.
 *
 * <p>The carcass is placed by the ordinary item placement, as a click on the underside of the hook
 * ({@code AbstractCarcassBlock#getStateForPlacement} hangs it), so everything that guards placing a block guards hanging
 * one: Adventure mode needs the item's {@code CanPlaceOn} for the hook or rope, spectators never place, spawn protection
 * and the world border are checked at the destination below the hook, and Forge's block place event lets protection
 * mods refuse it, the placement then being rolled back with the item restored. The rope only advances, the sound only
 * plays and advancements are only granted once the hanging carcass and its block entity are really there. An earlier
 * handler's cancellation or denial of item use is respected.
 */
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID)
public final class HangHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getUseItem() == Event.Result.DENY) return;
        ItemStack stack = event.getItemStack();
        ModItems.Owner owner = ModItems.owner(stack.getItem());
        if (owner == null || (owner.kind() != ModItems.Kind.CARCASS && owner.kind() != ModItems.Kind.DRAINED
                && owner.kind() != ModItems.Kind.SKELETON)) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState clicked = level.getBlockState(pos);
        if (!isSupport(clicked)) return;
        Player player = event.getEntity();
        if (player.isSpectator()) return;
        MobDef def = owner.def();
        if (!def.hookable()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.alexsbutchery.too_heavy_to_hang"), true);
            return;
        }
        boolean rope = clicked.is(ButcheryHooks.rope());
        int ropeState = rope ? intState(clicked) : -1;
        if (rope && (ropeState == 3 || ropeState == 7 || ropeState < 0)) return;
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isAir()) return;

        // From here the click is ours either way: a refused hanging must not turn into an ordinary placement beside the hook.
        event.setCanceled(true);
        if (!mayHang(player, level, pos, below, stack)) {
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (level.isClientSide) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        event.setCancellationResult(hang(player, level, pos, below, clicked, stack, def, owner.kind(), rope, ropeState));
    }

    /** Butchery's hook or rope, the blocks a carcass hangs from. */
    public static boolean isSupport(BlockState state) {
        return state.is(ButcheryHooks.hook()) || state.is(ButcheryHooks.rope());
    }

    /**
     * What the player may do here, on both sides: build at all (Adventure mode only with the item's {@code CanPlaceOn}
     * naming the clicked hook or rope, as for any block placement), and reach a destination inside the world, its border
     * and outside spawn protection.
     */
    static boolean mayHang(Player player, Level level, BlockPos support, BlockPos below, ItemStack stack) {
        if (player.isSpectator()) return false;
        if (!player.getAbilities().mayBuild && !stack.hasAdventureModePlaceTagForBlock(
                level.registryAccess().registryOrThrow(Registries.BLOCK), new BlockInWorld(level, support, false))) return false;
        return !level.isOutsideBuildHeight(below) && level.getWorldBorder().isWithinBounds(below)
                && level.mayInteract(player, support) && level.mayInteract(player, below);
    }

    private static InteractionResult hang(Player player, Level level, BlockPos support, BlockPos below, BlockState originalSupport,
                                          ItemStack stack, MobDef def,
                                          ModItems.Kind kind, boolean rope, int ropeState) {
        Block block = switch (kind) {
            case CARCASS -> ModBlocks.of(def).carcass().get();
            case DRAINED -> ModBlocks.of(def).drained().get();
            default -> ModBlocks.of(def).skeleton().get();
        };
        ItemStack originalStack = stack.copy();
        BlockSnapshot originalDestination = BlockSnapshot.create(level.dimension(), level, below);
        // The ordinary placement: Forge captures the change, posts the place event and undoes it (item included) if refused.
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(support), Direction.DOWN, support, false));
        InteractionResult placed;
        CarcassBlockEntity installed;
        try (MobBlockItem.PlacementCapture capture = MobBlockItem.capturePlacement(level, below)) {
            placed = stack.useOn(context);
            installed = capture.placed();
        }
        BlockState now = level.getBlockState(below);
        CarcassBlockEntity carcass = level.getBlockEntity(below) instanceof CarcassBlockEntity found ? found : null;
        if (!placed.consumesAction() || !now.is(block) || !((AbstractCarcassBlock) block).hanging(now)
                || carcass == null
                || !PortableCarcass.matches(originalStack, carcass)
                || level.getBlockState(support) != originalSupport) {
            if (now.is(block) && (carcass == installed || carcass == null))
                rollbackPlaced(level, below, now, carcass, originalDestination);
            restoreStack(stack, originalStack);
            return InteractionResult.FAIL;
        }
        if (rope && !advanceRope(level, support, originalSupport, ropeState)) {
            if (carcass == installed) rollbackPlaced(level, below, now, carcass, originalDestination);
            restoreStack(stack, originalStack);
            return InteractionResult.FAIL;
        }
        SoundEvent sound = ButcheryHooks.ropeSound();
        if (sound != null) level.playSound(null, support, sound, SoundSource.BLOCKS, 0.5F, 1.0F);
        player.swing(InteractionHand.MAIN_HAND, true);
        if (player instanceof ServerPlayer sp && kind != ModItems.Kind.SKELETON) {
            // Butchery's "Hanging Around" only knows its own carcass items; the rest of its tree follows from here.
            ButcheryHooks.grantAdvancement(sp, "hangingaround");
            ButcheringTrigger.INSTANCE.trigger(sp, def, ButcheringTrigger.Action.HANG);
        }
        return InteractionResult.SUCCESS;
    }

    private static int intState(BlockState state) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty("blockstate");
        return property instanceof IntegerProperty ip ? state.getValue(ip) : -1;
    }

    /** Butchery's rope steps 0-1-2-3 and 4-5-6-7 as carcasses are hung. */
    private static boolean advanceRope(Level level, BlockPos pos, BlockState state, int current) {
        if (current == 3 || current == 7 || !state.is(ButcheryHooks.rope()) || intState(state) != current) return false;
        Property<?> property = state.getBlock().getStateDefinition().getProperty("blockstate");
        if (property instanceof IntegerProperty ip && ip.getPossibleValues().contains(current + 1)) {
            BlockState next = state.setValue(ip, current + 1);
            return level.setBlock(pos, next, Block.UPDATE_ALL) && level.getBlockState(pos) == next;
        }
        return false;
    }

    private static void rollbackPlaced(Level level, BlockPos pos, BlockState placed, @org.jetbrains.annotations.Nullable CarcassBlockEntity carcass,
                                       BlockSnapshot original) {
        if (level.getBlockState(pos) != placed || level.getBlockEntity(pos) != carcass) return;
        boolean wasRestoring = level.restoringBlockSnapshots;
        level.restoringBlockSnapshots = true;
        try {
            original.restore(true, false);
        } finally {
            level.restoringBlockSnapshots = wasRestoring;
        }
    }

    private static void restoreStack(ItemStack stack, ItemStack original) {
        stack.setCount(original.getCount());
        stack.setTag(original.hasTag() ? original.getTag().copy() : null);
    }

    private HangHandler() {}
}
