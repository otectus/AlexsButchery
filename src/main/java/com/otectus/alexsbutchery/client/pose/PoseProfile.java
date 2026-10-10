package com.otectus.alexsbutchery.client.pose;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * How one mob's model is posed as a carcass and which parts each butchering stage removes. Loaded from
 * {@code assets/alexsbutchery/pose_profiles/<mob>.json}; every field is optional and falls back to the name
 * heuristics in {@link #DEFAULT}, so a mob renders acceptably before its profile is tuned.
 */
public final class PoseProfile {
    public static final PoseProfile DEFAULT = new PoseProfile();

    /** Roll around the mob's forward axis when lying (vanilla's death flip is 90). */
    public float lyingRoll = 90F;
    public float lyingPitch = 0F;
    public float lyingYaw = 0F;
    public float[] lyingOffset = {0F, 0F, 0F};
    /** Absolute local joint angles in degrees while lying, to lay limbs down instead of propping the body up on them. */
    public java.util.Map<String, float[]> lyingRotations = new java.util.LinkedHashMap<>();
    /** Parts that do not hold the lying body up (an ear, a fin, a flipper): left out of its ground contact, drawn as ever. */
    public Set<String> lyingIgnore = new LinkedHashSet<>();
    /** Extra rotation when hanging; 180 hangs the mob upside down by its feet. */
    public float hangingFlip = 180F;
    public float hangingPitch = 0F;
    /** Hip/upper-leg parts drawn inward to seat the hocks on the hook. */
    public Set<String> hangingLegs = new LinkedHashSet<>();
    public Set<String> hangingForelegs = new LinkedHashSet<>();
    /** Bend hind legs toward the tail before the body is suspended. */
    public float hangingLegPitch = 0F;
    /** Ordered attachment parts for bodies whose wings or tentacles extend beyond the torso. */
    public Set<String> hangingAttachment = new LinkedHashSet<>();
    /** Absolute local joint angles in degrees, used only while the carcass hangs. */
    public java.util.Map<String, float[]> hangingRotations = new java.util.LinkedHashMap<>();
    /** The living model is already skeletal (bone serpent, skelewag); cuts retain that anatomy. */
    public boolean skeletal = false;
    public float scale = 1F;
    public float headScale = 1F;
    public float[] headOffset = {0F, 0F, 0F};
    /** Parts that never render on a carcass (saddles, chests, held items, pouches). */
    public Set<String> alwaysHidden = new LinkedHashSet<>();
    public Set<String> head = new LinkedHashSet<>();
    public Set<String> cut1 = new LinkedHashSet<>();
    public Set<String> cut2 = new LinkedHashSet<>();
    /** The part a trophy shows; defaults to the first head part or a part named "head". */
    public String headRoot = "head";
    /** A pelt rug's head: lift above the floor and downward tilt, on top of {@link #headOffset}. */
    public float rugHeadLift = 0F;
    public float rugHeadPitch = 12F;
    /** Rug-only sizing, independent of severed heads and wall trophies. */
    public float rugHeadScale = 1F;
    /** The body segments of a multipart mob, or null. */
    @org.jetbrains.annotations.Nullable
    public SegmentChain segments;

    public boolean isHead(String part) {
        return head.isEmpty() ? HEAD_HEURISTIC.test(part) : head.contains(part);
    }

    public boolean isCut1(String part) {
        return cut1.isEmpty() ? LIMB_HEURISTIC.test(part) : cut1.contains(part);
    }

    public boolean isCut2(String part) {
        return cut2.isEmpty() ? TAIL_HEURISTIC.test(part) : cut2.contains(part);
    }

    public boolean isAlwaysHidden(String part) {
        return alwaysHidden.isEmpty() ? GEAR_HEURISTIC.test(part) : alwaysHidden.contains(part);
    }

    static final Predicate<String> HEAD_HEURISTIC = name -> {
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals("head") || n.startsWith("head") || n.endsWith("head") || n.contains("snout") || n.contains("nose")
                || n.contains("jaw") || n.contains("beak") || n.contains("ear") || n.contains("mouth") || n.contains("muzzle")
                || n.contains("trunk") || n.contains("tusk") || n.contains("horn") || n.contains("antler") || n.contains("crest")
                || n.contains("mandible") || n.contains("antenna") || n.contains("whisker");
    };
    static final Predicate<String> LIMB_HEURISTIC = name -> {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("leg") || n.contains("foot") || n.contains("feet") || n.contains("knee") || n.contains("paw")
                || n.contains("arm") || n.contains("hand") || n.contains("wing") || n.contains("fin") || n.contains("claw")
                || n.contains("hoof") || n.contains("flipper") || n.contains("thigh") || n.contains("shin") || n.contains("toe");
    };
    static final Predicate<String> TAIL_HEURISTIC = name -> name.toLowerCase(Locale.ROOT).contains("tail");
    /**
     * Saddle chests are named for their side ({@code left_chest}); a plain {@code chest} is the torso of the gorilla,
     * the rhinoceros and the Warped Mosco, and must stay.
     */
    static final Predicate<String> GEAR_HEURISTIC = name -> {
        String n = name.toLowerCase(Locale.ROOT);
        boolean saddleChest = n.contains("chest") && (n.contains("left") || n.contains("right") || n.endsWith("chests"));
        return n.contains("saddle") || saddleChest || n.contains("cabin") || n.contains("pouch")
                || n.contains("harness") || n.contains("held") || n.contains("item") || n.contains("carpet") || n.contains("armor");
    };

    public static PoseProfile fromJson(JsonObject json) {
        PoseProfile p = new PoseProfile();
        if (json.has("lying")) {
            JsonObject lying = GsonHelper.getAsJsonObject(json, "lying");
            p.lyingRoll = GsonHelper.getAsFloat(lying, "roll", p.lyingRoll);
            p.lyingPitch = GsonHelper.getAsFloat(lying, "pitch", p.lyingPitch);
            p.lyingYaw = GsonHelper.getAsFloat(lying, "yaw", p.lyingYaw);
            p.lyingOffset = vec(lying, "offset", p.lyingOffset);
            p.lyingIgnore = names(lying, "ignore");
            if (lying.has("rotations")) GsonHelper.getAsJsonObject(lying, "rotations").entrySet().forEach(e -> {
                var v = e.getValue().getAsJsonArray();
                p.lyingRotations.put(e.getKey(), new float[]{v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()});
            });
        }
        if (json.has("hanging")) {
            JsonObject hanging = GsonHelper.getAsJsonObject(json, "hanging");
            p.hangingFlip = GsonHelper.getAsFloat(hanging, "flip", p.hangingFlip);
            p.hangingPitch = GsonHelper.getAsFloat(hanging, "pitch", p.hangingPitch);
            p.hangingLegs = names(hanging, "legs");
            p.hangingForelegs = names(hanging, "forelegs");
            p.hangingAttachment = names(hanging, hanging.has("attachment") ? "attachment" : "binding");
            if (hanging.has("rotations")) GsonHelper.getAsJsonObject(hanging, "rotations").entrySet().forEach(e -> {
                var v = e.getValue().getAsJsonArray();
                p.hangingRotations.put(e.getKey(), new float[]{v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()});
            });
            p.hangingLegPitch = GsonHelper.getAsFloat(hanging, "leg_pitch", p.hangingLegPitch);
        }
        p.skeletal = GsonHelper.getAsBoolean(json, "skeletal", false);
        p.scale = GsonHelper.getAsFloat(json, "scale", p.scale);
        p.headScale = GsonHelper.getAsFloat(json, "head_scale", p.headScale);
        p.headOffset = vec(json, "head_offset", p.headOffset);
        p.headRoot = GsonHelper.getAsString(json, "head_root", p.headRoot);
        if (json.has("rug")) {
            JsonObject rug = GsonHelper.getAsJsonObject(json, "rug");
            p.rugHeadLift = GsonHelper.getAsFloat(rug, "lift", p.rugHeadLift);
            p.rugHeadPitch = GsonHelper.getAsFloat(rug, "pitch", p.rugHeadPitch);
            p.rugHeadScale = GsonHelper.getAsFloat(rug, "scale", p.rugHeadScale);
        }
        if (json.has("segments")) p.segments = SegmentChain.fromJson(GsonHelper.getAsJsonObject(json, "segments"));
        p.alwaysHidden = names(json, "hide");
        p.head = names(json, "head");
        p.cut1 = names(json, "cut_1");
        p.cut2 = names(json, "cut_2");
        return p;
    }

    private static float[] vec(JsonObject json, String key, float[] fallback) {
        if (!json.has(key)) return fallback;
        JsonArray array = GsonHelper.getAsJsonArray(json, key);
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static Set<String> names(JsonObject json, String key) {
        Set<String> out = new LinkedHashSet<>();
        if (!json.has(key)) return out;
        for (JsonElement e : GsonHelper.getAsJsonArray(json, key)) out.add(e.getAsString());
        return out;
    }
}
