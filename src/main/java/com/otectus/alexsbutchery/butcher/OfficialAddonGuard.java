package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.config.ServerConfig;
import com.otectus.alexsbutchery.def.MobDef;
import net.minecraftforge.fml.ModList;

/**
 * The official "Butchery: Alex's Mobs Addon" (mod id {@code butchery_alexs_mobs}) covers a handful of creatures.
 * When it is installed, those are left to it so a kill never yields two carcasses.
 */
public final class OfficialAddonGuard {
    public static final String OFFICIAL_MOD_ID = "butchery_alexs_mobs";
    private static Boolean loaded;

    public static boolean officialAddonLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(OFFICIAL_MOD_ID);
            if (loaded) AlexsButchery.LOGGER.warn("{} is installed; leaving its creatures to it (config compat.deferToOfficialAddon)", OFFICIAL_MOD_ID);
        }
        return loaded;
    }

    public static boolean covers(MobDef def) {
        return ServerConfig.DEFER_TO_OFFICIAL_ADDON.get() && officialAddonLoaded()
                && ServerConfig.OFFICIAL_ADDON_MOBS.get().contains(def.id());
    }

    private OfficialAddonGuard() {}
}
