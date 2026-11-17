package com.otectus.alexsbutchery.compat;

import com.otectus.alexsbutchery.compat.profile.GroupSpec;
import com.otectus.alexsbutchery.compat.profile.ProfileSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The resolved compatibility rules of one running server: for every creature, product group and stage, the ordered
 * providers that may answer it (ending in the native drops), the items a carcass kill removes from the death drops,
 * which of our items no source yields any more, the recipe policy result and what clients are told. Built by
 * {@link CompatResolver} when the server starts and after each successful data reload, published atomically, and
 * dropped when the server stops; nothing here is shared between servers or worlds.
 */
public final class CompatState {
    public record Key(ResourceLocation entity, String group, String stage) {
        @Override
        public String toString() {
            return entity + " " + group + " @ " + stage;
        }
    }

    public enum Severity { INFO, WARNING, ERROR }

    public record Diagnostic(Severity severity, String source, String message) {
        @Override
        public String toString() {
            return severity + " " + source + ": " + message;
        }
    }

    /** One link of a resolution chain: a provider's group, or the native drops. */
    public sealed interface Choice permits Provided, Native {}

    public record Provided(ResourceLocation profile, String provider, GroupSpec spec, @Nullable Item item,
                           Map<Integer, Item> variantItems) implements Choice {
        /** The item this carcass yields, or null when the profile does not apply to it (unknown variant). */
        @Nullable
        public Item select(CompoundTag snapshot) {
            if (item != null) return item;
            if (!snapshot.contains(spec.variants().nbt(), net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) return null;
            return variantItems.get(snapshot.getInt(spec.variants().nbt()));
        }

        /** Whether some carcasses of this creature fall through to the next link (variant-chosen items). */
        public boolean conditional() {
            return item == null;
        }
    }

    public enum Native implements Choice { INSTANCE }

    /** A profile that could have answered a key and why it does not. */
    public record Rejection(ResourceLocation profile, String provider, String reason, Severity severity) {}

    public record Resolution(List<Choice> chain, List<Rejection> rejected) {
        public Resolution {
            chain = List.copyOf(chain);
            rejected = List.copyOf(rejected);
        }

        /** The choice a carcass without variant data gets (the first unconditional link). */
        public Choice primary() {
            for (Choice c : chain) if (!(c instanceof Provided p) || !p.conditional()) return c;
            return Native.INSTANCE;
        }
    }

    /** An optional provider mod as this server sees it. */
    public record ProviderStatus(String provider, boolean installed, String version, boolean enabled, int activeProfiles) {}

    public static final Resolution NATIVE_ONLY = new Resolution(List.of(Native.INSTANCE), List.of());

    final CompatPolicy policy;
    final ProfileSet profiles;
    final Map<Key, Resolution> resolutions;
    final Map<ResourceLocation, Set<Item>> suppression;
    final Map<ResourceLocation, Set<Item>> nativeSuppression;
    final Set<Item> packGlobalRemovals;
    final Set<ResourceLocation> hiddenItems;
    final Map<String, ProviderStatus> providers;
    final List<Diagnostic> diagnostics;
    final RecipePolicy.Result recipes;
    final CompatView view;
    final boolean degraded;

    CompatState(CompatPolicy policy, ProfileSet profiles, Map<Key, Resolution> resolutions, Map<ResourceLocation, Set<Item>> suppression,
                Map<ResourceLocation, Set<Item>> nativeSuppression, Set<Item> packGlobalRemovals, Set<ResourceLocation> hiddenItems,
                Map<String, ProviderStatus> providers, List<Diagnostic> diagnostics, RecipePolicy.Result recipes, CompatView view,
                boolean degraded) {
        this.policy = policy;
        this.profiles = profiles;
        this.resolutions = Collections.unmodifiableMap(resolutions);
        this.suppression = Collections.unmodifiableMap(suppression);
        this.nativeSuppression = Collections.unmodifiableMap(nativeSuppression);
        this.packGlobalRemovals = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(packGlobalRemovals));
        this.hiddenItems = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(hiddenItems));
        this.providers = Collections.unmodifiableMap(providers);
        this.diagnostics = List.copyOf(diagnostics);
        this.recipes = recipes;
        this.view = view;
        this.degraded = degraded;
    }

    public CompatPolicy policy() { return policy; }
    public ProfileSet profiles() { return profiles; }
    public Map<Key, Resolution> resolutions() { return resolutions; }
    public Map<String, ProviderStatus> providers() { return providers; }
    public List<Diagnostic> diagnostics() { return diagnostics; }
    public RecipePolicy.Result recipes() { return recipes; }
    public CompatView view() { return view; }
    public Set<ResourceLocation> hiddenItems() { return hiddenItems; }
    public Set<Item> packGlobalRemovals() { return packGlobalRemovals; }
    /** True when the loaded profile files were malformed and nothing was retained: every creature yields native items. */
    public boolean degraded() { return degraded; }

    public Resolution resolution(ResourceLocation entity, String group, String stage) {
        Resolution r = resolutions.get(new Key(entity, group, stage));
        return r == null ? NATIVE_ONLY : r;
    }

    /** What a carcass kill of this creature removes from the death drops (with {@code replaceMobDrops} on). */
    public Set<Item> suppression(ResourceLocation entity) {
        return suppression.getOrDefault(entity, Set.of());
    }

    /** The part of {@link #suppression} that comes from this mod's own native definitions (governed by the tag). */
    public Set<Item> nativeSuppression(ResourceLocation entity) {
        return nativeSuppression.getOrDefault(entity, Set.of());
    }

    public long count(Severity severity) {
        return diagnostics.stream().filter(d -> d.severity() == severity).count();
    }

    // ---- per-server holder -------------------------------------------------------------------------------------------

    private record Holder(MinecraftServer server, CompatState state) {}

    private static volatile Holder current;

    /** The rules of {@code server}, or null before it has started (callers then use the native drops). */
    @Nullable
    public static CompatState get(MinecraftServer server) {
        Holder h = current;
        return h != null && h.server == server ? h.state : null;
    }

    static void publish(MinecraftServer server, CompatState state) {
        current = new Holder(server, state);
    }

    static void clear(MinecraftServer server) {
        Holder h = current;
        if (h != null && h.server == server) current = null;
    }

    /**
     * Runs {@code body} with {@code state} published for {@code server}, then restores the previous rules. For
     * GameTests that compare policies within one synchronous test body.
     */
    public static void withState(MinecraftServer server, CompatState state, Runnable body) {
        Holder previous = current;
        current = new Holder(server, state);
        try {
            body.run();
        } finally {
            current = previous;
        }
    }
}
