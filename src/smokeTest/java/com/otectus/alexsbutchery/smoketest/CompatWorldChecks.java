package com.otectus.alexsbutchery.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.CompatState;
import com.otectus.alexsbutchery.compat.CompatView;
import com.otectus.alexsbutchery.butcher.CarcassPolicy;
import com.otectus.alexsbutchery.def.DeathSpawnMode;
import com.otectus.alexsbutchery.def.MobDefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Two single-player worlds with different compatibility settings in one client process ({@code -PcompatWorlds}): the
 * first with the defaults, the second created with {@code preferExternalProducts = false} through Forge's
 * {@code defaultconfigs}. Each world's server builds its own rules, a {@code /reload} rebuilds them and re-sends the
 * view, the client's view and JEI follow the world it is in, and leaving a world clears them. Writes
 * {@code compat-worlds-result.json}.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class CompatWorldChecks {
    static final boolean ENABLED = Boolean.getBoolean("alexsbutchery.smoketest.compatWorlds");
    private static final String OUTPUT = System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY);
    private static final Path DEFAULT_CONFIG = FMLPaths.GAMEDIR.get().resolve("defaultconfigs").resolve("alexsbutchery-server.toml");
    private static final Map<String, Object> checks = new LinkedHashMap<>();
    private static int phase, ticks;
    private static boolean done;
    private static String digestA;
    private static int jeiHeldBeforeReload, jeiShownBeforeReload;
    private static int receivedBeforeReload;
    private static CompatState stateBeforeReload;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || OUTPUT == null || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            ticks++;
            switch (phase) {
                case 0 -> { // title: world A with default settings
                    if (mc.screen instanceof TitleScreen && ticks >= 40) {
                        Files.deleteIfExists(DEFAULT_CONFIG);
                        create(mc, "CompatWorldA");
                        next();
                    }
                }
                case 1 -> { // in world A, then a data reload in it
                    if (mc.player != null && mc.screen != null && ticks > 20) mc.setScreen(null);
                    if (mc.player != null && ticks == 120) {
                        CompatClientChecks.run(mc, checks, "a_");
                        CompatState state = CompatState.get(mc.getSingleplayerServer());
                        checks.put("a_prefers_external", state != null && state.policy().preferExternal());
                        checks.put("a_carcass_policy_synced", CarcassPolicy.hasAuthoritativeSnapshot());
                        checkCarcassPolicy("a_", false);
                        CompatView view = CompatView.received();
                        digestA = view == null ? "" : view.digest();
                        receivedBeforeReload = CompatView.receivedCount();
                        stateBeforeReload = state;
                        if (ModList.get().isLoaded("jei")) {
                            jeiHeldBeforeReload = JeiProbe.held();
                            jeiShownBeforeReload = JeiProbe.count();
                        }
                        mc.player.connection.sendCommand("reload");
                    }
                    if (mc.player != null && ticks == 260) {
                        checks.put("a_reload_view_resent", CompatView.receivedCount() > receivedBeforeReload);
                        checks.put("a_reload_rules_rebuilt", CompatState.get(mc.getSingleplayerServer()) != stateBeforeReload);
                        CompatClientChecks.run(mc, checks, "a_reload_");
                        CompatView view = CompatView.received();
                        checks.put("a_reload_same_view", view != null && view.digest().equals(digestA));
                        checkCarcassPolicy("a_reload_", false);
                        if (ModList.get().isLoaded("jei")) {
                            // The same view again: JEI's Butchering tab keeps its recipe objects and shows the same ones.
                            checks.put("a_reload_jei_holds_no_new_recipes", JeiProbe.held() == jeiHeldBeforeReload);
                            checks.put("a_reload_jei_shows_the_same", JeiProbe.count() == jeiShownBeforeReload);
                        }
                        leave(mc);
                        next();
                    }
                }
                case 2 -> { // back at the title: nothing left from world A
                    if (mc.level == null && mc.screen instanceof TitleScreen && ticks >= 20) {
                        checks.put("view_cleared_on_leaving", CompatView.received() == null);
                        checks.put("carcass_policy_cleared_on_leaving", !CarcassPolicy.hasAuthoritativeSnapshot());
                        if (ModList.get().isLoaded("jei")) checks.put("jei_cleared_on_leaving", JeiProbe.count() == 0);
                        Files.createDirectories(DEFAULT_CONFIG.getParent());
                        Files.writeString(DEFAULT_CONFIG, "[compat]\n\tpreferExternalProducts = false\n"
                                + "[carcasses]\n\tdeathSpawnOverrides = [\"alexsmobs:moose=PLACED_BLOCK\"]\n"
                                + "\tgroundBleedingMobs = [\"alexsmobs:moose\"]\n", StandardCharsets.UTF_8);
                        create(mc, "CompatWorldB");
                        next();
                    }
                }
                case 3 -> { // in world B: native mode, its own rules
                    if (mc.player != null && mc.screen != null && ticks > 20) mc.setScreen(null);
                    if (mc.player != null && ticks == 120) {
                        CompatClientChecks.run(mc, checks, "b_");
                        CompatState state = CompatState.get(mc.getSingleplayerServer());
                        checks.put("b_native_mode", state != null && !state.policy().preferExternal());
                        checks.put("b_carcass_policy_synced", CarcassPolicy.hasAuthoritativeSnapshot());
                        checkCarcassPolicy("b_", true);
                        CompatView view = CompatView.received();
                        boolean addon = ModList.get().isLoaded("alexsmobsdelight") || ModList.get().isLoaded("alexsdelight");
                        if (addon) checks.put("b_view_differs_from_a", view != null && !view.digest().equals(digestA));
                        checks.put("b_bison_native_steaks", view != null && CompatClientChecks.outputs(view, "bison", "cut_1").contains("butchery:raw_chuck_steak"));
                        leave(mc);
                        next();
                    }
                }
                case 4 -> {
                    if (mc.level == null && mc.screen instanceof TitleScreen && ticks >= 20) {
                        checks.put("view_cleared_after_b", CompatView.received() == null);
                        checks.put("carcass_policy_cleared_after_b", !CarcassPolicy.hasAuthoritativeSnapshot());
                        checkCarcassPolicy("after_b_", false);
                        finish(mc);
                    }
                }
                default -> { }
            }
        } catch (Exception e) {
            AlexsButchery.LOGGER.error("COMPATWORLDS failed", e);
            checks.put("exception", e.toString());
            finish(mc);
        }
    }

    private static void next() {
        phase++;
        ticks = 0;
    }

    private static void checkCarcassPolicy(String prefix, boolean groundMoose) {
        var moose = MobDefs.byId("moose");
        checks.put(prefix + "carcass_death_mode", CarcassPolicy.current().deathSpawnMode(moose)
                == (groundMoose ? DeathSpawnMode.PLACED_BLOCK : DeathSpawnMode.ITEM));
        checks.put(prefix + "carcass_ground_bleeding", CarcassPolicy.current().canBleedOnGround(moose) == groundMoose);
        checks.put(prefix + "carcass_hookability_unchanged", moose.hookable());
    }

    private static void create(Minecraft mc, String base) {
        String name = base + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261025L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static void leave(Minecraft mc) {
        mc.level.disconnect();
        mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        mc.setScreen(new TitleScreen());
    }

    private static void finish(Minecraft mc) {
        done = true;
        try {
            Files.deleteIfExists(DEFAULT_CONFIG);
        } catch (java.io.IOException e) {
            checks.put("cleanup", e.toString());
        }
        boolean pass = !checks.containsKey("exception") && checks.values().stream().noneMatch(v -> v instanceof Boolean b && !b);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", pass);
        result.put("checks", checks);
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT, "compat-worlds-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            AlexsButchery.LOGGER.error("COMPATWORLDS could not write its result", e);
        }
        AlexsButchery.LOGGER.info("COMPATWORLDS finished: pass={}", pass);
        mc.stop();
    }

    private CompatWorldChecks() {}
}
