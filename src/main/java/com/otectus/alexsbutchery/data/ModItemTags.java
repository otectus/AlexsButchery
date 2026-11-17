package com.otectus.alexsbutchery.data;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.def.CompatDefs;
import com.otectus.alexsbutchery.def.ItemDefs;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.Weight;
import com.otectus.alexsbutchery.registry.ModItems;
import com.otectus.alexsbutchery.registry.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/**
 * Butchery's item tags (grinder scrap, carcass weight, trophies), our own drop-replacement tag and the cooking addons'
 * product tags we extend with the other addon's equal cuts. {@code #alexsbutchery:replaced_by_carcass} lists every
 * creature's own replaced parts; a carcass kill removes one only for the creature that declares it (and only while it
 * stays in the tag), and an item a pack adds is removed for every carcass kill.
 */
final class ModItemTags extends ItemTagsProvider {

    ModItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, ExistingFileHelper helper) {
        super(output, lookup, blockTags, AlexsButchery.MOD_ID, helper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Several creatures replace the same item (feathers, bones); the tag lists it once.
        java.util.Set<ResourceLocation> replaced = new java.util.LinkedHashSet<>();
        for (ModItems.Entries e : ModItems.all()) replaced.addAll(e.def().replacedDrops());
        for (ResourceLocation item : replaced) tag(ModTags.Items.REPLACED_BY_CARCASS).addOptional(item);
        for (ModItems.Entries e : ModItems.all()) {
            MobDef def = e.def();
            Item carcass = e.carcass().get();
            tag(ModTags.Items.SCRAPPABLE).add(carcass);
            tag(ModTags.Items.CARCASSES).add(carcass);
            weight(def, carcass);
            if (e.drained() != null) {
                tag(ModTags.Items.SCRAPPABLE).add(e.drained().get());
                tag(ModTags.Items.CARCASSES).add(e.drained().get());
                weight(def, e.drained().get());
            }
            if (e.head() != null) {
                tag(ModTags.Items.SCRAPPABLE).add(e.head().get());
                tag(ModTags.Items.HEADS).add(e.head().get());
            }
            if (e.mount() != null) {
                tag(ModTags.Items.HEAD_MOUNT).add(e.mount().get());
                tag(ModTags.Items.HEAD_MOUNTS).add(e.mount().get());
            }
            if (e.skeleton() != null) tag(ModTags.Items.SKELETONS).add(e.skeleton().get());
            if (e.rug() != null) tag(ModTags.Items.RUGS).add(e.rug().get());
        }
        simpleItemTags();
        // Equal cuts of the same creature join the other cooking addon's product tag (optional: absent mods are skipped).
        for (CompatDefs.TagJoin join : CompatDefs.tagJoins()) tag(TagKey.create(Registries.ITEM, join.tag())).addOptional(join.item());
    }

    private void simpleItemTags() {
        for (ItemDefs.ItemDef def : ItemDefs.all()) {
            Item item = ModItems.simple(def.id()).get();
            switch (def.kind()) {
                case RAW_MEAT -> {
                    tag(ModTags.Items.FORGE_RAW_MEAT).add(item);
                    tag(ModTags.Items.BUTCHERY_RAW_MEAT).add(item);
                    tag(ModTags.Items.BUTCHERY_MEAT).add(item);
                }
                case COOKED_MEAT -> {
                    tag(ModTags.Items.FORGE_COOKED_MEAT).add(item);
                    tag(ModTags.Items.BUTCHERY_MEAT).add(item);
                }
                case SKIN -> tag(ModTags.Items.SKINS).add(item);
                default -> {
                }
            }
        }
    }

    private void weight(MobDef def, Item item) {
        if (def.weight() == Weight.LIGHT) tag(ModTags.Items.WEIGHTED_LIGHT).add(item);
        else if (def.weight() == Weight.HEAVY) tag(ModTags.Items.WEIGHTED_HEAVY).add(item);
    }
}
