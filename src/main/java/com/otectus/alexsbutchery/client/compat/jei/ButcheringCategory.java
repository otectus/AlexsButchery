package com.otectus.alexsbutchery.client.compat.jei;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class ButcheringCategory implements IRecipeCategory<ButcheringRecipe> {
    public static final RecipeType<ButcheringRecipe> TYPE = RecipeType.create(AlexsButchery.MOD_ID, "butchering", ButcheringRecipe.class);
    private static final int WIDTH = 162;
    private static final int HEIGHT = 56;

    private final IDrawable background;
    private final IDrawable icon;

    public ButcheringCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helper.createDrawableItemStack(new ItemStack(ButcheryHooks.hook()));
    }

    @Override
    public RecipeType<ButcheringRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.alexsbutchery.butchering");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ButcheringRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 20).addIngredient(VanillaTypes.ITEM_STACK, recipe.carcass());
        builder.addSlot(RecipeIngredientRole.CATALYST, 24, 20).addIngredient(VanillaTypes.ITEM_STACK, recipe.tool());
        int i = 0;
        for (ItemStack output : recipe.outputs()) {
            int x = 58 + (i % 5) * 18;
            int y = 11 + (i / 5) * 18;
            builder.addSlot(RecipeIngredientRole.OUTPUT, x, y).addIngredient(VanillaTypes.ITEM_STACK, output);
            i++;
        }
    }

    @Override
    public void draw(ButcheringRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        Component stage = Component.translatable("jei.alexsbutchery.stage." + stageKey(recipe));
        graphics.drawString(font, stage, 1, 1, 0x404040, false);
        graphics.drawString(font, ">", 46, 25, 0x404040, false);
    }

    private static String stageKey(ButcheringRecipe recipe) {
        if (recipe.action() == Stages.Action.SKIN) return recipe.def().skin().table();
        return recipe.action().name().toLowerCase(Locale.ROOT);
    }
}
