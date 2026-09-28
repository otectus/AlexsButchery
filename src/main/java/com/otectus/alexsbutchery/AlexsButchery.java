package com.otectus.alexsbutchery;

import com.mojang.logging.LogUtils;
import com.otectus.alexsbutchery.advancement.ButcheringTrigger;
import com.otectus.alexsbutchery.client.ClientSetup;
import com.otectus.alexsbutchery.config.ClientConfig;
import com.otectus.alexsbutchery.config.ServerConfig;
import com.otectus.alexsbutchery.registry.ModBlockEntities;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AlexsButchery.MOD_ID)
public class AlexsButchery {
    public static final String MOD_ID = "alexsbutchery";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AlexsButchery(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);

        modBus.addListener(AlexsButchery::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        // The vanilla trigger map is a plain HashMap and setup runs in parallel across mods.
        event.enqueueWork(() -> CriteriaTriggers.register(ButcheringTrigger.INSTANCE));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
