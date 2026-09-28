package com.otectus.alexsbutchery.client.gui;

import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBlock;
import com.otectus.alexsbutchery.block.DrainedCarcassBlock;
import com.otectus.alexsbutchery.block.StagedCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.butcher.Stages;
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
        if (!(state.getBlock() instanceof AbstractCarcassBlock block) || !block.def().floor()) return;
        Component text = hint(state, block, mc.level.getBlockEntity(blockHit.getBlockPos()));
        if (text == null) return;
        Font font = mc.font;
        graphics.drawCenteredString(font, text, width / 2, height / 2 + 14, 0xFFFFFF);
    }

    private static Component hint(BlockState state, AbstractCarcassBlock block, net.minecraft.world.level.block.entity.BlockEntity be) {
        MobDef def = block.def();
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
            boolean bleeding = be instanceof CarcassBlockEntity carcass && carcass.isBleeding();
            return Component.translatable(bleeding ? "hint.alexsbutchery.bleeding" : "hint.alexsbutchery.bleed");
        }
        return null;
    }
}
