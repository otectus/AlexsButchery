package com.otectus.alexsbutchery.data;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.RugBlock;
import com.otectus.alexsbutchery.def.ItemDefs;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.registry.ModBlocks;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
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
            if (e.rug() != null) rug(e.rug().get(), e.def());
        }
        for (ItemDefs.ItemDef def : ItemDefs.all()) itemModels().basicItem(ModItems.simple(def.id()).get());
        getVariantBuilder(ModBlocks.SKIN_RACK.get()).forAllStates(state -> ConfiguredModel.builder().modelFile(carcass).build());
    }

    /**
     * A rug is the flat pelt as a block model ({@code block/rug}, textured with the pelt drawn for it) turned to face
     * the player; the head is drawn by the block entity renderer. The item shows the pelt texture flat. A mob with a
     * coat-variant pelt ({@link MobDef#rugCoat()}) gets a second model for {@code coat_variant=true}, and its item switches
     * to that pelt through the {@code alexsbutchery:coat_variant} item property (registered in {@code ClientSetup}).
     */
    private void rug(RugBlock block, MobDef def) {
        String path = ForgeRegistries.BLOCKS.getKey(block).getPath();
        ResourceLocation texture = modLoc("block/rug/" + def.rug());
        ModelFile model = models().withExistingParent("block/" + path, modLoc("block/rug")).texture("pelt", texture);
        ModelFile coat = model;
        ItemModelBuilder item = itemModels().withExistingParent(path, mcLoc("item/generated")).texture("layer0", texture);
        if (def.rugCoat() != null) {
            ResourceLocation coatTexture = modLoc("block/rug/" + def.rugCoat().pelt());
            coat = models().withExistingParent("block/" + path + "_coat_variant", modLoc("block/rug")).texture("pelt", coatTexture);
            ModelFile coatItem = itemModels().withExistingParent(path + "_coat_variant", mcLoc("item/generated")).texture("layer0", coatTexture);
            item.override().predicate(AlexsButchery.id("coat_variant"), 1).model(coatItem).end();
        }
        ModelFile coatModel = coat;
        // North-facing is the model as drawn (y 0), the other facings turn from there.
        horizontalBlock(block, state -> state.getValue(RugBlock.COAT) ? coatModel : model, 180);
    }

    private void entity(Block block, ModelFile model, String unusedIcon) {
        getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder().modelFile(model).build());
        String path = ForgeRegistries.BLOCKS.getKey(block).getPath();
        // Java owns every context transform, including measured icon fitting. No second hidden scale in JSON.
        itemModels().getBuilder(path).parent(new ModelFile.UncheckedModelFile("minecraft:builtin/entity"));
    }
}
