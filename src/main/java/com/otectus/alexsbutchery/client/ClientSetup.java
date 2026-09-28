package com.otectus.alexsbutchery.client;

import com.otectus.alexsbutchery.client.gui.FloorHintOverlay;
import com.otectus.alexsbutchery.client.pose.PoseProfiles;
import com.otectus.alexsbutchery.client.render.CarcassModels;
import com.otectus.alexsbutchery.client.render.CarcassRenderer;
import com.otectus.alexsbutchery.client.render.SkinRackRenderer;
import com.otectus.alexsbutchery.registry.ModBlockEntities;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Client-only mod-bus registration. Reached only through DistExecutor, so it never loads on a server. */
public final class ClientSetup {

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::onRegisterRenderers);
        modBus.addListener(ClientSetup::onRegisterReloadListeners);
        modBus.addListener(ClientSetup::onRegisterOverlays);
        CarcassModels.init();
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CARCASS.get(), CarcassRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SKIN_RACK.get(), SkinRackRenderer::new);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(PoseProfiles.INSTANCE);
    }

    private static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("carcass_hint", new FloorHintOverlay());
    }

    private ClientSetup() {}
}
