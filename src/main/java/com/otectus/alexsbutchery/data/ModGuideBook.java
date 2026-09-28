package com.otectus.alexsbutchery.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.otectus.alexsbutchery.def.ItemDefs;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.def.SkinStep;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * An "Alex's Mobs" category for Butchery's own guide book. Patchouli 1.20 dropped book extension, and loads a book's
 * contents from every resource pack under the book's namespace, so these pages ship at
 * {@code assets/butchery/patchouli_books/butchers_guide/en_us/...} beside Butchery's, in files named for this mod so
 * none of Butchery's is replaced. The item lists come from the mob table; the pages are inert without Patchouli.
 */
final class ModGuideBook implements DataProvider {
    private static final String BOOK = "butchery/patchouli_books/butchers_guide/en_us/";
    private static final String CATEGORY = "butchery:alexsbutchery";

    private final PackOutput output;
    private final List<CompletableFuture<?>> writes = new ArrayList<>();

    ModGuideBook(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        writes.clear();
        JsonObject category = new JsonObject();
        category.addProperty("name", "Alex's Mobs");
        category.addProperty("description", "Butchering the creatures of Alex's Mobs, from the kangaroo to the Void Worm, with Butchery's own tools and stations.");
        category.addProperty("icon", carcass(MobDefs.byId("kangaroo")));
        category.addProperty("sortnum", 4);
        write(cache, "categories/alexsbutchery.json", category);

        int creatures = MobDefs.all().size();
        entry(cache, "overview", "Alex's Mobs Carcasses", carcass(MobDefs.byId("kangaroo")), 0,
                spotlight(carcasses(def -> true), "Wild Game",
                        "Every one of the " + creatures + " creatures of Alex's Mobs that can be butchered drops a carcass when killed with a "
                                + "cleaver, like Butchery's own animals. Babies never do. The carcass replaces the meat, hides and parts it "
                                + "yields later; anything else the creature drops, it still drops."),
                text("Most carcasses are carried to a $(l:butchery:items/hook)hook$(), hung and bled, then taken apart with the cleaver "
                        + "and the skinning knife. The biggest animals are butchered where they fall, birds are plucked instead of skinned, "
                        + "and creatures without blood are cut up straight away.$(br2)The Butchering tab in JEI and Jade's tooltip show "
                        + "what every step yields and which tool comes next."));

        entry(cache, "hook_carcasses", "Hook Carcasses", carcass(MobDefs.byId("kangaroo")), 1,
                spotlight(carcasses(def -> def.bleeds() && !def.floor()), "On the Hook",
                        "Hang the carcass on a hook or a rope by right-clicking it with the carcass. A cleaver starts the bleeding: small "
                                + "creatures give 250 mB of blood, the rest 1000 mB, into a $(l:butchery:blocks/blood_grate)blood grate$() below."),
                text("Once drained, the cleaver takes the head, the skinning knife takes the skin, and three more cleaver cuts give the "
                        + "meat, fat, bones and Butchery's organs. A drained carcass can also be taken down and butchered on the ground."));

        entry(cache, "floor_carcasses", "Floor Carcasses", carcass(MobDefs.byId("elephant")), 2,
                spotlight(carcasses(MobDef::floor), "Where It Falls",
                        "Elephants, whales, the Laviathan and a few other giants are far too heavy to carry. Their carcass falls where "
                                + "they die. Bleed it with a cleaver right there, 2000 mB of blood, then cut it up in place."),
                text("A blood grate under the carcass still catches the blood; without one it pools on the ground. The Void Worm needs "
                        + "Butchery's boss tool, the netherite cleaver, for the killing blow before it leaves a carcass at all."));

        entry(cache, "birds", "Birds", carcass(MobDefs.byId("roadrunner")), 3,
                spotlight(carcasses(def -> def.skin() == SkinStep.PLUCK), "Plucking",
                        "Birds hang and bleed like any carcass, but the skinning knife plucks them instead of skinning them: feathers, "
                                + "and the emu's and roadrunner's own plumes."));

        entry(cache, "bloodless", "Bloodless Creatures", carcass(MobDefs.byId("lobster")), 4,
                spotlight(carcasses(def -> !def.bleeds()), "No Blood to Drain",
                        "Fish, insects, crustaceans and the creatures of bone, slime and fungus have nothing to drain. Their carcass is "
                                + "cut up right away with the cleaver, on the ground or on a hook."));

        entry(cache, "skins", "Skins and Pelts", ItemDefs.all().stream().filter(d -> d.kind() == ItemDefs.Kind.SKIN).findFirst().map(d -> "alexsbutchery:" + d.id()).orElseThrow(), 5,
                spotlight(itemDefs(ItemDefs.Kind.SKIN), "Pelts and Hides",
                        "Skinning gives a pelt, hide or skin of its own. Hang it on Butchery's $(l:butchery:blocks/skin_rack)skin rack$(), "
                                + "salt it, wet it with a sponge and wait a minute and a half for leather. With Immersive Tanning they are "
                                + "raw hides for its rack as well."));

        entry(cache, "trophies", "Heads and Trophies", id(ModItems.of(MobDefs.byId("rhinoceros")).mount()), 6,
                spotlight(list(e -> e.head()), "Trophies",
                        "The first cut takes the head. At Butchery's taxidermy table, wheat, the head and an empty head mount of the right "
                                + "size make a trophy for the wall; the mount's size follows the creature's."),
                spotlight(list(e -> e.mount()), "Mounted Heads",
                        "Small creatures need the small mount, large ones such as the rhinoceros, the moose and the bison the large one. "
                                + "Butchery's hammer breaks an unwanted head down."));

        entry(cache, "skeletons", "Skeletons", id(ModItems.of(MobDefs.byId("tiger")).skeleton()), 7,
                spotlight(list(e -> e.skeleton()), "Bare Bones",
                        "Pour a $(item)bottle of sulfuric acid$() on a whole carcass, fresh or drained, lying or hanging. It fizzes for "
                                + "eleven seconds and leaves the creature's skeleton in its place. Creatures without bones leave none."));

        List<JsonObject> rugPages = new ArrayList<>();
        rugPages.add(spotlight(list(e -> e.rug()), "Pelt Rugs",
                "Three pelts around the head make a rug that spreads over the floor, head and all. Rugs dampen vibrations like carpet."));
        for (ModItems.Entries e : ModItems.all()) {
            if (e.rug() == null) continue;
            JsonObject page = new JsonObject();
            page.addProperty("type", "patchouli:crafting");
            page.addProperty("recipe", "alexsbutchery:rug/" + e.def().id() + "_rug");
            rugPages.add(page);
        }
        entry(cache, "rugs", "Pelt Rugs", id(ModItems.of(MobDefs.byId("tiger")).rug()), 8, rugPages.toArray(JsonObject[]::new));

        entry(cache, "meats", "Wild Meats", "alexsbutchery:raw_venison", 9,
                spotlight(itemDefs(ItemDefs.Kind.RAW_MEAT), "Wild Meats",
                        "New meats cook in a furnace, smoker or over a campfire. On Farmer's Delight's cutting board a knife turns them "
                                + "into minced beef, chicken cuts, bacon or cod slices for its recipes."),
                text("With Alex's Mobs Delight installed, the carcasses yield that mod's bear, emu, seal, whale and other meats "
                        + "instead of ours, so there is only one of each."));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private void entry(CachedOutput cache, String file, String name, String icon, int sortnum, JsonObject... pages) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", name);
        entry.addProperty("icon", icon);
        entry.addProperty("category", CATEGORY);
        entry.addProperty("read_by_default", true);
        entry.addProperty("sortnum", sortnum);
        JsonArray array = new JsonArray();
        for (JsonObject page : pages) array.add(page);
        entry.add("pages", array);
        write(cache, "entries/alexsbutchery/" + file + ".json", entry);
    }

    private static JsonObject spotlight(String items, String title, String text) {
        JsonObject page = new JsonObject();
        page.addProperty("type", "patchouli:spotlight");
        page.addProperty("item", items);
        page.addProperty("title", title);
        page.addProperty("text", text);
        return page;
    }

    private static JsonObject text(String text) {
        JsonObject page = new JsonObject();
        page.addProperty("type", "patchouli:text");
        page.addProperty("text", text);
        return page;
    }

    /** The carcass items of the matching mobs, as Patchouli's comma-separated item list (the spotlight cycles them). */
    private static String carcasses(Predicate<MobDef> filter) {
        return MobDefs.all().stream().filter(filter).map(ModGuideBook::carcass).collect(Collectors.joining(","));
    }

    private static String list(Function<ModItems.Entries, RegistryObject<Item>> kind) {
        return ModItems.all().stream().map(kind).filter(Objects::nonNull).map(ModGuideBook::id).collect(Collectors.joining(","));
    }

    private static String itemDefs(ItemDefs.Kind kind) {
        return ItemDefs.all().stream().filter(d -> d.kind() == kind).map(d -> "alexsbutchery:" + d.id()).collect(Collectors.joining(","));
    }

    private static String carcass(MobDef def) {
        return id(ModItems.of(def).carcass());
    }

    private static String id(RegistryObject<Item> item) {
        return Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item.get())).toString();
    }

    private void write(CachedOutput cache, String path, JsonObject json) {
        Path target = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(BOOK + path);
        writes.add(DataProvider.saveStable(cache, json, target));
    }

    @Override
    public String getName() {
        return "Butchers Guide pages (Alex's Mobs)";
    }
}
