package com.jaffh.alkahestdrops.client.compat.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws mobs in a GUI the way the inventory screen draws the player (turned by two angle components), but with a
 * fractional scale and position, and measures the screen box a mob's model really covers. Measuring renders the model
 * into a buffer that only records vertex positions, so the box follows the model (beaks, necks, fins, wings,
 * tentacles), not the hitbox.
 */
final class MobModelRenderer {
    private static final int FULL_BRIGHT = 15728880;
    private static final float DEGREES_PER_COMPONENT = 20.0F;
    private static final float HEAD_TURN_FACTOR = 40.0F;
    /** Depth of the drawn mob in the GUI, in front of the recipe background. */
    private static final float GUI_DEPTH = 50.0F;
    /** Largest angle component the mouse can give: the inventory screen passes {@code atan(offset / 40)}. */
    static final float MAX_ANGLE = (float) (Math.PI / 2);

    /**
     * Prevents instantiation of this utility class.
     */
    private MobModelRenderer() {
    }

    /**
     * Draws a mob.
     *
     * @param guiGraphics Graphics of the screen. Never null.
     * @param originX     X of the mob's origin (feet), in GUI pixels.
     * @param originY     Y of the mob's origin (feet), in GUI pixels.
     * @param scale       Pixels per block, above 0.
     * @param angleX      Turn component: the body turns {@code 20°} and the head {@code 40°} per unit.
     * @param angleY      Tilt component: the model tilts {@code 20°} per unit.
     * @param entity      Mob to draw. Never null.
     */
    static void render(GuiGraphics guiGraphics, float originX, float originY, float scale, float angleX, float angleY, LivingEntity entity) {
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(originX, originY, GUI_DEPTH);
        pose.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
        Quaternionf tilt = tiltOf(angleY);
        pose.mulPose(new Quaternionf().rotateZ((float) Math.PI).mul(tilt));
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.overrideCameraOrientation(tilt.conjugate(new Quaternionf()));
        dispatcher.setRenderShadow(false);
        try {
            withTurn(entity, angleX, angleY, () -> RenderSystem.runAsFancy(() ->
                    dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, pose, guiGraphics.bufferSource(), FULL_BRIGHT)));
            guiGraphics.flush();
        } finally {
            dispatcher.setRenderShadow(true);
            pose.popPose();
            Lighting.setupFor3DItems();
        }
    }

    /**
     * Measures the box a mob's model covers, over several poses.
     *
     * @param entity  Mob to measure. Never null.
     * @param anglesX Turn components to measure. Never null, not empty.
     * @param anglesY Tilt components to measure. Never null, not empty.
     * @return The box that holds the model in every pose, or null when its renderer drew nothing measurable.
     */
    @Nullable
    static ModelBounds measure(LivingEntity entity, float[] anglesX, float[] anglesY) {
        Box box = new Box();
        // One recorder per render type, like a real buffer source: a layer that mixes two buffers (an enchanted item
        // and its glint) throws when both are the same object.
        Map<RenderType, VertexConsumer> recorders = new HashMap<>();
        MultiBufferSource buffers = type -> type == RenderType.lines()
                ? new BoundsRecorder(new Box())
                : recorders.computeIfAbsent(type, key -> new BoundsRecorder(box));
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            for (float angleX : anglesX) {
                for (float angleY : anglesY) {
                    PoseStack pose = new PoseStack();
                    pose.mulPose(new Quaternionf().rotateZ((float) Math.PI).mul(tiltOf(angleY)));
                    withTurn(entity, angleX, angleY, () ->
                            dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, pose, buffers, FULL_BRIGHT));
                }
            }
        } finally {
            dispatcher.setRenderShadow(true);
        }
        return box.bounds();
    }

    /**
     * Returns the tilt of the whole model for a tilt component.
     *
     * @param angleY Tilt component.
     * @return The rotation around the x axis.
     */
    private static Quaternionf tiltOf(float angleY) {
        return new Quaternionf().rotateX(angleY * DEGREES_PER_COMPONENT * ((float) Math.PI / 180.0F));
    }

    /**
     * Turns a mob's body and head for a draw, then puts them back.
     *
     * @param entity Mob. Never null.
     * @param angleX Turn component.
     * @param angleY Tilt component, which also tilts the head.
     * @param action Draw done while the mob is turned. Never null.
     */
    private static void withTurn(LivingEntity entity, float angleX, float angleY, Runnable action) {
        float bodyRot = entity.yBodyRot;
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        float headRotO = entity.yHeadRotO;
        float headRot = entity.yHeadRot;
        entity.yBodyRot = 180.0F + angleX * DEGREES_PER_COMPONENT;
        entity.setYRot(180.0F + angleX * HEAD_TURN_FACTOR);
        entity.setXRot(-angleY * DEGREES_PER_COMPONENT);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        try {
            action.run();
        } finally {
            entity.yBodyRot = bodyRot;
            entity.setYRot(yRot);
            entity.setXRot(xRot);
            entity.yHeadRotO = headRotO;
            entity.yHeadRot = headRot;
        }
    }

    /**
     * Holds the smallest screen box around the vertices recorded into it.
     */
    private static final class Box {
        private float minX = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;

        /**
         * Grows the box to hold a point.
         *
         * @param x X.
         * @param y Y.
         */
        void include(float x, float y) {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        /**
         * Returns the recorded box.
         *
         * @return The box, or null when no vertex with an area was recorded.
         */
        @Nullable
        ModelBounds bounds() {
            if (!(maxX > minX) || !(maxY > minY)) return null;
            return new ModelBounds(minX, maxX, minY, maxY);
        }
    }

    /**
         * Records the position of every vertex it receives into a box, and draws nothing.
         */
        private record BoundsRecorder(Box box) implements VertexConsumer {
        /**
         * Creates a recorder.
         *
         * @param box Box the vertices are recorded into. Never null.
         */
        private BoundsRecorder {
        }

            /**
             * Records the position of a vertex, already transformed by the pose.
             *
             * @param x X.
             * @param y Y.
             * @param z Z (unused: the depth does not show on screen).
             * @return This recorder.
             */
            @Override
            public VertexConsumer vertex(double x, double y, double z) {
                box.include((float) x, (float) y);
                return this;
            }

            /**
             * Ignores the color.
             *
             * @param red   Red.
             * @param green Green.
             * @param blue  Blue.
             * @param alpha Alpha.
             * @return This recorder.
             */
            @Override
            public VertexConsumer color(int red, int green, int blue, int alpha) {
                return this;
            }

            /**
             * Ignores the texture coordinates.
             *
             * @param u U.
             * @param v V.
             * @return This recorder.
             */
            @Override
            public VertexConsumer uv(float u, float v) {
                return this;
            }

            /**
             * Ignores the overlay coordinates.
             *
             * @param u U.
             * @param v V.
             * @return This recorder.
             */
            @Override
            public VertexConsumer overlayCoords(int u, int v) {
                return this;
            }

            /**
             * Ignores the light coordinates.
             *
             * @param u U.
             * @param v V.
             * @return This recorder.
             */
            @Override
            public VertexConsumer uv2(int u, int v) {
                return this;
            }

            /**
             * Ignores the normal.
             *
             * @param x X.
             * @param y Y.
             * @param z Z.
             * @return This recorder.
             */
            @Override
            public VertexConsumer normal(float x, float y, float z) {
                return this;
            }

            /**
             * Ends a vertex; nothing to do.
             */
            @Override
            public void endVertex() {
            }

            /**
             * Ignores the default color.
             *
             * @param red   Red.
             * @param green Green.
             * @param blue  Blue.
             * @param alpha Alpha.
             */
            @Override
            public void defaultColor(int red, int green, int blue, int alpha) {
            }

            /**
             * Ignores the default color.
             */
            @Override
            public void unsetDefaultColor() {
            }
        }
}
