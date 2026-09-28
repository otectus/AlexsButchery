package com.otectus.alexsbutchery.compat;

import com.otectus.alexsbutchery.AlexsButchery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * When another mod already adds an item ours would duplicate, the carcass yields theirs and ours is hidden from the
 * creative tabs, so a pack sees one bear meat and one terrapin shell. Alex's Mobs Delight is the current case.
 */
public final class Substitutions {

    private record Rule(String mod, String ours, String theirs) {}

    private static final List<Rule> RULES = List.of(
            new Rule("alexsmobsdelight", "raw_bear_meat", "alexsmobsdelight:raw_bear_meat"),
            new Rule("alexsmobsdelight", "cooked_bear_meat", "alexsmobsdelight:cooked_bear_meat"),
            new Rule("alexsmobsdelight", "raw_emu_meat", "alexsmobsdelight:raw_emu"),
            new Rule("alexsmobsdelight", "cooked_emu_meat", "alexsmobsdelight:cooked_emu"),
            new Rule("alexsmobsdelight", "raw_froststalker_meat", "alexsmobsdelight:raw_froststalker_meat"),
            new Rule("alexsmobsdelight", "cooked_froststalker_meat", "alexsmobsdelight:cooked_froststalker_meat"),
            new Rule("alexsmobsdelight", "raw_tusklin_meat", "alexsmobsdelight:raw_tusklin_meat"),
            new Rule("alexsmobsdelight", "cooked_tusklin_meat", "alexsmobsdelight:cooked_tusklin_meat"),
            new Rule("alexsmobsdelight", "raw_seal_meat", "alexsmobsdelight:seal_meat"),
            new Rule("alexsmobsdelight", "cooked_seal_meat", "alexsmobsdelight:cooked_seal_meat"),
            new Rule("alexsmobsdelight", "raw_whale_meat", "alexsmobsdelight:whale_meat"),
            new Rule("alexsmobsdelight", "cooked_whale_meat", "alexsmobsdelight:cooked_whale_meat"),
            new Rule("alexsmobsdelight", "raw_bunfungus_meat", "alexsmobsdelight:raw_bunfungus_leg"),
            new Rule("alexsmobsdelight", "cooked_bunfungus_meat", "alexsmobsdelight:cooked_bunfungus_leg"),
            new Rule("alexsmobsdelight", "terrapin_shell", "alexsmobsdelight:terrapin_shell"),
            new Rule("alexsmobsdelight", "snapping_turtle_shell", "alexsmobsdelight:alligator_snapping_turtle_shell"));

    private static Map<Item, Item> active;

    /** Our item to the installed mod's item, resolved once after registries are frozen. */
    private static Map<Item, Item> active() {
        if (active == null) {
            Map<Item, Item> map = new HashMap<>();
            for (Rule rule : RULES) {
                if (!ModList.get().isLoaded(rule.mod())) continue;
                Item ours = ForgeRegistries.ITEMS.getValue(AlexsButchery.id(rule.ours()));
                Item theirs = ForgeRegistries.ITEMS.getValue(new ResourceLocation(rule.theirs()));
                if (ours == null || theirs == null || theirs == net.minecraft.world.item.Items.AIR) continue;
                map.put(ours, theirs);
            }
            if (!map.isEmpty()) AlexsButchery.LOGGER.info("Substituting {} items with other mods' equivalents", map.size());
            active = map;
        }
        return active;
    }

    public static boolean isReplaced(Item item) {
        return active().containsKey(item);
    }

    /** The stack to give instead, or the same stack. */
    public static ItemStack apply(ItemStack stack) {
        Item theirs = active().get(stack.getItem());
        if (theirs == null) return stack;
        return new ItemStack(theirs, stack.getCount());
    }

    private Substitutions() {}
}
