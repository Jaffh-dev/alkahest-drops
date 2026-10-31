package com.jaffh.alkahestdrops.client.compat.jei;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.ForgeSpawnEggItem;

/**
 * Draws {@link MobIngredient}s: as a small 3D model in JEI's ingredient list, or as nothing over the mob card of a
 * drop page, where the category already draws the mob following the mouse (the slot only makes the card clickable).
 */
final class MobIngredientRenderer implements IIngredientRenderer<MobIngredient> {
    private static final int ICON_SIZE = 16;

    private final EntityPreviews previews;
    private final int width;
    private final int height;
    private final boolean shouldDraw;

    /**
     * Creates a renderer.
     *
     * @param previews   Shared mob previews. Never null.
     * @param width      Width of the slot, in pixels.
     * @param height     Height of the slot, in pixels.
     * @param shouldDraw Whether the mob is drawn, or only its slot area is used.
     */
    private MobIngredientRenderer(EntityPreviews previews, int width, int height, boolean shouldDraw) {
        this.previews = previews;
        this.width = width;
        this.height = height;
        this.shouldDraw = shouldDraw;
    }

    /**
     * Creates the renderer of the ingredient list: a 16×16 model, or the spawn egg when the mob cannot be drawn.
     *
     * @param previews Shared mob previews. Never null.
     * @return The renderer.
     */
    static MobIngredientRenderer icon(EntityPreviews previews) {
        return new MobIngredientRenderer(previews, ICON_SIZE, ICON_SIZE, true);
    }

    /**
     * Creates the renderer of the mob card's slot, which draws nothing.
     *
     * @param previews Shared mob previews. Never null.
     * @param width    Width of the card's inside, in pixels.
     * @param height   Height of the card's inside, in pixels.
     * @return The renderer.
     */
    static MobIngredientRenderer card(EntityPreviews previews, int width, int height) {
        return new MobIngredientRenderer(previews, width, height, false);
    }

    /**
     * Draws the mob, or its spawn egg when it cannot be drawn.
     *
     * @param guiGraphics Graphics, translated to the slot. Never null.
     * @param ingredient  Mob. Never null.
     */
    @Override
    public void render(GuiGraphics guiGraphics, MobIngredient ingredient) {
        if (!shouldDraw || previews.drawIcon(guiGraphics, ingredient.type(), ICON_SIZE)) return;
        SpawnEggItem egg = ForgeSpawnEggItem.fromEntityType(ingredient.type());
        if (egg != null) guiGraphics.renderFakeItem(new ItemStack(egg), 0, 0);
    }

    /**
     * Returns the tooltip of a mob.
     *
     * @param ingredient  Mob. Never null.
     * @param tooltipFlag Normal or advanced tooltips. Never null.
     * @return The tooltip lines.
     * @deprecated Kept because JEI 15 still declares it abstract; JEI calls the overload with a player.
     */
    @Deprecated
    @Override
    public List<Component> getTooltip(MobIngredient ingredient, TooltipFlag tooltipFlag) {
        return getTooltip(ingredient, Minecraft.getInstance().player, tooltipFlag);
    }

    /**
     * Returns the tooltip of a mob: its name, and its id with advanced tooltips (F3+H).
     *
     * @param ingredient  Mob. Never null.
     * @param player      Viewing player, or null.
     * @param tooltipFlag Normal or advanced tooltips. Never null.
     * @return The tooltip lines.
     */
    @Override
    public List<Component> getTooltip(MobIngredient ingredient, @Nullable Player player, TooltipFlag tooltipFlag) {
        List<Component> lines = new ArrayList<>();
        lines.add(ingredient.type().getDescription());
        if (tooltipFlag.isAdvanced())
            lines.add(Component.literal(ingredient.id().toString()).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /**
     * Returns the width of the slot.
     *
     * @return The width in pixels.
     */
    @Override
    public int getWidth() {
        return width;
    }

    /**
     * Returns the height of the slot.
     *
     * @return The height in pixels.
     */
    @Override
    public int getHeight() {
        return height;
    }
}
