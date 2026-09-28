package com.otectus.alexsbutchery.def;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The items this mod adds where neither Butchery nor Alex's Mobs has one: meat families (raw and cooked, with
 * smelting, smoking and campfire recipes), skins and pelts for the skin rack, and a few trophies. Everything else a
 * carcass drops is an existing Butchery or Alex's Mobs item.
 */
public final class ItemDefs {

    public enum Kind { RAW_MEAT, COOKED_MEAT, SKIN, SPECIAL }

    /** {@code icon}/{@code palette} select skin/trophy art; food cuts in {@code art/food_art.py} use {@code id}. */
    public record ItemDef(String id, String name, Kind kind, int nutrition, float saturationMod, @Nullable String cookedId,
                          String icon, String palette) {
        public boolean food() {
            return kind == Kind.RAW_MEAT || kind == Kind.COOKED_MEAT;
        }
    }

    private static final Map<String, ItemDef> BY_ID = new LinkedHashMap<>();
    private static final List<ItemDef> MEAT_FAMILIES = new ArrayList<>();

    static {
        // family, name, raw food, cooked food, icon template, palette
        meat("venison", "Venison", 3, 0.3F, 8, 0.8F, "steak", "venison");
        meat("game_bird", "Game Bird", 2, 0.3F, 6, 0.6F, "drumstick", "poultry");
        meat("big_cat_meat", "Big Cat Meat", 3, 0.3F, 7, 0.7F, "steak", "dark_red");
        meat("bear_meat", "Bear Meat", 3, 0.3F, 8, 0.8F, "chunk", "dark_red");
        meat("primate_meat", "Primate Meat", 3, 0.3F, 7, 0.7F, "chunk", "red");
        meat("reptile_meat", "Reptile Meat", 2, 0.3F, 6, 0.6F, "fillet", "pale");
        meat("bushmeat", "Bushmeat", 2, 0.3F, 5, 0.6F, "chunk", "red");
        meat("whale_meat", "Whale Meat", 4, 0.4F, 10, 0.9F, "slab", "dark_red");
        meat("seal_meat", "Seal Meat", 3, 0.3F, 8, 0.8F, "chunk", "dark_red");
        meat("shark_meat", "Shark Meat", 3, 0.3F, 7, 0.7F, "fillet", "pale");
        meat("elephant_meat", "Elephant Meat", 4, 0.4F, 10, 0.9F, "slab", "red");
        meat("rhino_meat", "Rhinoceros Meat", 4, 0.4F, 9, 0.8F, "slab", "red");
        meat("cosmaw_meat", "Cosmaw Meat", 3, 0.3F, 7, 0.7F, "chunk", "violet");
        meat("endergrade_meat", "Endergrade Meat", 3, 0.3F, 7, 0.7F, "chunk", "violet");
        meat("laviathan_meat", "Laviathan Meat", 4, 0.4F, 9, 0.9F, "slab", "ember");
        meat("straddler_meat", "Straddler Meat", 3, 0.3F, 7, 0.7F, "chunk", "ember");
        meat("froststalker_meat", "Froststalker Meat", 3, 0.3F, 7, 0.7F, "chunk", "frost");
        meat("tusklin_meat", "Tusklin Meat", 3, 0.3F, 8, 0.8F, "steak", "pork");
        meat("emu_meat", "Emu Meat", 3, 0.3F, 7, 0.7F, "drumstick", "poultry");
        meat("warped_toad_leg", "Warped Toad Leg", 2, 0.3F, 5, 0.6F, "drumstick", "teal");
        meat("bunfungus_meat", "Bunfungus Meat", 3, 0.3F, 7, 0.7F, "chunk", "pale");
        meat("mantis_shrimp_tail", "Mantis Shrimp Tail", 2, 0.3F, 6, 0.6F, "tail", "shrimp");
        meatIds("void_worm_flesh", "cooked_void_worm_flesh", "Void Worm Flesh", "Cooked Void Worm Flesh", 3, 0.3F, 10, 1.0F, "slab", "void");

        skin("grizzly_pelt", "Grizzly Pelt", "pelt", "brown_fur");
        skin("gazelle_hide", "Gazelle Hide", "hide", "tan");
        skin("gorilla_hide", "Gorilla Hide", "pelt", "black_fur");
        skin("elephant_hide", "Elephant Hide", "hide", "grey");
        skin("rhino_hide", "Rhinoceros Hide", "hide", "grey");
        skin("bison_hide", "Bison Hide", "pelt", "brown_fur");
        skin("moose_hide", "Moose Hide", "hide", "dark_brown");
        skin("tiger_pelt", "Tiger Pelt", "pelt_striped", "tiger");
        skin("snow_leopard_pelt", "Snow Leopard Pelt", "pelt_spotted", "snow_leopard");
        skin("maned_wolf_pelt", "Maned Wolf Pelt", "pelt", "rust_fur");
        skin("anteater_hide", "Anteater Hide", "pelt", "grey_fur");
        skin("gelada_pelt", "Gelada Pelt", "pelt", "brown_fur");
        skin("dropbear_pelt", "Dropbear Pelt", "pelt", "grey_fur");
        skin("froststalker_hide", "Froststalker Hide", "hide", "ice");
        skin("tusklin_hide", "Tusklin Hide", "hide", "pork_hide");
        skin("cosmaw_hide", "Cosmaw Hide", "hide", "violet_hide");
        skin("endergrade_hide", "Endergrade Hide", "hide", "violet_hide");
        skin("laviathan_hide", "Laviathan Hide", "hide", "obsidian");
        skin("anaconda_skin", "Anaconda Skin", "scales", "olive");
        skin("rattlesnake_skin", "Rattlesnake Skin", "scales", "sand");
        skin("komodo_hide", "Komodo Hide", "scales", "olive");
        skin("crocodile_hide", "Crocodile Hide", "scales", "croc");
        skin("caiman_hide", "Caiman Hide", "scales", "croc");
        skin("raccoon_pelt", "Raccoon Pelt", "pelt", "grey_fur");
        skin("skunk_pelt", "Skunk Pelt", "pelt_striped", "skunk");
        skin("tasmanian_devil_pelt", "Tasmanian Devil Pelt", "pelt", "black_fur");
        skin("platypus_pelt", "Platypus Pelt", "pelt", "dark_brown");
        skin("seal_skin", "Seal Skin", "hide", "seal");
        skin("orca_hide", "Orca Hide", "hide", "orca");
        skin("whale_hide", "Whale Hide", "hide", "whale");
        skin("sea_bear_hide", "Sea Bear Hide", "pelt", "sea_bear");
        skin("shark_skin", "Shark Skin", "hide", "shark");

        special("elephant_tusk", "Elephant Tusk", "tusk", "ivory");
        special("rhino_horn", "Rhinoceros Horn", "horn", "horn");
        special("tusklin_tusk", "Tusklin Tusk", "tusk", "ivory");
        special("snapping_turtle_shell", "Snapping Turtle Shell", "shell", "dark_shell");
        special("terrapin_shell", "Terrapin Shell", "shell", "green_shell");
    }

    private static void meat(String family, String name, int rawFood, float rawSat, int cookedFood, float cookedSat, String icon, String palette) {
        meatIds("raw_" + family, "cooked_" + family, "Raw " + name, "Cooked " + name, rawFood, rawSat, cookedFood, cookedSat, icon, palette);
    }

    private static void meatIds(String rawId, String cookedId, String rawName, String cookedName, int rawFood, float rawSat,
                                int cookedFood, float cookedSat, String icon, String palette) {
        ItemDef raw = new ItemDef(rawId, rawName, Kind.RAW_MEAT, rawFood, rawSat, cookedId, icon, palette);
        ItemDef cooked = new ItemDef(cookedId, cookedName, Kind.COOKED_MEAT, cookedFood, cookedSat, null, icon + "_cooked", palette);
        add(raw);
        add(cooked);
        MEAT_FAMILIES.add(raw);
    }

    private static void skin(String id, String name, String icon, String palette) {
        add(new ItemDef(id, name, Kind.SKIN, 0, 0F, null, icon, palette));
    }

    private static void special(String id, String name, String icon, String palette) {
        add(new ItemDef(id, name, Kind.SPECIAL, 0, 0F, null, icon, palette));
    }

    private static void add(ItemDef def) {
        if (BY_ID.put(def.id(), def) != null) throw new IllegalStateException("Duplicate item definition " + def.id());
    }

    public static List<ItemDef> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    /** The raw meats; each one's {@code cookedId} names its cooked counterpart. */
    public static List<ItemDef> meatFamilies() {
        return Collections.unmodifiableList(MEAT_FAMILIES);
    }

    @Nullable
    public static ItemDef byId(String id) {
        return BY_ID.get(id);
    }

    private ItemDefs() {}
}
