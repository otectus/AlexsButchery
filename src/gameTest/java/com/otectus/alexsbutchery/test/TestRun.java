package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.electronwill.nightconfig.core.file.FileWatcher;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The run itself: which tests were registered (written to {@code gametest-registered.txt} for the Gradle check that
 * every one of them ran and passed), and whether the loaded optional mods are exactly those the run's flags asked for,
 * so a missing addon jar fails the run instead of quietly testing the core profile.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID)
public final class TestRun {
    static final String EXPECTED_MODS = "alexsbutchery.test.expectedMods";

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        stopConfigReloads();
        List<String> names = new ArrayList<>();
        for (TestFunction function : GameTestRegistry.getAllTestFunctions()) names.add(function.getTestName());
        names.sort(null);
        try {
            Files.write(Path.of("gametest-registered.txt"), names, StandardCharsets.UTF_8);
        } catch (IOException e) {
            AlexsButchery.LOGGER.error("Could not record the registered GameTests", e);
        }
    }

    /**
     * Tests set this mod's and Butchery's config values in memory and put them back within one test body. Forge saves
     * each change to the file and its file watcher reloads that file later on another thread, which can land in the
     * middle of another test and put an intermediate value back. The test server stops watching those two mods' files,
     * so the in-memory values the tests set are the only ones; nothing else in the run edits them.
     */
    private static void stopConfigReloads() {
        List<String> stopped = new ArrayList<>();
        for (ModConfig config : ConfigTracker.INSTANCE.fileMap().values()) {
            if (!Set.of(AlexsButchery.MOD_ID, "butchery").contains(config.getModId())) continue;
            try {
                Path path = config.getFullPath();
                FileWatcher.defaultInstance().removeWatch(path);
                stopped.add(path.getFileName().toString());
            } catch (RuntimeException e) {
                // Not loaded on this side (client configs on a server): nothing is watched.
            }
        }
        AlexsButchery.LOGGER.info("GameTests: config file reloads off for {}", stopped);
    }

    /** {@code mod=true|false,...} from the Gradle flags; empty outside a Gradle run. */
    static Map<String, Boolean> expectedMods() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        String raw = System.getProperty(EXPECTED_MODS, "");
        for (String part : raw.split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) out.put(part.substring(0, eq).trim(), Boolean.parseBoolean(part.substring(eq + 1).trim()));
        }
        return out;
    }

    @GameTest(template = "empty")
    public static void optionalModSetMatchesRun(GameTestHelper h) {
        Map<String, Boolean> expected = expectedMods();
        h.assertTrue(!expected.isEmpty(), "the run names its optional mods (" + EXPECTED_MODS + ")");
        List<String> wrong = new ArrayList<>();
        expected.forEach((mod, present) -> {
            if (ModList.get().isLoaded(mod) != present) wrong.add(mod + (present ? " missing" : " loaded"));
        });
        h.assertTrue(wrong.isEmpty(), "optional mods differ from this run's flags: " + wrong);
        h.succeed();
    }

    private TestRun() {}
}
