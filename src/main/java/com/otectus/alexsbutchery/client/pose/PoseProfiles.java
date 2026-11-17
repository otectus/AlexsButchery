package com.otectus.alexsbutchery.client.pose;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.client.render.CarcassModels;
import com.otectus.alexsbutchery.def.MobDef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client reload listener for {@code assets/alexsbutchery/pose_profiles/*.json}; F3+T reloads them. */
public final class PoseProfiles extends SimpleJsonResourceReloadListener {
    // GSON must be initialised before INSTANCE: the constructor hands it to the reload listener.
    private static final Gson GSON = new Gson();
    public static final PoseProfiles INSTANCE = new PoseProfiles();
    private Map<ResourceLocation, PoseProfile> profiles = Map.of();

    private PoseProfiles() {
        super(GSON, "pose_profiles");
    }

    /**
     * The profile for a mob, {@code pose_profiles/<mob id>.json}, or the defaults. Keyed by the mob id, not the entity
     * id: the cave centipede's entity is {@code centipede_head}.
     */
    public static PoseProfile get(MobDef def) {
        return INSTANCE.profiles.getOrDefault(AlexsButchery.id(def.id()), PoseProfile.DEFAULT);
    }

    /** Whether the mob has a profile file of its own. */
    public static boolean has(MobDef def) {
        return INSTANCE.profiles.containsKey(AlexsButchery.id(def.id()));
    }

    /**
     * Resource packs may change pose profiles; that changes only how carcasses are drawn. What can be selected and cut is
     * the anatomy exported from the bundled profiles ({@code CarcassBounds}), which the server uses too and never takes
     * from a client, so a changed pose can be drawn outside its outline. The profiles a pack changed are logged.
     */
    @Override
    protected void apply(Map<ResourceLocation, com.google.gson.JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, PoseProfile> loaded = new HashMap<>();
        List<ResourceLocation> changed = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                PoseProfile pose = PoseProfile.fromJson(json.getAsJsonObject());
                loaded.put(id, pose);
                if (pose.presentationError != null) AlexsButchery.LOGGER.warn("Bad presentation in {}: {}; using default item/carry presentation", id, pose.presentationError);
            } catch (RuntimeException e) {
                AlexsButchery.LOGGER.warn("Bad pose profile {}", id, e);
            }
            if (!json.equals(bundled(id))) changed.add(id);
        });
        profiles = Map.copyOf(loaded);
        overridden = List.copyOf(changed);
        // Textures and cached models are rebuilt from the new resources on the next frame.
        CarcassModels.clear();
        AlexsButchery.LOGGER.info("Loaded {} carcass pose profiles", loaded.size());
        if (!changed.isEmpty()) AlexsButchery.LOGGER.warn("Resource packs change {} carcass pose profiles ({}): carcasses are still selected by "
                + "the anatomy bundled with Alex's Butchery, which servers use, so these may be drawn outside their outline", changed.size(), changed);
    }

    private static List<ResourceLocation> overridden = List.of();

    /** The pose profiles the loaded resource packs changed from the bundled ones. */
    public static List<ResourceLocation> overridden() {
        return overridden;
    }

    @Nullable
    private static com.google.gson.JsonElement bundled(ResourceLocation id) {
        try (var in = PoseProfiles.class.getResourceAsStream("/assets/" + id.getNamespace() + "/pose_profiles/" + id.getPath() + ".json")) {
            return in == null ? null : com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    public static JsonObject empty() {
        return new JsonObject();
    }
}
