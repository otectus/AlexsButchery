package com.otectus.alexsbutchery.block;

import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.butcher.Acid;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.registry.ModBlockEntities;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Shared shape of every carcass block. The integer property is literally named {@code blockstate} with Butchery's
 * numbering so Butchery's tag-driven hints, drips and advancements read our blocks like its own. Rendered by
 * CarcassRenderer with the mob's own Alex's Mobs model; no collision, like Butchery's carcasses.
 */
public abstract class AbstractCarcassBlock extends Block implements EntityBlock, MobBlock {
    /** Fresh bleeding carcass: 0 on the ground, 1 hanging. */
    public static final IntegerProperty BLOCKSTATE_FRESH = IntegerProperty.create("blockstate", 0, 1);
    /** Drained carcasses and skinless (no-blood) carcasses: Butchery's 0..9 cut numbering. */
    public static final IntegerProperty BLOCKSTATE_STAGED = IntegerProperty.create("blockstate", 0, 9);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape GROUND = Block.box(0, 0, 0, 16, 10, 16);
    private static final VoxelShape HANGING = Block.box(2, -14, 2, 14, 16, 14);

    protected final MobDef def;

    protected AbstractCarcassBlock(MobDef def) {
        this(def, SoundType.HONEY_BLOCK);
    }

    protected AbstractCarcassBlock(MobDef def, SoundType sound) {
        super(Properties.of().sound(sound).strength(1.0F, 10.0F).noOcclusion()
                .isRedstoneConductor((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false));
        this.def = def;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(stateProperty(), 0));
    }

    @Override
    public MobDef def() {
        return def;
    }

    /** The {@code blockstate} property of this block; called from the Block constructor, so it must not read fields. */
    public abstract IntegerProperty stateProperty();

    /** Whether the carcass in this state hangs from a hook (rendered upside down, shape extends below). */
    public abstract boolean hanging(BlockState state);

    public int stage(BlockState state) {
        return state.getValue(stateProperty());
    }

    /**
     * Acid dissolves a whole carcass whatever else the block does with a right-click; everything else is the
     * block's own {@link #useTool}.
     */
    @Override
    public final InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (ButcheryHooks.isAcidBottle(player.getItemInHand(hand))) {
            InteractionResult acid = Acid.pour(level, pos, state, this, player, hand);
            if (acid != InteractionResult.PASS) return acid;
        }
        return useTool(state, level, pos, player, hand, hit);
    }

    /** A right-click with anything but acid. */
    protected InteractionResult useTool(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /** Bleeding and acid both count down in the block entity, on the server only. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.CARCASS.get()) return null;
        return (lvl, pos, st, be) -> CarcassBlockEntity.serverTick(lvl, pos, st, (CarcassBlockEntity) be);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, stateProperty());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return hanging(state) ? HANGING : GROUND;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return new CarcassBlockEntity(pos, state);
    }
}
