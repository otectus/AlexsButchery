package com.otectus.alexsbutchery.smoketest;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.CompatState;
import com.otectus.alexsbutchery.compat.CompatView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the client shows about butchering yields, compared with what the server decided: the received view against the
 * integrated server's rules, JEI's Butchering recipes against the view, the creative tabs against the hidden items,
 * and the loaded optional mods against the run's flags.
 */
final class CompatClientChecks {
    static final ResourceLocation FOOD_TAB = new ResourceLocation("butchery", "butchery_food");

    static void run(Minecraft mc, Map<String, Object> checks, String prefix) {
        CompatView view = CompatView.received();
        checks.put(prefix + "view_received", view != null);
        IntegratedServer server = mc.getSingleplayerServer();
        if (view != null && server != null) {
            CompatState state = CompatState.get(server);
            checks.put(prefix + "server_rules", state != null);
            if (state != null) {
                checks.put(prefix + "view_digest", view.digest());
                checks.put(prefix + "view_matches_server", view.digest().equals(state.view().digest()) && view.equals(state.view()));
                checks.put(prefix + "policy", state.policy().describe());
                // The recipe list JEI's other tabs read is the server's after the conflict policy and conversions.
                if (mc.getConnection() != null) {
                    java.util.Set<ResourceLocation> client = new java.util.HashSet<>();
                    mc.getConnection().getRecipeManager().getRecipeIds().forEach(client::add);
                    java.util.Set<ResourceLocation> serverIds = new java.util.HashSet<>();
                    server.getRecipeManager().getRecipeIds().forEach(serverIds::add);
                    checks.put(prefix + "client_recipes_match_server", client.equals(serverIds));
                    for (var outcome : state.recipes().outcomes()) {
                        if (outcome.status() != com.otectus.alexsbutchery.compat.RecipePolicy.Status.RESOLVED) continue;
                        checks.put(prefix + "conflict_" + outcome.conflict() + "_owner", outcome.owner());
                        checks.put(prefix + "conflict_" + outcome.conflict() + "_losers_gone_on_client", outcome.removed().stream().noneMatch(client::contains));
                        checks.put(prefix + "conflict_" + outcome.conflict() + "_alternates_on_client", client.containsAll(outcome.alternates()));
                    }
                }
            }
        }
        if (view != null) {
            checks.put(prefix + "bison_cut_1", outputs(view, "bison", "cut_1"));
            if (ModList.get().isLoaded("jei")) {
                checks.put(prefix + "jei_runtime", JeiProbe.available());
                List<String> jei = JeiProbe.outputs("bison", "cut_1");
                checks.put(prefix + "jei_bison_cut_1", jei);
                checks.put(prefix + "jei_matches_view", jei.equals(outputs(view, "bison", "cut_1")));
                checks.put(prefix + "jei_recipe_count_matches_view", JeiProbe.count() == stages(view));
            }
            checks.put(prefix + "creative_tabs_follow_view", creativeTabsFollow(mc, view));
        }
        // Gradle runs name their optional mods; a real pack (tools/packtest) has no such flags to compare with.
        if (!System.getProperty("alexsbutchery.test.expectedMods", "").isEmpty())
            checks.put(prefix + "optional_mods_match_flags", modsMatch(checks, prefix));
    }

    /** The first item of each output slot the view lists for a stage. */
    static List<String> outputs(CompatView view, String mob, String stage) {
        List<String> out = new ArrayList<>();
        CompatView.StageView s = view.stage(mob, stage);
        if (s != null) for (CompatView.OutputView o : s.outputs()) out.add(o.items().get(0).toString());
        return out;
    }

    private static int stages(CompatView view) {
        int n = 0;
        for (List<CompatView.StageView> stages : view.mobs().values())
            for (CompatView.StageView s : stages) if (!s.outputs().isEmpty() || s.custom()) n++;
        return n;
    }

    private static boolean creativeTabsFollow(Minecraft mc, CompatView view) {
        if (mc.player == null || mc.level == null) return false;
        CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(FOOD_TAB);
        if (tab == null) return false;
        List<ResourceLocation> shown = new ArrayList<>();
        for (ItemStack stack : tab.getDisplayItems()) shown.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        for (ResourceLocation hidden : view.hiddenItems()) if (shown.contains(hidden)) return false;
        return shown.contains(AlexsButchery.id("raw_reptile_meat"));
    }

    private static boolean modsMatch(Map<String, Object> checks, String prefix) {
        String raw = System.getProperty("alexsbutchery.test.expectedMods", "");
        Map<String, Boolean> wrong = new LinkedHashMap<>();
        for (String part : raw.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String mod = part.substring(0, eq).trim();
            boolean expected = Boolean.parseBoolean(part.substring(eq + 1).trim());
            if (ModList.get().isLoaded(mod) != expected) wrong.put(mod, expected);
        }
        if (!wrong.isEmpty()) checks.put(prefix + "optional_mods_wrong", wrong.toString());
        return !raw.isEmpty() && wrong.isEmpty();
    }

    private CompatClientChecks() {}
}
