package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.compat.Substitutions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolls a butchering stage's loot table as a block drop (origin, tool, state, player), swaps in another mod's
 * equivalent items where one is installed, and spawns the result.
 */
public final class CarcassLoot {

    public static List<ItemStack> roll(ServerLevel level, ResourceLocation table, BlockPos pos, BlockState state, ItemStack tool,
                                       @Nullable Player player, @Nullable BlockEntity blockEntity, int rolls) {
        LootTable lootTable = level.getServer().getLootData().getLootTable(table);
        List<ItemStack> out = new ArrayList<>();
        if (lootTable == LootTable.EMPTY) return out;
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, tool)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
                .create(LootContextParamSets.BLOCK);
        for (int i = 0; i < Math.max(1, rolls); i++) {
            for (ItemStack stack : lootTable.getRandomItems(params)) out.add(Substitutions.apply(stack));
        }
        return out;
    }

    public static void drop(ServerLevel level, BlockPos pos, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            entity.setPickUpDelay(10);
            level.addFreshEntity(entity);
        }
    }

    private CarcassLoot() {}
}
