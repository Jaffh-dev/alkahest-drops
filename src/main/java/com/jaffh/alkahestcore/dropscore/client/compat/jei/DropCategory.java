package com.jaffh.alkahestcore.dropscore.client.compat.jei;

import com.jaffh.alkahestcore.dropscore.common.drops.SyncedDrop;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Shared parts of the drop categories: one target per recipe, its name as the title, and the target's drops in a
 * panel of slots with their full explanation in the tooltip. Item drops are output slots, so looking up an item's
 * recipes shows what drops it; experience drops are drawn as an orb and are not looked up.
 *
 * @param <T> Target type, such as {@code EntityType<?>} or {@code Block}.
 */
abstract class DropCategory<T> implements IRecipeCategory<DropRecipe<T>> {
    /** Width of both categories, the width of a vanilla container screen. */
    protected static final int WIDTH = 176;
    /** Color of the texts drawn on the recipe background. */
    protected static final int TEXT_COLOR = 0xFF404040;
    private static final int CARD_FILL = 0xFF8B8B8B;
    private static final int CARD_SHADOW = 0xFF373737;
    private static final int CARD_LIGHT = 0xFFFFFFFF;

    private final RecipeType<DropRecipe<T>> recipeType;
    private final DropDisplayFormat format;
    private final IDrawable icon;
    private final IDrawableStatic slotBackground;
    private final DropGrid grid;

    /**
     * Creates a category.
     *
     * @param recipeType Recipe type of the category. Never null.
     * @param format     Wording of the category's module. Never null.
     * @param icon       Icon of the category tab. Never null.
     * @param guiHelper  JEI drawing helper. Never null.
     * @param grid       Panel of drop slots. Never null.
     */
    protected DropCategory(RecipeType<DropRecipe<T>> recipeType, DropDisplayFormat format, IDrawable icon,
                           IGuiHelper guiHelper, DropGrid grid) {
        this.recipeType = recipeType;
        this.format = format;
        this.icon = icon;
        this.slotBackground = guiHelper.getSlotDrawable();
        this.grid = grid;
    }

    /**
     * Adds the slots or hidden ingredients of the recipe's target.
     *
     * @param builder Layout builder. Never null.
     * @param recipe  Recipe being laid out. Never null.
     */
    protected abstract void setTarget(IRecipeLayoutBuilder builder, DropRecipe<T> recipe);

    /**
     * Draws everything but the drop panel: the title, the target's card and any decoration.
     *
     * @param recipe      Recipe being drawn. Never null.
     * @param guiGraphics Graphics of the recipe, translated to its top-left corner. Never null.
     * @param mouseX      Mouse x relative to the recipe.
     * @param mouseY      Mouse y relative to the recipe.
     */
    protected abstract void drawLayout(DropRecipe<T> recipe, GuiGraphics guiGraphics, double mouseX, double mouseY);

    /**
     * Returns the name of a target.
     *
     * @param target Target. Never null.
     * @return The translated name.
     */
    protected abstract Component nameOf(T target);

    /**
     * Returns the recipe type of this category.
     *
     * @return The recipe type.
     */
    @Override
    public RecipeType<DropRecipe<T>> getRecipeType() {
        return recipeType;
    }

    /**
     * Returns the title of this category.
     *
     * @return The translated title, such as "Mob Drops".
     */
    @Override
    public Component getTitle() {
        return format.moduleText("category");
    }

    /**
     * Returns the width of a recipe.
     *
     * @return The width in pixels.
     */
    @Override
    public int getWidth() {
        return WIDTH;
    }

    /**
     * Tells JEI not to draw its border around each recipe; the pages draw their own cards and panels, and the space
     * goes to the drops.
     *
     * @return Always false.
     */
    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    /**
     * Returns the icon of the category tab.
     *
     * @return The icon.
     */
    @Override
    public IDrawable getIcon() {
        return icon;
    }

    /**
     * Returns the id JEI uses for a recipe, such as for bookmarks.
     *
     * @param recipe Recipe. Never null.
     * @return The recipe id, unique inside this category.
     */
    @Override
    public ResourceLocation getRegistryName(DropRecipe<T> recipe) {
        return recipe.id();
    }

    /**
     * Returns the underlying recipes JEI shows as "Recipe ID" in advanced tooltips. Drop pages are not recipes, and
     * each drop's tooltip says what added it instead.
     *
     * @param recipe Recipe. Never null.
     * @return Always an empty list.
     */
    @Override
    public List<ResourceLocation> getSourceRecipeIds(DropRecipe<T> recipe) {
        return List.of();
    }

    /**
     * Returns how many drops one recipe of this category shows.
     *
     * @return The number of cells of the drop panel.
     */
    final int dropsPerPage() {
        return grid.size();
    }

    /**
     * Lays out the target and one output slot per item drop, each with its explanation in the tooltip.
     *
     * @param builder Layout builder. Never null.
     * @param recipe  Recipe being laid out. Never null.
     * @param focuses Focused ingredients. Never null.
     */
    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DropRecipe<T> recipe, IFocusGroup focuses) {
        setTarget(builder, recipe);
        List<SyncedDrop> drops = recipe.drops();
        for (int i = 0; i < drops.size(); i++) {
            SyncedDrop drop = drops.get(i);
            if (drop.isExperience()) continue;
            builder.addSlot(RecipeIngredientRole.OUTPUT, grid.cellX(i) + 1, grid.cellY(i) + 1)
                    .addItemStack(drop.stack())
                    .addRichTooltipCallback((view, tooltip) -> DropTooltip.addTo(tooltip, drop, format, Screen.hasShiftDown()));
        }
    }

    /**
     * Draws the layout, the drop panel with a slot background in every cell, and the experience orbs.
     *
     * @param recipe          Recipe being drawn. Never null.
     * @param recipeSlotsView Slots of the recipe. Never null.
     * @param guiGraphics     Graphics of the recipe, translated to its top-left corner. Never null.
     * @param mouseX          Mouse x relative to the recipe.
     * @param mouseY          Mouse y relative to the recipe.
     */
    @Override
    public void draw(DropRecipe<T> recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        drawLayout(recipe, guiGraphics, mouseX, mouseY);
        drawCard(guiGraphics, grid.x(), grid.y(), grid.width(), grid.height());
        for (int i = 0; i < grid.size(); i++) slotBackground.draw(guiGraphics, grid.cellX(i), grid.cellY(i));
        List<SyncedDrop> drops = recipe.drops();
        for (int i = 0; i < drops.size(); i++) {
            if (drops.get(i).isExperience()) ExperienceOrbIcon.draw(guiGraphics, grid.cellX(i) + 1, grid.cellY(i) + 1);
        }
    }

    /**
     * Shows the explanation of an experience drop under the mouse; item drops have it in their slot tooltip.
     *
     * @param tooltip         Tooltip being built. Never null.
     * @param recipe          Recipe under the mouse. Never null.
     * @param recipeSlotsView Slots of the recipe. Never null.
     * @param mouseX          Mouse x relative to the recipe.
     * @param mouseY          Mouse y relative to the recipe.
     */
    @Override
    public void getTooltip(ITooltipBuilder tooltip, DropRecipe<T> recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        int cell = grid.cellAt(mouseX, mouseY);
        if (cell < 0 || cell >= recipe.drops().size()) return;
        SyncedDrop drop = recipe.drops().get(cell);
        if (drop.isExperience()) DropTooltip.addTo(tooltip, drop, format, Screen.hasShiftDown());
    }

    /**
     * Draws the target's name as the title, wrapped to a width.
     *
     * @param guiGraphics Graphics of the recipe. Never null.
     * @param recipe      Recipe being drawn. Never null.
     * @param x           Left edge of the title.
     * @param y           Top edge of the title.
     * @param maxWidth    Widest the title may be, in pixels.
     */
    protected final void drawTitle(GuiGraphics guiGraphics, DropRecipe<T> recipe, int x, int y, int maxWidth) {
        Font font = Minecraft.getInstance().font;
        Component title = nameOf(recipe.target());
        List<FormattedCharSequence> lines = font.split(title, maxWidth);
        for (int i = 0; i < lines.size(); i++) {
            guiGraphics.drawString(font, lines.get(i), x, y + i * font.lineHeight, TEXT_COLOR, false);
        }
    }

    /**
     * Draws a sunken gray card, like the inside of a vanilla slot.
     *
     * @param guiGraphics Graphics of the recipe. Never null.
     * @param x           Left edge.
     * @param y           Top edge.
     * @param width       Width, border included.
     * @param height      Height, border included.
     */
    protected static void drawCard(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, CARD_SHADOW);
        guiGraphics.fill(x + 1, y + 1, x + width, y + height, CARD_LIGHT);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, CARD_FILL);
    }
}
