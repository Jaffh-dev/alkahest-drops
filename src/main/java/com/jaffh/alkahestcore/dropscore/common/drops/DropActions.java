package com.jaffh.alkahestcore.dropscore.common.drops;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Represents what happens when an item drop spawns in the world ({@code on_drop}).
 *
 * @param shouldGlow  Whether the dropped item glows through walls.
 * @param runFunction Datapack function run as the dropped item, at its position, or null for none.
 */
public record DropActions(boolean shouldGlow, @Nullable ResourceLocation runFunction) {
    /** No action at all. */
    public static final DropActions NONE = new DropActions(false, null);

    /**
     * Tells whether at least one action is on.
     *
     * @return True when the drop glows or runs a function.
     */
    public boolean hasActions() {
        return shouldGlow || runFunction != null;
    }
}
