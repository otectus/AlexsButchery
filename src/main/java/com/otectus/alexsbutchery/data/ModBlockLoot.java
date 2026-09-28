package com.otectus.alexsbutchery.data;

import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.CopyNbtFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.nbt.ContextNbtProvider;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.List;
import java.util.Set;

/**
 * What breaking a mob block drops. A whole carcass (Butchery states 0 and 1) comes back as its item with the mob
 * snapshot; a carcass mid-butchering is worth only meat scraps, like Butchery's. Heads, mounts and skeletons drop
 * themselves with the snapshot, rugs drop themselves.
 */
final class ModBlockLoot extends BlockLootSubProvider {

    ModBlockLoot() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    protected void generate() {
        for (ModBlocks.Entries e : ModBlocks.all()) {
            AbstractCarcassBlock carcass = e.carcass().get();
            add(carcass, wholeOrScraps(carcass, carcass.asItem()));
            if (e.drained() != null) add(e.drained().get(), wholeOrScraps(e.drained().get(), e.drained().get().asItem()));
            if (e.head() != null) add(e.head().get(), withMobData(e.head().get(), e.head().get().asItem()));
            if (e.mount() != null) add(e.mount().get(), withMobData(e.mount().get(), e.mount().get().asItem()));
            if (e.skeleton() != null) add(e.skeleton().get(), withMobData(e.skeleton().get(), e.skeleton().get().asItem()));
            if (e.rug() != null) dropSelf(e.rug().get());
        }
        add(ModBlocks.SKIN_RACK.get(), noDrop());
    }

    private static LootTable.Builder wholeOrScraps(AbstractCarcassBlock block, ItemLike item) {
        LootPoolEntryContainer.Builder<?> whole0 = LootItem.lootTableItem(item).apply(copyMobData())
                .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(block.stateProperty(), 0)));
        LootPoolEntryContainer.Builder<?> whole1 = LootItem.lootTableItem(item).apply(copyMobData())
                .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(block.stateProperty(), 1)));
        LootPoolEntryContainer.Builder<?> scraps = LootItem.lootTableItem(ButcheryHooks.meatScraps());
        return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                .add(AlternativesEntry.alternatives(whole0, whole1, scraps)));
    }

    private static LootTable.Builder withMobData(Block block, ItemLike item) {
        return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(item).apply(copyMobData())));
    }

    private static CopyNbtFunction.Builder copyMobData() {
        return CopyNbtFunction.copyData(ContextNbtProvider.BLOCK_ENTITY)
                .copy(CarcassBlockEntity.MOB_DATA, "BlockEntityTag." + CarcassBlockEntity.MOB_DATA);
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        List<Block> blocks = new java.util.ArrayList<>(List.of(ModBlocks.allBlocks()));
        blocks.add(ModBlocks.SKIN_RACK.get());
        return blocks;
    }
}
