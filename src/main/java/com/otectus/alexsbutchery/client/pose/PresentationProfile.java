package com.otectus.alexsbutchery.client.pose;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Resource-only tuning. Offsets are blocks in the rotated presentation frame, rotations are degrees. */
public record PresentationProfile(float scale, float maxSpan, Vec3 offset, Vec3 rotation) {
    public static final PresentationProfile DEFAULT = new PresentationProfile(1, 0, Vec3.ZERO, Vec3.ZERO);
    private static final Set<String> CONTEXTS = Set.of("gui", "ground", "fixed", "head", "none", "first_person",
            "third_person", "carry_first_person", "carry_third_person");

    public static Map<String, PresentationProfile> parse(JsonObject json) {
        Map<String, PresentationProfile> result = new LinkedHashMap<>();
        json.entrySet().forEach(entry -> {
            if (!CONTEXTS.contains(entry.getKey())) throw new IllegalArgumentException("Unknown presentation " + entry.getKey());
            JsonObject value = entry.getValue().getAsJsonObject();
            float scale = GsonHelper.getAsFloat(value, "scale", 1), cap = GsonHelper.getAsFloat(value, "max_span", 0);
            if (!Float.isFinite(scale) || scale <= 0 || scale > 16 || !Float.isFinite(cap) || cap < 0 || cap > 128)
                throw new IllegalArgumentException("presentation scale must be (0,16], max_span [0,128]");
            result.put(entry.getKey(), new PresentationProfile(scale, cap, vec(value, "offset", 32), vec(value, "rotation", 360)));
        });
        return Map.copyOf(result);
    }

    private static Vec3 vec(JsonObject json, String key, double bound) {
        if (!json.has(key)) return Vec3.ZERO;
        var array = json.getAsJsonArray(key);
        if (array.size() != 3) throw new IllegalArgumentException(key + " needs three values");
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) {
            v[i] = array.get(i).getAsDouble();
            if (!Double.isFinite(v[i]) || Math.abs(v[i]) > bound) throw new IllegalArgumentException("Invalid " + key);
        }
        return new Vec3(v[0], v[1], v[2]);
    }
}
