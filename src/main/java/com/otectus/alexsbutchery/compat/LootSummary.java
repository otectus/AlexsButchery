package com.otectus.alexsbutchery.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.profile.CountRange;
import com.otectus.alexsbutchery.compat.profile.GroupSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Describes a loaded stage table for clients without rolling it: the table is serialised back to JSON and read
 * structurally. Pools of one item entry with constant or uniform {@code set_count} and {@code random_chance} conditions
 * are described exactly; {@code compat_yield} entries are described through the server's resolved rules. Anything else a
 * pack wrote (weights, nested tables, other functions or conditions) marks the stage custom: the client labels it as
 * server-defined instead of promising a fixed result. Never consumes randomness, spawns items or grants loot.
 */
final class LootSummary {
    record Summary(List<CompatView.OutputView> outputs, boolean custom) {}

    private final LootDataManager loot;
    private final Function<CompatState.Key, CompatState.Resolution> resolutions;

    LootSummary(LootDataManager loot, Function<CompatState.Key, CompatState.Resolution> resolutions) {
        this.loot = loot;
        this.resolutions = resolutions;
    }

    Summary table(ResourceLocation id) {
        return table(id, 0);
    }

    private Summary table(ResourceLocation id, int depth) {
        LootTable table = loot.getLootTable(id);
        if (table == LootTable.EMPTY || depth > 4) return new Summary(List.of(), table != LootTable.EMPTY);
        JsonElement json;
        try {
            json = LootDataType.TABLE.parser().toJsonTree(table);
        } catch (RuntimeException e) {
            AlexsButchery.LOGGER.debug("Cannot describe loot table {}", id, e);
            return new Summary(List.of(), true);
        }
        if (!json.isJsonObject()) return new Summary(List.of(), true);
        JsonObject root = json.getAsJsonObject();
        boolean custom = nonEmptyArray(root, "functions");
        List<CompatView.OutputView> outputs = new ArrayList<>();
        for (JsonElement poolElement : array(root, "pools")) {
            if (!poolElement.isJsonObject()) { custom = true; continue; }
            JsonObject pool = poolElement.getAsJsonObject();
            if (!constantOne(pool.get("rolls")) || (pool.has("bonus_rolls") && !zero(pool.get("bonus_rolls"))) || nonEmptyArray(pool, "functions")) custom = true;
            float poolChance = 1F;
            String needs = null;
            for (JsonElement condition : array(pool, "conditions")) {
                Float chance = randomChance(condition);
                String part = mobData(condition);
                if (chance != null) poolChance *= chance;
                else if (part != null) needs = part;
                else custom = true;
            }
            JsonArray entries = array(pool, "entries");
            if (entries.size() != 1) custom = true;
            for (JsonElement entryElement : entries) {
                if (!entryElement.isJsonObject()) { custom = true; continue; }
                JsonObject entry = entryElement.getAsJsonObject();
                String type = type(entry);
                float chance = poolChance;
                for (JsonElement condition : array(entry, "conditions")) {
                    Float c = randomChance(condition);
                    if (c == null) custom = true;
                    else chance *= c;
                }
                switch (type) {
                    case "minecraft:item" -> {
                        int[] range = {1, 1};
                        for (JsonElement function : array(entry, "functions")) if (!setCount(function, range) && !lookOnly(function)) custom = true;
                        outputs.add(new CompatView.OutputView(List.of(new ResourceLocation(entry.get("name").getAsString())), range[0], range[1],
                                chance, ProviderNames.NATIVE, needs, false, null));
                    }
                    case "alexsbutchery:compat_yield" -> {
                        if (nonEmptyArray(entry, "functions")) custom = true;
                        Summary answered = compatYield(entry, chance, depth);
                        custom |= answered.custom();
                        outputs.addAll(answered.outputs());
                    }
                    case "minecraft:empty" -> { }
                    default -> custom = true;
                }
            }
        }
        return new Summary(outputs, custom);
    }

    /**
     * A {@code compat_yield} entry as {@link CompatYieldEntry} rolls it: each provider of the chain in order, a
     * variant-chosen one only for the carcasses whose saved variant it maps (the rest fall through to the next link), up to
     * the first that always answers; the native group last, for carcasses no provider answered. Every link is an output
     * of its own, with its own counts and provider; a later link is marked {@code otherwise}.
     */
    private Summary compatYield(JsonObject entry, float chance, int depth) {
        String group = entry.get("group").getAsString();
        CompatState.Key key = new CompatState.Key(new ResourceLocation(entry.get("entity").getAsString()), group, entry.get("stage").getAsString());
        Summary fallback = table(new ResourceLocation(entry.get("fallback").getAsString()), depth + 1);
        CompatState.Resolution resolution = resolutions.apply(key);
        List<CompatView.OutputView> out = new ArrayList<>();
        boolean after = false;
        for (CompatState.Choice choice : resolution.chain()) {
            if (!(choice instanceof CompatState.Provided p)) break;
            List<ResourceLocation> items = new ArrayList<>();
            if (p.item() != null) items.add(ForgeRegistries.ITEMS.getKey(p.item()));
            else p.variantItems().values().forEach(i -> items.add(ForgeRegistries.ITEMS.getKey(i)));
            int min, max;
            float c = chance;
            if (p.spec().countMode() == GroupSpec.CountMode.NATIVE_GROUP) {
                Total total = total(fallback.outputs());
                min = total.min();
                max = total.max();
                c *= total.chance();
            } else {
                CountRange range = p.spec().counts().get(key.stage());
                if (range == null) continue;
                min = Math.min(range.min(), CompatYieldEntry.MAX_EMITTED);
                max = Math.min(range.max(), CompatYieldEntry.MAX_EMITTED);
                c *= range.chance();
            }
            if (max > 0) out.add(new CompatView.OutputView(items, min, max, c, p.provider(), p.conditional() ? p.spec().variants().nbt() : null, after, group));
            // A provider that answers every carcass ends the chain: nothing after it is ever rolled.
            if (!p.conditional()) return new Summary(out, fallback.custom());
            after = true;
        }
        for (CompatView.OutputView o : scaled(fallback.outputs(), chance)) out.add((after ? o.asOtherwise() : o).inGroup(group));
        return new Summary(out, fallback.custom());
    }

    private record Total(int min, int max, float chance) {}

    /**
     * The native group's total count, as {@code native_group} gives it for one roll: independent pools, each with its own
     * chance. It is non-zero unless every uncertain pool misses (1 - the product of the misses); its smallest non-zero
     * total is the certain pools' minimum (or, with none certain, the smallest single pool's); its largest is every
     * pool's maximum together, capped as the entry caps any emission.
     */
    private static Total total(List<CompatView.OutputView> pools) {
        if (pools.isEmpty()) return new Total(0, 0, 1F);
        int certainMin = 0, max = 0, smallest = Integer.MAX_VALUE;
        boolean certain = false;
        double miss = 1;
        for (CompatView.OutputView o : pools) {
            max += o.max();
            if (o.chance() >= 1F) {
                certain = true;
                certainMin += o.min();
            } else {
                miss *= 1 - o.chance();
                smallest = Math.min(smallest, o.min());
            }
        }
        int min = certain ? certainMin : smallest;
        return new Total(Math.min(min, CompatYieldEntry.MAX_EMITTED), Math.min(max, CompatYieldEntry.MAX_EMITTED), certain ? 1F : (float) (1 - miss));
    }

    private static List<CompatView.OutputView> scaled(List<CompatView.OutputView> outputs, float chance) {
        if (chance >= 1F) return outputs;
        List<CompatView.OutputView> out = new ArrayList<>();
        for (CompatView.OutputView o : outputs) out.add(o.withChance(o.chance() * chance));
        return out;
    }

    // ---- JSON helpers ------------------------------------------------------------------------------------------------

    private static String type(JsonObject o) {
        String type = o.has("type") ? o.get("type").getAsString() : "";
        return type.contains(":") ? type : "minecraft:" + type;
    }

    private static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private static boolean nonEmptyArray(JsonObject o, String key) {
        return array(o, key).size() > 0;
    }

    private static boolean constantOne(JsonElement rolls) {
        if (rolls == null) return false;
        if (rolls.isJsonPrimitive()) return rolls.getAsDouble() == 1.0;
        if (rolls.isJsonObject() && "minecraft:constant".equals(type(rolls.getAsJsonObject())))
            return rolls.getAsJsonObject().get("value").getAsDouble() == 1.0;
        return false;
    }

    private static boolean zero(JsonElement value) {
        if (value.isJsonPrimitive()) return value.getAsDouble() == 0.0;
        return value.isJsonObject() && "minecraft:constant".equals(type(value.getAsJsonObject())) && value.getAsJsonObject().get("value").getAsDouble() == 0.0;
    }

    /** The snapshot key of an {@code alexsbutchery:mob_data} condition (a part only creatures that have it give), or null. */
    private static String mobData(JsonElement condition) {
        if (!condition.isJsonObject()) return null;
        JsonObject o = condition.getAsJsonObject();
        return "alexsbutchery:mob_data".equals(o.has("condition") ? o.get("condition").getAsString() : "") && o.has("key") ? o.get("key").getAsString() : null;
    }

    /** The chance of a plain {@code random_chance} condition, or null for any other condition. */
    private static Float randomChance(JsonElement condition) {
        if (!condition.isJsonObject()) return null;
        JsonObject o = condition.getAsJsonObject();
        String type = o.has("condition") ? o.get("condition").getAsString() : "";
        if (!type.equals("minecraft:random_chance") && !type.equals("random_chance")) return null;
        return o.get("chance").getAsFloat();
    }

    /** A head's {@code copy_nbt} of the carcass's {@code MobData}: it changes neither the item nor the count. */
    private static boolean lookOnly(JsonElement function) {
        if (!function.isJsonObject()) return false;
        JsonObject o = function.getAsJsonObject();
        String name = o.has("function") ? o.get("function").getAsString() : "";
        return (name.equals("minecraft:copy_nbt") || name.equals("copy_nbt")) && !(o.has("conditions") && o.getAsJsonArray("conditions").size() > 0);
    }

    /** Reads a {@code set_count} into range; false for any other function or count provider. */
    private static boolean setCount(JsonElement function, int[] range) {
        if (!function.isJsonObject()) return false;
        JsonObject o = function.getAsJsonObject();
        String name = o.has("function") ? o.get("function").getAsString() : "";
        if (!name.equals("minecraft:set_count") && !name.equals("set_count")) return false;
        if (o.has("conditions") && o.getAsJsonArray("conditions").size() > 0) return false;
        if (o.has("add") && o.get("add").getAsBoolean()) return false;
        JsonElement count = o.get("count");
        if (count.isJsonPrimitive()) {
            range[0] = range[1] = (int) Math.floor(count.getAsDouble());
            return true;
        }
        if (count.isJsonObject()) {
            JsonObject c = count.getAsJsonObject();
            String type = c.has("type") ? type(c) : "minecraft:uniform";
            if (type.equals("minecraft:constant")) {
                range[0] = range[1] = (int) Math.floor(c.get("value").getAsDouble());
                return true;
            }
            if (type.equals("minecraft:uniform") && c.get("min").isJsonPrimitive() && c.get("max").isJsonPrimitive()) {
                range[0] = (int) Math.floor(c.get("min").getAsDouble());
                range[1] = (int) Math.floor(c.get("max").getAsDouble());
                return true;
            }
        }
        return false;
    }
}
