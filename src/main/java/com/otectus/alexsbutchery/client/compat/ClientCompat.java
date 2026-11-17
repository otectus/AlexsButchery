package com.otectus.alexsbutchery.client.compat;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.client.compat.jei.AlexsButcheryJeiPlugin;
import com.otectus.alexsbutchery.compat.CompatView;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Client follow-up of the server's {@link CompatView}: JEI's Butchering tab is replaced with the new view, and the
 * creative tabs are rebuilt the next time the inventory opens (they hide our items no creature yields here). Leaving a
 * world or server forgets the view, so the next one never starts from the last one's rules.
 */
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID, value = Dist.CLIENT)
public final class ClientCompat {

    public static void onReceived(@Nullable CompatView view) {
        if (ModList.get().isLoaded("jei")) AlexsButcheryJeiPlugin.refresh();
        invalidateCreativeTabs();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (CompatView.received() == null) return;
        CompatView.setReceived(null);
        onReceived(null);
    }

    /** Vanilla rebuilds tab contents only when its cached display parameters change; forget them. */
    private static void invalidateCreativeTabs() {
        try {
            ObfuscationReflectionHelper.setPrivateValue(CreativeModeTabs.class, null, null, "f_268496_");
        } catch (RuntimeException e) {
            AlexsButchery.LOGGER.debug("Could not reset the creative tab cache; tabs refresh on the next world", e);
        }
    }

    private ClientCompat() {}
}
