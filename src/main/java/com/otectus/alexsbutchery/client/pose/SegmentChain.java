package com.otectus.alexsbutchery.client.pose;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The body of a multipart mob (anaconda, cave centipede, bone serpent, Void Worm), whose segments are separate
 * entities with their own renderers: which entity each segment is, the data that picks its model (its part type,
 * tail flag or size), and how far it trails the one before. Lying, the chain curls by {@code turn} degrees per
 * segment (plus {@code turn_step} more each time, a spiral; {@code wave} flips the direction every that many
 * segments, an S-curve); hanging, it hangs straight from its tail.
 * <pre>
 * "segments": {"parts": [{"entity": "alexsmobs:anaconda_part", "data": "{BodyModel:1}", "spacing": 0.9, "count": 2}],
 *              "turn": 35, "turn_step": 4, "wave": 0, "head_texture": true, "copy": ["Yellow"],
 *              "drop_head_model": true, "truncate_cuts": true, "hanging_spacing": 0.6}
 * </pre>
 */
public final class SegmentChain {

    public record Segment(ResourceLocation entity, CompoundTag data, float spacing) {}

    /** One placed segment in creature space (blocks, before the profile's scale): position and yaw in degrees. */
    public record Placement(Segment segment, float x, float z, float yaw) {}

    public final List<Segment> segments;
    public final float turn;
    public final float turnStep;
    public final int wave;
    /** Draw every segment with the head's texture (the anaconda's segments follow its yellow or shedding look). */
    public final boolean headTexture;
    /** Snapshot keys copied onto every segment's data (a variant flag the segments read too). */
    public final List<String> copy;
    /** Taking the head removes the whole head segment rather than its named head parts. */
    public final boolean dropHeadModel;
    /** Each cut takes the last third of the body away. */
    public final boolean truncateCuts;
    public final float hangingSpacing;

    private SegmentChain(JsonObject json) {
        List<Segment> list = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "parts")) {
            JsonObject part = e.getAsJsonObject();
            ResourceLocation entity = new ResourceLocation(GsonHelper.getAsString(part, "entity"));
            CompoundTag data = parse(GsonHelper.getAsString(part, "data", "{}"));
            float spacing = GsonHelper.getAsFloat(part, "spacing", 0.9F);
            int count = GsonHelper.getAsInt(part, "count", 1);
            for (int i = 0; i < count; i++) list.add(new Segment(entity, data, spacing));
        }
        this.segments = List.copyOf(list);
        this.turn = GsonHelper.getAsFloat(json, "turn", 30F);
        this.turnStep = GsonHelper.getAsFloat(json, "turn_step", 0F);
        this.wave = GsonHelper.getAsInt(json, "wave", 0);
        this.headTexture = GsonHelper.getAsBoolean(json, "head_texture", false);
        List<String> keys = new ArrayList<>();
        if (json.has("copy")) for (JsonElement e : GsonHelper.getAsJsonArray(json, "copy")) keys.add(e.getAsString());
        this.copy = List.copyOf(keys);
        this.dropHeadModel = GsonHelper.getAsBoolean(json, "drop_head_model", false);
        this.truncateCuts = GsonHelper.getAsBoolean(json, "truncate_cuts", false);
        this.hangingSpacing = GsonHelper.getAsFloat(json, "hanging_spacing", 1F);
    }

    static SegmentChain fromJson(JsonObject json) {
        return new SegmentChain(json);
    }

    private static CompoundTag parse(String snbt) {
        try {
            return TagParser.parseTag(snbt);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("Bad segment data " + snbt, e);
        }
    }

    /** How many segments remain after {@code cuts} of the three cuts. */
    public int remaining(int cuts) {
        if (!truncateCuts || cuts <= 0) return segments.size();
        int total = segments.size() + 1;
        return Math.max(0, segments.size() - Math.round(total * Math.min(cuts, 3) / 3F));
    }

    /** The segments trailing the head (at the origin, facing -Z), each placed behind the one before. */
    public List<Placement> layout(boolean straight, float spacingScale, int count) {
        List<Placement> out = new ArrayList<>(count);
        float x = 0F;
        float z = 0F;
        float heading = 0F;
        for (int i = 0; i < count && i < segments.size(); i++) {
            Segment segment = segments.get(i);
            float step = segment.spacing() * spacingScale;
            double rad = Math.toRadians(heading);
            x += (float) Math.sin(rad) * step;
            z += (float) Math.cos(rad) * step;
            out.add(new Placement(segment, x, z, heading));
            if (!straight) {
                float t = turn + i * turnStep;
                if (wave > 0 && (i / wave) % 2 == 1) t = -t;
                heading += t;
            }
        }
        return out;
    }

    /** Length of the straight chain, head origin to the end of the last segment. */
    public float straightLength(float spacingScale) {
        float length = 0F;
        for (Segment segment : segments) length += segment.spacing() * spacingScale;
        return length + (segments.isEmpty() ? 0F : segments.get(segments.size() - 1).spacing() * spacingScale * 0.5F);
    }
}
