package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.config.ServerConfig;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Keeps the immutable policy aligned with Forge's per-world, server-to-client synchronized SERVER config. */
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CarcassPolicyEvents {
    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) CarcassPolicy.reloadFromConfig();
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != ServerConfig.SPEC) return;
        // Forge delivers a dedicated server's synchronized SERVER config to a remote client as Reloading. A running
        // local/dedicated server instead keeps worldRestart policy stable until its next Loading event.
        if (ServerLifecycleHooks.getCurrentServer() == null && !CarcassPolicy.hasAuthoritativeSnapshot()) CarcassPolicy.reloadFromConfig();
        else AlexsButchery.LOGGER.warn("Carcass policy config changed; worldRestart settings keep the running world's policy until restart");
    }

    @SubscribeEvent
    public static void onConfigUnloading(ModConfigEvent.Unloading event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) CarcassPolicy.reset();
    }

    private CarcassPolicyEvents() {}
}
