package com.otectus.alexsbutchery.compat;

import com.otectus.alexsbutchery.config.ServerConfig;
import com.otectus.alexsbutchery.def.CompatDefs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The server's compatibility settings, read once when the server starts. Config edits made while it runs (Forge
 * reloads SERVER config files live) take effect on the next start, so yields, recipes and client displays always
 * describe one consistent set of settings; datapack {@code /reload}s reuse this snapshot.
 */
public record CompatPolicy(boolean preferExternal, List<String> providerPriority, Set<String> disabledProviders, boolean resolveConflicts) {
    public static final CompatPolicy DEFAULTS = new CompatPolicy(true, CompatDefs.DEFAULT_PRIORITY, Set.of(), true);

    public CompatPolicy {
        providerPriority = List.copyOf(new LinkedHashSet<>(providerPriority));
        disabledProviders = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(disabledProviders));
    }

    public static CompatPolicy fromConfig() {
        List<String> priority = new ArrayList<>();
        for (Object o : ServerConfig.PROVIDER_PRIORITY.get()) priority.add(String.valueOf(o).trim());
        Set<String> disabled = new LinkedHashSet<>();
        for (Object o : ServerConfig.DISABLED_PROVIDERS.get()) disabled.add(String.valueOf(o).trim());
        return new CompatPolicy(ServerConfig.PREFER_EXTERNAL_PRODUCTS.get(), priority, disabled, ServerConfig.RESOLVE_KNOWN_RECIPE_CONFLICTS.get());
    }

    public CompatPolicy withPriority(List<String> priority) {
        return new CompatPolicy(preferExternal, priority, disabledProviders, resolveConflicts);
    }

    public CompatPolicy withPreferExternal(boolean prefer) {
        return new CompatPolicy(prefer, providerPriority, disabledProviders, resolveConflicts);
    }

    public CompatPolicy withDisabled(Set<String> disabled) {
        return new CompatPolicy(preferExternal, providerPriority, disabled, resolveConflicts);
    }

    public CompatPolicy withResolveConflicts(boolean resolve) {
        return new CompatPolicy(preferExternal, providerPriority, disabledProviders, resolve);
    }

    /** Whether the provider's mappings count at all (installed is checked separately). */
    public boolean enabled(String provider) {
        return !disabledProviders.contains(provider);
    }

    public String describe() {
        return "preferExternalProducts=" + preferExternal + ", providerPriority=" + providerPriority + ", disabledProviders="
                + disabledProviders + ", resolveKnownRecipeConflicts=" + resolveConflicts;
    }
}
