package com.otectus.alexsbutchery.data;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.def.ItemDefs;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

final class ModLang extends LanguageProvider {

    ModLang(PackOutput output) {
        super(output, AlexsButchery.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        for (ModBlocks.Entries e : ModBlocks.all()) {
            MobDef def = e.def();
            add(e.carcass().get(), def.displayName() + " Carcass");
            if (e.drained() != null) add(e.drained().get(), "Drained " + def.displayName() + " Carcass");
            if (e.head() != null) add(e.head().get(), def.displayName() + " Head");
            if (e.mount() != null) add(e.mount().get(), def.displayName() + " Head Mount");
            if (e.skeleton() != null) add(e.skeleton().get(), def.displayName() + " Skeleton");
            if (e.rug() != null) add(e.rug().get(), def.displayName() + " Rug");
        }
        for (ItemDefs.ItemDef def : ItemDefs.all()) add(ModItems.simple(def.id()).get(), def.name());
        add("message.alexsbutchery.too_heavy_to_hang", "This carcass is too heavy to hang; butcher it where it lies");
        add("hint.alexsbutchery.bleed", "Cleaver: bleed the carcass");
        add("hint.alexsbutchery.bleeding", "Bleeding...");
        add("hint.alexsbutchery.head", "Cleaver: remove the head");
        add("hint.alexsbutchery.skin", "Skinning knife: skin");
        add("hint.alexsbutchery.pluck", "Skinning knife: pluck");
        add("hint.alexsbutchery.cut", "Cleaver: cut");
        add(ModBlocks.SKIN_RACK.get(), "Skin Rack");
        add("message.alexsbutchery.rack_already_salted", "You've already applied salt to this skin.");
        add("message.alexsbutchery.rack_curing", "The skin is still curing.");
        add("jei.alexsbutchery.butchering", "Butchering");
        add("jei.alexsbutchery.stage.head", "Remove the head");
        add("jei.alexsbutchery.stage.skin", "Skin");
        add("jei.alexsbutchery.stage.pluck", "Pluck");
        add("jei.alexsbutchery.stage.cut_1", "First cut");
        add("jei.alexsbutchery.stage.cut_2", "Second cut");
        add("jei.alexsbutchery.stage.cut_3", "Last cut");

        // Jade
        add("config.jade.plugin_alexsbutchery.carcass", "Alex's Butchery carcass");
        add("config.jade.plugin_alexsbutchery.skin_rack", "Alex's Butchery skin rack");
        add("jade.alexsbutchery.next", "Next: %s, %s");
        add("jade.alexsbutchery.progress", "Butchered: %s of %s steps");
        add("jade.alexsbutchery.hang", "Hang it on a hook to bleed it");
        add("jade.alexsbutchery.bleeding", "Bleeding: %s%%");
        add("jade.alexsbutchery.dissolving", "Dissolving: %s%%");
        add("jade.alexsbutchery.acid", "Acid: dissolves it into a skeleton");
        add("jade.alexsbutchery.tool.cleaver", "Cleaver");
        add("jade.alexsbutchery.tool.skinning_knife", "Skinning Knife");
        add("jade.alexsbutchery.action.bleed", "bleed it");
        add("jade.alexsbutchery.action.head", "take the head");
        add("jade.alexsbutchery.action.skin", "skin it");
        add("jade.alexsbutchery.action.pluck", "pluck it");
        add("jade.alexsbutchery.action.cut", "cut it up");
        add("jade.alexsbutchery.action.last_cut", "the last cut");
        add("jade.alexsbutchery.rack.skin", "Skin: %s");
        add("jade.alexsbutchery.rack.salt", "Next: salt");
        add("jade.alexsbutchery.rack.sponge", "Next: a wet sponge");
        add("jade.alexsbutchery.rack.curing", "Curing: %s%%");
        add("jade.alexsbutchery.rack.done", "Cured: take the leather");

        ModAdvancements.lang(this::add);
    }
}
