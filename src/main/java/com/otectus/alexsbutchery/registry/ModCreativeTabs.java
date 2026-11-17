package com.otectus.alexsbutchery.registry;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.CompatView;
import com.otectus.alexsbutchery.def.ItemDefs;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Our items live in Butchery's own creative tabs, where players already look for carcasses and trophies. A meat, shell
 * or trophy that no creature yields on the connected server (every source replaced by a cooking addon's item) is left
 * out of the tabs; it stays registered, and existing stacks keep their recipes and stay findable in JEI.
 */
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModCreativeTabs {
    private static final ResourceLocation BUTCHERY_CARCASSES = new ResourceLocation("butchery", "butcherycarcasses");
    private static final ResourceLocation BUTCHERY_BLOCKS = new ResourceLocation("butchery", "butchery_blocks");
    private static final ResourceLocation BUTCHERY_FOOD = new ResourceLocation("butchery", "butchery_food");
    private static final ResourceLocation BUTCHERY_ITEMS = new ResourceLocation("butchery", "butchery_items");

    @SubscribeEvent
    public static void onBuildTabs(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (tab.equals(BUTCHERY_CARCASSES)) {
            for (ModItems.Entries e : ModItems.all()) {
                event.accept(e.carcass().get());
                if (e.drained() != null) event.accept(e.drained().get());
                if (e.head() != null) event.accept(e.head().get());
            }
        } else if (tab.equals(BUTCHERY_BLOCKS)) {
            for (ModItems.Entries e : ModItems.all()) {
                if (e.mount() != null) event.accept(e.mount().get());
            }
            for (ModItems.Entries e : ModItems.all()) {
                if (e.skeleton() != null) event.accept(e.skeleton().get());
            }
            for (ModItems.Entries e : ModItems.all()) {
                if (e.rug() != null) event.accept(e.rug().get());
            }
        } else if (tab.equals(BUTCHERY_FOOD)) {
            for (ItemDefs.ItemDef def : ItemDefs.all()) {
                if (def.food() && !hidden(def)) event.accept(ModItems.simple(def.id()).get());
            }
        } else if (tab.equals(BUTCHERY_ITEMS)) {
            for (ItemDefs.ItemDef def : ItemDefs.all()) {
                if (!def.food() && !hidden(def)) event.accept(ModItems.simple(def.id()).get());
            }
        }
    }

    /** Hidden while the connected server's view says no creature yields the item; shown without a view. */
    private static boolean hidden(ItemDefs.ItemDef def) {
        CompatView view = CompatView.received();
        return view != null && view.hiddenItems().contains(AlexsButchery.id(def.id()));
    }

    private ModCreativeTabs() {}
}
