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
        add("message.alexsbutchery.too_heavy_to_hang", "This carcass cannot be hung; butcher it where it lies");
        add("hint.alexsbutchery.bleed", "Cleaver: bleed the carcass");
        add("hint.alexsbutchery.bleeding", "Bleeding...");
        add("hint.alexsbutchery.dissolving", "Dissolving in acid...");
        add("message.alexsbutchery.dissolving", "The acid is still dissolving this carcass.");
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
        add("jei.alexsbutchery.count", "Per cut: %s");
        add("jei.alexsbutchery.chance", "Chance: %s%%");
        add("jei.alexsbutchery.provider", "From %s");
        add("jei.alexsbutchery.variant", "Matches the creature's saved %s");
        add("jei.alexsbutchery.otherwise", "Carcasses no variant above matches");
        add("jei.alexsbutchery.custom_short", "Server-defined");
        add("jei.alexsbutchery.custom", "This server's data pack sets this stage's drops; the items shown may be incomplete.");

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
        add("jade.alexsbutchery.rack.wet", "Next: a wet sponge or rag");
        add("jade.alexsbutchery.rack.curing_wait", "Curing...");
        add("config.jade.plugin_alexsbutchery.butchery_carcass", "Butchery carcass (Alex's Butchery)");
        add("config.jade.plugin_alexsbutchery.butchery_skin_rack", "Butchery skin rack (Alex's Butchery)");
        add("jade.alexsbutchery.bleeding_wait", "Bleeding...");
        add("jade.alexsbutchery.tray", "Put it on a metal tray to cut it up");
        add("jade.alexsbutchery.tool.boss_tool", "Boss tool (Netherite Cleaver)");
        add("jade.alexsbutchery.yields", "%s: %s");
        add("jade.alexsbutchery.group.meat", "Meat");
        add("jade.alexsbutchery.group.shell", "Shell");
        add("jade.alexsbutchery.group.tail", "Tail");
        add("jade.alexsbutchery.group.tentacles", "Tentacles");
        add("jade.alexsbutchery.group.fish", "Fish");

        // Operator diagnostics: /alexsbutchery compat ...
        String c = "commands.alexsbutchery.compat.";
        add(c + "none", "No compatibility rules are loaded on this server.");
        add(c + "status.title", "Alex's Butchery compatibility");
        add(c + "provider.absent", "%s (%s): not installed");
        add(c + "provider.active", "%s (%s): %s, %s active profiles");
        add(c + "provider.disabled", "%s (%s): %s, disabled by compat.disabledProviders");
        add(c + "settings", "Settings: %s");
        add(c + "files", "%s profiles, %s selections, %s disabled files from %s");
        add(c + "conflict.owned", "Recipe conflict %s: owned by %s");
        add(c + "conflict.reported", "Recipe conflict %s: %s");
        add(c + "degraded", "The profile files could not be loaded: every carcass yields native items.");
        add(c + "counts", "%s errors, %s warnings (/alexsbutchery compat validate); client view %s");
        add(c + "validate.title", "Compatibility validation");
        add(c + "validate.ok", "No errors or warnings: every active profile's items exist and every rule is unambiguous.");
        add(c + "diagnostic", "%s %s: %s");
        add(c + "unknown", "%s is not one of Alex's Butchery's carcasses.");
        add(c + "explain.providers", "Providers: %s");
        add(c + "explain.disabled_mob", "disabledMobs lists %s: its kills never drop a carcass.");
        add(c + "explain.deferred", "Left to %s (deferToOfficialAddon).");
        add(c + "explain.rule", "%s at %s: %s");
        add(c + "explain.fallback", "  native fallback: %s");
        add(c + "explain.rejected", "  not used: %s (%s): %s");
        add(c + "explain.profiles", "Profiles: %s");
        add(c + "explain.removes", "A carcass kill removes from the death drops: %s");
        add(c + "explain.removes_global", "For every creature (added to #alexsbutchery:replaced_by_carcass): %s");
        add(c + "explain.kept", "Kept: every other death drop, and any stack carrying item data.");
        add(c + "explain.no_replace", "replaceMobDrops=false: a carcass kill keeps every death drop, so the meat drops at death and again from the carcass.");
        add(c + "explain.conflict", "Recipe conflict %s: owned by %s; removed %s; alternates %s");
        add(c + "more", "... %s more lines; the server log lists every diagnostic.");

        ModAdvancements.lang(this::add);
    }
}
