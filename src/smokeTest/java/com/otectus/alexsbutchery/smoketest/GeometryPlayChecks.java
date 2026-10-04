package com.otectus.alexsbutchery.smoketest;

import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.Map;

/** Uses the real client picker and normal network packets against the integrated server. */
final class GeometryPlayChecks {
    static boolean tick(Minecraft mc, int tick, BlockPos base, Map<String, Object> checks) {
        int step = tick / 40, phase = tick % 40;
        if (step > 5) return true;
        var def = MobDefs.byId("laviathan");
        var target = base.offset(0, 6, 35);
        if (phase == 0) mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            if (step == 0) level.setBlockAndUpdate(target, ModBlocks.of(def).drained().get().defaultBlockState());
            if (step == 5) level.setBlockAndUpdate(target, ModBlocks.of(def).carcass().get().defaultBlockState());
            player.setGameMode(step == 5 ? GameType.CREATIVE : GameType.SURVIVAL);
            player.getAbilities().mayfly = true; player.getAbilities().flying = true; player.onUpdateAbilities();
            var state = level.getBlockState(target);
            if (!(state.getBlock() instanceof AbstractCarcassBlock)) { checks.put("play_missing_stage_" + step, false); return; }
            var far = CarcassBounds.boxes(state, level, target).stream().min(Comparator.comparingDouble(b -> b.minZ)).orElseThrow();
            double x = target.getX() + far.getCenter().x, y = target.getY() + far.getCenter().y, z = target.getZ() + far.minZ - 2;
            player.teleportTo(level, x, y - player.getEyeHeight(), z, 0, 0);
            String tool = step == 1 ? "iron_skinning_knife" : "iron_cleaver";
            player.getInventory().selected = 0;
            player.setItemInHand(InteractionHand.MAIN_HAND, step == 5 ? ItemStack.EMPTY
                    : new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("butchery", tool))));
        });
        if (phase == 10) {
            mc.gameRenderer.pick(1);
            boolean selected = mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(target);
            checks.put("play_client_selected_stage_" + step, selected);
            if (selected) {
                var hit = (BlockHitResult) mc.hitResult;
                checks.put("play_surface_outside_anchor_" + step,
                        Math.abs(hit.getLocation().z - target.getZ() - .5) > 1.5);
                if (step == 5) mc.gameMode.startDestroyBlock(target, hit.getDirection());
                else mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            }
        }
        if (phase == 25) mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            int expected = step < 4 ? new int[]{6, 7, 8, 9}[step] : Stages.REMOVED;
            checks.put("play_server_completed_stage_" + step, expected == Stages.REMOVED ? level.isEmptyBlock(target)
                    : level.getBlockState(target).hasProperty(AbstractCarcassBlock.BLOCKSTATE_STAGED)
                    && level.getBlockState(target).getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == expected);
        });
        return false;
    }
    private GeometryPlayChecks() {}
}
