package com.otectus.alexsbutchery.data;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.advancement.ButcheringTrigger;
import com.otectus.alexsbutchery.advancement.ButcheringTrigger.Action;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModItems;
import com.otectus.alexsbutchery.registry.ModTags;
import net.mcreator.butchery.init.ButcheryModItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.FrameType;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.ItemUsedOnLocationTrigger;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.ForgeAdvancementProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Our advancements, hung under Butchery's own tree ({@code butchery:butcher}) so they show in its tab. Most use the
 * {@code alexsbutchery:butchering} criterion; the trophy and rug ones use vanilla inventory and placement criteria.
 */
final class ModAdvancements implements ForgeAdvancementProvider.AdvancementGenerator {
    private static final Map<String, String[]> TEXT = new LinkedHashMap<>();

    static {
        text("wild_game", "Wild Game", "Kill one of Alex's Mobs creatures with a cleaver to get its carcass");
        text("where_it_lies", "Where It Lies", "Bleed a carcass too heavy to hang, right where it fell");
        text("birds_of_a_feather", "Birds of a Feather", "Pluck a bird's carcass with a skinning knife");
        text("big_game_hunter", "Big Game Hunter", "Mount the head of an Alex's Mobs creature at the taxidermy table");
        text("bare_bones", "Bare Bones", "Pour a bottle of sulfuric acid on a whole carcass and wait for its skeleton");
        text("ties_the_room_together", "Really Ties the Room Together", "Lay down a pelt rug");
        text("worm_food", "Worm Food", "Butcher a Void Worm down to its last cut");
    }

    private static void text(String id, String title, String description) {
        TEXT.put(id, new String[]{title, description});
    }

    /** Titles and descriptions for {@link ModLang}. */
    static void lang(BiConsumer<String, String> out) {
        TEXT.forEach((id, text) -> {
            out.accept(key(id, "title"), text[0]);
            out.accept(key(id, "description"), text[1]);
        });
    }

    private static String key(String id, String part) {
        return "advancements." + AlexsButchery.MOD_ID + "." + id + "." + part;
    }

    @Override
    public void generate(HolderLookup.Provider registries, Consumer<Advancement> out, ExistingFileHelper helper) {
        // Butchery's root lives in its jar, which datagen cannot see; a stand-in lets the builder write the parent id.
        Advancement butcher = Advancement.Builder.advancement().build(new ResourceLocation("butchery", "butcher"));
        var kangaroo = ModItems.of(MobDefs.byId("kangaroo"));
        var elephant = ModItems.of(MobDefs.byId("elephant"));

        Advancement wildGame = save(out, "wild_game", butcher, kangaroo.carcass().get(), FrameType.TASK,
                ButcheringTrigger.Instance.of(Action.KILL, null, null));
        Advancement whereItLies = save(out, "where_it_lies", wildGame, elephant.carcass().get(), FrameType.TASK,
                ButcheringTrigger.Instance.of(Action.BLEED, null, true));
        save(out, "birds_of_a_feather", wildGame, ModItems.of(MobDefs.byId("roadrunner")).carcass().get(), FrameType.TASK,
                ButcheringTrigger.Instance.of(Action.PLUCK, null, null));
        Advancement bigGame = save(out, "big_game_hunter", wildGame, ModItems.of(MobDefs.byId("rhinoceros")).mount().get(), FrameType.GOAL,
                InventoryChangeTrigger.TriggerInstance.hasItems(ItemPredicate.Builder.item().of(ModTags.Items.HEAD_MOUNTS).build()));
        save(out, "bare_bones", wildGame, ButcheryModItems.BOTTLE_OF_SULFURIC_ACID.get(), FrameType.TASK,
                ButcheringTrigger.Instance.of(Action.ACID, null, null));
        save(out, "ties_the_room_together", bigGame, ModItems.of(MobDefs.byId("tiger")).rug().get(), FrameType.TASK,
                ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(LocationCheck.checkLocation(LocationPredicate.Builder.location()
                        .setBlock(BlockPredicate.Builder.block().of(ModTags.Blocks.RUGS).build()))));
        save(out, "worm_food", whereItLies, ModItems.of(MobDefs.byId("void_worm")).carcass().get(), FrameType.CHALLENGE,
                ButcheringTrigger.Instance.of(Action.CUT_3, "void_worm", null));
    }

    private static Advancement save(Consumer<Advancement> out, String id, Advancement parent, ItemLike icon, FrameType frame,
                                    CriterionTriggerInstance criterion) {
        return Advancement.Builder.advancement()
                .parent(parent)
                .display(icon, Component.translatable(key(id, "title")), Component.translatable(key(id, "description")), null, frame,
                        true, true, false)
                .addCriterion(id, criterion)
                .save(out, AlexsButchery.id(id).toString());
    }
}
