package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.butcher.CarcassLoot;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.compat.CompatPolicy;
import com.otectus.alexsbutchery.compat.CompatResolver;
import com.otectus.alexsbutchery.compat.CompatState;
import com.otectus.alexsbutchery.compat.profile.ProfileLoader;
import com.otectus.alexsbutchery.compat.profile.ProfileSet;
import com.otectus.alexsbutchery.def.CompatDefs;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.otectus.alexsbutchery.test.TestSupport.butcheryItem;

/** Helpers for the compatibility GameTests: the loaded addons, alternative rules, and real processing of a carcass. */
final class CompatTestSupport {
    static final String AMD = CompatDefs.AMD;
    static final String AD = CompatDefs.AD;

    static boolean amd() {
        return ModList.get().isLoaded(AMD);
    }

    static boolean ad() {
        return ModList.get().isLoaded(AD);
    }

    static boolean fd() {
        return ModList.get().isLoaded("farmersdelight");
    }

    static ResourceLocation id(String id) {
        return new ResourceLocation(id);
    }

    static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null || item == Items.AIR) throw new GameTestAssertException("item " + id + " is not registered");
        return item;
    }

    @Nullable
    static Item itemOrNull(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null || item == Items.AIR ? null : item;
    }

    static CompatState state(GameTestHelper h) {
        CompatState state = CompatState.get(h.getLevel().getServer());
        if (state == null) throw new GameTestAssertException("the server has no compatibility rules");
        return state;
    }

    static ProfileSet loadedProfiles(GameTestHelper h) {
        ProfileSet set = ProfileLoader.loaded(h.getLevel().getServer().getServerResources().managers());
        if (set == null) throw new GameTestAssertException("no profile set was loaded with the server's resources");
        return set;
    }

    /** The server's rules rebuilt under another policy (same loaded profiles, registries, recipes and tables). */
    static CompatState with(GameTestHelper h, CompatPolicy policy) {
        return with(h, loadedProfiles(h), policy);
    }

    static CompatState with(GameTestHelper h, ProfileSet profiles, CompatPolicy policy) {
        return CompatResolver.build(h.getLevel().getServer(), profiles, policy, state(h).recipes(), List.of(), false);
    }

    /**
     * The meat item ids a stage of {@code mob} may yield under {@code policy} with the loaded addons, as the plan's
     * provider table says: Alex's Mobs Delight, then Alex's Delight, by priority; native otherwise.
     */
    static String expectedMeatProvider(String mob, CompatPolicy policy) {
        if (!policy.preferExternal()) return "native";
        List<String> supports = switch (mob) {
            case "bison", "bunfungus" -> List.of(AMD, AD);
            case "grizzly_bear", "dropbear", "sea_bear", "crocodile", "caiman", "anaconda", "emu", "seagull", "hummingbird", "tusklin",
                    "froststalker", "seal", "orca", "cachalot_whale", "terrapin", "alligator_snapping_turtle" -> List.of(AMD);
            default -> List.of();
        };
        for (String provider : policy.providerPriority()) {
            if (supports.contains(provider) && ModList.get().isLoaded(provider) && policy.enabled(provider)) return provider;
        }
        // Providers missing from the order are used only when exactly one is left; two at one priority are ambiguous.
        List<String> unlisted = supports.stream()
                .filter(p -> !policy.providerPriority().contains(p) && ModList.get().isLoaded(p) && policy.enabled(p)).toList();
        return unlisted.size() == 1 ? unlisted.get(0) : "native";
    }

    /** Places a carcass ready for its first cut: the drained block for bleeders (on the ground), the fresh one otherwise. */
    static BlockPos placeReady(GameTestHelper h, MobDef def, BlockPos rel, CompoundTag snapshot) {
        ModBlocks.Entries blocks = ModBlocks.of(def);
        AbstractCarcassBlock block = def.bleeds() ? blocks.drained().get() : blocks.carcass().get();
        h.setBlock(rel, block.defaultBlockState().setValue(AbstractCarcassBlock.FACING, Direction.NORTH).setValue(block.stateProperty(), 0));
        if (h.getBlockEntity(rel) instanceof CarcassBlockEntity carcass) carcass.setMobData(snapshot);
        return rel;
    }

    /** Places a carcass of {@code def} at {@code rel} in an explicit state (Butchery numbering) with a snapshot. */
    static void place(GameTestHelper h, MobDef def, boolean drained, BlockPos rel, int stage, CompoundTag snapshot) {
        ModBlocks.Entries blocks = ModBlocks.of(def);
        AbstractCarcassBlock block = drained ? blocks.drained().get() : blocks.carcass().get();
        h.setBlock(rel, block.defaultBlockState().setValue(AbstractCarcassBlock.FACING, Direction.NORTH).setValue(block.stateProperty(), stage));
        if (h.getBlockEntity(rel) instanceof CarcassBlockEntity carcass) carcass.setMobData(snapshot);
    }

    /**
     * Works the carcass at {@code rel} through every remaining stage with the right tool, as a player would, and
     * returns what each stage dropped (stage table name to items), in order. Fails if a stage does not advance.
     */
    static Map<String, Map<Item, Integer>> process(GameTestHelper h, BlockPos rel, Player player) {
        Map<String, Map<Item, Integer>> out = new LinkedHashMap<>();
        for (int guard = 0; guard < 8; guard++) {
            BlockState state = h.getBlockState(rel);
            if (!(state.getBlock() instanceof AbstractCarcassBlock block)) return out;
            MobDef def = block.def();
            boolean fresh = !(state.getBlock() instanceof com.otectus.alexsbutchery.block.DrainedCarcassBlock);
            Stages.Step step = Stages.next(def, fresh, block.stage(state));
            if (step == null) throw new GameTestAssertException(def.id() + " in state " + block.stage(state) + " has no next step");
            player.setItemInHand(InteractionHand.MAIN_HAND, step.action().needsKnife() ? butcheryItem("iron_skinning_knife") : butcheryItem("iron_cleaver"));
            TestSupport.clearItems(h);
            TestSupport.use(h, rel, player);
            BlockState after = h.getBlockState(rel);
            boolean advanced = step.doneState() == Stages.REMOVED ? !(after.getBlock() instanceof AbstractCarcassBlock)
                    : after.getBlock() == state.getBlock() && ((AbstractCarcassBlock) after.getBlock()).stage(after) == step.doneState();
            if (!advanced) throw new GameTestAssertException(def.id() + " did not advance from " + block.stage(state) + " with " + step.action());
            out.put(step.action().table(def), drops(h));
            TestSupport.clearItems(h);
        }
        throw new GameTestAssertException("the carcass never finished");
    }

    /** Every item entity in the test structure, totalled by item. */
    static Map<Item, Integer> drops(GameTestHelper h) {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (ItemEntity e : TestSupport.items(h)) out.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
        return out;
    }

    /** All items of all stages. */
    static Map<Item, Integer> total(Map<String, Map<Item, Integer>> stages) {
        Map<Item, Integer> out = new LinkedHashMap<>();
        stages.values().forEach(m -> m.forEach((k, v) -> out.merge(k, v, Integer::sum)));
        return out;
    }

    /** Rolls one stage table of a carcass placed at {@code rel}, {@code rolls} times, without spawning anything. */
    static List<ItemStack> roll(GameTestHelper h, MobDef def, String stage, BlockPos rel, @Nullable Player player, ItemStack tool, int rolls) {
        BlockPos abs = h.absolutePos(rel);
        CarcassBlockEntity carcass = h.getBlockEntity(rel) instanceof CarcassBlockEntity c ? c : null;
        return CarcassLoot.roll(h.getLevel(), def.stageTable(stage), abs, h.getBlockState(rel), tool, player, carcass, rolls);
    }

    static Map<Item, Integer> totals(List<ItemStack> stacks) {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (ItemStack s : stacks) out.merge(s.getItem(), s.getCount(), Integer::sum);
        return out;
    }

    /** The mob snapshot carried by the first carcass item of {@code def} in the structure. */
    static CompoundTag droppedSnapshot(GameTestHelper h, MobDef def) {
        Item carcass = ModItems.of(def).carcass().get();
        for (ItemEntity e : TestSupport.items(h)) {
            if (!e.getItem().is(carcass)) continue;
            CompoundTag tag = BlockItem.getBlockEntityData(e.getItem());
            return tag == null ? new CompoundTag() : tag.getCompound(CarcassBlockEntity.MOB_DATA);
        }
        throw new GameTestAssertException("no " + def.id() + " carcass item dropped; drops: " + drops(h));
    }

    static String key(Item item) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(item));
    }

    static String keys(Map<Item, Integer> items) {
        List<String> out = new ArrayList<>();
        items.forEach((k, v) -> out.add(key(k) + " x" + v));
        return out.toString();
    }

    /** Whether any item of {@code items} is in the namespace-qualified id list. */
    static boolean anyOf(Map<Item, Integer> items, Set<String> ids) {
        for (Item item : items.keySet()) if (ids.contains(key(item))) return true;
        return false;
    }

    static MobDef mob(String id) {
        MobDef def = MobDefs.byId(id);
        if (def == null) throw new GameTestAssertException("no mob " + id);
        return def;
    }

    /**
     * Runs {@code body} with some loot tables of the server replaced, then puts the loaded ones back: a pack's own
     * tables without a reload. Synchronous test bodies only.
     */
    @SuppressWarnings("unchecked")
    static void withLootTables(GameTestHelper h, Map<ResourceLocation, net.minecraft.world.level.storage.loot.LootTable> tables, Runnable body) {
        var manager = h.getLevel().getServer().getLootData();
        java.lang.reflect.Field field = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(
                net.minecraft.world.level.storage.loot.LootDataManager.class, "f_278415_");
        try {
            Map<net.minecraft.world.level.storage.loot.LootDataId<?>, Object> original = (Map<net.minecraft.world.level.storage.loot.LootDataId<?>, Object>) field.get(manager);
            Map<net.minecraft.world.level.storage.loot.LootDataId<?>, Object> replaced = new java.util.HashMap<>(original);
            tables.forEach((id, table) -> replaced.put(new net.minecraft.world.level.storage.loot.LootDataId<>(
                    net.minecraft.world.level.storage.loot.LootDataType.TABLE, id), table));
            field.set(manager, replaced);
            try {
                body.run();
            } finally {
                field.set(manager, original);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private CompatTestSupport() {}
}
