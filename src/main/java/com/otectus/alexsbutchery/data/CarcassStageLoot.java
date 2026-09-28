package com.otectus.alexsbutchery.data;

import com.otectus.alexsbutchery.def.Drop;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The butchering stage tables, {@code alexsbutchery:carcass/<mob>/<stage>}, one pool per drop as in Butchery's own
 * cut tables. Rolled by the cut machine; packs can override them like any loot table.
 */
final class CarcassStageLoot implements LootTableSubProvider {

    @Override
    public void generate(BiConsumer<ResourceLocation, LootTable.Builder> out) {
        for (MobDef def : MobDefs.all()) {
            for (Map.Entry<String, List<Drop>> stage : def.drops().entrySet()) {
                LootTable.Builder table = LootTable.lootTable();
                for (Drop drop : stage.getValue()) {
                    Item item = ForgeRegistries.ITEMS.getValue(drop.item());
                    if (item == null || item == net.minecraft.world.item.Items.AIR) {
                        throw new IllegalStateException(def.id() + "/" + stage.getKey() + ": unknown item " + drop.item());
                    }
                    LootItem.Builder<?> entry = LootItem.lootTableItem(item);
                    entry.apply(drop.min() == drop.max() ? SetItemCountFunction.setCount(ConstantValue.exactly(drop.min()))
                            : SetItemCountFunction.setCount(UniformGenerator.between(drop.min(), drop.max())));
                    LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(entry);
                    if (drop.chance() < 1F) pool.when(LootItemRandomChanceCondition.randomChance(drop.chance()));
                    table.withPool(pool);
                }
                out.accept(def.stageTable(stage.getKey()), table);
            }
        }
    }
}
