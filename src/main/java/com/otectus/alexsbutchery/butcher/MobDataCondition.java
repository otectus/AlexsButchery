package com.otectus.alexsbutchery.butcher;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.registry.ModLootEntries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/**
 * {@code {"condition": "alexsbutchery:mob_data", "key": "Tusked", "unless": false}}: passes unless the carcass's mob
 * snapshot records the boolean {@code key} as {@code unless}. A stage yields an anatomical part only from a creature that
 * had it (an elephant's tusks, a moose's antlers); a carcass whose snapshot does not record it (one saved before the
 * snapshot kept it, or made by a command) yields it as before, at the stage's own chance.
 */
public record MobDataCondition(String key, boolean unless) implements LootItemCondition {

    public static LootItemCondition.Builder unless(String key, boolean value) {
        return () -> new MobDataCondition(key, value);
    }

    @Override
    public LootItemConditionType getType() {
        return ModLootEntries.MOB_DATA.get();
    }

    @Override
    public boolean test(LootContext context) {
        CompoundTag data = context.getParamOrNull(LootContextParams.BLOCK_ENTITY) instanceof CarcassBlockEntity carcass ? carcass.mobData() : null;
        if (data == null || !data.contains(key, Tag.TAG_ANY_NUMERIC)) return true;
        return data.getBoolean(key) != unless;
    }

    public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<MobDataCondition> {
        @Override
        public void serialize(JsonObject json, MobDataCondition condition, JsonSerializationContext context) {
            json.addProperty("key", condition.key);
            json.addProperty("unless", condition.unless);
        }

        @Override
        public MobDataCondition deserialize(JsonObject json, JsonDeserializationContext context) {
            return new MobDataCondition(GsonHelper.getAsString(json, "key"), GsonHelper.getAsBoolean(json, "unless"));
        }
    }
}
