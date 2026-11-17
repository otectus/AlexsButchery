package com.otectus.alexsbutchery.client.gui;

import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBlock;
import com.otectus.alexsbutchery.block.DrainedCarcassBlock;
import com.otectus.alexsbutchery.block.StagedCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.butcher.Bleeding;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.butcher.CarcassPolicy;
import com.otectus.alexsbutchery.config.ClientConfig;
import com.otectus.alexsbutchery.def.MobDef;
import net.mcreator.butchery.configuration.ButcheryconfigConfiguration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * A one-line tool hint under the crosshair for floor carcasses, which Butchery's own hint overlays do not know
 * (they key on its hook-carcass tags). Respects Butchery's "Show Hints" option.
 */
public final class FloorHintOverlay implements IGuiOverlay {

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = gui.getMinecraft();
        if (mc.level == null || mc.options.hideGui || !ClientConfig.FLOOR_HINTS.get() || !ButcheryconfigConfiguration.SHOW_HINTS.get()) return;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockState state = mc.level.getBlockState(blockHit.getBlockPos());
        if (!(state.getBlock() instanceof AbstractCarcassBlock block)) return;
        boolean groundBleeding = CarcassPolicy.current().canBleedOnGround(block.def());
        boolean unsupportedHanging = block instanceof CarcassBlock fresh && fresh.hanging(state)
                && !Bleeding.canBleedAt(mc.level, blockHit.getBlockPos(), state, block.def());
        // Butchery supplies the ordinary hook hint. Cover configured ground processing and stale hanging state restored
        // by a carrier, whose static tag hint would otherwise claim it can bleed without a hook.
        if (!groundBleeding && !unsupportedHanging) return;
        Component text = hint(state, block, mc.level.getBlockEntity(blockHit.getBlockPos()), !unsupportedHanging);
        if (text == null) return;
        Font font = mc.font;
        // Configured ground bleeding species still belong to Butchery's static hook-carcass tag. Cover that static
        // "hang it" line before drawing the server-authoritative action so the two hints cannot contradict each other.
        graphics.fill(width / 2 - 140, height / 2 + 12, width / 2 + 140, height / 2 + 25, 0xA0000000);
        graphics.drawCenteredString(font, text, width / 2, height / 2 + 14, 0xFFFFFF);
    }

    private static Component hint(BlockState state, AbstractCarcassBlock block,
                                  net.minecraft.world.level.block.entity.BlockEntity be, boolean canBleedHere) {
        MobDef def = block.def();
        // The same rule as the interaction itself: while acid works, no tool does anything.
        if (be instanceof CarcassBlockEntity carcass && carcass.isDissolving()) return Component.translatable("hint.alexsbutchery.dissolving");
        if (block instanceof StagedCarcassBlock || block instanceof DrainedCarcassBlock) {
            Stages.Step step = Stages.next(def, block instanceof StagedCarcassBlock, block.stage(state));
            if (step == null) return null;
            return switch (step.action()) {
                case HEAD -> Component.translatable("hint.alexsbutchery.head");
                case SKIN -> Component.translatable(def.skin() == com.otectus.alexsbutchery.def.SkinStep.PLUCK ? "hint.alexsbutchery.pluck" : "hint.alexsbutchery.skin");
                default -> Component.translatable("hint.alexsbutchery.cut");
            };
        }
        if (block instanceof CarcassBlock) {
            if (!canBleedHere) return Component.translatable("jade.alexsbutchery.hang");
            boolean bleeding = be instanceof CarcassBlockEntity carcass && carcass.isBleeding();
            return Component.translatable(bleeding ? "hint.alexsbutchery.bleeding" : "hint.alexsbutchery.bleed");
        }
        return null;
    }
}
