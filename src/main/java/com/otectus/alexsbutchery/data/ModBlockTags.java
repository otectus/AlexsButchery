package com.otectus.alexsbutchery.data;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.SkinStep;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/** Joins Butchery's carcass tags so its hints, drips, apron and advancements treat our blocks as its own. */
final class ModBlockTags extends BlockTagsProvider {

    ModBlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper helper) {
        super(output, lookup, AlexsButchery.MOD_ID, helper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        for (ModBlocks.Entries e : ModBlocks.all()) {
            MobDef def = e.def();
            if (def.floor()) {
                // Butchery's hang hint would tell players to hook these; only its drip particles should apply.
                tag(ModTags.Blocks.CARCASS_NO_STATES).add(e.carcass().get());
                tag(ModTags.Blocks.FLOOR_CARCASS).add(e.carcass().get());
            } else {
                tag(ModTags.Blocks.CARCASS).add(e.carcass().get());
                if (def.small()) tag(ModTags.Blocks.SMALL_CARCASS).add(e.carcass().get());
            }
            if (!def.bleeds()) tag(ModTags.Blocks.CLEAVER_ONLY_HINT).add(e.carcass().get());
            if (e.drained() != null) {
                tag(ModTags.Blocks.DRAINED_CARCASS).add(e.drained().get());
                if (def.skin() == SkinStep.NONE) tag(ModTags.Blocks.CLEAVER_ONLY_HINT).add(e.drained().get());
            }
            if (e.head() != null) tag(ModTags.Blocks.HEADS).add(e.head().get());
            if (e.skeleton() != null) tag(ModTags.Blocks.SKELETONS).add(e.skeleton().get());
            if (e.rug() != null) {
                tag(ModTags.Blocks.RUGS).add(e.rug().get());
                tag(BlockTags.DAMPENS_VIBRATIONS).add(e.rug().get());
            }
        }
    }
}
