package com.otectus.alexsbutchery.compat.profile;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A pack's provider order for one creature (optionally only some of its product groups), ahead of the server's
 * {@code compat.providerPriority}. {@code native} in the list stops there: later providers are never used.
 */
public record ProviderSelection(ResourceLocation entity, List<String> groups, List<String> providerPriority) {
    public static final String NATIVE = "native";

    public ProviderSelection {
        groups = List.copyOf(groups);
        providerPriority = List.copyOf(providerPriority);
    }

    public boolean appliesTo(String group) {
        return groups.isEmpty() || groups.contains(group);
    }
}
