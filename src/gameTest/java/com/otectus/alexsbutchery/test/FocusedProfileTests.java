package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.butcher.OfficialAddonGuard;
import com.otectus.alexsbutchery.config.ServerConfig;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.otectus.alexsbutchery.test.CompatTestSupport.mob;
import static com.otectus.alexsbutchery.test.TestSupport.ORIGIN;
import static com.otectus.alexsbutchery.test.TestSupport.alexsMob;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryItem;
import static com.otectus.alexsbutchery.test.TestSupport.clearItems;
import static com.otectus.alexsbutchery.test.TestSupport.count;

/** Behaviour that depends on the neighbouring mods of the focused profiles (Immersive Tanning, the official addon). */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FocusedProfileTests {

    /** Our skins stay Immersive Tanning raw hides of their sizes, exactly when that mod is installed. */
    @GameTest(template = "empty")
    public static void hideMountingFollowsImmersiveTanning(GameTestHelper h) {
        boolean tanning = ModList.get().isLoaded("immersive_tanning");
        for (String size : List.of("small", "medium", "large")) {
            boolean present = h.getLevel().getRecipeManager().byKey(AlexsButchery.id("immersive_tanning/hide_mounting_" + size)).isPresent();
            h.assertTrue(present == tanning, "hide mounting " + size + " present=" + present + " with Immersive Tanning " + tanning);
        }
        h.succeed();
    }

    /** With the official Butchery addon, its creatures never yield a second carcass from this mod. */
    @GameTest(template = "empty")
    public static void officialAddonKeepsItsCreatures(GameTestHelper h) {
        boolean official = ModList.get().isLoaded(OfficialAddonGuard.OFFICIAL_MOD_ID);
        h.assertTrue(OfficialAddonGuard.officialAddonLoaded() == official, "the guard sees the addon: " + official);
        var player = TestSupport.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        clearItems(h);
        LivingEntity emu = (LivingEntity) h.spawn(alexsMob("emu"), ORIGIN);
        TestSupport.kill(h, emu, player);
        long ours = count(h, ModItems.of(mob("emu")).carcass().get());
        boolean deferred = official && ServerConfig.DEFER_TO_OFFICIAL_ADDON.get() && ServerConfig.OFFICIAL_ADDON_MOBS.get().contains("emu");
        h.assertTrue(ours == (deferred ? 0 : 1), "our emu carcasses: " + ours + " (deferred to the official addon: " + deferred + ")");
        clearItems(h);
        h.succeed();
    }
}
