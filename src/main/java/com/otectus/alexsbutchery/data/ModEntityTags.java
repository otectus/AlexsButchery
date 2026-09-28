package com.otectus.alexsbutchery.data;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/** Every creature in the table, for packs and other mods that want to know what butchers. */
final class ModEntityTags extends EntityTypeTagsProvider {

    ModEntityTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper helper) {
        super(output, lookup, AlexsButchery.MOD_ID, helper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        for (MobDef def : MobDefs.all()) tag(ModTags.Entities.BUTCHERABLE).addOptional(def.entity());
    }
}
