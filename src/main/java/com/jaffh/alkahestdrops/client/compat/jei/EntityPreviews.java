package com.jaffh.alkahestdrops.client.compat.jei;

import com.mojang.logging.LogUtils;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Creates, measures and draws the mobs shown in JEI: on the mob drop card (following the mouse) and as ingredient
 * icons. Each mob is scaled and centered by the box its model really covers ({@link MobModelRenderer#measure}), so
 * small mobs with a large model, flying and swimming mobs drawn away from their hitbox, and long mobs all fit the same
 * way. One mob per type is created and measured on first draw and reused; a type that cannot be created or drawn
 * (some modded mobs need a server) is remembered and never tried again. Used on the render thread only.
 */
final class EntityPreviews {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Turns measured for the card: the mouse can turn the mob up to {@link MobModelRenderer#MAX_ANGLE} either way. */
    private static final float[] CARD_TURNS = {
        -MobModelRenderer.MAX_ANGLE,
        -MobModelRenderer.MAX_ANGLE / 2,
        0.0F,
        MobModelRenderer.MAX_ANGLE / 2,
        MobModelRenderer.MAX_ANGLE
    };
    /** Tilts measured for the card, up and down. */
    private static final float[] CARD_TILTS = {-MobModelRenderer.MAX_ANGLE, 0.0F, MobModelRenderer.MAX_ANGLE};
    /** Turn of the ingredient icons, which do not follow the mouse. */
    private static final float ICON_TURN = 1.0F;
    /** Room, relative to the hitbox width, that a mob that cannot be measured gets for its turn and fins. */
    private static final float FALLBACK_TURN_WIDTH = 1.4F;

    private static final int CARD_MARGIN = 3;
    private static final float MOUSE_ANGLE_DIVISOR = 40.0F;

    private final Map<EntityType<?>, Optional<Preview>> previews = new IdentityHashMap<>();

    @Nullable
    private Level level;

    /**
     * Draws a mob centered in a box, turned towards the mouse, scaled so its model fits the box in every turn.
     * Anything still outside the box is cut off.
     *
     * @param guiGraphics Graphics of the recipe. Never null.
     * @param type        Type of the mob. Never null.
     * @param area        Box to draw the mob in, relative to the recipe. Never null.
     * @param mouseX      Mouse x relative to the recipe.
     * @param mouseY      Mouse y relative to the recipe.
     * @return False when the mob cannot be shown.
     */
    boolean draw(GuiGraphics guiGraphics, EntityType<?> type, PreviewArea area, double mouseX, double mouseY) {
        Preview preview = previewOf(type);
        if (preview == null) return false;
        float centerX = area.x() + area.width() / 2.0F;
        float centerY = area.y() + area.height() / 2.0F;
        float angleX = (float) Math.atan((centerX - mouseX) / MOUSE_ANGLE_DIVISOR);
        float angleY = (float) Math.atan((centerY - mouseY) / MOUSE_ANGLE_DIVISOR);

        enableClip(guiGraphics, area.x(), area.y(), area.width(), area.height());
        try {
            drawFitted(
                    guiGraphics,
                    preview.entity(),
                    preview.cardBounds(),
                    centerX,
                    centerY,
                    area.width() - 2 * CARD_MARGIN,
                    area.height() - 2 * CARD_MARGIN,
                    angleX,
                    angleY);
            return true;
        } catch (RuntimeException e) {
            return fail(type, e);
        } finally {
            guiGraphics.disableScissor();
        }
    }

    /**
     * Draws a mob as a small icon, slightly turned, centered and scaled to fit a square. Anything outside the square
     * is cut off, so a modded mob that draws outside its measured box never covers the next ingredient.
     *
     * @param guiGraphics Graphics, translated to the icon. Never null.
     * @param type        Type of the mob. Never null.
     * @param size        Width and height of the square, in pixels.
     * @return False when the mob cannot be shown.
     */
    boolean drawIcon(GuiGraphics guiGraphics, EntityType<?> type, int size) {
        Preview preview = previewOf(type);
        if (preview == null) return false;
        enableClip(guiGraphics, 0, 0, size, size);
        try {
            drawFitted(
                    guiGraphics,
                    preview.entity(),
                    preview.iconBounds(),
                    size / 2.0F,
                    size / 2.0F,
                    size,
                    size,
                    ICON_TURN,
                    0.0F);
            return true;
        } catch (RuntimeException e) {
            return fail(type, e);
        } finally {
            guiGraphics.disableScissor();
        }
    }

    /**
     * Starts cutting off everything drawn outside a box, until {@link GuiGraphics#disableScissor()}. The scissor works
     * in screen coordinates, so the box is moved and scaled by the current pose (the recipe's or the ingredient
     * list's position); it is intersected with any scissor already active.
     *
     * @param guiGraphics Graphics. Never null.
     * @param x           Left edge of the box, in the current pose.
     * @param y           Top edge of the box, in the current pose.
     * @param width       Width of the box, in the current pose.
     * @param height      Height of the box, in the current pose.
     */
    private static void enableClip(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        Matrix4f pose = guiGraphics.pose().last().pose();
        int left = (int) Math.floor(pose.m30() + pose.m00() * x);
        int top = (int) Math.floor(pose.m31() + pose.m11() * y);
        int right = (int) Math.ceil(pose.m30() + pose.m00() * (x + width));
        int bottom = (int) Math.ceil(pose.m31() + pose.m11() * (y + height));
        guiGraphics.enableScissor(left, top, right, bottom);
    }

    /**
     * Forgets every mob, so none keeps a level that was left.
     */
    void clear() {
        previews.clear();
        level = null;
    }

    /**
     * Draws a mob so its measured box fills an area around a center.
     *
     * @param guiGraphics Graphics. Never null.
     * @param entity      Mob. Never null.
     * @param bounds      Measured box of the mob. Never null.
     * @param centerX     Center x of the area.
     * @param centerY     Center y of the area.
     * @param width       Width of the area.
     * @param height      Height of the area.
     * @param angleX      Turn component.
     * @param angleY      Tilt component.
     */
    private static void drawFitted(
            GuiGraphics guiGraphics,
            LivingEntity entity,
            ModelBounds bounds,
            float centerX,
            float centerY,
            float width,
            float height,
            float angleX,
            float angleY) {
        float scale = bounds.scaleToFit(width, height);
        float originX = centerX - scale * bounds.centerX();
        float originY = centerY - scale * bounds.centerY();
        MobModelRenderer.render(guiGraphics, originX, originY, scale, angleX, angleY, entity);
    }

    /**
     * Remembers that a mob cannot be drawn.
     *
     * @param type Type of the mob. Never null.
     * @param e    Error of the draw. Never null.
     * @return Always false.
     */
    private boolean fail(EntityType<?> type, RuntimeException e) {
        LOGGER.warn(
                "[alkahestdrops] Cannot draw {} in JEI, showing its name or spawn egg instead.",
                EntityType.getKey(type),
                e);
        previews.put(type, Optional.empty());
        return false;
    }

    /**
     * Returns the reusable, measured mob of a type, created in the current client level.
     *
     * @param type Type of the mob. Never null.
     * @return The preview, or null without a level or when the type cannot be created as a living entity.
     */
    @Nullable
    private Preview previewOf(EntityType<?> type) {
        Level current = Minecraft.getInstance().level;
        if (current == null) return null;
        if (current != level) {
            previews.clear();
            level = current;
        }
        return previews.computeIfAbsent(type, key -> create(key, current)).orElse(null);
    }

    /**
     * Creates and measures a mob for display.
     *
     * @param type  Type of the mob. Never null.
     * @param level Client level. Never null.
     * @return The preview, or empty when the type cannot be created as a living entity.
     */
    private static Optional<Preview> create(EntityType<?> type, Level level) {
        try {
            Entity entity = type.create(level);
            if (!(entity instanceof LivingEntity living)) return Optional.empty();
            ModelBounds card = measureOrHitbox(living, CARD_TURNS, CARD_TILTS);
            ModelBounds icon = measureOrHitbox(living, new float[] {ICON_TURN}, new float[] {0.0F});
            return Optional.of(new Preview(living, card, icon));
        } catch (RuntimeException e) {
            LOGGER.warn(
                    "[alkahestdrops] Cannot create {} for JEI, showing its name or spawn egg instead.",
                    EntityType.getKey(type),
                    e);
            return Optional.empty();
        }
    }

    /**
     * Measures a mob's model, or falls back to its hitbox when its renderer draws nothing measurable.
     *
     * @param entity Mob. Never null.
     * @param turns  Turn components to measure. Never null.
     * @param tilts  Tilt components to measure. Never null.
     * @return The box of the model, or of the hitbox (widened for the turn) standing on the origin.
     */
    private static ModelBounds measureOrHitbox(LivingEntity entity, float[] turns, float[] tilts) {
        ModelBounds measured = MobModelRenderer.measure(entity, turns, tilts);
        if (measured != null) return measured;
        float halfWidth = Math.max(entity.getBbWidth(), 0.1F) * FALLBACK_TURN_WIDTH / 2.0F;
        return new ModelBounds(-halfWidth, halfWidth, -Math.max(entity.getBbHeight(), 0.1F), 0.0F);
    }

    /**
     * Represents a mob ready to draw.
     *
     * @param entity     The mob, never added to the level. Never null.
     * @param cardBounds Box its model covers in every turn and tilt the card can show. Never null.
     * @param iconBounds Box its model covers in the icon's turn. Never null.
     */
    private record Preview(LivingEntity entity, ModelBounds cardBounds, ModelBounds iconBounds) {}

    /**
     * Represents the box a mob is drawn in.
     *
     * @param x      Left edge, relative to the recipe.
     * @param y      Top edge, relative to the recipe.
     * @param width  Width in pixels, above 0.
     * @param height Height in pixels, above 0.
     */
    record PreviewArea(int x, int y, int width, int height) {}
}
