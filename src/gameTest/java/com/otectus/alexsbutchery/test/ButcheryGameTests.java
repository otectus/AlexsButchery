package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.SkinRackBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.block.entity.SkinRackBlockEntity;
import com.otectus.alexsbutchery.butcher.Acid;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import net.mcreator.butchery.jei_recipes.TaxidermyRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.otectus.alexsbutchery.test.TestSupport.ORIGIN;
import static com.otectus.alexsbutchery.test.TestSupport.alexsMob;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryBlock;
import static com.otectus.alexsbutchery.test.TestSupport.butcheryItem;
import static com.otectus.alexsbutchery.test.TestSupport.clearItems;
import static com.otectus.alexsbutchery.test.TestSupport.count;
import static com.otectus.alexsbutchery.test.TestSupport.kill;
import static com.otectus.alexsbutchery.test.TestSupport.player;
import static com.otectus.alexsbutchery.test.TestSupport.use;

/** The butchering pipeline end to end, against the real Butchery and Alex's Mobs on a game test server. */
@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ButcheryGameTests {
    private static final ResourceLocation KANGAROO_MEAT = new ResourceLocation("alexsmobs:kangaroo_meat");
    private static final ResourceLocation KANGAROO_HIDE = new ResourceLocation("alexsmobs:kangaroo_hide");

    @GameTest(template = "empty")
    public static void cleaverKillDropsOneCarcassAndNoMeat(GameTestHelper h) {
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        LivingEntity kangaroo = (LivingEntity) h.spawn(alexsMob("kangaroo"), ORIGIN);
        kill(h, kangaroo, player);
        h.runAfterDelay(5, () -> {
            long carcasses = count(h, ModItems.of(MobDefs.byId("kangaroo")).carcass().get());
            h.assertTrue(carcasses == 1, "exactly one kangaroo carcass, found " + carcasses + " among " + TestSupport.items(h));
            h.assertTrue(count(h, KANGAROO_MEAT) == 0, "kangaroo meat replaced by the carcass");
            h.assertTrue(count(h, KANGAROO_HIDE) == 0, "kangaroo hide replaced by the carcass");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void swordKillKeepsVanillaDrops(GameTestHelper h) {
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        LivingEntity kangaroo = (LivingEntity) h.spawn(alexsMob("kangaroo"), ORIGIN);
        kill(h, kangaroo, player);
        h.runAfterDelay(5, () -> {
            h.assertTrue(count(h, ModItems.of(MobDefs.byId("kangaroo")).carcass().get()) == 0, "no carcass without a cleaver");
            h.assertTrue(count(h, KANGAROO_MEAT) >= 1, "Alex's Mobs' own meat drops");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void babyDropsNoCarcass(GameTestHelper h) {
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        Entity spawned = h.spawn(alexsMob("kangaroo"), ORIGIN);
        ((AgeableMob) spawned).setAge(-24_000);
        kill(h, (LivingEntity) spawned, player);
        h.runAfterDelay(5, () -> {
            h.assertTrue(count(h, ModItems.of(MobDefs.byId("kangaroo")).carcass().get()) == 0, "babies never drop carcasses");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void carcassHangsOnButcheryHook(GameTestHelper h) {
        var player = player(h);
        BlockPos hook = ORIGIN.above(2);
        h.setBlock(hook, butcheryBlock("hook").defaultBlockState());
        ItemStack carcass = new ItemStack(ModItems.of(MobDefs.byId("kangaroo")).carcass().get());
        player.setItemInHand(InteractionHand.MAIN_HAND, carcass);
        BlockPos abs = h.absolutePos(hook);
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, abs,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false));
        MinecraftForge.EVENT_BUS.post(event);
        h.assertTrue(event.isCanceled(), "the hook click is handled");
        BlockState below = h.getBlockState(hook.below());
        h.assertTrue(below.is(ModBlocks.of(MobDefs.byId("kangaroo")).carcass().get()), "carcass placed under the hook");
        h.assertTrue(below.getValue(AbstractCarcassBlock.BLOCKSTATE_FRESH) == 1, "carcass is hanging (blockstate 1)");
        h.assertTrue(carcass.isEmpty(), "the carcass item was consumed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1100)
    public static void hangingCarcassBleedsIntoGrateAndDrains(GameTestHelper h) {
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        BlockPos carcassPos = ORIGIN.above(1);
        h.setBlock(carcassPos.above(), butcheryBlock("hook").defaultBlockState());
        h.setBlock(ORIGIN, butcheryBlock("blood_grate").defaultBlockState());
        h.setBlock(carcassPos, ModBlocks.of(MobDefs.byId("kangaroo")).carcass().get().defaultBlockState()
                .setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, 1));
        use(h, carcassPos, player);
        h.assertTrue(h.getBlockEntity(carcassPos) instanceof CarcassBlockEntity c && c.isBleeding(), "cleaver starts the bleeding");
        h.runAfterDelay(950, () -> {
            BlockState state = h.getBlockState(carcassPos);
            h.assertTrue(state.is(ModBlocks.of(MobDefs.byId("kangaroo")).drained().get()), "fresh carcass became the drained carcass");
            h.assertTrue(state.getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == 1, "drained carcass still hangs (blockstate 1)");
            int amount = h.getBlockEntity(ORIGIN).getCapability(ForgeCapabilities.FLUID_HANDLER)
                    .map(handler -> handler.getFluidInTank(0).getAmount()).orElse(-1);
            h.assertTrue(amount == 1000, "grate holds 1000 mB of blood, got " + amount);
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void hangingCutSequenceYieldsHeadSkinCutsAndOrgans(GameTestHelper h) {
        var player = player(h);
        var def = MobDefs.byId("kangaroo");
        BlockPos pos = ORIGIN.above(1);
        h.setBlock(pos.above(), butcheryBlock("hook").defaultBlockState());
        h.setBlock(pos, ModBlocks.of(def).drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 1));
        ItemStack cleaver = butcheryItem("iron_cleaver");
        ItemStack knife = butcheryItem("iron_skinning_knife");

        player.setItemInHand(InteractionHand.MAIN_HAND, cleaver);
        use(h, pos, player);
        h.assertTrue(h.getBlockState(pos).getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == 2, "cleaver removes the head -> 2");
        h.assertTrue(count(h, ModItems.of(def).head().get()) == 1, "one kangaroo head dropped");
        clearItems(h);

        use(h, pos, player);
        h.assertTrue(h.getBlockState(pos).getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == 2, "cleaver cannot skin");
        player.setItemInHand(InteractionHand.MAIN_HAND, knife);
        use(h, pos, player);
        h.assertTrue(h.getBlockState(pos).getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == 3, "knife skins -> 3");
        h.assertTrue(count(h, KANGAROO_HIDE) >= 1, "kangaroo hide dropped");
        clearItems(h);

        player.setItemInHand(InteractionHand.MAIN_HAND, cleaver);
        use(h, pos, player);
        h.assertTrue(h.getBlockState(pos).getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == 4, "first cut -> 4");
        h.assertTrue(count(h, KANGAROO_MEAT) >= 1, "first cut drops kangaroo meat");
        h.assertTrue(count(h, butcheryItem("heart").getItem()) == 1, "Butchery organs table 1 drops a heart");
        clearItems(h);

        use(h, pos, player);
        h.assertTrue(h.getBlockState(pos).getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == 5, "second cut -> 5");
        clearItems(h);

        use(h, pos, player);
        h.assertTrue(h.getBlockState(pos).isAir(), "third cut removes the carcass");
        h.assertTrue(count(h, Items.BONE) >= 2, "last cut leaves bones");
        h.assertTrue(cleaver.getDamageValue() == 4, "the cleaver wore one point per cut, got " + cleaver.getDamageValue());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingWholeCarcassReturnsItButMidCutGivesScraps(GameTestHelper h) {
        var player = player(h);
        var def = MobDefs.byId("kangaroo");
        BlockPos whole = ORIGIN;
        BlockPos cut = ORIGIN.east(2);
        h.setBlock(whole, ModBlocks.of(def).drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 0));
        h.setBlock(cut, ModBlocks.of(def).drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 8));
        h.getLevel().destroyBlock(h.absolutePos(whole), true, player);
        h.getLevel().destroyBlock(h.absolutePos(cut), true, player);
        h.assertTrue(count(h, ModItems.of(def).drained().get()) == 1, "whole drained carcass drops its item");
        h.assertTrue(count(h, butcheryItem("meat_scraps").getItem()) == 1, "half-butchered carcass drops scraps");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void elephantFallsAsFloorCarcassAndBleedsInPlace(GameTestHelper h) {
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        BlockPos spawnAt = ORIGIN.above(1);
        LivingEntity elephant = (LivingEntity) h.spawn(alexsMob("elephant"), spawnAt);
        kill(h, elephant, player);
        h.runAfterDelay(40, () -> {
            var def = MobDefs.byId("elephant");
            h.assertTrue(!elephant.isAlive(), "the elephant died");
            BlockPos found = null;
            for (int y = 0; y <= 3 && found == null; y++) {
                for (int x = 0; x <= 4 && found == null; x++) {
                    for (int z = 0; z <= 4; z++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (h.getBlockState(p).is(ModBlocks.of(def).carcass().get())) {
                            found = p;
                            break;
                        }
                    }
                }
            }
            h.assertTrue(found != null, "the elephant carcass was placed as a block");
            h.assertTrue(count(h, ModItems.of(def).carcass().get()) == 0, "floor carcasses are not items");
            use(h, found, player);
            h.assertTrue(h.getBlockEntity(found) instanceof CarcassBlockEntity c && c.isBleeding(), "a floor carcass bleeds where it lies");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void taxidermyRecipeIsRegistered(GameTestHelper h) {
        var recipe = h.getLevel().getRecipeManager().byKey(AlexsButchery.id("taxidermy/kangaroo_head_mount"));
        h.assertTrue(recipe.isPresent(), "kangaroo head mount taxidermy recipe exists");
        h.assertTrue(recipe.get() instanceof TaxidermyRecipe, "it is a Butchery taxidermy recipe");
        h.assertTrue(recipe.get().getResultItem(h.getLevel().registryAccess()).is(ModItems.of(MobDefs.byId("kangaroo")).mount().get()),
                "it produces the kangaroo head mount");
        h.setBlock(ORIGIN, Blocks.AIR.defaultBlockState());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void acidDissolvesHangingCarcassIntoHangingSkeleton(GameTestHelper h) {
        var player = TestSupport.advancementPlayer(h);
        var def = MobDefs.byId("kangaroo");
        BlockPos pos = ORIGIN.above(1);
        h.setBlock(pos.above(), butcheryBlock("hook").defaultBlockState());
        h.setBlock(pos, ModBlocks.of(def).carcass().get().defaultBlockState()
                .setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, 1).setValue(AbstractCarcassBlock.FACING, Direction.EAST));
        CompoundTag look = new CompoundTag();
        look.putInt("Variant", 1);
        ((CarcassBlockEntity) h.getBlockEntity(pos)).setMobData(look);
        ItemStack acid = butcheryItem("bottle_of_sulfuric_acid");
        player.setItemInHand(InteractionHand.MAIN_HAND, acid);
        use(h, pos, player);
        h.assertTrue(((CarcassBlockEntity) h.getBlockEntity(pos)).isDissolving(), "acid starts dissolving the carcass");
        h.assertTrue(acid.isEmpty(), "the acid bottle was used up");
        h.assertTrue(player.getInventory().contains(new ItemStack(Items.GLASS_BOTTLE)), "the empty bottle comes back");
        h.assertTrue(advancementDone(h, player, AlexsButchery.id("bare_bones")), "pouring acid grants Bare Bones");
        h.runAfterDelay(Acid.DISSOLVE_TICKS + 5, () -> {
            BlockState state = h.getBlockState(pos);
            h.assertTrue(state.is(ModBlocks.of(def).skeleton().get()), "the carcass became the kangaroo skeleton");
            h.assertTrue(state.getValue(AbstractCarcassBlock.BLOCKSTATE_FRESH) == 1, "the skeleton still hangs");
            h.assertTrue(state.getValue(AbstractCarcassBlock.FACING) == Direction.EAST, "the skeleton keeps the carcass's facing");
            h.assertTrue(((CarcassBlockEntity) h.getBlockEntity(pos)).mobData().getInt("Variant") == 1, "the skeleton keeps the mob snapshot");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void acidLeavesHalfButcheredAndBonelessCarcassesAlone(GameTestHelper h) {
        var player = player(h);
        BlockPos cut = ORIGIN;
        BlockPos slug = ORIGIN.east(2);
        h.setBlock(cut, ModBlocks.of(MobDefs.byId("kangaroo")).drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 7));
        h.setBlock(slug, ModBlocks.of(MobDefs.byId("banana_slug")).carcass().get().defaultBlockState());
        ItemStack acid = butcheryItem("bottle_of_sulfuric_acid");
        acid.setCount(2);
        player.setItemInHand(InteractionHand.MAIN_HAND, acid);
        use(h, cut, player);
        use(h, slug, player);
        h.assertTrue(!((CarcassBlockEntity) h.getBlockEntity(cut)).isDissolving(), "a skinned carcass is not whole any more");
        h.assertTrue(!((CarcassBlockEntity) h.getBlockEntity(slug)).isDissolving(), "a banana slug has no skeleton");
        h.assertTrue(acid.getCount() == 2, "no acid was used");
        h.assertTrue(ModBlocks.of(MobDefs.byId("banana_slug")).skeleton() == null, "no skeleton block is registered for it");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void skeletonBreaksIntoItsItemWithTheSnapshot(GameTestHelper h) {
        var player = player(h);
        var def = MobDefs.byId("tiger");
        h.setBlock(ORIGIN, ModBlocks.of(def).skeleton().get().defaultBlockState());
        CompoundTag look = new CompoundTag();
        look.putBoolean("White", true);
        ((CarcassBlockEntity) h.getBlockEntity(ORIGIN)).setMobData(look);
        h.getLevel().destroyBlock(h.absolutePos(ORIGIN), true, player);
        var drops = TestSupport.items(h).stream().filter(e -> e.getItem().is(ModItems.of(def).skeleton().get())).toList();
        h.assertTrue(drops.size() == 1, "the skeleton drops itself, found " + drops.size() + " among " + TestSupport.items(h));
        CompoundTag tag = BlockItem.getBlockEntityData(drops.get(0).getItem());
        h.assertTrue(tag != null && tag.getCompound(CarcassBlockEntity.MOB_DATA).getBoolean("White"), "the dropped skeleton keeps the snapshot");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rugRecipesLoadAndCuttingRecipesWaitForFarmersDelight(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        for (String mob : new String[]{"tiger", "snow_leopard", "grizzly_bear", "bison"}) {
            var recipe = recipes.byKey(AlexsButchery.id("rug/" + mob + "_rug"));
            h.assertTrue(recipe.isPresent(), mob + " rug recipe exists");
            h.assertTrue(recipe.get().getResultItem(h.getLevel().registryAccess()).is(ModItems.of(MobDefs.byId(mob)).rug().get()),
                    mob + " rug recipe makes the " + mob + " rug");
        }
        boolean farmersDelight = net.minecraftforge.fml.ModList.get().isLoaded("farmersdelight");
        h.assertTrue(recipes.byKey(AlexsButchery.id("farmersdelight/cutting/raw_whale_meat")).isPresent() == farmersDelight,
                "cutting board recipes load only with Farmer's Delight");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rugNeedsSolidGround(GameTestHelper h) {
        var rug = ModBlocks.of(MobDefs.byId("tiger")).rug().get().defaultBlockState();
        h.setBlock(ORIGIN.below(), Blocks.STONE.defaultBlockState());
        h.assertTrue(rug.canSurvive(h.getLevel(), h.absolutePos(ORIGIN)), "a rug lies on the floor");
        h.assertTrue(!rug.canSurvive(h.getLevel(), h.absolutePos(ORIGIN.above(2))), "a rug does not float");
        h.assertTrue(rug.is(net.minecraft.tags.BlockTags.DAMPENS_VIBRATIONS), "a rug dampens vibrations like carpet");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hangingGrantsButcheryAdvancementOnlyAfterItsParent(GameTestHelper h) {
        var player = TestSupport.advancementPlayer(h);
        BlockPos hookAt = ORIGIN.above(2);
        h.setBlock(hookAt, butcheryBlock("hook").defaultBlockState());
        ResourceLocation hanging = new ResourceLocation("butchery", "hangingaround");
        hangCarcass(h, player, hookAt);
        h.assertTrue(!advancementDone(h, player, hanging), "Hanging Around waits for The Chosen One");
        h.assertTrue(advancementDone(h, player, AlexsButchery.id("wild_game")) == false, "Wild Game needs a kill");
        grant(h, player, new ResourceLocation("butchery", "butcher"));
        grant(h, player, new ResourceLocation("butchery", "firstcarcass"));
        h.setBlock(hookAt.below(), Blocks.AIR.defaultBlockState());
        hangCarcass(h, player, hookAt);
        h.assertTrue(advancementDone(h, player, hanging), "hanging our carcass grants Butchery's Hanging Around");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cleaverKillGrantsWildGameAndTheChosenOne(GameTestHelper h) {
        var player = TestSupport.advancementPlayer(h);
        grant(h, player, new ResourceLocation("butchery", "butcher"));
        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("iron_cleaver"));
        LivingEntity kangaroo = (LivingEntity) h.spawn(alexsMob("kangaroo"), ORIGIN);
        kill(h, kangaroo, player);
        h.runAfterDelay(2, () -> {
            h.assertTrue(advancementDone(h, player, AlexsButchery.id("wild_game")), "a carcass kill grants Wild Game");
            h.assertTrue(advancementDone(h, player, new ResourceLocation("butchery", "firstcarcass")), "and Butchery's The Chosen One");
            h.succeed();
        });
    }

    private static void hangCarcass(GameTestHelper h, ServerPlayer player, BlockPos hookAt) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.of(MobDefs.byId("kangaroo")).carcass().get()));
        BlockPos abs = h.absolutePos(hookAt);
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, abs,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false)));
    }

    private static boolean advancementDone(GameTestHelper h, ServerPlayer player, ResourceLocation id) {
        var advancement = h.getLevel().getServer().getAdvancements().getAdvancement(id);
        if (advancement == null) throw new IllegalStateException("No advancement " + id);
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static void grant(GameTestHelper h, ServerPlayer player, ResourceLocation id) {
        var advancement = h.getLevel().getServer().getAdvancements().getAdvancement(id);
        if (advancement == null) throw new IllegalStateException("No advancement " + id);
        var progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) player.getAdvancements().award(advancement, criterion);
    }

    @GameTest(template = "empty", timeoutTicks = 2200)
    public static void skinRackTakeoverCuresOurSkinIntoLeather(GameTestHelper h) {
        var player = player(h);
        h.setBlock(ORIGIN, butcheryBlock("skin_rack").defaultBlockState());
        ItemStack pelt = new ItemStack(ModItems.simple("tiger_pelt").get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pelt);
        BlockPos abs = h.absolutePos(ORIGIN);
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, abs,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false));
        MinecraftForge.EVENT_BUS.post(event);
        h.assertTrue(h.getBlockState(ORIGIN).is(ModBlocks.SKIN_RACK.get()), "our skin swaps Butchery's rack for ours");
        h.assertTrue(pelt.isEmpty(), "the pelt was hung");
        h.assertTrue(h.getBlockEntity(ORIGIN) instanceof SkinRackBlockEntity rack && rack.skin().is(ModItems.simple("tiger_pelt").get()), "the rack holds the pelt");

        player.setItemInHand(InteractionHand.MAIN_HAND, butcheryItem("salt"));
        use(h, ORIGIN, player);
        h.assertTrue(((SkinRackBlockEntity) h.getBlockEntity(ORIGIN)).stage() == SkinRackBlock.STAGE_SALTED, "salt -> salted");
        ItemStack sponge = butcheryItem("sponge");
        sponge.getOrCreateTag().putDouble("spongeWetness", 2.0);
        player.setItemInHand(InteractionHand.MAIN_HAND, sponge);
        use(h, ORIGIN, player);
        h.assertTrue(((SkinRackBlockEntity) h.getBlockEntity(ORIGIN)).stage() == SkinRackBlock.STAGE_CURING, "wet sponge -> curing");
        h.assertTrue(sponge.getOrCreateTag().getDouble("spongeWetness") == 1.0, "the sponge lost one use");
        h.runAfterDelay(SkinRackBlock.CURE_TICKS + 10, () -> {
            h.assertTrue(((SkinRackBlockEntity) h.getBlockEntity(ORIGIN)).stage() == SkinRackBlock.STAGE_DONE, "cured after 90 seconds");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            use(h, ORIGIN, player);
            h.assertTrue(count(h, Items.LEATHER) == 1, "one leather");
            h.assertTrue(h.getBlockState(ORIGIN).is(butcheryBlock("skin_rack")), "Butchery's empty rack is back");
            h.succeed();
        });
    }
}
