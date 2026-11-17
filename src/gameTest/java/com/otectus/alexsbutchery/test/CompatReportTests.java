package com.otectus.alexsbutchery.test;

import com.google.gson.GsonBuilder;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static com.otectus.alexsbutchery.test.TestSupport.ORIGIN;
import static com.otectus.alexsbutchery.test.TestSupport.alexsMob;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryItem;

/**
 * Evidence for the release report: three real kills and full processings each of a grizzly bear, a crocodile and a
 * bison under this run's mods and rules, written to {@code compat-three-animals.json} (death drops and per-stage
 * outputs). The assertions live in CompatYieldTests; this only records.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompatReportTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void recordThreeAnimals(GameTestHelper h) {
        Map<String, Object> report = new LinkedHashMap<>();
        for (String mod : new String[]{AlexsButchery.MOD_ID, "butchery", "alexsmobs", "farmersdelight", "alexsmobsdelight", "alexsdelight"})
            report.put(mod, ModList.get().getModContainerById(mod).map(c -> c.getModInfo().getVersion().toString()).orElse("absent"));
        report.put("policy", CompatTestSupport.state(h).policy().describe());
        for (String mobId : new String[]{"grizzly_bear", "crocodile", "bison"}) {
            MobDef def = CompatTestSupport.mob(mobId);
            for (int trial = 0; trial < 3; trial++) {
                var player = TestSupport.player(h);
                player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
                TestSupport.clearItems(h);
                LivingEntity victim = (LivingEntity) h.spawn(alexsMob(mobId), ORIGIN);
                TestSupport.kill(h, victim, player);
                Map<String, Object> run = new LinkedHashMap<>();
                run.put("death", names(CompatTestSupport.drops(h)));
                CompoundTag snapshot = CompatTestSupport.droppedSnapshot(h, def);
                TestSupport.clearItems(h);
                BlockPos at = CompatTestSupport.placeReady(h, def, ORIGIN.east(2), snapshot);
                Map<String, Object> stages = new LinkedHashMap<>();
                CompatTestSupport.process(h, at, player).forEach((stage, items) -> stages.put(stage, names(items)));
                run.put("stages", stages);
                report.put(mobId + "#" + trial, run);
            }
        }
        try {
            Files.writeString(Path.of("compat-three-animals.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        h.succeed();
    }

    private static Map<String, Integer> names(Map<Item, Integer> items) {
        Map<String, Integer> out = new TreeMap<>();
        items.forEach((item, n) -> out.merge(BuiltInRegistries.ITEM.getKey(item).toString(), n, Integer::sum));
        return out;
    }
}
