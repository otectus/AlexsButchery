package com.otectus.alexsbutchery.data;

import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.SkinRackBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.butcher.PortableCarcass;
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
 * snapshot and whatever bleeding or acid is under way, which resumes where it is put down again; a carcass mid-butchering
 * is worth only meat scraps, like Butchery's. Heads, mounts, skeletons and rugs drop themselves with the snapshot and
 * nothing else. The skin rack's table drops Butchery's rack; its skin or leather drops as the rack's contents
 * ({@link SkinRackBlock#getDrops}).
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
            if (e.head() != null) add(e.head().get(), withMobData(e.head().get().asItem()));
            if (e.mount() != null) add(e.mount().get(), withMobData(e.mount().get().asItem()));
            if (e.skeleton() != null) add(e.skeleton().get(), withMobData(e.skeleton().get().asItem()));
            if (e.rug() != null) add(e.rug().get(), withMobData(e.rug().get().asItem()));
        }
        Block rack = ModBlocks.SKIN_RACK.get();
        // The skin or leather on it is the rack's contents, dropped by SkinRackBlock itself; only the rack is loot.
        add(rack, LootTable.lootTable()
                .withPool(applyExplosionCondition(ButcheryHooks.skinRack(), LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(ButcheryHooks.skinRack())))));
    }

    private static LootTable.Builder wholeOrScraps(AbstractCarcassBlock block, ItemLike item) {
        LootPoolEntryContainer.Builder<?> whole0 = LootItem.lootTableItem(item).apply(copyMobDataAndProcess())
                .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(block.stateProperty(), 0)));
        LootPoolEntryContainer.Builder<?> whole1 = LootItem.lootTableItem(item).apply(copyMobDataAndProcess())
                .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(block.stateProperty(), 1)));
        LootPoolEntryContainer.Builder<?> scraps = LootItem.lootTableItem(ButcheryHooks.meatScraps());
        return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                .add(AlternativesEntry.alternatives(whole0, whole1, scraps)));
    }

    private static LootTable.Builder withMobData(ItemLike item) {
        return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(item).apply(copyMobData())));
    }

    /** The appearance only: {@code MobData}, nothing else of the block entity. */
    static CopyNbtFunction.Builder copyMobData() {
        return CopyNbtFunction.copyData(ContextNbtProvider.BLOCK_ENTITY)
                .copy(CarcassBlockEntity.MOB_DATA, "BlockEntityTag." + CarcassBlockEntity.MOB_DATA);
    }

    private static CopyNbtFunction.Builder copyMobDataAndProcess() {
        CopyNbtFunction.Builder copy = copyMobData();
        for (String field : PortableCarcass.PROCESS_FIELDS) copy.copy(field, "BlockEntityTag." + field);
        return copy;
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        List<Block> blocks = new java.util.ArrayList<>(List.of(ModBlocks.allBlocks()));
        blocks.add(ModBlocks.SKIN_RACK.get());
        return blocks;
    }
}
