package com.otectus.alexsbutchery.compat.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Schema version 1 of {@code alexsbutchery/compat_profiles} files. A file is one of three kinds:
 *
 * <pre>
 * yield profile:  {"schema_version": 1, "entity": "alexsmobs:crocodile", "provider": "alexsmobsdelight",
 *                  "required_mods": ["alexsmobsdelight"],
 *                  "groups": {"meat": {"item": "alexsmobsdelight:raw_crocodile_meat", "count_mode": "native_group",
 *                                      "death_equivalents": ["alexsmobsdelight:raw_crocodile_meat"]}}}
 * selection:      {"schema_version": 1, "entity": "alexsmobs:bison", "groups": ["meat"],
 *                  "provider_priority": ["alexsdelight", "alexsmobsdelight"]}
 * disable:        {"schema_version": 1, "disabled": true}
 * </pre>
 *
 * A group takes {@code item} or {@code variants} ({@code {"nbt": "Variant", "items": {"0": "...", "1": "..."}}}),
 * {@code count_mode} {@code native_group} (optional {@code stages}) or {@code per_stage} ({@code counts}: stage to a
 * count or {@code {"min", "max", "chance"}}), and optional {@code death_equivalents}. Unknown keys are errors, so a
 * misspelt key never silently changes a yield; {@code comment} is allowed everywhere.
 */
public final class ProfileCodec {
    public static final int SCHEMA_VERSION = 1;
    public static final Set<String> STAGES = Set.of("head", "skin", "pluck", "cut_1", "cut_2", "cut_3");
    private static final Pattern PROVIDER = Pattern.compile("[a-z0-9_.-]+");

    /** A parsed file. */
    public sealed interface Entry permits ProfileEntry, SelectionEntry, DisabledEntry {}
    public record ProfileEntry(CompatProfile profile) implements Entry {}
    public record SelectionEntry(ProviderSelection selection) implements Entry {}
    public record DisabledEntry() implements Entry {}

    /** A schema violation, with the JSON path that caused it. */
    public static final class ProfileException extends Exception {
        public ProfileException(String message) {
            super(message);
        }
    }

    public static Entry parse(JsonElement json) throws ProfileException {
        if (!json.isJsonObject()) throw new ProfileException("the file must be a JSON object");
        JsonObject root = json.getAsJsonObject();
        int version = integer(root, "schema_version", "schema_version");
        if (version != SCHEMA_VERSION) throw new ProfileException("schema_version " + version + " is not supported (expected " + SCHEMA_VERSION + ")");
        if (root.has("disabled")) {
            keys(root, "", "schema_version", "disabled", "comment");
            if (!bool(root, "disabled", "disabled")) throw new ProfileException("disabled must be true; delete the file to enable a profile");
            return new DisabledEntry();
        }
        if (root.has("provider_priority")) {
            keys(root, "", "schema_version", "entity", "groups", "provider_priority", "comment");
            ResourceLocation entity = id(root, "entity", "entity");
            List<String> groups = root.has("groups") ? strings(root.get("groups"), "groups", false) : List.of();
            List<String> priority = strings(root.get("provider_priority"), "provider_priority", true);
            for (int i = 0; i < priority.size(); i++) {
                String provider = priority.get(i);
                if (!PROVIDER.matcher(provider).matches()) throw new ProfileException("provider_priority[" + i + "]: invalid provider \"" + provider + "\"");
                if (priority.indexOf(provider) != i) throw new ProfileException("provider_priority lists \"" + provider + "\" twice");
            }
            return new SelectionEntry(new ProviderSelection(entity, groups, priority));
        }
        keys(root, "", "schema_version", "entity", "provider", "required_mods", "groups", "comment");
        ResourceLocation entity = id(root, "entity", "entity");
        String provider = string(root, "provider", "provider");
        if (!PROVIDER.matcher(provider).matches()) throw new ProfileException("provider: invalid provider \"" + provider + "\"");
        if (ProviderSelection.NATIVE.equals(provider)) throw new ProfileException("provider: \"native\" is reserved for the built-in drops");
        List<String> requiredMods = root.has("required_mods") ? strings(root.get("required_mods"), "required_mods", false) : List.of();
        if (!root.has("groups") || !root.get("groups").isJsonObject() || root.getAsJsonObject("groups").size() == 0)
            throw new ProfileException("groups: expected a non-empty object");
        Map<String, GroupSpec> groups = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> group : root.getAsJsonObject("groups").entrySet()) {
            groups.put(group.getKey(), group(group.getValue(), "groups." + group.getKey()));
        }
        return new ProfileEntry(new CompatProfile(entity, provider, requiredMods, groups));
    }

    private static GroupSpec group(JsonElement json, String path) throws ProfileException {
        if (!json.isJsonObject()) throw new ProfileException(path + ": expected an object");
        JsonObject o = json.getAsJsonObject();
        keys(o, path, "item", "variants", "count_mode", "stages", "counts", "death_equivalents", "comment");
        String modeName = string(o, "count_mode", path + ".count_mode");
        GroupSpec.CountMode mode = GroupSpec.CountMode.byJson(modeName);
        if (mode == null) throw new ProfileException(path + ".count_mode: expected native_group or per_stage, got \"" + modeName + "\"");
        if (o.has("item") == o.has("variants")) throw new ProfileException(path + ": give exactly one of item or variants");
        ResourceLocation item = o.has("item") ? id(o, "item", path + ".item") : null;
        GroupSpec.Variants variants = o.has("variants") ? variants(o.get("variants"), path + ".variants") : null;
        List<String> stages = new ArrayList<>();
        Map<String, CountRange> counts = new LinkedHashMap<>();
        if (mode == GroupSpec.CountMode.NATIVE_GROUP) {
            if (o.has("counts")) throw new ProfileException(path + ".counts: native_group keeps the native counts; use per_stage for explicit counts");
            if (o.has("stages")) {
                stages = strings(o.get("stages"), path + ".stages", true);
                for (String stage : stages) stage(stage, path + ".stages");
            }
        } else {
            if (o.has("stages")) throw new ProfileException(path + ".stages: per_stage groups cover the stages named in counts");
            if (!o.has("counts") || !o.get("counts").isJsonObject() || o.getAsJsonObject("counts").size() == 0)
                throw new ProfileException(path + ".counts: expected a non-empty object");
            for (Map.Entry<String, JsonElement> count : o.getAsJsonObject("counts").entrySet()) {
                stage(count.getKey(), path + ".counts");
                counts.put(count.getKey(), count(count.getValue(), path + ".counts." + count.getKey()));
            }
        }
        List<ResourceLocation> equivalents = new ArrayList<>();
        if (o.has("death_equivalents")) {
            List<String> raw = strings(o.get("death_equivalents"), path + ".death_equivalents", false);
            for (int i = 0; i < raw.size(); i++) equivalents.add(parseId(raw.get(i), path + ".death_equivalents[" + i + "]"));
        }
        return new GroupSpec(mode, item, variants, stages, counts, equivalents);
    }

    private static GroupSpec.Variants variants(JsonElement json, String path) throws ProfileException {
        if (!json.isJsonObject()) throw new ProfileException(path + ": expected an object");
        JsonObject o = json.getAsJsonObject();
        keys(o, path, "nbt", "items", "comment");
        String nbt = string(o, "nbt", path + ".nbt");
        if (!o.has("items") || !o.get("items").isJsonObject() || o.getAsJsonObject("items").size() == 0)
            throw new ProfileException(path + ".items: expected a non-empty object");
        Map<Integer, ResourceLocation> items = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("items").entrySet()) {
            int value;
            try {
                value = Integer.parseInt(e.getKey());
            } catch (NumberFormatException ex) {
                throw new ProfileException(path + ".items: key \"" + e.getKey() + "\" is not an integer");
            }
            if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isString())
                throw new ProfileException(path + ".items." + e.getKey() + ": expected an item id");
            items.put(value, parseId(e.getValue().getAsString(), path + ".items." + e.getKey()));
        }
        return new GroupSpec.Variants(nbt, items);
    }

    private static CountRange count(JsonElement json, String path) throws ProfileException {
        try {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) return CountRange.exactly(wholeNumber(json, path));
            if (!json.isJsonObject()) throw new ProfileException(path + ": expected a count or {\"min\", \"max\", \"chance\"}");
            JsonObject o = json.getAsJsonObject();
            keys(o, path, "min", "max", "chance", "comment");
            int min = integer(o, "min", path + ".min");
            int max = integer(o, "max", path + ".max");
            float chance = o.has("chance") ? number(o, "chance", path + ".chance") : 1F;
            return new CountRange(min, max, chance);
        } catch (IllegalArgumentException e) {
            throw new ProfileException(path + ": invalid " + e.getMessage() + " (counts are 0.." + CountRange.MAX_COUNT + ", min <= max, 0 < chance <= 1)");
        }
    }

    private static void stage(String stage, String path) throws ProfileException {
        if (!STAGES.contains(stage)) throw new ProfileException(path + ": unknown stage \"" + stage + "\" (expected one of " + STAGES + ")");
    }

    // ---- writing (datagen) ---------------------------------------------------------------------------------------

    public static JsonObject toJson(CompatProfile profile) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.addProperty("entity", profile.entity().toString());
        root.addProperty("provider", profile.provider());
        JsonArray mods = new JsonArray();
        profile.requiredMods().forEach(mods::add);
        root.add("required_mods", mods);
        JsonObject groups = new JsonObject();
        profile.groups().forEach((name, spec) -> groups.add(name, toJson(spec)));
        root.add("groups", groups);
        return root;
    }

    private static JsonObject toJson(GroupSpec spec) {
        JsonObject o = new JsonObject();
        if (spec.item() != null) o.addProperty("item", spec.item().toString());
        else {
            JsonObject variants = new JsonObject();
            variants.addProperty("nbt", spec.variants().nbt());
            JsonObject items = new JsonObject();
            spec.variants().items().forEach((value, item) -> items.addProperty(Integer.toString(value), item.toString()));
            variants.add("items", items);
            o.add("variants", variants);
        }
        o.addProperty("count_mode", spec.countMode().json);
        if (!spec.stages().isEmpty()) {
            JsonArray stages = new JsonArray();
            spec.stages().forEach(stages::add);
            o.add("stages", stages);
        }
        if (!spec.counts().isEmpty()) {
            JsonObject counts = new JsonObject();
            spec.counts().forEach((stage, range) -> {
                if (range.min() == range.max() && range.chance() >= 1F) counts.addProperty(stage, range.min());
                else {
                    JsonObject r = new JsonObject();
                    r.addProperty("min", range.min());
                    r.addProperty("max", range.max());
                    if (range.chance() < 1F) r.addProperty("chance", range.chance());
                    counts.add(stage, r);
                }
            });
            o.add("counts", counts);
        }
        JsonArray equivalents = new JsonArray();
        spec.deathEquivalents().forEach(id -> equivalents.add(id.toString()));
        o.add("death_equivalents", equivalents);
        return o;
    }

    public static JsonObject toJson(ProviderSelection selection) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.addProperty("entity", selection.entity().toString());
        if (!selection.groups().isEmpty()) {
            JsonArray groups = new JsonArray();
            selection.groups().forEach(groups::add);
            root.add("groups", groups);
        }
        JsonArray priority = new JsonArray();
        selection.providerPriority().forEach(priority::add);
        root.add("provider_priority", priority);
        return root;
    }

    // ---- primitives ------------------------------------------------------------------------------------------------

    private static void keys(JsonObject o, String path, String... allowed) throws ProfileException {
        Set<String> set = Set.of(allowed);
        for (String key : o.keySet()) {
            if (!set.contains(key)) throw new ProfileException((path.isEmpty() ? "" : path + ".") + key + ": unknown key (expected one of " + String.join(", ", allowed) + ")");
        }
    }

    private static String string(JsonObject o, String key, String path) throws ProfileException {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isEmpty())
            throw new ProfileException(path + ": expected a non-empty string");
        return e.getAsString();
    }

    private static boolean bool(JsonObject o, String key, String path) throws ProfileException {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw new ProfileException(path + ": expected true or false");
        return e.getAsBoolean();
    }

    private static int integer(JsonObject o, String key, String path) throws ProfileException {
        JsonElement e = o.get(key);
        if (e == null) throw new ProfileException(path + ": missing");
        return wholeNumber(e, path);
    }

    private static int wholeNumber(JsonElement e, String path) throws ProfileException {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new ProfileException(path + ": expected a whole number");
        double value = e.getAsDouble();
        if (value != Math.rint(value) || Math.abs(value) > 1_000_000) throw new ProfileException(path + ": expected a whole number");
        return (int) value;
    }

    private static float number(JsonObject o, String key, String path) throws ProfileException {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new ProfileException(path + ": expected a number");
        return e.getAsFloat();
    }

    private static ResourceLocation id(JsonObject o, String key, String path) throws ProfileException {
        return parseId(string(o, key, path), path);
    }

    private static ResourceLocation parseId(String raw, String path) throws ProfileException {
        if (!raw.contains(":")) throw new ProfileException(path + ": \"" + raw + "\" needs a namespace (for example minecraft:" + raw + ")");
        try {
            return new ResourceLocation(raw);
        } catch (ResourceLocationException e) {
            throw new ProfileException(path + ": invalid id \"" + raw + "\"");
        }
    }

    private static List<String> strings(@Nullable JsonElement e, String path, boolean nonEmpty) throws ProfileException {
        if (e == null || !e.isJsonArray()) throw new ProfileException(path + ": expected a list of strings");
        List<String> out = new ArrayList<>();
        JsonArray array = e.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement v = array.get(i);
            if (!(v instanceof JsonPrimitive p) || !p.isString() || p.getAsString().isEmpty())
                throw new ProfileException(path + "[" + i + "]: expected a non-empty string");
            out.add(p.getAsString());
        }
        if (nonEmpty && out.isEmpty()) throw new ProfileException(path + ": expected at least one entry");
        return out;
    }

    private ProfileCodec() {}
}
