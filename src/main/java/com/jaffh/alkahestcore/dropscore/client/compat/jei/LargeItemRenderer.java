package com.jaffh.alkahestcore.dropscore.client.compat.jei;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Draws an item stack at twice the normal size, for the block card of the block drop category. JEI sizes the slot
 * (hover area and highlight) from this renderer.
 */
final class LargeItemRenderer implements IIngredientRenderer<ItemStack> {
    /** Width and height of the drawn item. */
    static final int SIZE = 32;
    private static final float SCALE = SIZE / 16.0F;

    /**
     * Draws the item.
     *
     * @param guiGraphics Graphics, translated to the slot. Never null.
     * @param stack       Item to draw. Never null.
     */
    @Override
    public void render(GuiGraphics guiGraphics, ItemStack stack) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(SCALE, SCALE, 1.0F);
        guiGraphics.renderFakeItem(stack, 0, 0);
        guiGraphics.pose().popPose();
    }

    /**
     * Returns the item's own tooltip.
     *
     * @param stack       Item. Never null.
     * @param tooltipFlag Normal or advanced tooltips. Never null.
     * @return The tooltip lines.
     * @deprecated Kept because JEI 15 still declares it abstract; JEI calls the overload with a player.
     */
    @Deprecated
    @Override
    public List<Component> getTooltip(ItemStack stack, TooltipFlag tooltipFlag) {
        return getTooltip(stack, Minecraft.getInstance().player, tooltipFlag);
    }

    /**
     * Returns the item's own tooltip.
     *
     * @param stack       Item. Never null.
     * @param player      Viewing player, or null.
     * @param tooltipFlag Normal or advanced tooltips. Never null.
     * @return The tooltip lines.
     */
    @Override
    public List<Component> getTooltip(ItemStack stack, @Nullable Player player, TooltipFlag tooltipFlag) {
        return stack.getTooltipLines(player, tooltipFlag);
    }

    /**
     * Returns the width of the slot.
     *
     * @return {@link #SIZE}.
     */
    @Override
    public int getWidth() {
        return SIZE;
    }

    /**
     * Returns the height of the slot.
     *
     * @return {@link #SIZE}.
     */
    @Override
    public int getHeight() {
        return SIZE;
    }
}
