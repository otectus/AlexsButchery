package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.CompatState;
import com.otectus.alexsbutchery.compat.RecipePolicy;
import com.otectus.alexsbutchery.def.CompatDefs;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * A real data reload ({@code /reload}): the server builds new rules from the reloaded data, applies the recipe policy to
 * the new recipe manager from its own clean list, and ends with the same yields and recipes as before. Runs alone in its
 * batch, since a reload swaps the loot tables and recipes every other test uses.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompatReloadTests {

    @GameTest(template = "empty", batch = "reload", timeoutTicks = 1200)
    public static void reloadRebuildsRulesAndRecipes(GameTestHelper h) {
        var server = h.getLevel().getServer();
        CompatState before = CompatTestSupport.state(h);
        RecipeManager oldRecipes = server.getRecipeManager();
        Set<ResourceLocation> conversionsBefore = conversions(oldRecipes);
        CompletableFuture<Void> reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        h.succeedWhen(() -> {
            if (!reload.isDone()) throw new GameTestAssertException("reloading");
            h.assertTrue(!reload.isCompletedExceptionally(), "the reload succeeded");
            CompatState after = CompatState.get(server);
            h.assertTrue(after != null && after != before, "the rules were rebuilt from the reloaded data");
            RecipeManager recipes = server.getRecipeManager();
            h.assertTrue(recipes != oldRecipes, "the reload brought a new recipe manager");
            h.assertTrue(after.view().equals(before.view()), "the same data gives the same yields and view");
            h.assertTrue(conversions(recipes).equals(conversionsBefore), "the same conversion recipes");
            for (RecipePolicy.Outcome outcome : after.recipes().outcomes()) {
                RecipePolicy.Outcome earlier = before.recipes().outcome(outcome.conflict());
                h.assertTrue(earlier != null && earlier.status() == outcome.status() && java.util.Objects.equals(earlier.owner(), outcome.owner()),
                        outcome.conflict() + " resolves as before the reload: " + outcome + " / " + earlier);
                for (ResourceLocation removed : outcome.removed()) h.assertTrue(recipes.byKey(removed).isEmpty(), removed + " removed again");
                for (ResourceLocation alternate : outcome.alternates()) h.assertTrue(recipes.byKey(alternate).isPresent(), alternate + " added again");
            }
        });
    }

    private static Set<ResourceLocation> conversions(RecipeManager recipes) {
        return recipes.getRecipeIds().filter(id -> CompatDefs.providerOfEquivalenceRecipe(id) != null).collect(Collectors.toSet());
    }
}
