package com.otectus.alexsbutchery.registry;

import com.otectus.alexsbutchery.AlexsButchery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Tags this mod joins (Butchery's, so its tag-driven behaviour covers our blocks) and the ones it defines. */
public final class ModTags {

    public static final class Blocks {
        public static final TagKey<Block> CARCASS = butchery("carcass");
        public static final TagKey<Block> SMALL_CARCASS = butchery("small_carcass");
        public static final TagKey<Block> DRAINED_CARCASS = butchery("drained_carcass");
        public static final TagKey<Block> CLEAVER_ONLY_HINT = butchery("cleaver_only_hint");
        public static final TagKey<Block> CARCASS_NO_STATES = butchery("carcass_no_states");
        public static final TagKey<Block> HEADS = butchery("heads");
        public static final TagKey<Block> FLOOR_CARCASS = ours("floor_carcass");
        public static final TagKey<Block> SKELETONS = ours("skeletons");
        public static final TagKey<Block> RUGS = ours("rugs");

        private static TagKey<Block> butchery(String path) {
            return TagKey.create(Registries.BLOCK, new ResourceLocation("butchery", path));
        }

        private static TagKey<Block> ours(String path) {
            return TagKey.create(Registries.BLOCK, AlexsButchery.id(path));
        }

        private Blocks() {}
    }

    public static final class Items {
        public static final TagKey<Item> SCRAPPABLE = butchery("scrappable");
        public static final TagKey<Item> WEIGHTED_LIGHT = butchery("weighted_carcass_l");
        public static final TagKey<Item> WEIGHTED_HEAVY = butchery("weighted_carcass_ll");
        public static final TagKey<Item> SKINS = butchery("skins");
        public static final TagKey<Item> HEAD_MOUNT = butchery("head_mount");
        public static final TagKey<Item> BOSS_TOOL = butchery("boss_tool");
        public static final TagKey<Item> REPLACED_BY_CARCASS = ours("replaced_by_carcass");
        public static final TagKey<Item> CARCASSES = ours("carcasses");
        public static final TagKey<Item> HEADS = ours("heads");
        public static final TagKey<Item> HEAD_MOUNTS = ours("head_mounts");
        public static final TagKey<Item> SKELETONS = ours("skeletons");
        public static final TagKey<Item> RUGS = ours("rugs");
        public static final TagKey<Item> BUTCHERY_RAW_MEAT = butchery("raw_meat");
        public static final TagKey<Item> BUTCHERY_MEAT = butchery("meat");
        public static final TagKey<Item> FORGE_RAW_MEAT = TagKey.create(Registries.ITEM, new ResourceLocation("forge", "raw_meat"));
        public static final TagKey<Item> FORGE_COOKED_MEAT = TagKey.create(Registries.ITEM, new ResourceLocation("forge", "cooked_meat"));

        private static TagKey<Item> butchery(String path) {
            return TagKey.create(Registries.ITEM, new ResourceLocation("butchery", path));
        }

        private static TagKey<Item> ours(String path) {
            return TagKey.create(Registries.ITEM, AlexsButchery.id(path));
        }

        private Items() {}
    }

    public static final class Entities {
        public static final TagKey<EntityType<?>> BUTCHERABLE = TagKey.create(Registries.ENTITY_TYPE, AlexsButchery.id("butcherable"));

        private Entities() {}
    }

    private ModTags() {}
}
