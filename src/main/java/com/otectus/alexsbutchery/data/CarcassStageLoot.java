package com.otectus.alexsbutchery.data;

import com.otectus.alexsbutchery.butcher.MobDataCondition;
import com.otectus.alexsbutchery.compat.CompatYieldEntry;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The butchering stage tables, {@code alexsbutchery:carcass/<mob>/<stage>}, one pool per drop as in Butchery's own
 * cut tables. Rolled by the cut machine; packs can override them like any loot table.
 *
 * <p>A product group's drops (the meat, a shell, a tail; see {@link Drop#group()}) are not listed in the stage table
 * itself: at the group's first position there is one {@code alexsbutchery:compat_yield} pool, and the group's native
 * pools, unchanged, form the internal table {@code alexsbutchery:carcass_native/<mob>/<stage>/<group>} that the entry
 * rolls when no compatibility profile answers. Without a profile the stage therefore yields exactly what the plain
 * table used to; side products (fat, hide, bones, hooves...) stay ordinary pools a profile never touches. The mob's own
 * head item copies the carcass block entity's {@code MobData} (the cut machine passes the block entity to the roll).
 */
final class CarcassStageLoot implements LootTableSubProvider {

    @Override
    public void generate(BiConsumer<ResourceLocation, LootTable.Builder> out) {
        for (MobDef def : MobDefs.all()) {
            for (Map.Entry<String, List<Drop>> stage : def.drops().entrySet()) {
                LootTable.Builder table = LootTable.lootTable();
                Set<String> groups = new HashSet<>();
                for (Drop drop : stage.getValue()) {
                    if (drop.group() == null) {
                        table.withPool(pool(def, stage.getKey(), drop));
                    } else if (groups.add(drop.group())) {
                        ResourceLocation fallback = def.nativeGroupTable(stage.getKey(), drop.group());
                        table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                                .add(CompatYieldEntry.yield(def.entity(), drop.group(), stage.getKey(), fallback)));
                        LootTable.Builder group = LootTable.lootTable();
                        for (Drop member : def.groupDrops(stage.getKey(), drop.group())) group.withPool(pool(def, stage.getKey(), member));
                        out.accept(fallback, group);
                    }
                }
                out.accept(def.stageTable(stage.getKey()), table);
            }
        }
    }

    private static LootPool.Builder pool(MobDef def, String stage, Drop drop) {
        Item item = ForgeRegistries.ITEMS.getValue(drop.item());
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            throw new IllegalStateException(def.id() + "/" + stage + ": unknown item " + drop.item());
        }
        LootItem.Builder<?> entry = LootItem.lootTableItem(item);
        entry.apply(drop.min() == drop.max() ? SetItemCountFunction.setCount(ConstantValue.exactly(drop.min()))
                : SetItemCountFunction.setCount(UniformGenerator.between(drop.min(), drop.max())));
        // The head keeps the creature's look (the carcass's MobData), as the head block and its trophy draw it.
        // Meat, horns, feathers and every other product stay plain items.
        if (drop.item().equals(def.headId())) entry.apply(ModBlockLoot.copyMobData());
        LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(entry);
        if (drop.chance() < 1F) pool.when(LootItemRandomChanceCondition.randomChance(drop.chance()));
        if (drop.needs() != null) pool.when(MobDataCondition.unless(drop.needs(), false));
        return pool;
    }
}
