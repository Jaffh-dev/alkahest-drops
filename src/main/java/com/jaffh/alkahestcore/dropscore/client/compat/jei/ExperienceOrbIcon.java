package com.jaffh.alkahestcore.dropscore.client.compat.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the experience orb as the icon of experience drops (a bottle o' enchanting could be a real drop). The orb
 * texture is gray and tinted green to yellow over time, the same way vanilla renders orbs.
 */
final class ExperienceOrbIcon {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/experience_orb.png");
    private static final int TEXTURE_SIZE = 64;
    private static final int SPRITE_SIZE = 16;
    /** Sprite of a medium orb in the 4×4 sheet. */
    private static final int SPRITE_INDEX = 4;
    private static final float PULSE_SPEED = 0.005F;

    /**
     * Prevents instantiation of this utility class.
     */
    private ExperienceOrbIcon() {
    }

    /**
     * Draws the orb in a 16×16 area.
     *
     * @param guiGraphics Graphics of the recipe. Never null.
     * @param x           Left edge.
     * @param y           Top edge.
     */
    static void draw(GuiGraphics guiGraphics, int x, int y) {
        float time = (System.currentTimeMillis() % 100_000L) * PULSE_SPEED;
        float red = (Mth.sin(time) + 1.0F) * 0.5F;
        float blue = (Mth.sin(time + 4.1887903F) + 1.0F) * 0.1F;
        int u = (SPRITE_INDEX % 4) * SPRITE_SIZE;
        int v = (SPRITE_INDEX / 4) * SPRITE_SIZE;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(red, 1.0F, blue, 1.0F);
        guiGraphics.blit(TEXTURE, x, y, u, v, SPRITE_SIZE, SPRITE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
