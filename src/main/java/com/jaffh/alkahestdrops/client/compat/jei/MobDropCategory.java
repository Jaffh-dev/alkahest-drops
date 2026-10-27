package com.jaffh.alkahestdrops.client.compat.jei;

import com.jaffh.alkahestdrops.AlkahestDrops;
import com.jaffh.alkahestdrops.common.drops.DropTrigger;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.common.ForgeSpawnEggItem;

import java.util.EnumMap;
import java.util.Map;

/**
 * JEI category of mob drops: one page per mob with its full drops (loot table, experience and drop file drops).
 * The mob is drawn in a card on the left, with what triggers the drops under it, and the drops are in a panel on the
 * right. The card is a slot of the mob ingredient, and the mob is also a hidden output, so clicking a mob anywhere in
 * JEI (R or U) opens its page; its spawn egg is a hidden input too.
 */
final class MobDropCategory extends DropCategory<EntityType<?>> {
    /** Recipe type of mob drops. */
    static final RecipeType<DropRecipe<EntityType<?>>> TYPE = createType();

    private static final EntityPreviews.PreviewArea CARD = new EntityPreviews.PreviewArea(0, 11, 66, 86);
    private static final DropGrid GRID = new DropGrid(WIDTH - 4 * DropGrid.CELL - 2, 11, 4, 6);
    private static final int HEIGHT = GRID.y() + GRID.height();
    private static final int TRIGGER_Y = CARD.y() + CARD.height() + 4;
    private static final Map<DropTrigger, ItemStack> TRIGGER_ICONS = triggerIcons();

    private final EntityPreviews previews;
    private final MobIngredientRenderer cardRenderer;
    private final IDrawableStatic arrow;

    /**
     * Creates the category.
     *
     * @param guiHelper JEI drawing helper. Never null.
     * @param previews  Mob previews shared with the ingredient list. Never null.
     */
    MobDropCategory(IGuiHelper guiHelper, EntityPreviews previews) {
        super(TYPE, DropDisplayFormat.MOB, guiHelper.createDrawableItemStack(new ItemStack(Items.ZOMBIE_HEAD)), guiHelper, GRID);
        this.previews = previews;
        this.cardRenderer = MobIngredientRenderer.card(previews, CARD.width() - 2, CARD.height() - 2);
        this.arrow = guiHelper.getRecipeArrow();
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
     * Makes the card a slot of the mob, and adds the mob as a hidden output and its spawn egg as a hidden input.
     *
     * @param builder Layout builder. Never null.
     * @param recipe  Recipe being laid out. Never null.
     */
    @Override
    protected void setTarget(IRecipeLayoutBuilder builder, DropRecipe<EntityType<?>> recipe) {
        MobIngredient mob = new MobIngredient(recipe.target());
        builder.addSlot(RecipeIngredientRole.INPUT, CARD.x() + 1, CARD.y() + 1)
                .setCustomRenderer(MobIngredient.TYPE, cardRenderer)
                .addIngredient(MobIngredient.TYPE, mob);
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addIngredient(MobIngredient.TYPE, mob);
        SpawnEggItem egg = ForgeSpawnEggItem.fromEntityType(recipe.target());
        if (egg != null) builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(new ItemStack(egg));
    }

    /**
     * Draws the title, the mob in its card (or its name when it cannot be drawn), the arrow and the trigger.
     *
     * @param recipe      Recipe being drawn. Never null.
     * @param guiGraphics Graphics of the recipe. Never null.
     * @param mouseX      Mouse x relative to the recipe.
     * @param mouseY      Mouse y relative to the recipe.
     */
    @Override
    protected void drawLayout(DropRecipe<EntityType<?>> recipe, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        drawTitle(guiGraphics, recipe, 0, 0, WIDTH);
        drawCard(guiGraphics, CARD.x(), CARD.y(), CARD.width(), CARD.height());
        if (!previews.draw(guiGraphics, recipe.target(), CARD, mouseX, mouseY)) {
            guiGraphics.drawWordWrap(font, nameOf(recipe.target()), CARD.x() + 3, CARD.y() + 4, CARD.width() - 6, TEXT_COLOR);
        }
        int arrowX = CARD.width() + (GRID.x() - CARD.width() - arrow.getWidth()) / 2;
        int arrowY = CARD.y() + (CARD.height() - arrow.getHeight()) / 2;
        arrow.draw(guiGraphics, arrowX, arrowY);
        guiGraphics.renderFakeItem(TRIGGER_ICONS.get(recipe.trigger()), 0, TRIGGER_Y);
        Component trigger = DropDisplayFormat.text("trigger." + recipe.trigger().id());
        guiGraphics.drawString(font, trigger, 19, TRIGGER_Y + 4, TEXT_COLOR, false);
    }

    /**
     * Returns the name of a mob type.
     *
     * @param target Mob type. Never null.
     * @return The translated name, such as "Zombie".
     */
    @Override
    protected Component nameOf(EntityType<?> target) {
        return target.getDescription();
    }

    /**
     * Creates the recipe type, whose class is the raw {@link DropRecipe} class.
     *
     * @return The recipe type of mob drops.
     */
    /**
     * Creates the icon of every trigger, drawn beside the trigger's name under the mob.
     *
     * @return An icon per trigger.
     */
    private static Map<DropTrigger, ItemStack> triggerIcons() {
        Map<DropTrigger, ItemStack> icons = new EnumMap<>(DropTrigger.class);
        icons.put(DropTrigger.DEATH, new ItemStack(Items.IRON_SWORD));
        icons.put(DropTrigger.SHEAR, new ItemStack(Items.SHEARS));
        icons.put(DropTrigger.HIT, new ItemStack(Items.WOODEN_SWORD));
        icons.put(DropTrigger.LIGHTNING, new ItemStack(Items.LIGHTNING_ROD));
        icons.put(DropTrigger.PERIODIC, new ItemStack(Items.CLOCK));
        return icons;
    }

    @SuppressWarnings("unchecked")
    private static RecipeType<DropRecipe<EntityType<?>>> createType() {
        return new RecipeType<>(new ResourceLocation(AlkahestDrops.ID, "mob_drops"),
                (Class<DropRecipe<EntityType<?>>>) (Class<?>) DropRecipe.class);
    }
}
