package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.block.CarcassTargeting;
import com.otectus.alexsbutchery.block.HeadBlock;
import com.otectus.alexsbutchery.block.HeadMountBlock;
import com.otectus.alexsbutchery.block.RugBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.item.TrophyData;
import com.otectus.alexsbutchery.recipe.RugRecipe;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.List;

import static com.otectus.alexsbutchery.test.TestSupport.ORIGIN;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryBlock;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryItem;
import static com.otectus.alexsbutchery.test.TestSupport.clearItems;
import static com.otectus.alexsbutchery.test.TestSupport.player;
import static com.otectus.alexsbutchery.test.TestSupport.use;

/**
 * Heads, trophies and rugs (review findings AB-01 to AB-03): the creature's look travels from the carcass through the head,
 * the taxidermy table and the rug recipe, survives placing, saving and breaking, and never moves between creatures;
 * trophies hang only on a wall; and the drawn head, not just its block, is what the player selects.
 */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrophyTests {
    private static final MobDef TIGER = MobDefs.byId("tiger");
    private static final MobDef ELEPHANT = MobDefs.byId("elephant");
    private static final MobDef MOOSE = MobDefs.byId("moose");

    // ---- AB-01: the look travels -----------------------------------------------------------------------------

    @GameTest(template = "empty")
    public static void harvestedHeadKeepsTheCreaturesLook(GameTestHelper h) {
        var player = player(h);
        BlockPos hook = ORIGIN.above(2);
        BlockPos carcass = ORIGIN.above(1);
        h.setBlock(hook, butcheryBlock("hook").defaultBlockState());
        h.setBlock(carcass, ModBlocks.of(TIGER).drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 1));
        ((CarcassBlockEntity) h.getBlockEntity(carcass)).setMobData(look("White", true));
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        use(h, carcass, player);
        ItemStack head = only(h, ModItems.of(TIGER).head().get());
        h.assertTrue(lookOf(head).getBoolean("White"), "the harvested head is the white tiger's: " + head.getTag());
        clearItems(h);

        // Placed, saved and broken, the head keeps it; a head from an untagged carcass has none and stays default.
        var placer = TestSupport.advancementPlayer(h);
        h.setBlock(ORIGIN.east(2), Blocks.STONE.defaultBlockState());
        placer.setItemInHand(InteractionHand.MAIN_HAND, head);
        ProcessSafetyTests.useItemOn(h, placer, ORIGIN.east(2), Direction.UP);
        BlockPos placed = ORIGIN.east(2).above();
        h.assertTrue(h.getBlockEntity(placed) instanceof CarcassBlockEntity be && be.mobData().getBoolean("White"), "the placed head is white");
        CompoundTag saved = h.getBlockEntity(placed).saveWithoutMetadata();
        h.assertTrue(saved.getCompound(CarcassBlockEntity.MOB_DATA).getBoolean("White"), "and saves it");
        ItemStack again = ProcessSafetyTests.breakAndTake(h, placed, ModItems.of(TIGER).head().get());
        h.assertTrue(lookOf(again).getBoolean("White") && !again.getTag().getCompound("BlockEntityTag").contains("Bleeding"),
                "broken, it drops with the look and nothing else");

        // A fresh block entity: setting the same block again would keep the white tiger's.
        h.setBlock(carcass, Blocks.AIR.defaultBlockState());
        h.setBlock(carcass, ModBlocks.of(TIGER).drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 1));
        use(h, carcass, player);
        ItemStack plain = only(h, ModItems.of(TIGER).head().get());
        h.assertTrue(TrophyData.appearance(plain) == null, "an untagged carcass gives a default head");
        h.assertTrue(!ItemStack.isSameItemSameTags(plain, again), "a default and a white head never stack together");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void taxidermyTableMountsTheHeadsOwnLook(GameTestHelper h) {
        h.setBlock(ORIGIN, butcheryBlock("taxidermy_table").defaultBlockState());
        IItemHandlerModifiable table = (IItemHandlerModifiable) h.getBlockEntity(ORIGIN).getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElseThrow(() -> new GameTestAssertException("the taxidermy table has no item handler"));
        table.setStackInSlot(0, new ItemStack(Items.WHEAT, 4));
        table.setStackInSlot(1, head(ELEPHANT, look("Tusked", true)));
        table.setStackInSlot(2, butcheryItem(ELEPHANT.mount().butcheryMount));
        h.assertTrue(ButcheryHooks.taxidermyBridged(), "the taxidermy bridge is in this Butchery's procedure");
        // Polled, not timed: Butchery's table recomputes its output on its own scheduled tick.
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(table.getStackInSlot(3).is(ModItems.of(ELEPHANT).mount().get()), "the table offers the elephant mount: "
                        + table.getStackInSlot(3) + "; slots " + table.getStackInSlot(0) + ", " + table.getStackInSlot(1) + ", " + table.getStackInSlot(2)
                        + "; matching recipes " + matching(h, table) + "; table " + h.getBlockState(ORIGIN)))
                .thenExecute(() -> {
                    ItemStack tusked = table.getStackInSlot(3);
                    h.assertTrue(tusked.getCount() == 1, "one mount: " + tusked);
                    h.assertTrue(lookOf(tusked).getBoolean("Tusked"), "with the mounted head's tusks: " + tusked.getTag());
                    table.setStackInSlot(1, head(ELEPHANT, null));
                })
                .thenWaitUntil(() -> h.assertTrue(table.getStackInSlot(3).is(ModItems.of(ELEPHANT).mount().get())
                        && TrophyData.appearance(table.getStackInSlot(3)) == null, "an untagged head mounts with the default look"))
                .thenExecute(() -> {
                    table.setStackInSlot(1, head(MOOSE, look("Antlered", false)));
                    table.setStackInSlot(2, butcheryItem(MOOSE.mount().butcheryMount));
                })
                .thenWaitUntil(() -> {
                    ItemStack shed = table.getStackInSlot(3);
                    h.assertTrue(shed.is(ModItems.of(MOOSE).mount().get()) && lookOf(shed).contains("Antlered") && !lookOf(shed).getBoolean("Antlered"),
                            "a shed moose stays shed on its mount: " + shed.getTag());
                })
                .thenExecute(() -> {
                    // Butchery's own trophies pass through untouched.
                    table.setStackInSlot(1, butcheryItem("cow_head"));
                    table.setStackInSlot(2, butcheryItem("empty_head_mount"));
                })
                .thenWaitUntil(() -> {
                    ItemStack cow = table.getStackInSlot(3);
                    h.assertTrue(cow.is(butcheryItem("cow_head_mount").getItem()) && !cow.hasTag(), "Butchery's cow mount is unchanged: " + cow);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void tusklessAndTuskedMountsSurvivePlacingAndBreaking(GameTestHelper h) {
        var placer = TestSupport.advancementPlayer(h);
        h.setBlock(ORIGIN, Blocks.STONE.defaultBlockState());
        ItemStack mount = TrophyData.taxidermyResult(new ItemStack(ModItems.of(ELEPHANT).mount().get()), head(ELEPHANT, look("Tusked", true)));
        placer.setItemInHand(InteractionHand.MAIN_HAND, mount);
        ProcessSafetyTests.useItemOn(h, placer, ORIGIN, Direction.NORTH);
        BlockPos at = ORIGIN.north();
        h.assertTrue(h.getBlockState(at).is(ModBlocks.of(ELEPHANT).mount().get()), "the trophy hangs on the wall");
        h.assertTrue(((CarcassBlockEntity) h.getBlockEntity(at)).mobData().getBoolean("Tusked"), "with its tusks");
        CarcassBlockEntity reloaded = new CarcassBlockEntity(h.absolutePos(at), h.getBlockState(at));
        reloaded.load(h.getBlockEntity(at).saveWithFullMetadata());
        h.assertTrue(reloaded.mobData().getBoolean("Tusked"), "a reloaded trophy keeps them");
        ItemStack dropped = ProcessSafetyTests.breakAndTake(h, at, ModItems.of(ELEPHANT).mount().get());
        h.assertTrue(lookOf(dropped).getBoolean("Tusked"), "and so does the broken trophy");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rugCraftedFromAWhiteTigerHeadIsWhite(GameTestHelper h) {
        var recipe = h.getLevel().getRecipeManager().byKey(AlexsButchery.id("rug/tiger_rug")).orElseThrow();
        h.assertTrue(recipe instanceof RugRecipe, "the tiger rug is crafted by alexsbutchery:rug, got " + recipe.getClass());
        RugRecipe rug = (RugRecipe) recipe;
        ItemStack white = craftRug(h, rug, head(TIGER, look("White", true)));
        h.assertTrue(white.is(ModItems.of(TIGER).rug().get()) && lookOf(white).getBoolean("White"), "a white head makes a white rug: " + white.getTag());
        ItemStack plain = craftRug(h, rug, head(TIGER, null));
        h.assertTrue(TrophyData.appearance(plain) == null, "a default head makes the default rug");
        h.assertTrue(rug.getResultItem(h.getLevel().registryAccess()).getTag() == null, "the shown result stays the plain rug");

        var placer = TestSupport.advancementPlayer(h);
        h.setBlock(ORIGIN, Blocks.STONE.defaultBlockState());
        placer.setItemInHand(InteractionHand.MAIN_HAND, white);
        ProcessSafetyTests.useItemOn(h, placer, ORIGIN, Direction.UP);
        BlockState placed = h.getBlockState(ORIGIN.above());
        h.assertTrue(placed.is(ModBlocks.of(TIGER).rug().get()) && placed.getValue(RugBlock.COAT), "it lies with the white pelt");
        ItemStack back = ProcessSafetyTests.breakAndTake(h, ORIGIN.above(), ModItems.of(TIGER).rug().get());
        h.assertTrue(lookOf(back).getBoolean("White"), "and comes back white");
        h.setBlock(ORIGIN.east(2), Blocks.STONE.defaultBlockState());
        placer.setItemInHand(InteractionHand.MAIN_HAND, plain);
        ProcessSafetyTests.useItemOn(h, placer, ORIGIN.east(2), Direction.UP);
        h.assertTrue(!h.getBlockState(ORIGIN.east(2).above()).getValue(RugBlock.COAT), "a default rug keeps the default pelt");
        h.succeed();
    }

    // ---- AB-02: trophies hang on walls -----------------------------------------------------------------------

    @GameTest(template = "empty")
    public static void trophyHangsOnEveryWallAndDropsWhenItsWallGoes(GameTestHelper h) {
        var placer = TestSupport.advancementPlayer(h);
        BlockPos centre = new BlockPos(2, 2, 2);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos wall = centre;
            BlockPos at = wall.relative(facing);
            h.setBlock(wall, Blocks.STONE.defaultBlockState());
            ItemStack mount = TrophyData.taxidermyResult(new ItemStack(ModItems.of(TIGER).mount().get()), head(TIGER, look("White", true)));
            placer.setItemInHand(InteractionHand.MAIN_HAND, mount);
            ProcessSafetyTests.useItemOn(h, placer, wall, facing);
            BlockState state = h.getBlockState(at);
            h.assertTrue(state.is(ModBlocks.of(TIGER).mount().get()) && state.getValue(HeadBlock.FACING) == facing,
                    "clicking the wall's " + facing + " side hangs the trophy facing out, got " + state);
            clearItems(h);
            h.setBlock(wall, Blocks.AIR.defaultBlockState());
            h.assertTrue(h.getBlockState(at).isAir(), "without its wall the trophy falls (" + facing + ")");
            List<ItemEntity> drops = TestSupport.items(h).stream().filter(e -> e.getItem().is(ModItems.of(TIGER).mount().get())).toList();
            h.assertTrue(drops.size() == 1 && drops.get(0).getItem().getCount() == 1 && lookOf(drops.get(0).getItem()).getBoolean("White"),
                    "as exactly one trophy that keeps its look (" + facing + "): " + TestSupport.items(h));
            clearItems(h);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void trophyNeedsASturdyWall(GameTestHelper h) {
        var placer = TestSupport.advancementPlayer(h);
        ItemStack mount = new ItemStack(ModItems.of(TIGER).mount().get(), 3);
        placer.setItemInHand(InteractionHand.MAIN_HAND, mount);
        h.setBlock(ORIGIN, Blocks.STONE.defaultBlockState());
        ProcessSafetyTests.useItemOn(h, placer, ORIGIN, Direction.UP);
        h.assertTrue(!h.getBlockState(ORIGIN.above()).is(ModBlocks.of(TIGER).mount().get()) || wallBehind(h, ORIGIN.above()),
                "on a floor, a trophy only goes up against a wall the player faces");
        h.assertTrue(h.getBlockState(ORIGIN.above()).isAir(), "with no wall in sight nothing is placed");
        h.setBlock(ORIGIN.east(2), Blocks.GLASS_PANE.defaultBlockState());
        ProcessSafetyTests.useItemOn(h, placer, ORIGIN.east(2), Direction.NORTH);
        h.assertTrue(h.getBlockState(ORIGIN.east(2).north()).isAir(), "a glass pane's thin side holds no trophy");
        h.assertTrue(mount.getCount() == 3, "no trophy was used up");
        h.assertTrue(!ModBlocks.of(TIGER).mount().get().defaultBlockState().setValue(HeadBlock.FACING, Direction.SOUTH)
                .canSurvive(h.getLevel(), h.absolutePos(new BlockPos(0, 3, 0))), "a trophy in mid-air cannot survive");
        h.assertTrue(ModBlocks.of(TIGER).head().get().defaultBlockState().canSurvive(h.getLevel(), h.absolutePos(new BlockPos(0, 3, 0))),
                "a plain head is free-standing, as before");
        h.succeed();
    }

    // ---- AB-03: the drawn head is what is selected --------------------------------------------------------------

    @GameTest(template = "empty")
    public static void protrudingHeadIsSelectableBeyondItsBlock(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 1, 3);
        h.setBlock(rel, ModBlocks.of(ELEPHANT).head().get().defaultBlockState().setValue(HeadBlock.FACING, Direction.NORTH));
        ((CarcassBlockEntity) h.getBlockEntity(rel)).setMobData(look("Tusked", true));
        BlockState state = h.getBlockState(rel);
        h.assertTrue(CarcassBounds.exported(state, look("Tusked", true)), "the tusked elephant head has exported anatomy");
        BlockPos abs = h.absolutePos(rel);
        var boxes = CarcassBounds.boxes(state, h.getLevel(), abs);
        // A point of the drawn trunk or tusks north of the head's block: a ray along x through it never enters that block.
        AABB outside = boxes.stream().filter(b -> b.minZ < -0.1).min(java.util.Comparator.comparingDouble(b -> b.minZ))
                .orElseThrow(() -> new GameTestAssertException("the elephant head never reaches north of its block: " + boxes));
        Vec3 local = new Vec3(outside.getCenter().x, outside.getCenter().y, (outside.minZ + Math.min(outside.maxZ, 0)) / 2);
        Vec3 target = local.add(Vec3.atLowerCornerOf(abs));
        Vec3 eye = target.add(-3, 0, 0);
        Vec3 end = target.add(1, 0, 0);
        Player viewer = player(h);
        BlockHitResult hit = CarcassTargeting.pick(h.getLevel(), eye, end, viewer);
        h.assertTrue(hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(abs),
                "aiming at the overhang selects the head, got " + (hit == null ? null : hit.getBlockPos()));
        VoxelShape shape = state.getShape(h.getLevel(), abs);
        h.assertTrue(shape.bounds().minZ < 0, "the outline reaches past the block like the head: " + shape.bounds());
        h.assertTrue(state.getCollisionShape(h.getLevel(), abs).bounds().maxY <= .5, "collision stays the small rest");
        BlockHitResult tooShort = CarcassTargeting.pick(h.getLevel(), eye, eye.add(1, 0, 0), viewer);
        h.assertTrue(tooShort == null || !tooShort.getBlockPos().equals(abs), "a ray that ends short of the head does not reach it");
        // A wall between the eye and the head blocks it: the picker offers no trophy, so vanilla's hit (the wall) stands.
        BlockPos wall = BlockPos.containing(eye.add(1.5, 0, 0));
        h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        BlockHitResult blocked = CarcassTargeting.pick(h.getLevel(), eye, end, viewer);
        h.assertTrue(blocked == null || !blocked.getBlockPos().equals(abs), "a wall in front hides the head, got " + (blocked == null ? null : blocked.getBlockPos()));
        BlockHitResult vanilla = h.getLevel().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, viewer));
        h.assertTrue(vanilla.getBlockPos().equals(wall), "and the wall is what vanilla selects");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyHeadAndTrophyFormHasExportedAnatomy(GameTestHelper h) {
        List<String> missing = new java.util.ArrayList<>();
        for (MobDef def : MobDefs.all()) {
            var blocks = ModBlocks.of(def);
            for (var block : new HeadBlock[]{blocks.head() == null ? null : blocks.head().get(), blocks.mount() == null ? null : blocks.mount().get()}) {
                if (block == null) continue;
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    BlockState state = block.defaultBlockState().setValue(HeadBlock.FACING, facing);
                    if (!CarcassBounds.exported(state, new CompoundTag())) missing.add(CarcassBounds.key(state, new CompoundTag()));
                    var boxes = CarcassBounds.boxes(state, new CompoundTag(), Blocks.AIR.defaultBlockState());
                    if (block instanceof HeadMountBlock && boxes.stream().noneMatch(b -> b.equals(HeadMountBlock.plaque(block.def().mount(), facing).bounds())))
                        missing.add(CarcassBounds.key(state, new CompoundTag()) + " without its plaque");
                }
            }
        }
        h.assertTrue(missing.isEmpty(), "heads and trophies without exported anatomy: " + missing);
        h.succeed();
    }

    // ---- anatomy and loot ----------------------------------------------------------------------------------------

    /** A tuskless elephant and a moose that has shed its antlers give no tusks or antlers; others as before. */
    @GameTest(template = "empty")
    public static void partsACreatureLackedAreNotHarvested(GameTestHelper h) {
        for (var c : List.of(new Object[]{ELEPHANT, "Tusked", "alexsbutchery:elephant_tusk"}, new Object[]{MOOSE, "Antlered", "alexsmobs:moose_antler"})) {
            MobDef def = (MobDef) c[0];
            String key = (String) c[1];
            Item part = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation((String) c[2]));
            h.assertTrue(heads(h, def, look(key, false), part) == 0, def.id() + " without its " + key + " gives none");
            h.assertTrue(heads(h, def, look(key, true), part) > 0, def.id() + " with " + key + " gives them");
            h.assertTrue(heads(h, def, new CompoundTag(), part) > 0, def.id() + " saved without the flag gives them as before");
        }
        h.succeed();
    }

    private static int heads(GameTestHelper h, MobDef def, CompoundTag look, Item part) {
        BlockPos rel = new BlockPos(2, 1, 2);
        CompatTestSupport.place(h, def, true, rel, 0, look);
        int n = 0;
        for (ItemStack s : CompatTestSupport.roll(h, def, MobDef.HEAD, rel, null, butcheryItem("iron_cleaver"), 40)) if (s.is(part)) n += s.getCount();
        return n;
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    /** Which taxidermy recipes Butchery's own matching (slot by slot) accepts for the table's inputs, for diagnostics. */
    private static List<String> matching(GameTestHelper h, IItemHandlerModifiable table) {
        List<String> out = new java.util.ArrayList<>();
        for (var recipe : h.getLevel().getRecipeManager().getAllRecipesFor(net.mcreator.butchery.jei_recipes.TaxidermyRecipe.Type.INSTANCE)) {
            var in = recipe.getIngredients();
            if (in.size() >= 3 && in.get(0).test(table.getStackInSlot(0)) && in.get(1).test(table.getStackInSlot(1)) && in.get(2).test(table.getStackInSlot(2)))
                out.add(recipe.getId() + "->" + recipe.getResultItem(null));
        }
        out.add("of " + h.getLevel().getRecipeManager().getAllRecipesFor(net.mcreator.butchery.jei_recipes.TaxidermyRecipe.Type.INSTANCE).size());
        return out;
    }

    private static boolean wallBehind(GameTestHelper h, BlockPos rel) {
        BlockState state = h.getBlockState(rel);
        return state.getBlock() instanceof HeadMountBlock && state.canSurvive(h.getLevel(), h.absolutePos(rel));
    }

    private static ItemStack craftRug(GameTestHelper h, RugRecipe recipe, ItemStack head) {
        var grid = new TransientCraftingContainer(new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
        Item pelt = ModItems.simple(TIGER.rug()).get();
        grid.setItem(0, new ItemStack(pelt));
        grid.setItem(1, head);
        grid.setItem(2, new ItemStack(pelt));
        grid.setItem(4, new ItemStack(pelt));
        h.assertTrue(recipe.matches(grid, h.getLevel()), "the rug pattern matches");
        return recipe.assemble(grid, h.getLevel().registryAccess());
    }

    static CompoundTag look(String flag, boolean value) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(flag, value);
        return tag;
    }

    static ItemStack head(MobDef def, CompoundTag look) {
        ItemStack head = new ItemStack(ModItems.of(def).head().get());
        if (look != null) {
            CompoundTag tag = new CompoundTag();
            tag.put(CarcassBlockEntity.MOB_DATA, look.copy());
            BlockItem.setBlockEntityData(head, com.otectus.alexsbutchery.registry.ModBlockEntities.CARCASS.get(), tag);
        }
        return head;
    }

    static CompoundTag lookOf(ItemStack stack) {
        CompoundTag look = TrophyData.appearance(stack);
        return look == null ? new CompoundTag() : look;
    }

    private static ItemStack only(GameTestHelper h, Item item) {
        List<ItemEntity> drops = TestSupport.items(h).stream().filter(e -> e.getItem().is(item)).toList();
        if (drops.size() != 1) throw new GameTestAssertException("expected one " + new ResourceLocation(String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item)))
                + ", got " + TestSupport.items(h));
        return drops.get(0).getItem().copy();
    }

    private TrophyTests() {}
}
