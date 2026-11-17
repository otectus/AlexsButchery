package com.otectus.alexsbutchery.compat.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

/**
 * Reads every {@code data/<namespace>/alexsbutchery/compat_profiles/**.json} of a data reload. Unlike vanilla's JSON
 * listeners, a file that fails to parse is recorded instead of skipped, so one broken pack file can never quietly
 * re-enable a profile it meant to disable. The result is kept per server resource set and applied by
 * {@code CompatEvents} once the reload has finished (registries, tags and recipes in place).
 */
public final class ProfileLoader extends SimplePreparableReloadListener<ProfileSet> {
    public static final String DIRECTORY = "alexsbutchery/compat_profiles";
    private static final FileToIdConverter FILES = FileToIdConverter.json(DIRECTORY);
    private static final Map<ReloadableServerResources, ProfileSet> LOADED = new WeakHashMap<>();

    private final ReloadableServerResources resources;

    public ProfileLoader(ReloadableServerResources resources) {
        this.resources = resources;
    }

    /** The profiles loaded with this resource set, or null when its reload has not run (never for a running server). */
    @Nullable
    public static synchronized ProfileSet loaded(ReloadableServerResources resources) {
        return LOADED.get(resources);
    }

    @Override
    protected ProfileSet prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, String> raw = new TreeMap<>();
        Map<ResourceLocation, String> packs = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> file : FILES.listMatchingResources(manager).entrySet()) {
            ResourceLocation id = FILES.fileToId(file.getKey());
            packs.put(id, file.getValue().sourcePackId());
            try (Reader reader = file.getValue().openAsReader()) {
                raw.put(id, readAll(reader));
            } catch (IOException e) {
                errors.add(id + " (" + file.getValue().sourcePackId() + "): unreadable: " + e.getMessage());
            }
        }
        ProfileSet parsed = parse(raw, packs);
        if (errors.isEmpty()) return parsed;
        List<String> all = new ArrayList<>(errors);
        all.addAll(parsed.errors());
        return new ProfileSet(parsed.profiles(), parsed.selections(), parsed.disabled(), parsed.sources(), all);
    }

    @Override
    protected void apply(ProfileSet set, ResourceManager manager, ProfilerFiller profiler) {
        synchronized (ProfileLoader.class) {
            LOADED.put(resources, set);
        }
    }

    /** Parses file contents by resource id; also used by tests and by the documented examples' validation. */
    public static ProfileSet parse(Map<ResourceLocation, String> files, Map<ResourceLocation, String> packs) {
        Map<ResourceLocation, CompatProfile> profiles = new LinkedHashMap<>();
        Map<ResourceLocation, ProviderSelection> selections = new LinkedHashMap<>();
        Set<ResourceLocation> disabled = new LinkedHashSet<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<ResourceLocation, String> file : new TreeMap<>(files).entrySet()) {
            ResourceLocation id = file.getKey();
            String where = id + " (" + packs.getOrDefault(id, "?") + ")";
            try {
                JsonElement json = JsonParser.parseString(file.getValue());
                ProfileCodec.Entry entry = ProfileCodec.parse(json);
                if (entry instanceof ProfileCodec.ProfileEntry p) profiles.put(id, p.profile());
                else if (entry instanceof ProfileCodec.SelectionEntry s) selections.put(id, s.selection());
                else disabled.add(id);
            } catch (JsonParseException | IllegalStateException e) {
                errors.add(where + ": not valid JSON: " + e.getMessage());
            } catch (ProfileCodec.ProfileException e) {
                errors.add(where + ": " + e.getMessage());
            }
        }
        return new ProfileSet(profiles, selections, disabled, packs, errors);
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[4096];
        for (int n; (n = reader.read(buffer)) > 0; ) out.append(buffer, 0, n);
        return out.toString();
    }
}
