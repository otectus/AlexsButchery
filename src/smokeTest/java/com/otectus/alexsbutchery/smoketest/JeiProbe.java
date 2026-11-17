package com.otectus.alexsbutchery.smoketest;

import com.otectus.alexsbutchery.client.compat.jei.AlexsButcheryJeiPlugin;
import com.otectus.alexsbutchery.client.compat.jei.ButcheringRecipe;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;

/** What JEI's Butchering tab holds right now. Only touched when JEI is loaded. */
final class JeiProbe {
    static boolean available() {
        return AlexsButcheryJeiPlugin.runtimeAvailable();
    }

    static int count() {
        return AlexsButcheryJeiPlugin.shown().size();
    }

    /** Recipe objects JEI holds for this runtime, shown or hidden: an identical view must not add any. */
    static int held() {
        return AlexsButcheryJeiPlugin.held();
    }

    /** Set the real sidebar search for the inventory screenshot without widening the production plugin API. */
    static void filter(String text) {
        try {
            var field = AlexsButcheryJeiPlugin.class.getDeclaredField("runtime");
            field.setAccessible(true);
            var runtime = (mezz.jei.api.runtime.IJeiRuntime) field.get(null);
            if (runtime != null) runtime.getIngredientFilter().setFilterText(text);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set JEI gallery search", e);
        }
    }

    /** The first item of each output slot of one creature's stage, or empty when JEI shows no such recipe. */
    static List<String> outputs(String mob, String stage) {
        List<String> out = new ArrayList<>();
        for (ButcheringRecipe recipe : AlexsButcheryJeiPlugin.shown()) {
            if (!recipe.def().id().equals(mob) || !recipe.action().table(recipe.def()).equals(stage)) continue;
            for (ButcheringRecipe.Output o : recipe.outputs()) out.add(BuiltInRegistries.ITEM.getKey(o.stacks().get(0).getItem()).toString());
        }
        return out;
    }

    private JeiProbe() {}
}
