package com.otectus.alexsbutchery.data;

import com.otectus.alexsbutchery.AlexsButchery;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.ForgeAdvancementProvider;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** {@code runData} regenerates everything under {@code src/generated/resources} from the mob table. */
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        ExistingFileHelper helper = event.getExistingFileHelper();

        ModBlockTags blockTags = new ModBlockTags(output, lookup, helper);
        generator.addProvider(event.includeServer(), blockTags);
        generator.addProvider(event.includeServer(), new ModItemTags(output, lookup, blockTags.contentsGetter(), helper));
        generator.addProvider(event.includeServer(), new ModEntityTags(output, lookup, helper));
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(), List.of(
                new LootTableProvider.SubProviderEntry(ModBlockLoot::new, LootContextParamSets.BLOCK),
                new LootTableProvider.SubProviderEntry(CarcassStageLoot::new, LootContextParamSets.BLOCK))));
        generator.addProvider(event.includeServer(), new ModRecipes(output));
        generator.addProvider(event.includeServer(), new ForgeAdvancementProvider(output, lookup, helper, List.of(new ModAdvancements())));
        generator.addProvider(event.includeClient(), new ModGuideBook(output));
        generator.addProvider(event.includeClient(), new ModBlockStates(output, helper));
        generator.addProvider(event.includeClient(), new ModLang(output));
    }

    private DataGenerators() {}
}
