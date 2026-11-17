package com.otectus.alexsbutchery.client;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.RugBlock;
import com.otectus.alexsbutchery.client.gui.FloorHintOverlay;
import com.otectus.alexsbutchery.client.pose.PoseProfiles;
import com.otectus.alexsbutchery.client.render.CarcassModels;
import com.otectus.alexsbutchery.client.render.CarcassRenderer;
import com.otectus.alexsbutchery.client.render.SkinRackRenderer;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.item.TrophyData;
import com.otectus.alexsbutchery.registry.ModBlockEntities;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only mod-bus registration. Reached only through DistExecutor, so it never loads on a server. */
public final class ClientSetup {

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::onClientSetup);
        modBus.addListener(ClientSetup::onRegisterRenderers);
        modBus.addListener(ClientSetup::onRegisterReloadListeners);
        modBus.addListener(ClientSetup::onRegisterOverlays);
        CarcassModels.init();
    }

    /** A rug item made from a white tiger's head shows the white pelt (item model override {@code coat_variant}). */
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (ModItems.Entries e : ModItems.all()) {
                if (e.rug() == null || e.def().rugCoat() == null) continue;
                MobDef def = e.def();
                ItemProperties.register(e.rug().get(), AlexsButchery.id("coat_variant"),
                        (stack, level, entity, seed) -> RugBlock.coat(def, TrophyData.appearance(stack)) ? 1F : 0F);
            }
        });
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
