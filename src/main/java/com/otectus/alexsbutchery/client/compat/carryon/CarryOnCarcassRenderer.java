package com.otectus.alexsbutchery.client.compat.carryon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.MobBlock;
import com.otectus.alexsbutchery.client.compat.carryon.mixin.CarryOnDataAccessor;
import com.otectus.alexsbutchery.client.render.CarcassPresentation;
import com.otectus.alexsbutchery.client.render.CarcassModels;
import com.otectus.alexsbutchery.client.render.CarcassVisualState;
import com.otectus.alexsbutchery.config.ClientConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import tschipp.carryon.client.modeloverride.ModelOverrideHandler;
import tschipp.carryon.client.render.CarryRenderHelper;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnDataManager;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only boundary around Carry On 2.1.2.7. All callers are gated by the optional mixin plugin. */
public final class CarryOnCarcassRenderer {
    private static final float CARRY_ON_SCALE = 0.6F;
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private record Candidate(CarryOnData data, CarcassVisualState visual) {}
    private record Cached(CompoundTag raw, CarcassVisualState visual) {}
    private static final Map<UUID, Cached> VISUALS = new LinkedHashMap<>(16, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, Cached> entry) { return size() > 64; }
    };

    static {
        CarcassModels.addClearListener(CarryOnCarcassRenderer::clear);
    }

    /** Replaces the camera-frame icon render. Carry On's 2.5 scale existed to undo ordinary item icon fitting. */
    public static boolean renderFirstPerson(Player player, MultiBufferSource buffers, PoseStack pose, int light) {
        Candidate candidate = candidate(player);
        if (candidate == null) return false;
        PoseStack.Pose frame = pose.last();
        try {
            pose.pushPose();
            // The presentation anchors its measured torso at this camera-space grip. No selection bounds participate.
            pose.translate(0, -0.52, -1.15);
            candidate.data().getActiveScript().ifPresent(script -> CarryRenderHelper.performScriptTransformation(pose, script));
            CarcassPresentation.renderCarry(candidate.visual(), true, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            return true;
        } catch (RuntimeException error) {
            warn(error);
            return false;
        } finally {
            while (pose.last() != frame) pose.popPose();
        }
    }

    /**
     * Retains Carry On's player position, interpolated yaw, crouch, swimming and fall-flying transforms. It then
     * exactly removes the generic selection-height adjustment and the 0.6 item normalization, leaving one grip frame.
     */
    public static boolean applyThirdPersonTransform(Player player, float partialTick, PoseStack pose, Block renderedBlock) {
        Candidate candidate = candidate(player);
        if (candidate == null) return false;
        PoseStack.Pose frame = pose.last();
        try {
            CarryRenderHelper.applyBlockTransformations(player, partialTick, pose, renderedBlock);
            float genericHeight = CarryRenderHelper.getRenderHeight(player);
            pose.translate(0, (genericHeight - 1F) / 1.2F, 0);
            pose.scale(1F / CARRY_ON_SCALE, 1F / CARRY_ON_SCALE, 1F / CARRY_ON_SCALE);
            return true;
        } catch (RuntimeException error) {
            while (pose.last() != frame) pose.popPose();
            warn(error);
            return false;
        }
    }

    /** Called at the exact per-player baked-model invocation, after Carry On's cosmetic script transform. */
    public static boolean renderThirdPerson(Player player, MultiBufferSource buffers, PoseStack pose, int light) {
        Candidate candidate = candidate(player);
        if (candidate == null) return false;
        PoseStack.Pose frame = pose.last();
        try {
            CarcassPresentation.renderCarry(candidate.visual(), false, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            return true;
        } catch (RuntimeException error) {
            warn(error);
            return false;
        } finally {
            while (pose.last() != frame) pose.popPose();
        }
    }

    private static CarryOnData data(Player player) {
        return ClientConfig.CARRY_ON_PRESENTATION.get() ? CarryOnDataManager.getCarryData(player) : null;
    }

    private static Candidate candidate(Player player) {
        try {
            CarryOnData data = data(player);
            CarcassVisualState visual = visual(player, data);
            return visual == null || hasIntentionalOverride(data, visual.state()) ? null : new Candidate(data, visual);
        } catch (RuntimeException error) {
            warn(error);
            return null;
        }
    }

    private static CarcassVisualState visual(Player player, CarryOnData data) {
        if (data == null || !data.isCarrying(CarryOnData.CarryType.BLOCK)) {
            VISUALS.remove(player.getUUID());
            return null;
        }
        CompoundTag raw = ((CarryOnDataAccessor) (Object) data).alexsbutchery$getRawCarryData();
        Cached cached = VISUALS.get(player.getUUID());
        // CarryOnDataManager already returns a defensive NBT copy. Retain that immutable snapshot and avoid another
        // tile copy/model-state decode on every first/third-person render call; equals catches in-place synced changes.
        if (cached != null && cached.raw().equals(raw)) return cached.visual();
        BlockState state = data.getBlock(); // Unlike CarryRenderHelper.getRenderState(), retains the captured properties.
        if (!(state.getBlock() instanceof MobBlock)) return null;
        CompoundTag tile = raw.contains("tile", Tag.TAG_COMPOUND) ? raw.getCompound("tile") : new CompoundTag();
        CarcassVisualState visual = CarcassVisualState.from(state, tile);
        if (visual != null) VISUALS.put(player.getUUID(), new Cached(raw, visual));
        return visual;
    }

    /** Explicit block/item model substitutions win. Pure script transforms remain composable with this adapter. */
    private static boolean hasIntentionalOverride(CarryOnData data, BlockState actual) {
        if (ModelOverrideHandler.getModelOverride(actual, data.getContentNbt()).isPresent()) return true;
        return data.getActiveScript().map(script -> {
            var render = script.scriptRender();
            return render.renderNameBlock().isPresent() || render.renderNameEntity().isPresent() || render.renderNBT().isPresent();
        }).orElse(false);
    }

    private static void warn(RuntimeException error) {
        if (WARNED.compareAndSet(false, true)) {
            AlexsButchery.LOGGER.warn("Carry On carcass presentation failed; falling back to Carry On's renderer", error);
        }
    }

    private static void clear() {
        VISUALS.clear();
        WARNED.set(false);
    }

    private CarryOnCarcassRenderer() {}
}
