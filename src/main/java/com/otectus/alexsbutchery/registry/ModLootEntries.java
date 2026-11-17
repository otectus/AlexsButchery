package com.otectus.alexsbutchery.registry;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.butcher.MobDataCondition;
import com.otectus.alexsbutchery.compat.CompatYieldEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The {@code alexsbutchery:compat_yield} loot entry and {@code alexsbutchery:mob_data} condition of the generated stage tables. */
public final class ModLootEntries {
    public static final DeferredRegister<LootPoolEntryType> ENTRIES = DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, AlexsButchery.MOD_ID);

    public static final RegistryObject<LootPoolEntryType> COMPAT_YIELD =
            ENTRIES.register("compat_yield", () -> new LootPoolEntryType(new CompatYieldEntry.Serializer()));

    public static final DeferredRegister<LootItemConditionType> CONDITIONS = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, AlexsButchery.MOD_ID);

    public static final RegistryObject<LootItemConditionType> MOB_DATA =
            CONDITIONS.register("mob_data", () -> new LootItemConditionType(new MobDataCondition.Serializer()));

    private ModLootEntries() {}
}
