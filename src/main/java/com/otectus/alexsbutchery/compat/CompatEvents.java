package com.otectus.alexsbutchery.compat;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.CompatState.Diagnostic;
import com.otectus.alexsbutchery.compat.CompatState.Severity;
import com.otectus.alexsbutchery.compat.profile.ProfileLoader;
import com.otectus.alexsbutchery.compat.profile.ProfileSet;
import com.otectus.alexsbutchery.config.ServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The compatibility rules' lifecycle on a server. The settings are read when the server is about to start; the rules
 * are built when it starts (registries, tags, recipes and loot tables loaded, worlds created, no player yet) and rebuilt
 * after every successful data reload, before the reload's tags and recipes are sent to players. A reload whose profile
 * files are malformed is rejected as a whole: the server keeps the profiles it had (or, at first start, uses the native
 * drops) and says so. Clients are sent the resulting view on login and after each reload, ahead of the recipe and tag
 * packets their recipe viewers rebuild from.
 */
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID)
public final class CompatEvents {
    @Nullable private static MinecraftServer server;
    private static CompatPolicy policy = CompatPolicy.DEFAULTS;
    @Nullable private static ProfileSet lastValid;
    private static boolean started;

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ProfileLoader(event.getServerResources()));
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        server = event.getServer();
        policy = CompatPolicy.fromConfig();
        lastValid = null;
        started = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerStarting(ServerStartingEvent event) {
        if (event.getServer() != server) return;
        rebuild(event.getServer(), "start");
        started = true;
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD || !started) return;
        MinecraftServer current = ServerLifecycleHooks.getCurrentServer();
        if (current != null && current == server) rebuild(current, "reload");
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CompatState.clear(event.getServer());
        if (event.getServer() == server) {
            server = null;
            lastValid = null;
            started = false;
        }
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        CompatState state = CompatState.get(event.getPlayerList().getServer());
        if (state == null) return;
        for (ServerPlayer player : event.getPlayers()) CompatNetwork.send(player, state.view());
    }

    /** The settings this server started with (restart-required, see {@link CompatPolicy}). */
    public static CompatPolicy policy() {
        return policy;
    }

    /** Rebuilds and publishes the rules from the server's current resources. */
    public static CompatState rebuild(MinecraftServer server, String cause) {
        return rebuild(server, ProfileLoader.loaded(server.getServerResources().managers()), cause);
    }

    /**
     * Rebuilds and publishes the rules from {@code loaded} (null: the reload listener did not run). A malformed set is
     * rejected as a whole and the last valid one used instead; tests pass sets here to exercise exactly that path.
     */
    public static CompatState rebuild(MinecraftServer server, @Nullable ProfileSet loaded, String cause) {
        List<Diagnostic> carried = new ArrayList<>();
        ProfileSet use;
        boolean degraded = false;
        if (loaded == null) {
            carried.add(new Diagnostic(Severity.ERROR, "profiles", "the profile reload listener did not run for these resources"));
            use = lastValid != null ? lastValid : ProfileSet.EMPTY;
            degraded = lastValid == null;
        } else if (loaded.malformed()) {
            for (String error : loaded.errors()) carried.add(new Diagnostic(Severity.ERROR, "profiles", error));
            if (lastValid != null) {
                use = lastValid;
                carried.add(new Diagnostic(Severity.ERROR, "profiles", "this reload's profile files were rejected; the previous rules stay in force"));
            } else {
                use = ProfileSet.EMPTY;
                degraded = true;
            }
        } else {
            use = loaded;
            lastValid = loaded;
        }
        RecipePolicy.Result recipes = RecipePolicy.apply(server.getRecipeManager(), server.registryAccess(), policy);
        CompatState state = CompatResolver.build(server, use, policy, recipes, carried, degraded);
        CompatState.publish(server, state);
        report(state, cause);
        return state;
    }

    private static void report(CompatState state, String cause) {
        StringBuilder providers = new StringBuilder();
        for (CompatState.ProviderStatus p : state.providers().values()) {
            if (providers.length() > 0) providers.append(", ");
            providers.append(p.provider()).append(' ');
            if (!p.installed()) providers.append("not installed");
            else if (!p.enabled()) providers.append(p.version()).append(" disabled");
            else providers.append(p.version()).append(" (").append(p.activeProfiles()).append(" active profiles)");
        }
        long conflicts = state.recipes().outcomes().stream().filter(o -> o.status() != RecipePolicy.Status.NOT_PRESENT).count();
        AlexsButchery.LOGGER.info("Compatibility rules ({}): {}; {} profile files, {} errors, {} warnings; {} recipe conflicts; {}; view {}",
                cause, providers, state.profiles().files(), state.count(Severity.ERROR), state.count(Severity.WARNING), conflicts,
                state.policy().describe(), state.view().digest());
        for (Diagnostic d : state.diagnostics()) {
            if (d.severity() == Severity.ERROR) AlexsButchery.LOGGER.error("Compatibility: {}", d);
            else if (d.severity() == Severity.WARNING) AlexsButchery.LOGGER.warn("Compatibility: {}", d);
        }
        if (ServerConfig.DEBUG_LOGGING.get()) {
            int lines = 0;
            for (Map.Entry<CompatState.Key, CompatState.Resolution> e : state.resolutions().entrySet()) {
                if (++lines > 400) break;
                AlexsButchery.LOGGER.info("Compatibility rule {} -> {}", e.getKey(), describe(e.getValue()));
            }
        }
    }

    static String describe(CompatState.Resolution resolution) {
        List<String> links = new ArrayList<>();
        for (CompatState.Choice c : resolution.chain()) {
            if (c instanceof CompatState.Provided p) links.add(p.provider() + "(" + p.profile() + (p.conditional() ? ", by " + p.spec().variants().nbt() : "") + ")");
            else links.add("native");
        }
        return String.join(" > ", links);
    }

    /** Compatibility settings edited while the server runs wait for the restart; this says so instead of half-applying. */
    @Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ConfigWatch {
        @SubscribeEvent
        public static void onConfigReloading(ModConfigEvent.Reloading event) {
            if (event.getConfig().getSpec() != ServerConfig.SPEC || server == null) return;
            CompatPolicy edited = CompatPolicy.fromConfig();
            if (!edited.equals(policy)) AlexsButchery.LOGGER.warn("Compatibility settings changed to {}; they take effect when the server restarts "
                    + "(running with {})", edited.describe(), policy.describe());
        }

        private ConfigWatch() {}
    }

    private CompatEvents() {}
}
