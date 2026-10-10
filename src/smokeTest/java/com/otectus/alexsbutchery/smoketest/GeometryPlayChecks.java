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
        String species = null;
        int localStep = step;
        for (String id : new String[]{"laviathan", "void_worm", "centipede", "farseer"}) {
            int count = Stages.actions(MobDefs.byId(id)).size() + 1;
            if (localStep < count) { species = id; break; }
            localStep -= count;
        }
        if (species == null) return true;
        var def = MobDefs.byId(species);
        var actions = Stages.actions(def);
        int stageIndex = localStep;
        boolean breaking = stageIndex == actions.size();
        var entry = ModBlocks.of(def);
        boolean fresh = entry.drained() == null;
        var cuttingBlock = (AbstractCarcassBlock) (fresh ? entry.carcass().get() : entry.drained().get());
        String label = def.id() + "_" + stageIndex;
        var target = base.offset(0, 6, 35);
        if (phase == 0) mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            if (stageIndex == 0) level.setBlockAndUpdate(target, cuttingBlock.defaultBlockState());
            if (breaking) level.setBlockAndUpdate(target, entry.carcass().get().defaultBlockState());
            player.setGameMode(breaking ? GameType.CREATIVE : GameType.SURVIVAL);
            player.getAbilities().mayfly = true; player.getAbilities().flying = true; player.onUpdateAbilities();
            var state = level.getBlockState(target);
            if (!(state.getBlock() instanceof AbstractCarcassBlock)) { checks.put("play_missing_stage_" + label, false); return; }
            var far = CarcassBounds.boxes(state, level, target).stream().min(Comparator.comparingDouble(b -> b.minZ)).orElseThrow();
            double x = target.getX() + far.getCenter().x, y = target.getY() + far.getCenter().y, z = target.getZ() + far.minZ - 2;
            player.teleportTo(level, x, y - player.getEyeHeight(), z, 0, 0);
            String tool = !breaking && actions.get(stageIndex).needsKnife() ? "iron_skinning_knife" : "iron_cleaver";
            player.getInventory().selected = 0;
            player.setItemInHand(InteractionHand.MAIN_HAND, breaking ? ItemStack.EMPTY
                    : new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("butchery", tool))));
        });
        if (phase == 10) {
            mc.gameRenderer.pick(1);
            boolean selected = mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(target);
            checks.put("play_client_selected_stage_" + label, selected);
            if (selected) {
                var hit = (BlockHitResult) mc.hitResult;
                if (def.id().equals("laviathan")) checks.put("play_surface_outside_anchor_" + label,
                        Math.abs(hit.getLocation().z - target.getZ() - .5) > 1.5);
                if (breaking) mc.gameMode.startDestroyBlock(target, hit.getDirection());
                else mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            }
        }
        if (phase == 25) mc.getSingleplayerServer().execute(() -> {
            var level = mc.getSingleplayerServer().overworld();
            int expected = breaking ? Stages.REMOVED : Stages.doneState(actions.get(stageIndex), fresh, false);
            checks.put("play_server_completed_stage_" + label, expected == Stages.REMOVED ? level.isEmptyBlock(target)
                    : level.getBlockState(target).is(cuttingBlock) && cuttingBlock.stage(level.getBlockState(target)) == expected);
        });
        return false;
    }
    private GeometryPlayChecks() {}
}
