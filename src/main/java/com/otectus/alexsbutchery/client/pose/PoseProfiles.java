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

import java.util.HashMap;
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

    @Override
    protected void apply(Map<ResourceLocation, com.google.gson.JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, PoseProfile> loaded = new HashMap<>();
        files.forEach((id, json) -> {
            try {
                loaded.put(id, PoseProfile.fromJson(json.getAsJsonObject()));
            } catch (RuntimeException e) {
                AlexsButchery.LOGGER.warn("Bad pose profile {}", id, e);
            }
        });
        profiles = Map.copyOf(loaded);
        // Textures and cached models are rebuilt from the new resources on the next frame.
        CarcassModels.clear();
        AlexsButchery.LOGGER.info("Loaded {} carcass pose profiles", loaded.size());
    }

    public static JsonObject empty() {
        return new JsonObject();
    }
}
