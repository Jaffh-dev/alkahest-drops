package com.jaffh.alkahestdrops.client.compat.jei;

import com.jaffh.alkahestdrops.AlkahestDrops;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * JEI category of block drops: one page per block that drops anything, with its full drops (loot table,
 * experience and drop file drops). The block is a large input slot in a card at the top left, next to the title, and
 * the drops fill a panel under them. A block without an item (such as a piston head) leaves the card empty.
 */
final class BlockDropCategory extends DropCategory<Block> {
    /** Recipe type of block drops. */
    static final RecipeType<DropRecipe<Block>> TYPE = createType();

    private static final int CARD_SIZE = LargeItemRenderer.SIZE + 4;
    private static final DropGrid GRID = new DropGrid((WIDTH - 9 * DropGrid.CELL - 2) / 2, CARD_SIZE + 4, 9, 4);
    /** Left edge of the card, lined up with the drop panel under it. */
    private static final int CARD_X = GRID.x();
    private static final int HEIGHT = GRID.y() + GRID.height();
    private static final int TITLE_X = CARD_X + CARD_SIZE + 6;
    private static final LargeItemRenderer CARD_RENDERER = new LargeItemRenderer();

    /**
     * Creates the category.
     *
     * @param guiHelper JEI drawing helper. Never null.
     */
    BlockDropCategory(IGuiHelper guiHelper) {
        super(TYPE, DropDisplayFormat.BLOCK, guiHelper.createDrawableItemStack(new ItemStack(Items.IRON_PICKAXE)), guiHelper, GRID);
    }

    /**
     * Returns the height of a recipe.
     *
     * @return The height in pixels.
     */
    @Override
    public int getHeight() {
        return HEIGHT;
    }

    /**
     * Adds the block's item as a large input slot in the card.
     *
     * @param builder Layout builder. Never null.
     * @param recipe  Recipe being laid out. Never null.
     */
    @Override
    protected void setTarget(IRecipeLayoutBuilder builder, DropRecipe<Block> recipe) {
        if (recipe.target().asItem() == Items.AIR) return;
        builder.addSlot(RecipeIngredientRole.INPUT, CARD_X + 2, 2)
                .setCustomRenderer(VanillaTypes.ITEM_STACK, CARD_RENDERER)
                .addItemStack(new ItemStack(recipe.target()));
    }

    /**
     * Draws the card and the title.
     *
     * @param recipe      Recipe being drawn. Never null.
     * @param guiGraphics Graphics of the recipe. Never null.
     * @param mouseX      Mouse x relative to the recipe.
     * @param mouseY      Mouse y relative to the recipe.
     */
    @Override
    protected void drawLayout(DropRecipe<Block> recipe, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        drawCard(guiGraphics, CARD_X, 0, CARD_SIZE, CARD_SIZE);
        drawTitle(guiGraphics, recipe, TITLE_X, 4, WIDTH - TITLE_X);
    }

    /**
     * Returns the name of a block.
     *
     * @param target Block. Never null.
     * @return The translated name, such as "Oak Log".
     */
    @Override
    protected Component nameOf(Block target) {
        return target.getName();
    }

    /**
     * Creates the recipe type, whose class is the raw {@link DropRecipe} class.
     *
     * @return The recipe type of block drops.
     */
    @SuppressWarnings("unchecked")
    private static RecipeType<DropRecipe<Block>> createType() {
        return new RecipeType<>(new ResourceLocation(AlkahestDrops.ID, "block_drops"), (Class<DropRecipe<Block>>) (Class<?>) DropRecipe.class);
    }
}
