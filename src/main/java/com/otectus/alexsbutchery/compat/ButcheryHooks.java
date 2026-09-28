package com.otectus.alexsbutchery.compat;

import net.mcreator.butchery.configuration.ButcheryconfigConfiguration;
import net.mcreator.butchery.init.ButcheryModBlocks;
import net.mcreator.butchery.init.ButcheryModEnchantments;
import net.mcreator.butchery.init.ButcheryModItems;
import net.mcreator.butchery.procedures.BlooddrippingProcedure;
import net.mcreator.butchery.procedures.FillbloodgrateProcedure;
import net.mcreator.butchery.procedures.InstantfillbloodgrateProcedure;
import net.mcreator.butchery.procedures.PlacebloodpuddleProcedure;
import net.mcreator.butchery.procedures.SmallInstantfillbloodgrateProcedure;
import net.mcreator.butchery.procedures.SmallblooddrippingProcedure;
import net.mcreator.butchery.procedures.SmallfillbloodgrateProcedure;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Every Butchery member this mod touches, in one place: its config, its tool tags, its blocks and items, and the
 * public static procedure entry points that implement the shared behaviours (grate filling, blood drips, puddles).
 * Butchery is an MCreator mod, so these are the only stable seams it offers; if a Butchery update renames one, this
 * is the only file to change.
 */
public final class ButcheryHooks {
    public static final TagKey<Item> FORGE_CLEAVER = ItemTags.create(new ResourceLocation("forge:cleaver"));
    public static final TagKey<Item> C_CLEAVER = ItemTags.create(new ResourceLocation("c:cleaver"));
    public static final TagKey<Item> FORGE_SKINNING_KNIVES = ItemTags.create(new ResourceLocation("forge:skinning_knives"));
    public static final TagKey<Item> C_SKINNING_KNIVES = ItemTags.create(new ResourceLocation("c:skinning_knives"));
    public static final TagKey<Item> FORGE_HAMMER = ItemTags.create(new ResourceLocation("forge:hammer"));
    public static final TagKey<Item> C_HAMMER = ItemTags.create(new ResourceLocation("c:hammer"));
    public static final TagKey<Item> BOSS_TOOL = ItemTags.create(new ResourceLocation("butchery:boss_tool"));

    public static final TagKey<Item> FORGE_ACID_BOTTLE = ItemTags.create(new ResourceLocation("forge:acid_bottle"));
    public static final TagKey<Item> C_ACID_BOTTLE = ItemTags.create(new ResourceLocation("c:acid_bottle"));

    public static final ResourceLocation ROPE_SOUND = new ResourceLocation("butchery:rope");
    public static final ResourceLocation ACID_SOUND = new ResourceLocation("butchery:acid");

    // --- config -------------------------------------------------------------------------------------------------

    public static boolean cleaverOnlyKills() { return ButcheryconfigConfiguration.SPECIFIC_TOOLS.get(); }
    public static boolean instantBleed() { return ButcheryconfigConfiguration.INSTANT_BLEED.get(); }
    public static boolean organs() { return ButcheryconfigConfiguration.ORGANS.get(); }
    public static boolean lootingEnabled() { return ButcheryconfigConfiguration.LOOTING_ENCHANT.get(); }

    // --- tools --------------------------------------------------------------------------------------------------

    public static boolean isCleaver(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(FORGE_CLEAVER) || stack.is(C_CLEAVER));
    }

    public static boolean isSkinningKnife(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(FORGE_SKINNING_KNIVES) || stack.is(C_SKINNING_KNIVES));
    }

    public static boolean isHammer(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(FORGE_HAMMER) || stack.is(C_HAMMER));
    }

    /** Butchery's bottle of sulfuric acid, which dissolves a whole carcass into its skeleton. */
    public static boolean isAcidBottle(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(FORGE_ACID_BOTTLE) || stack.is(C_ACID_BOTTLE));
    }

    public static boolean hasButchersTouch(ItemStack stack) {
        return !stack.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(ButcheryModEnchantments.BUTCHERSTOUCH.get(), stack) > 0;
    }

    /** Butchery's kill rule: with cleaver-only kills on, the weapon must be a cleaver or carry Butcher's Touch. */
    public static boolean weaponButchers(ItemStack weapon) {
        return !cleaverOnlyKills() || isCleaver(weapon) || hasButchersTouch(weapon);
    }

    // --- blocks and items ---------------------------------------------------------------------------------------

    public static Block hook() { return ButcheryModBlocks.HOOK.get(); }
    public static Block rope() { return ButcheryModBlocks.ROPE.get(); }
    public static Block bloodGrate() { return ButcheryModBlocks.BLOOD_GRATE.get(); }
    public static Block skinRack() { return ButcheryModBlocks.SKIN_RACK.get(); }
    public static Item meatScraps() { return ButcheryModItems.MEAT_SCRAPS.get(); }

    public static final TagKey<Item> FORGE_SALT = ItemTags.create(new ResourceLocation("forge:salt"));
    public static final TagKey<Item> C_SALT = ItemTags.create(new ResourceLocation("c:salt"));

    public static boolean isSalt(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ButcheryModItems.SALT.get()) || stack.is(FORGE_SALT) || stack.is(C_SALT));
    }

    /** Butchery's sponge, wetted in a cauldron; its {@code spongeWetness} counts the uses left. */
    public static boolean isWetSponge(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ButcheryModItems.SPONGE.get()) && stack.getOrCreateTag().getDouble("spongeWetness") >= 1.0;
    }

    public static void wringSponge(ItemStack stack) {
        stack.getOrCreateTag().putDouble("spongeWetness", stack.getOrCreateTag().getDouble("spongeWetness") - 1.0);
    }

    public static Block emptyMount(String butcheryMount) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("butchery", butcheryMount));
        return block == null ? ButcheryModBlocks.EMPTY_HEAD_MOUNT.get() : block;
    }

    public static SoundEvent ropeSound() {
        return ForgeRegistries.SOUND_EVENTS.getValue(ROPE_SOUND);
    }

    @Nullable
    public static SoundEvent acidSound() {
        return ForgeRegistries.SOUND_EVENTS.getValue(ACID_SOUND);
    }

    // --- advancements -------------------------------------------------------------------------------------------

    /**
     * Grants one of Butchery's advancements ({@code butchery:<id>}). Butchery's tree uses {@code minecraft:impossible}
     * criteria that its procedures grant from its own carcass items and blocks, one step at a time; ours stand in for
     * those steps. Like Butchery, a step is only granted once its parent is done, so the tree unlocks in order.
     */
    public static void grantAdvancement(ServerPlayer player, String id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(new ResourceLocation("butchery", id));
        if (advancement == null) return;
        PlayerAdvancements progress = player.getAdvancements();
        Advancement parent = advancement.getParent();
        if (parent != null && !progress.getOrStartProgress(parent).isDone()) return;
        AdvancementProgress state = progress.getOrStartProgress(advancement);
        if (state.isDone()) return;
        for (String criterion : state.getRemainingCriteria()) progress.award(advancement, criterion);
    }

    /** Butchery's organ tables, rolled at the cut stages: {@code butchery:blocks/organs_drop_1..3}. */
    public static ResourceLocation organsTable(int cut) {
        return new ResourceLocation("butchery", "blocks/organs_drop_" + cut);
    }

    // --- shared procedures --------------------------------------------------------------------------------------

    /** One 50 mB fill of the first blood grate up to 16 blocks below the carcass. */
    public static void fillGrate(LevelAccessor level, BlockPos pos, boolean small) {
        if (small) SmallfillbloodgrateProcedure.execute(level, pos.getX(), pos.getY(), pos.getZ());
        else FillbloodgrateProcedure.execute(level, pos.getX(), pos.getY(), pos.getZ());
    }

    /** The whole carcass's blood at once (Butchery's "Instant Bleeding" option). */
    public static void instantFill(LevelAccessor level, BlockPos pos, boolean small) {
        if (small) SmallInstantfillbloodgrateProcedure.execute(level, pos.getX(), pos.getY(), pos.getZ());
        else InstantfillbloodgrateProcedure.execute(level, pos.getX(), pos.getY(), pos.getZ());
    }

    /** Blood particles under a hanging carcass (needs the block tagged {@code butchery:carcass} or {@code carcass_no_states}). */
    public static void drip(LevelAccessor level, BlockPos pos, boolean small) {
        if (small) SmallblooddrippingProcedure.execute(level, pos.getX(), pos.getY(), pos.getZ());
        else BlooddrippingProcedure.execute(level, pos.getX(), pos.getY(), pos.getZ());
    }

    /** Places Butchery's blood puddle on the ground below when no grate catches the blood (config-gated inside). */
    public static void puddle(LevelAccessor level, BlockPos pos) {
        PlacebloodpuddleProcedure.execute(level, pos.getX(), pos.getY(), pos.getZ());
    }

    public static TagKey<Item> itemTag(String id) {
        return TagKey.create(Registries.ITEM, new ResourceLocation(id));
    }

    private ButcheryHooks() {}
}
