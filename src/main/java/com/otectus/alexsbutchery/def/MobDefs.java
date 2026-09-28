package com.otectus.alexsbutchery.def;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.otectus.alexsbutchery.def.Drop.chance;
import static com.otectus.alexsbutchery.def.Drop.of;

/**
 * The mob table: every Alex's Mobs creature that butchers, in creative-tab order. Spectre, Sunbird, Guster, Rocky
 * Roller, Underminer, Enderiophage and Comb Jelly have nothing organic to butcher and are left out.
 */
public final class MobDefs {
    private static final Map<String, MobDef> BY_ID = new LinkedHashMap<>();
    private static final Map<ResourceLocation, MobDef> BY_ENTITY = new LinkedHashMap<>();

    private static final String FAT = "butchery:animal_fat";
    private static final String HOOF = "butchery:hoof";
    private static final String SCRAPS = "butchery:meat_scraps";
    private static final String TENTACLES = "butchery:tentacles";
    private static final String POISON = "butchery:poison_sac";
    private static final String FLESH = "butchery:flesh";
    private static final String SLIME = "butchery:slime_chunks";
    private static final String SCULK_BONE = "butchery:sculk_bone";
    private static final String BIRD_FOOT = "butchery:bird_foot";
    private static final String BONE = "minecraft:bone";
    private static final String FEATHER = "minecraft:feather";
    private static final String INK = "minecraft:ink_sac";
    private static final String FISH_BONES = "alexsmobs:fish_bones";
    private static final String FISH_OIL = "alexsmobs:fish_oil";

    static {
        // ---- Land mammals -------------------------------------------------------------------------------------
        add(mammal("grizzly_bear", "Grizzly Bear", "raw_bear_meat", ours("grizzly_pelt"), 1, 2).rug("grizzly_pelt")
                .drops(MobDef.CUT_3, of("alexsmobs:bear_fur", 1, 2)).replaces("alexsmobs:bear_fur"));
        add(mammal("gazelle", "Gazelle", "raw_venison", ours("gazelle_hide"), 1, 1)
                .drops(MobDef.HEAD, of("alexsmobs:gazelle_horn", 2)).replaces("alexsmobs:gazelle_horn"));
        add(mammal("gorilla", "Gorilla", "raw_primate_meat", ours("gorilla_hide"), 1, 2));
        add(mammal("kangaroo", "Kangaroo", "alexsmobs:kangaroo_meat", "alexsmobs:kangaroo_hide", 1, 2).heavy()
                .replaces("alexsmobs:kangaroo_meat", "alexsmobs:kangaroo_hide"));
        add(floor("elephant", "Elephant", "raw_elephant_meat", ours("elephant_hide"), 4, 6)
                .drops(MobDef.HEAD, chance(ours("elephant_tusk"), 2, 2, 0.75F)));
        add(mammal("rhinoceros", "Rhinoceros", "raw_rhino_meat", ours("rhino_hide"), 2, 3).heavy().mount(MountSize.LARGE)
                .drops(MobDef.HEAD, of(ours("rhino_horn"), 1)));
        add(mammal("bison", "Bison", "butchery:raw_chuck_steak", ours("bison_hide"), 1, 2).heavy().mount(MountSize.LARGE).rug("bison_hide")
                .drops("skin", of("alexsmobs:bison_fur", 2, 3))
                .drops(MobDef.CUT_1, of("butchery:raw_ribeye_steak", 1, 2))
                .drops(MobDef.CUT_2, of("butchery:raw_sirloin_steak", 1, 2), of("butchery:raw_tbone_steak", 1))
                .drops(MobDef.CUT_3, of("butchery:raw_rump_steak", 1, 2), of(HOOF, 2))
                .replaces("minecraft:beef", "alexsmobs:bison_fur"));
        add(mammal("moose", "Moose", "alexsmobs:moose_ribs", ours("moose_hide"), 1, 2).heavy().mount(MountSize.LARGE)
                .drops(MobDef.HEAD, chance("alexsmobs:moose_antler", 2, 2, 0.8F))
                .drops(MobDef.CUT_2, of(ours("raw_venison"), 1, 2)).replaces("alexsmobs:moose_ribs", "alexsmobs:moose_antler"));
        add(mammal("tiger", "Tiger", "raw_big_cat_meat", ours("tiger_pelt"), 1, 1).rug("tiger_pelt"));
        add(mammal("snow_leopard", "Snow Leopard", "raw_big_cat_meat", ours("snow_leopard_pelt"), 1, 1).rug("snow_leopard_pelt"));
        add(mammal("maned_wolf", "Maned Wolf", "butchery:raw_wolf_meat", ours("maned_wolf_pelt"), 1, 1));
        add(mammal("anteater", "Anteater", "raw_bushmeat", ours("anteater_hide"), 1, 1));
        add(mammal("gelada_monkey", "Gelada Monkey", "raw_primate_meat", ours("gelada_pelt"), 1, 1));
        add(mammal("dropbear", "Dropbear", "raw_bear_meat", ours("dropbear_pelt"), 1, 1)
                .drops(MobDef.CUT_1, of("alexsmobs:dropbear_claw", 2, 4)).replaces("alexsmobs:dropbear_claw"));
        add(mammal("froststalker", "Froststalker", "raw_froststalker_meat", ours("froststalker_hide"), 1, 1)
                .drops(MobDef.HEAD, of("alexsmobs:froststalker_horn", 1)).replaces("alexsmobs:froststalker_horn"));
        add(mammal("tusklin", "Tusklin", "raw_tusklin_meat", ours("tusklin_hide"), 1, 2)
                .drops(MobDef.HEAD, of(ours("tusklin_tusk"), 2)));
        add(mammal("bunfungus", "Bunfungus", "raw_bunfungus_meat", null, 1, 2)
                .drops(MobDef.CUT_2, of("minecraft:red_mushroom", 1, 2), of("minecraft:brown_mushroom", 1, 2)));
        add(mammal("warped_toad", "Warped Toad", "raw_warped_toad_leg", null, 1, 1));
        add(mammal("cosmaw", "Cosmaw", "raw_cosmaw_meat", ours("cosmaw_hide"), 1, 2).heavy());
        add(mammal("endergrade", "Endergrade", "raw_endergrade_meat", ours("endergrade_hide"), 1, 1)
                .drops(MobDef.CUT_2, of("minecraft:chorus_fruit", 1, 2)));
        add(mammal("straddler", "Straddler", "raw_straddler_meat", null, 1, 1).heavy().headless()
                .drops(MobDef.CUT_3, of("alexsmobs:straddlite", 1, 2)).replaces("alexsmobs:straddlite"));
        add(floor("laviathan", "Laviathan", "raw_laviathan_meat", ours("laviathan_hide"), 3, 4));
        add(mammal("seal", "Seal", "raw_seal_meat", ours("seal_skin"), 2, 2));
        add(floor("sea_bear", "Sea Bear", "raw_bear_meat", ours("sea_bear_hide"), 3, 4));

        // ---- Reptiles -----------------------------------------------------------------------------------------
        add(mammal("anaconda", "Anaconda", "raw_reptile_meat", ours("anaconda_skin"), 0, 2));
        add(small("rattlesnake", "Rattlesnake", "raw_reptile_meat", ours("rattlesnake_skin"))
                .drops(MobDef.CUT_2, of("alexsmobs:rattlesnake_rattle", 1)).drops(MobDef.CUT_1, of(POISON, 1))
                .replaces("alexsmobs:rattlesnake_rattle"));
        add(mammal("komodo_dragon", "Komodo Dragon", "raw_reptile_meat", ours("komodo_hide"), 1, 1)
                .drops(MobDef.CUT_1, of(POISON, 1)));
        add(mammal("crocodile", "Crocodile", "raw_reptile_meat", ours("crocodile_hide"), 1, 2)
                .drops("skin", of("alexsmobs:crocodile_scute", 2, 4)).replaces("alexsmobs:crocodile_scute"));
        add(small("caiman", "Caiman", "raw_reptile_meat", ours("caiman_hide"))
                .drops("skin", of("alexsmobs:crocodile_scute", 1, 2)));
        add(mammal("alligator_snapping_turtle", "Alligator Snapping Turtle", "butchery:raw_turtle_meat", null, 1, 1)
                .drops(MobDef.CUT_1, of(ours("snapping_turtle_shell"), 1), of("alexsmobs:spiked_scute", 1, 2))
                .replaces("alexsmobs:spiked_scute"));
        add(small("terrapin", "Terrapin", "butchery:raw_turtle_meat", null)
                .drops(MobDef.CUT_1, of(ours("terrapin_shell"), 1)));

        // ---- Birds --------------------------------------------------------------------------------------------
        add(bird("emu", "Emu", "raw_emu_meat", BloodClass.REGULAR)
                .drops("pluck", of("alexsmobs:emu_feather", 1, 2)));
        add(bird("roadrunner", "Roadrunner", "raw_game_bird", BloodClass.SMALL)
                .drops("pluck", chance("alexsmobs:roadrunner_feather", 1, 1, 0.5F)));
        add(bird("shoebill", "Shoebill", "raw_game_bird", BloodClass.REGULAR));
        add(bird("seagull", "Seagull", "raw_game_bird", BloodClass.SMALL));
        add(bird("crow", "Crow", "raw_game_bird", BloodClass.SMALL));
        add(bird("blue_jay", "Blue Jay", "raw_game_bird", BloodClass.SMALL));
        add(bird("toucan", "Toucan", "raw_game_bird", BloodClass.SMALL));
        add(bird("potoo", "Potoo", "raw_game_bird", BloodClass.SMALL));
        add(bird("bald_eagle", "Bald Eagle", "raw_game_bird", BloodClass.SMALL));
        add(bird("hummingbird", "Hummingbird", "raw_game_bird", BloodClass.SMALL));
        add(bird("soul_vulture", "Soul Vulture", null, BloodClass.REGULAR)
                .drops(MobDef.CUT_1, of("alexsmobs:soul_heart", 1)).drops(MobDef.CUT_2, of(BONE, 1, 2))
                .replaces("alexsmobs:soul_heart"));

        // ---- Small mammals ------------------------------------------------------------------------------------
        add(small("raccoon", "Raccoon", "raw_bushmeat", ours("raccoon_pelt"))
                .drops(MobDef.CUT_2, of("alexsmobs:raccoon_tail", 1)).replaces("alexsmobs:raccoon_tail"));
        add(small("skunk", "Skunk", "raw_bushmeat", ours("skunk_pelt")));
        add(small("tasmanian_devil", "Tasmanian Devil", "raw_bushmeat", ours("tasmanian_devil_pelt")));
        add(small("platypus", "Platypus", "raw_bushmeat", ours("platypus_pelt")).drops(MobDef.CUT_1, of(POISON, 1)));
        add(small("jerboa", "Jerboa", "raw_bushmeat", null).headless());
        add(small("sugar_glider", "Sugar Glider", "raw_bushmeat", null));
        add(small("capuchin_monkey", "Capuchin Monkey", "raw_primate_meat", null));
        add(small("rain_frog", "Rain Frog", "butchery:raw_gray_frog_leg", null).headless());

        // ---- Aquatic ------------------------------------------------------------------------------------------
        add(floor("orca", "Orca", "raw_whale_meat", ours("orca_hide"), 3, 4).drops(MobDef.CUT_2, of(FAT, 2, 3)));
        add(floor("cachalot_whale", "Cachalot Whale", "raw_whale_meat", ours("whale_hide"), 4, 6)
                .drops(MobDef.HEAD, of("alexsmobs:cachalot_whale_tooth", 2, 4), chance("alexsmobs:ambergris", 1, 1, 0.15F))
                .drops(MobDef.CUT_2, of(FAT, 3, 4)).replaces("alexsmobs:cachalot_whale_tooth", "alexsmobs:ambergris"));
        add(mammal("hammerhead_shark", "Hammerhead Shark", "raw_shark_meat", ours("shark_skin"), 0, 1)
                .drops(MobDef.HEAD, of("alexsmobs:shark_tooth", 2, 4)).replaces("alexsmobs:shark_tooth"));
        add(small("frilled_shark", "Frilled Shark", "raw_shark_meat", null)
                .drops(MobDef.HEAD, of("alexsmobs:serrated_shark_tooth", 1)).replaces("alexsmobs:serrated_shark_tooth"));
        add(MobDef.builder("giant_squid", "Giant Squid").floor().skinless().headless().noSkeleton()
                .drops(MobDef.CUT_1, of(TENTACLES, 3, 4), of("alexsmobs:lost_tentacle", 1))
                .drops(MobDef.CUT_2, of(TENTACLES, 3, 4), of(INK, 1, 2))
                .drops(MobDef.CUT_3, of(INK, 1), of(SCRAPS, 2, 3)).replaces("alexsmobs:lost_tentacle"));
        add(MobDef.builder("mimic_octopus", "Mimic Octopus").small().noBlood().headless()
                .drops(MobDef.CUT_1, of(TENTACLES, 1, 2)).drops(MobDef.CUT_2, of(TENTACLES, 1, 2)).drops(MobDef.CUT_3, of(INK, 1)));
        add(fish("catfish", "Catfish", "alexsmobs:raw_catfish", 1, 2).replaces("alexsmobs:raw_catfish"));
        add(fish("blobfish", "Blobfish", "alexsmobs:blobfish", 1, 1).replaces("alexsmobs:blobfish"));
        add(fish("cosmic_cod", "Cosmic Cod", "alexsmobs:cosmic_cod", 1, 1).replaces("alexsmobs:cosmic_cod"));
        add(fish("flying_fish", "Flying Fish", "alexsmobs:flying_fish", 1, 1).replaces("alexsmobs:flying_fish"));
        add(fish("devils_hole_pupfish", "Devil's Hole Pupfish", "minecraft:tropical_fish", 1, 1));
        add(fish("mudskipper", "Mudskipper", "minecraft:tropical_fish", 1, 1).replaces("minecraft:tropical_fish"));
        add(MobDef.builder("lobster", "Lobster").small().noBlood().headless()
                .drops(MobDef.CUT_1, of("alexsmobs:lobster_tail", 1)).drops(MobDef.CUT_2, of(SCRAPS, 1)).drops(MobDef.CUT_3, of(SCRAPS, 1))
                .replaces("alexsmobs:lobster_tail"));
        add(MobDef.builder("mantis_shrimp", "Mantis Shrimp").small().noBlood().headless()
                .drops(MobDef.CUT_1, of(ours("raw_mantis_shrimp_tail"), 1, 2)).drops(MobDef.CUT_2, of(SCRAPS, 1)).drops(MobDef.CUT_3, of(SCRAPS, 1)));
        add(MobDef.builder("triops", "Triops").small().noBlood().headless()
                .drops(MobDef.CUT_1, of(SCRAPS, 1)).drops(MobDef.CUT_2, of(FISH_BONES, 1)).drops(MobDef.CUT_3, of(SCRAPS, 1)));
        add(MobDef.builder("stradpole", "Stradpole").small().noBlood().headless()
                .drops(MobDef.CUT_1, of(SCRAPS, 1)).drops(MobDef.CUT_2, chance("alexsmobs:straddlite", 1, 1, 0.5F)).drops(MobDef.CUT_3, of(SCRAPS, 1)));

        // ---- Insects and other invertebrates ------------------------------------------------------------------
        add(MobDef.builder("banana_slug", "Banana Slug").small().noBlood().headless()
                .drops(MobDef.CUT_1, of("alexsmobs:banana_slug_slime", 1, 2)).drops(MobDef.CUT_2, of(SLIME, 1)).drops(MobDef.CUT_3, of(SCRAPS, 1))
                .replaces("alexsmobs:banana_slug_slime"));
        add(MobDef.builder("leafcutter_ant", "Leafcutter Ant").small().noBlood()
                .drops(MobDef.CUT_1, of(SCRAPS, 1)).drops(MobDef.CUT_2, of(SCRAPS, 1)).drops(MobDef.CUT_3, of(SCRAPS, 1)));
        add(MobDef.builder("cockroach", "Cockroach").small().noBlood()
                .drops(MobDef.CUT_1, of("alexsmobs:cockroach_wing", 1, 2)).drops(MobDef.CUT_2, of(SCRAPS, 1)).drops(MobDef.CUT_3, of(SCRAPS, 1))
                .replaces("alexsmobs:cockroach_wing"));
        add(MobDef.builder("fly", "Fly").small().noBlood().headless()
                .drops(MobDef.CUT_1, of("alexsmobs:maggot", 1, 2)).drops(MobDef.CUT_2, of(SCRAPS, 1)).drops(MobDef.CUT_3, of(SCRAPS, 1)));
        add(MobDef.builder("crimson_mosquito", "Crimson Mosquito").small().skinless().noSkeleton()
                .drops(MobDef.CUT_1, of("alexsmobs:mosquito_proboscis", 1)).drops(MobDef.CUT_2, chance("alexsmobs:blood_sac", 1, 1, 0.5F))
                .drops(MobDef.CUT_3, of(SCRAPS, 1, 2)).replaces("alexsmobs:mosquito_proboscis", "alexsmobs:blood_sac"));
        add(MobDef.builder("tarantula_hawk", "Tarantula Hawk").noBlood()
                .drops(MobDef.CUT_1, of("alexsmobs:tarantula_hawk_wing", 1)).drops(MobDef.CUT_2, of(SCRAPS, 1, 2)).drops(MobDef.CUT_3, of(SCRAPS, 1, 2))
                .replaces("alexsmobs:tarantula_hawk_wing"));
        add(MobDef.builder("centipede", "Cave Centipede").entity("alexsmobs:centipede_head").noBlood()
                .drops(MobDef.CUT_1, of("alexsmobs:centipede_leg", 2, 4)).drops(MobDef.CUT_2, of("alexsmobs:centipede_leg", 2, 4))
                .drops(MobDef.CUT_3, of(SCRAPS, 2, 3)).replaces("alexsmobs:centipede_leg"));
        add(MobDef.builder("warped_mosco", "Warped Mosco").floor().skinless().mount(MountSize.LARGE).noOrgans().noSkeleton()
                .drops(MobDef.CUT_1, of("alexsmobs:warped_muscle", 1, 2)).drops(MobDef.CUT_2, of("alexsmobs:warped_muscle", 1, 2), of("alexsmobs:hemolymph_sac", 1))
                .drops(MobDef.CUT_3, of("alexsmobs:hemolymph_sac", 1), of(SCRAPS, 2, 4)).replaces("alexsmobs:warped_muscle", "alexsmobs:hemolymph_sac"));
        add(MobDef.builder("mungus", "Mungus").noBlood().headless()
                .drops(MobDef.CUT_1, of("minecraft:red_mushroom", 1, 2), of("minecraft:brown_mushroom", 1, 2))
                .drops(MobDef.CUT_2, of("alexsmobs:mungal_spores", 1)).drops(MobDef.CUT_3, of("minecraft:mushroom_stem", 1, 2))
                .replaces("alexsmobs:mungal_spores"));

        // ---- Bone, sculk, void and other exotics --------------------------------------------------------------
        add(MobDef.builder("bone_serpent", "Bone Serpent").noBlood()
                .drops(MobDef.CUT_1, of("alexsmobs:bone_serpent_tooth", 1, 2)).drops(MobDef.CUT_2, of(BONE, 2, 4)).drops(MobDef.CUT_3, of(BONE, 2, 4))
                .replaces("alexsmobs:bone_serpent_tooth"));
        add(MobDef.builder("skelewag", "Skelewag").noBlood()
                .drops(MobDef.CUT_1, of(BONE, 1, 2)).drops(MobDef.CUT_2, of(BONE, 1, 2)).drops(MobDef.CUT_3, of(BONE, 1, 2)));
        add(MobDef.builder("skreecher", "Skreecher").noBlood()
                .drops(MobDef.CUT_1, of("alexsmobs:skreecher_soul", 1)).drops(MobDef.CUT_2, of(SCULK_BONE, 1, 2)).drops(MobDef.CUT_3, of(SCULK_BONE, 1))
                .replaces("alexsmobs:skreecher_soul"));
        add(mammal("farseer", "Farseer", FLESH, null, 1, 1)
                .drops(MobDef.CUT_1, of("alexsmobs:farseer_arm", 1)).replaces("alexsmobs:farseer_arm"));
        add(mammal("murmur", "Murmur", FLESH, null, 1, 1).headless().noSkeleton());
        add(MobDef.builder("mimicube", "Mimicube").noBlood().headless()
                .drops(MobDef.CUT_1, of(SLIME, 1, 2)).drops(MobDef.CUT_2, of(SLIME, 1, 2)).drops(MobDef.CUT_3, of("alexsmobs:mimicream", 1))
                .replaces("alexsmobs:mimicream"));
        add(MobDef.builder("void_worm", "Void Worm").floor().skinless().mount(MountSize.LARGE).bossTool().noSkeleton()
                .drops(MobDef.HEAD, of("alexsmobs:void_worm_mandible", 2), of("alexsmobs:void_worm_eye", 1))
                .drops(MobDef.CUT_1, of(ours("void_worm_flesh"), 3, 4)).drops(MobDef.CUT_2, of(ours("void_worm_flesh"), 3, 4))
                .drops(MobDef.CUT_3, of(ours("void_worm_flesh"), 2, 4), of(BONE, 4, 6))
                .replaces("alexsmobs:void_worm_eye", "alexsmobs:void_worm_mandible"));
    }

    // ---- family helpers --------------------------------------------------------------------------------------

    private static String ours(String path) {
        return "alexsbutchery:" + path;
    }

    private static String meatId(String meat) {
        return meat.contains(":") ? meat : ours(meat);
    }

    /** A regular hook carcass: skin, then three cuts of its meat with fat and bones. */
    private static MobDef.Builder mammal(String id, String name, String meat, @Nullable String skin, int fat, int skinCount) {
        MobDef.Builder b = MobDef.builder(id, name);
        String m = meatId(meat);
        if (skin == null) b.skinless();
        else b.drops("skin", of(skin, Math.max(1, skinCount - 1), skinCount));
        b.drops(MobDef.CUT_1, of(m, 1, 2));
        if (fat > 0) b.drops(MobDef.CUT_1, of(FAT, 1, fat));
        b.drops(MobDef.CUT_2, of(m, 2, 3));
        b.drops(MobDef.CUT_3, of(m, 1, 2), of(BONE, 2, 3));
        return b;
    }

    /** A small hook carcass: one skin, three light cuts. */
    private static MobDef.Builder small(String id, String name, String meat, @Nullable String skin) {
        MobDef.Builder b = MobDef.builder(id, name).small();
        String m = meatId(meat);
        if (skin == null) b.skinless();
        else b.drops("skin", of(skin, 1));
        b.drops(MobDef.CUT_1, of(m, 1));
        b.drops(MobDef.CUT_2, of(m, 1));
        b.drops(MobDef.CUT_3, of(BONE, 1));
        return b;
    }

    /** A bird: plucked instead of skinned, feet in the last cut. {@code meat} null means no meat family (bones only). */
    private static MobDef.Builder bird(String id, String name, @Nullable String meat, BloodClass blood) {
        MobDef.Builder b = MobDef.builder(id, name).pluck().blood(blood);
        if (blood == BloodClass.SMALL) b.mount(MountSize.SMALL);
        boolean regular = blood == BloodClass.REGULAR;
        b.drops("pluck", of(FEATHER, regular ? 3 : 1, regular ? 5 : 3));
        if (meat != null) {
            String m = meatId(meat);
            b.drops(MobDef.CUT_1, of(m, 1, regular ? 2 : 1));
            b.drops(MobDef.CUT_2, of(m, 1));
        }
        b.drops(MobDef.CUT_3, of(BIRD_FOOT, 2), of(BONE, 1));
        return b;
    }

    /** A floor carcass: placed where it dies, thick hide, heavy cuts. */
    private static MobDef.Builder floor(String id, String name, String meat, @Nullable String skin, int skinMin, int skinMax) {
        MobDef.Builder b = MobDef.builder(id, name).floor();
        String m = meatId(meat);
        if (skin == null) b.skinless();
        else b.drops("skin", of(skin, skinMin, skinMax));
        b.drops(MobDef.CUT_1, of(m, 3, 5), of(FAT, 2, 3));
        b.drops(MobDef.CUT_2, of(m, 4, 6));
        b.drops(MobDef.CUT_3, of(m, 3, 4), of(BONE, 6, 8));
        return b;
    }

    /** A fish: no blood, no head block, but a fish skeleton; the fish item, then bones and a chance of oil. */
    private static MobDef.Builder fish(String id, String name, String item, int min, int max) {
        return MobDef.builder(id, name).small().noBlood().headless().skeleton()
                .drops(MobDef.CUT_1, of(item, min, max))
                .drops(MobDef.CUT_2, of(FISH_BONES, 1))
                .drops(MobDef.CUT_3, chance(FISH_OIL, 1, 1, 0.15F), of(SCRAPS, 1));
    }

    private static void add(MobDef.Builder builder) {
        MobDef def = builder.build();
        if (BY_ID.put(def.id(), def) != null) throw new IllegalStateException("Duplicate mob definition " + def.id());
        BY_ENTITY.put(def.entity(), def);
    }

    public static Collection<MobDef> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    @Nullable
    public static MobDef byId(String id) {
        return BY_ID.get(id);
    }

    @Nullable
    public static MobDef byEntity(EntityType<?> type) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return key == null ? null : BY_ENTITY.get(key);
    }

    private MobDefs() {}
}
