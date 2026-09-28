package com.otectus.alexsbutchery.registry;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.def.ItemDefs;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.item.MobBlockItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AlexsButchery.MOD_ID);

    public enum Kind { CARCASS, DRAINED, HEAD, MOUNT, SKELETON, RUG }

    public record Entries(MobDef def, RegistryObject<Item> carcass, @Nullable RegistryObject<Item> drained,
                          @Nullable RegistryObject<Item> head, @Nullable RegistryObject<Item> mount,
                          @Nullable RegistryObject<Item> skeleton, @Nullable RegistryObject<Item> rug) {
        @Nullable
        public RegistryObject<Item> of(Kind kind) {
            return switch (kind) {
                case CARCASS -> carcass;
                case DRAINED -> drained;
                case HEAD -> head;
                case MOUNT -> mount;
                case SKELETON -> skeleton;
                case RUG -> rug;
            };
        }
    }

    /** What a mob item is, for the hook handler and tabs. */
    public record Owner(MobDef def, Kind kind) {}

    private static final Map<MobDef, Entries> BY_DEF = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<Item>> SIMPLE = new LinkedHashMap<>();
    private static Map<Item, Owner> owners;

    static {
        for (ModBlocks.Entries blocks : ModBlocks.all()) {
            MobDef def = blocks.def();
            RegistryObject<Item> carcass = ITEMS.register(def.carcassId().getPath(),
                    () -> new MobBlockItem(blocks.carcass().get(), new Item.Properties().stacksTo(def.floor() ? 1 : 8)));
            RegistryObject<Item> drained = blocks.drained() == null ? null : ITEMS.register(def.drainedId().getPath(),
                    () -> new MobBlockItem(blocks.drained().get(), new Item.Properties().stacksTo(def.floor() ? 1 : 8)));
            RegistryObject<Item> head = blocks.head() == null ? null : ITEMS.register(def.headId().getPath(),
                    () -> new MobBlockItem(blocks.head().get(), new Item.Properties()));
            RegistryObject<Item> mount = blocks.mount() == null ? null : ITEMS.register(def.mountId().getPath(),
                    () -> new MobBlockItem(blocks.mount().get(), new Item.Properties()));
            RegistryObject<Item> skeleton = blocks.skeleton() == null ? null : ITEMS.register(def.skeletonId().getPath(),
                    () -> new MobBlockItem(blocks.skeleton().get(), new Item.Properties().stacksTo(def.floor() ? 1 : 16)));
            // The rug's pelt is a block model, so its item is a plain block item with a flat icon.
            RegistryObject<Item> rug = blocks.rug() == null ? null : ITEMS.register(def.rugId().getPath(),
                    () -> new BlockItem(blocks.rug().get(), new Item.Properties()));
            BY_DEF.put(def, new Entries(def, carcass, drained, head, mount, skeleton, rug));
        }
        for (ItemDefs.ItemDef def : ItemDefs.all()) {
            SIMPLE.put(def.id(), ITEMS.register(def.id(), () -> {
                Item.Properties properties = new Item.Properties();
                if (def.food()) {
                    properties.food(new FoodProperties.Builder().nutrition(def.nutrition()).saturationMod(def.saturationMod()).meat().build());
                }
                return new Item(properties);
            }));
        }
    }

    /** One of the mod's own meat, skin or trophy items by id (see {@link ItemDefs}). */
    public static RegistryObject<Item> simple(String id) {
        RegistryObject<Item> item = SIMPLE.get(id);
        if (item == null) throw new IllegalArgumentException("No item definition " + id);
        return item;
    }

    public static Collection<RegistryObject<Item>> simpleItems() {
        return Collections.unmodifiableCollection(SIMPLE.values());
    }

    public static Entries of(MobDef def) {
        return BY_DEF.get(def);
    }

    public static Collection<Entries> all() {
        return Collections.unmodifiableCollection(BY_DEF.values());
    }

    /** The mob and kind behind one of our items, or null for any other item. Built on first use, after registration. */
    @Nullable
    public static Owner owner(Item item) {
        if (owners == null) {
            Map<Item, Owner> map = new HashMap<>();
            for (Entries e : BY_DEF.values()) {
                for (Kind kind : Kind.values()) {
                    RegistryObject<Item> ro = e.of(kind);
                    if (ro != null) map.put(ro.get(), new Owner(e.def(), kind));
                }
            }
            owners = map;
        }
        return owners.get(item);
    }

    private ModItems() {}
}
