package com.otectus.alexsbutchery.compat.profile;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One provider's yields for one creature's carcass, loaded from
 * {@code data/<namespace>/alexsbutchery/compat_profiles/<path>.json} (see {@link ProfileCodec} for the schema). The
 * profile applies only while every one of {@code requiredMods} is loaded; its groups replace the matching native
 * product groups of the creature's stage tables.
 */
public record CompatProfile(ResourceLocation entity, String provider, List<String> requiredMods, Map<String, GroupSpec> groups) {
    public CompatProfile {
        requiredMods = List.copyOf(requiredMods);
        groups = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(groups));
    }
}
