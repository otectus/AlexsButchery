package com.otectus.alexsbutchery.block;

import com.otectus.alexsbutchery.block.entity.SkinRackBlockEntity;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Butchery's skin rack while one of our skins is on it. Butchery's rack knows only its own skins by block state,
 * so hanging ours swaps the block for this one, which walks the same steps (salt, a wet sponge, 90 seconds, leather)
 * and swaps Butchery's empty rack back when the leather is taken. Rendered with Butchery's own rack models.
 */
public class SkinRackBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final int STAGE_HUNG = 0;
    public static final int STAGE_SALTED = 1;
    public static final int STAGE_CURING = 2;
    public static final int STAGE_DONE = 3;
    /** The rack's contents in its loot table ({@code minecraft:dynamic}, {@code alexsbutchery:contents}). */
    /** Butchery's "Leather Late Than Never": the curing takes one minute thirty. */
    public static final int CURE_TICKS = 1800;
    private static final VoxelShape NS = Shapes.or(Block.box(0, 0, 1, 16, 5, 15), Block.box(0, 5, 3, 16, 10, 13), Block.box(0, 10, 5, 16, 15, 11));
    private static final VoxelShape EW = Shapes.or(Block.box(1, 0, 0, 15, 5, 16), Block.box(3, 5, 0, 13, 10, 16), Block.box(5, 10, 0, 11, 15, 16));

    public SkinRackBlock() {
        super(Properties.of().sound(SoundType.WOOD).strength(1.0F, 10.0F).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return facing.getAxis() == Direction.Axis.Z ? NS : EW;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkinRackBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.SKIN_RACK.get()) return null;
        return (lvl, pos, st, be) -> ((SkinRackBlockEntity) be).serverTick();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SkinRackBlockEntity rack)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        int stage = rack.stage();
        if (stage == STAGE_DONE) {
            if (!level.isClientSide) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, new ItemStack(Items.LEATHER));
                restoreButcheryRack(level, pos, state);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stage == STAGE_HUNG && ButcheryHooks.isSalt(held)) {
            if (!level.isClientSide) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                rack.setStage(STAGE_SALTED);
                level.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.3F, 1.0F);
                if (player instanceof ServerPlayer sp) ButcheryHooks.grantAdvancement(sp, "seasonedtoperfection");
            }
            player.swing(hand, true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stage == STAGE_SALTED && ButcheryHooks.isSalt(held)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.alexsbutchery.rack_already_salted"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stage == STAGE_SALTED && ButcheryHooks.isWetSponge(held)) {
            if (!level.isClientSide) {
                ButcheryHooks.wringSponge(held);
                rack.startCuring(CURE_TICKS);
                level.playSound(null, pos, SoundEvents.WET_GRASS_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
                if (player instanceof ServerPlayer sp) ButcheryHooks.grantAdvancement(sp, "splish_splash");
            }
            player.swing(hand, true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stage == STAGE_CURING) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.alexsbutchery.rack_curing"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stage <= STAGE_SALTED && player.isShiftKeyDown() && held.isEmpty()) {
            if (!level.isClientSide) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, rack.skin().copy());
                restoreButcheryRack(level, pos, state);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** Puts Butchery's empty rack back, facing the same way. */
    public static void restoreButcheryRack(Level level, BlockPos pos, BlockState ours) {
        BlockState rack = ButcheryHooks.skinRack().defaultBlockState();
        if (rack.hasProperty(HorizontalDirectionalBlock.FACING)) rack = rack.setValue(HorizontalDirectionalBlock.FACING, ours.getValue(FACING));
        Property<?> stageProperty = rack.getBlock().getStateDefinition().getProperty("blockstate");
        if (stageProperty instanceof IntegerProperty ip) rack = rack.setValue(ip, 0);
        level.setBlock(pos, rack, Block.UPDATE_ALL);
    }

    /**
     * What the rack holds if it is destroyed now: the leather once cured, before that the skin itself (the salt and
     * sponge already used are spent). Empty without a skin.
     */
    public static ItemStack contents(SkinRackBlockEntity rack) {
        if (rack.stage() == STAGE_DONE) return new ItemStack(Items.LEATHER);
        return rack.skin().copy();
    }

    /**
     * Every destruction that drops anything (a survival player, {@code destroyBlock} with drops, an explosion) gives
     * Butchery's rack from the block's loot table and what the rack holds, as a container's contents: outside the loot
     * table, so a global loot modifier never turns the leather on the rack into something its owner could not have taken
     * off by hand (Immersive Tanning's Binding Leather, for one). Taking the leather or the raw skin off swaps the block
     * without dropping anything, so nothing is paid twice.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof SkinRackBlockEntity rack) {
            ItemStack contents = contents(rack);
            if (!contents.isEmpty()) drops.add(contents);
        }
        return drops;
    }

    /** A creative player's break rolls no loot: the skin or leather still drops, the rack does not. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.getAbilities().instabuild && level.getBlockEntity(pos) instanceof SkinRackBlockEntity rack) {
            ItemStack contents = contents(rack);
            if (!contents.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, contents);
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
