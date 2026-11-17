package com.otectus.alexsbutchery.compat;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.compat.profile.GroupSpec;
import com.otectus.alexsbutchery.compat.profile.ProfileCodec;
import com.otectus.alexsbutchery.registry.ModLootEntries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code {"type": "alexsbutchery:compat_yield", "entity": "alexsmobs:bison", "group": "meat", "stage": "cut_1",
 * "fallback": "alexsbutchery:carcass_native/bison/cut_1/meat"}}: one creature's product group in one butchering stage.
 *
 * <p>The server's rules ({@link CompatState}) name the providers that may answer it, in order. The first that applies
 * to this carcass (a variant-chosen item needs the carcass's saved variant) gives its item; otherwise, and whenever no
 * provider is active, the {@code fallback} table is rolled in this same loot context, so the native counts, chances
 * and functions are exactly those of the generated group. Global loot modifiers apply once, to the stage table that
 * contains this entry. Only this mod's generated stage tables contain the entry: a pack's own replacement table without
 * it yields exactly what that table says.
 */
public final class CompatYieldEntry extends LootPoolSingletonContainer {
    /** No single emission exceeds this many items, split into stacks the item allows. */
    static final int MAX_EMITTED = 9 * 64;

    final ResourceLocation entity;
    final String group;
    final String stage;
    final ResourceLocation fallback;

    CompatYieldEntry(ResourceLocation entity, String group, String stage, ResourceLocation fallback, int weight, int quality,
                     LootItemCondition[] conditions, LootItemFunction[] functions) {
        super(weight, quality, conditions, functions);
        this.entity = entity;
        this.group = group;
        this.stage = stage;
        this.fallback = fallback;
    }

    public static LootPoolSingletonContainer.Builder<?> yield(ResourceLocation entity, String group, String stage, ResourceLocation fallback) {
        return simpleBuilder((weight, quality, conditions, functions) ->
                new CompatYieldEntry(entity, group, stage, fallback, weight, quality, conditions, functions));
    }

    public ResourceLocation entity() { return entity; }
    public String group() { return group; }
    public String stage() { return stage; }
    public ResourceLocation fallback() { return fallback; }

    @Override
    public LootPoolEntryType getType() {
        return ModLootEntries.COMPAT_YIELD.get();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        CompatState state = CompatState.get(context.getLevel().getServer());
        CompatState.Resolution resolution = state == null ? CompatState.NATIVE_ONLY : state.resolution(entity, group, stage);
        CompoundTag snapshot = context.getParamOrNull(LootContextParams.BLOCK_ENTITY) instanceof CarcassBlockEntity carcass
                ? carcass.mobData() : new CompoundTag();
        for (CompatState.Choice choice : resolution.chain()) {
            if (!(choice instanceof CompatState.Provided provided)) break;
            Item item = provided.select(snapshot);
            if (item == null) continue;
            int count = provided.spec().countMode() == GroupSpec.CountMode.NATIVE_GROUP
                    ? nativeTotal(context)
                    : provided.spec().counts().get(stage).roll(context.getRandom());
            emit(output, item, count);
            return;
        }
        rollNative(context, output);
    }

    /**
     * The fallback table's items, rolled here in the same context: zero or more stacks of the native group. Like a
     * {@code minecraft:loot_table} entry, {@link LootTable#getRandomItemsRaw} guards against a table reaching itself.
     */
    private void rollNative(LootContext context, Consumer<ItemStack> output) {
        context.getResolver().getLootTable(fallback).getRandomItemsRaw(context, output);
    }

    /** The native group's total count for this roll: what {@code native_group} gives as the provider's item. */
    private int nativeTotal(LootContext context) {
        List<ItemStack> stacks = new ArrayList<>();
        rollNative(context, stacks::add);
        int total = 0;
        for (ItemStack stack : stacks) total += stack.getCount();
        return total;
    }

    private static void emit(Consumer<ItemStack> output, Item item, int count) {
        int remaining = Math.min(Math.max(count, 0), MAX_EMITTED);
        int stackSize = Math.max(1, new ItemStack(item).getMaxStackSize());
        while (remaining > 0) {
            int n = Math.min(remaining, stackSize);
            output.accept(new ItemStack(item, n));
            remaining -= n;
        }
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);
        LootDataId<LootTable> id = new LootDataId<>(LootDataType.TABLE, fallback);
        if (context.hasVisitedElement(id)) {
            context.reportProblem("Table " + fallback + " is recursively called");
        } else {
            context.resolver().getElementOptional(id).ifPresentOrElse(
                    table -> table.validate(context.enterElement("->{" + fallback + "}", id)),
                    () -> context.reportProblem("Unknown compat_yield fallback table " + fallback));
        }
    }

    public static final class Serializer extends LootPoolSingletonContainer.Serializer<CompatYieldEntry> {
        @Override
        public void serializeCustom(JsonObject json, CompatYieldEntry entry, JsonSerializationContext context) {
            super.serializeCustom(json, entry, context);
            json.addProperty("entity", entry.entity.toString());
            json.addProperty("group", entry.group);
            json.addProperty("stage", entry.stage);
            json.addProperty("fallback", entry.fallback.toString());
        }

        @Override
        protected CompatYieldEntry deserialize(JsonObject json, JsonDeserializationContext context, int weight, int quality,
                                               LootItemCondition[] conditions, LootItemFunction[] functions) {
            String stage = GsonHelper.getAsString(json, "stage");
            if (!ProfileCodec.STAGES.contains(stage)) throw new com.google.gson.JsonSyntaxException("Unknown butchering stage " + stage);
            return new CompatYieldEntry(new ResourceLocation(GsonHelper.getAsString(json, "entity")), GsonHelper.getAsString(json, "group"),
                    stage, new ResourceLocation(GsonHelper.getAsString(json, "fallback")), weight, quality, conditions, functions);
        }
    }
}
