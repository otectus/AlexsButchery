package com.otectus.alexsbutchery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.DrainedCarcassBlock;
import com.otectus.alexsbutchery.block.HeadBlock;
import com.otectus.alexsbutchery.block.HeadMountBlock;
import com.otectus.alexsbutchery.block.MobBlock;
import com.otectus.alexsbutchery.block.RugBlock;
import com.otectus.alexsbutchery.block.SkeletonBlock;
import com.otectus.alexsbutchery.block.StagedCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.client.pose.PoseProfile;
import com.otectus.alexsbutchery.client.pose.PoseProfiles;
import com.otectus.alexsbutchery.config.ClientConfig;
import com.otectus.alexsbutchery.def.MobDef;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Draws every mob block with the mob's own Alex's Mobs model: carcasses lying on their side or hanging from the hook
 * above, skeletons the same way in bone, heads on their own, trophies on Butchery's plaque and a rug's head at its
 * front edge. The block state says which stages are done; the pose profile says how the model lies and which parts
 * each stage removed.
 */
public class CarcassRenderer implements BlockEntityRenderer<CarcassBlockEntity> {

    public CarcassRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CarcassBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = entity.getBlockState();
        if (!(state.getBlock() instanceof MobBlock mobBlock)) return;
        MobDef def = mobBlock.def();
        CarcassModels.Handle handle = CarcassModels.get(def.entity());
        if (handle == null) return;
        PoseProfile profile = PoseProfiles.get(def);
        if (state.getBlock() instanceof HeadBlock) {
            CarcassScene.renderHead(handle, def, profile, entity.mobData(), state.getBlock() instanceof HeadMountBlock,
                    state.getValue(HeadBlock.FACING), pose, buffers, light, overlay);
            return;
        }
        if (state.getBlock() instanceof RugBlock) {
            CarcassScene.renderRugHead(handle, profile, entity.mobData(), state.getValue(RugBlock.FACING), pose, buffers, light);
            return;
        }
        if (!(state.getBlock() instanceof AbstractCarcassBlock block)) return;
        int stage = block.stage(state);
        Set<Stages.Action> done = block instanceof DrainedCarcassBlock ? Stages.done(def, false, stage)
                : block instanceof StagedCarcassBlock ? Stages.done(def, true, stage) : Set.of();
        StageTextures.Look look = block instanceof SkeletonBlock ? StageTextures.Look.BONE
                : block instanceof DrainedCarcassBlock
                ? (done.contains(Stages.Action.SKIN) ? StageTextures.Look.SKINNED : StageTextures.Look.DRAINED)
                : StageTextures.Look.FRESH;
        CarcassScene.Subject subject = new CarcassScene.Subject(def, handle, profile, entity.mobData(), look, done);
        Direction facing = state.getValue(AbstractCarcassBlock.FACING);
        if (block.hanging(state)) CarcassScene.renderHanging(subject, facing,
                entity.getLevel().getBlockState(entity.getBlockPos().above()), pose, buffers, light);
        else CarcassScene.renderLying(subject, facing, pose, buffers, light);
    }

    @Override
    public boolean shouldRenderOffScreen(CarcassBlockEntity entity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return ClientConfig.CARCASS_RENDER_DISTANCE.get();
    }
}
