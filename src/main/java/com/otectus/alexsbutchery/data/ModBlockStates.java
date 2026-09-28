package com.otectus.alexsbutchery.data;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.RugBlock;
import com.otectus.alexsbutchery.def.ItemDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Every mob block is drawn by the block entity renderer, so each block state points at one empty model that only
 * names the break particle. Item models defer to the built-in entity renderer (MobBlockItem).
 */
final class ModBlockStates extends BlockStateProvider {

    ModBlockStates(PackOutput output, ExistingFileHelper helper) {
        super(output, AlexsButchery.MOD_ID, helper);
    }

    @Override
    protected void registerStatesAndModels() {
        ModelFile carcass = models().getExistingFile(modLoc("block/carcass"));
        for (ModBlocks.Entries e : ModBlocks.all()) {
            entity(e.carcass().get(), carcass, "beef");
            if (e.drained() != null) entity(e.drained().get(), carcass, "rotten_flesh");
            if (e.head() != null) entity(e.head().get(), carcass, "bone");
            if (e.mount() != null) entity(e.mount().get(), carcass, "item_frame");
            if (e.skeleton() != null) entity(e.skeleton().get(), carcass, "bone_block");
            if (e.rug() != null) rug(e.rug().get(), e.def().rug());
        }
        for (ItemDefs.ItemDef def : ItemDefs.all()) itemModels().basicItem(ModItems.simple(def.id()).get());
        getVariantBuilder(ModBlocks.SKIN_RACK.get()).forAllStates(state -> ConfiguredModel.builder().modelFile(carcass).build());
    }

    /**
     * A rug is the flat pelt as a block model ({@code block/rug}, textured with the pelt drawn for it) turned to face
     * the player; the head is drawn by the block entity renderer. The item shows the pelt texture flat.
     */
    private void rug(RugBlock block, String skin) {
        String path = ForgeRegistries.BLOCKS.getKey(block).getPath();
        ResourceLocation texture = modLoc("block/rug/" + skin);
        ModelFile model = models().withExistingParent("block/" + path, modLoc("block/rug")).texture("pelt", texture);
        // North-facing is the model as drawn (y 0), the other facings turn from there.
        horizontalBlock(block, model);
        itemModels().withExistingParent(path, mcLoc("item/generated")).texture("layer0", texture);
    }

    private void entity(Block block, ModelFile model, String unusedIcon) {
        getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder().modelFile(model).build());
        String path = ForgeRegistries.BLOCKS.getKey(block).getPath();
        // builtin/entity hands the item to MobBlockItem's BlockEntityWithoutLevelRenderer.
        itemModels().getBuilder(path).parent(new ModelFile.UncheckedModelFile("minecraft:builtin/entity"))
                .transforms()
                .transform(ItemDisplayContext.GUI).rotation(30, 225, 0).scale(0.625F).end()
                .transform(ItemDisplayContext.GROUND).translation(0, 3, 0).scale(0.25F).end()
                .transform(ItemDisplayContext.FIXED).scale(0.5F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).rotation(75, 45, 0).translation(0, 2.5F, 0).scale(0.375F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND).rotation(75, 45, 0).translation(0, 2.5F, 0).scale(0.375F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(0, 45, 0).scale(0.4F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(0, 225, 0).scale(0.4F).end()
                .end();
    }
}
