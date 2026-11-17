package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.Collection;
import java.util.List;

/**
 * The real Jade providers' output (AB-04): registered only when Jade is loaded, so the run without Jade never loads a
 * Jade class. The checks themselves are in {@link JadeChecks}.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
public final class JadeTooltipTests {

    @GameTestGenerator
    public static Collection<TestFunction> tooltips() {
        if (!ModList.get().isLoaded("jade")) return List.of();
        return List.of(new TestFunction("jade", "alexsbutchery.jadetooltips", AlexsButchery.MOD_ID + ":empty", 200, 0L, true,
                h -> JadeChecks.run(h)));
    }

    private JadeTooltipTests() {}
}
