package com.otectus.alexsbutchery.compat.profile;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every {@code alexsbutchery/compat_profiles} file of one data reload, by resource id (the file path without
 * {@code .json}). Files that failed to parse are listed in {@code errors}; a set with errors is never applied (the
 * server keeps its previous rules), see {@code CompatState}.
 */
public record ProfileSet(Map<ResourceLocation, CompatProfile> profiles, Map<ResourceLocation, ProviderSelection> selections,
                         Set<ResourceLocation> disabled, Map<ResourceLocation, String> sources, List<String> errors) {
    public static final ProfileSet EMPTY = new ProfileSet(Map.of(), Map.of(), Set.of(), Map.of(), List.of());

    public ProfileSet {
        profiles = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        selections = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(selections));
        disabled = java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(disabled));
        sources = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        errors = List.copyOf(errors);
    }

    public boolean malformed() {
        return !errors.isEmpty();
    }

    public int files() {
        return profiles.size() + selections.size() + disabled.size() + errors.size();
    }
}
