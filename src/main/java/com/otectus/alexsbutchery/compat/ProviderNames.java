package com.otectus.alexsbutchery.compat;

import net.minecraftforge.fml.ModList;

/** Display names and versions of providers; a provider id is normally the supplying mod's id. */
public final class ProviderNames {
    public static final String NATIVE = "native";

    public static String name(String provider) {
        if (NATIVE.equals(provider)) return "Alex's Butchery";
        return ModList.get().getModContainerById(provider).map(c -> c.getModInfo().getDisplayName()).orElse(switch (provider) {
            case "alexsmobsdelight" -> "Alex's Mobs Delight";
            case "alexsdelight" -> "Alex's Delight";
            default -> provider;
        });
    }

    /** The installed mod's version, or "-" when no mod has the provider's id. */
    public static String version(String provider) {
        return ModList.get().getModContainerById(provider).map(c -> c.getModInfo().getVersion().toString()).orElse("-");
    }

    public static boolean installed(String provider) {
        return ModList.get().isLoaded(provider);
    }

    private ProviderNames() {}
}
