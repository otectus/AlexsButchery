package com.otectus.alexsbutchery.data;

import com.otectus.alexsbutchery.compat.profile.CompatProfile;
import com.otectus.alexsbutchery.compat.profile.ProfileCodec;
import com.otectus.alexsbutchery.compat.profile.ProfileLoader;
import com.otectus.alexsbutchery.def.CompatDefs;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the built-in compatibility profiles ({@link CompatDefs}) to
 * {@code data/alexsbutchery/alexsbutchery/compat_profiles/<provider>/<mob>.json}. They name other mods' items as plain
 * ids and are checked only when loaded, so the output does not depend on which addons the data run had installed.
 */
final class CompatProfileProvider implements DataProvider {
    private final PackOutput output;

    CompatProfileProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        Path root = output.getOutputFolder(PackOutput.Target.DATA_PACK);
        for (Map.Entry<ResourceLocation, CompatProfile> e : CompatDefs.profiles().entrySet()) {
            ResourceLocation id = e.getKey();
            Path file = root.resolve(id.getNamespace()).resolve(ProfileLoader.DIRECTORY).resolve(id.getPath() + ".json");
            writes.add(DataProvider.saveStable(cache, ProfileCodec.toJson(e.getValue()), file));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Alex's Butchery compatibility profiles";
    }
}
