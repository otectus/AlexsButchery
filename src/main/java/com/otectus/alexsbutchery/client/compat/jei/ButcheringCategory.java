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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
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
        for (ButcheringRecipe.Output output : recipe.outputs()) {
            if (i >= 10) break;
            int x = 58 + (i % 5) * 18;
            int y = 11 + (i / 5) * 18;
            List<ItemStack> stacks = new ArrayList<>(output.stacks());
            List<Component> lines = tooltip(output);
            builder.addSlot(RecipeIngredientRole.OUTPUT, x, y).addItemStacks(stacks)
                    .addTooltipCallback((view, tooltip) -> tooltip.addAll(lines));
            i++;
        }
    }

    private static List<Component> tooltip(ButcheringRecipe.Output output) {
        List<Component> lines = new ArrayList<>();
        String count = output.min() == output.max() ? Integer.toString(output.max()) : output.min() + "-" + output.max();
        lines.add(Component.translatable("jei.alexsbutchery.count", count).withStyle(ChatFormatting.GRAY));
        if (output.chance() < 0.999F)
            lines.add(Component.translatable("jei.alexsbutchery.chance", Math.round(output.chance() * 1000F) / 10F).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("jei.alexsbutchery.provider", output.provider())
                .withStyle(output.external() ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
        if (output.variantTag() != null) lines.add(Component.translatable("jei.alexsbutchery.variant", output.variantTag()).withStyle(ChatFormatting.AQUA));
        if (output.otherwise()) lines.add(Component.translatable("jei.alexsbutchery.otherwise").withStyle(ChatFormatting.AQUA));
        return lines;
    }

    @Override
    public void draw(ButcheringRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        Component stage = Component.translatable("jei.alexsbutchery.stage." + stageKey(recipe));
        graphics.drawString(font, stage, 1, 1, 0x404040, false);
        graphics.drawString(font, ">", 46, 25, 0x404040, false);
        if (recipe.custom()) graphics.drawString(font, Component.translatable("jei.alexsbutchery.custom_short"), 1, HEIGHT - 9, 0x8B2500, false);
    }

    @Override
    public List<Component> getTooltipStrings(ButcheringRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (recipe.custom() && mouseY >= HEIGHT - 10 && mouseX < 100) return List.of(Component.translatable("jei.alexsbutchery.custom"));
        return List.of();
    }

    private static String stageKey(ButcheringRecipe recipe) {
        if (recipe.action() == Stages.Action.SKIN) return recipe.def().skin().table();
        return recipe.action().name().toLowerCase(Locale.ROOT);
    }
}
