package com.otectus.alexsbutchery.registry;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.block.entity.SkinRackBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AlexsButchery.MOD_ID);

    /** One block entity type shared by every mob block (carcasses, heads, mounts); the block carries the definition. */
    public static final RegistryObject<BlockEntityType<CarcassBlockEntity>> CARCASS = BLOCK_ENTITIES.register("carcass",
            () -> BlockEntityType.Builder.of(CarcassBlockEntity::new, ModBlocks.allBlocks()).build(null));

    public static final RegistryObject<BlockEntityType<SkinRackBlockEntity>> SKIN_RACK = BLOCK_ENTITIES.register("skin_rack",
            () -> BlockEntityType.Builder.of(SkinRackBlockEntity::new, ModBlocks.SKIN_RACK.get()).build(null));

    private ModBlockEntities() {}
}
