package com.otectus.alexsbutchery.compat;

import com.otectus.alexsbutchery.AlexsButchery;
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
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/**
 * Every Butchery member this mod touches, in one place: its config, its tool tags, its blocks and items, and the
 * public static procedure entry points that implement the shared behaviours (grate filling, blood drips, puddles).
 * Butchery is an MCreator mod, so these are the only stable seams it offers; if a Butchery update renames one, this
 * is the only file to change.
 */
public final class ButcheryHooks {
    public static final TagKey<Item> FORGE_CLEAVER = ItemTags.create(new ResourceLocation("forge:cleaver"));
    public static final TagKey<Item> C_CLEAVER = ItemTags.create(new ResourceLocation("c:cleaver"));
    /** Butchery's own cleavers (5.3+), which its kill rule reads; unlike the two above it holds no knives. */
    public static final TagKey<Item> BUTCHERY_CLEAVER = ItemTags.create(new ResourceLocation("butchery:cleaver"));
    /** Farmer's Delight's knives. Butchery includes this tag in {@code forge:cleaver} and {@code c:cleaver}. */
    public static final TagKey<Item> FARMERS_DELIGHT_KNIVES = ItemTags.create(new ResourceLocation("farmersdelight:tools/knives"));
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

    /**
     * Butchery 5.3's {@code ["Farmers Delight Compatibility"] "Farmers Delight Knives Drop Carcasses"} (common
     * {@code Butchery.toml}, default false), read live so file reloads apply. Butchery 5.2 has no such option; there its
     * kill rule took the cleaver tags, which contain Farmer's Delight's knives, so they always butcher.
     */
    public static boolean farmersDelightKnivesButcher() { return FarmersDelightKnives.allowed(); }

    /** Logs which Farmer's Delight knife rule applies, at load rather than on the first kill. */
    public static void resolveOptionalConfig() { FarmersDelightKnives.allowed(); }

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

    /**
     * Butchery's kill rule as its carcass procedures apply it to the killer's main-hand item: with cleaver-only kills
     * off anything butchers; on, the weapon must be a cleaver or carry Butcher's Touch, and Farmer's Delight's knives
     * count only while {@link #farmersDelightKnivesButcher()} allows them, even though Butchery tags them as cleavers.
     * A Butchery cleaver that is also tagged as a knife stays a cleaver. Other {@code forge:}/{@code c:cleaver} items
     * keep counting, as they always have here. Tool checks for cutting a carcass are separate ({@link #isCleaver}).
     */
    public static boolean allowsCarcassKill(ItemStack weapon) {
        if (!cleaverOnlyKills() || hasButchersTouch(weapon) || weapon.is(BUTCHERY_CLEAVER)) return true;
        if (weapon.is(FARMERS_DELIGHT_KNIVES)) return farmersDelightKnivesButcher();
        return isCleaver(weapon);
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

    /**
     * {@code ButcheryconfigConfiguration.FD_CARCASS_DROP}, which Butchery 5.2 lacks, looked up once by reflection so
     * this mod still links against 5.2. If a Butchery that should have the option (5.3+) does not expose it as a
     * boolean config value, the knives are refused rather than guessed, with one error naming the cause.
     */
    private static final class FarmersDelightKnives {
        private static final String FIELD = "FD_CARCASS_DROP";
        @Nullable private static final ForgeConfigSpec.ConfigValue<?> OPTION;
        /** The rule when the option cannot be read: knives butcher on Butchery before 5.3, are refused after. */
        private static final boolean WITHOUT_OPTION;
        private static boolean reported;

        static {
            ArtifactVersion version = ModList.get().getModContainerById("butchery").map(c -> c.getModInfo().getVersion()).orElse(null);
            boolean expected = version == null || version.compareTo(new DefaultArtifactVersion("5.3")) >= 0;
            Object value = null;
            Throwable failure = null;
            try {
                value = ButcheryconfigConfiguration.class.getField(FIELD).get(null);
            } catch (NoSuchFieldException e) {
                if (expected) failure = e;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                failure = e;
            }
            if (value instanceof ForgeConfigSpec.ConfigValue<?> option) {
                OPTION = option;
                WITHOUT_OPTION = false;
                AlexsButchery.LOGGER.info("Farmer's Delight knives follow Butchery {}'s \"Farmers Delight Knives Drop Carcasses\" option", version);
            } else if (failure == null && value == null) {
                OPTION = null;
                WITHOUT_OPTION = true;
                AlexsButchery.LOGGER.info("Butchery {} has no Farmer's Delight knife option; its knives butcher as cleavers", version);
            } else {
                OPTION = null;
                WITHOUT_OPTION = false;
                reported = true;
                AlexsButchery.LOGGER.error("Butchery {} should provide ButcheryconfigConfiguration.{} but it could not be read ({}). "
                        + "Farmer's Delight knives will not drop Alex's Mobs carcasses until Alex's Butchery supports this Butchery version.",
                        version, FIELD, failure != null ? failure : "not a config value: " + value);
            }
        }

        static boolean allowed() {
            if (OPTION == null) return WITHOUT_OPTION;
            Object value;
            try {
                value = OPTION.get();
            } catch (IllegalStateException e) {
                value = e;
            }
            if (value instanceof Boolean allowed) return allowed;
            if (!reported) {
                reported = true;
                AlexsButchery.LOGGER.error("Butchery's {} option is unreadable ({}); refusing Farmer's Delight knife carcasses", FIELD, value);
            }
            return false;
        }
    }

    private ButcheryHooks() {}
}
