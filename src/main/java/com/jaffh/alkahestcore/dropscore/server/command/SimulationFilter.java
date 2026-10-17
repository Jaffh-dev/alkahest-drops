package com.jaffh.alkahestcore.dropscore.server.command;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Represents what a simulation shows (and drops with {@code drop}). The filter never changes what is rolled, only
 * what is counted afterward.
 *
 * @param label           Filter as the player typed it, or null when everything is shown.
 * @param matchesItem     Tells whether a dropped stack is shown. Never null.
 * @param showsExperience Whether the experience is shown.
 */
public record SimulationFilter(@Nullable String label, Predicate<ItemStack> matchesItem, boolean showsExperience) {
    /** Shows every item and the experience. */
    public static final SimulationFilter ALL = new SimulationFilter(null, stack -> true, true);
    /** Shows only the experience. */
    public static final SimulationFilter EXPERIENCE = new SimulationFilter("experience", stack -> false, true);

    /**
     * Creates a filter that shows only the stacks an item predicate matches.
     *
     * @param label     Filter as the player typed it, such as {@code #minecraft:logs}. Never null.
     * @param predicate Item predicate of the filter. Never null.
     * @return The filter.
     */
    public static SimulationFilter ofItems(String label, Predicate<ItemStack> predicate) {
        return new SimulationFilter(label, predicate, false);
    }

    /**
     * Tells whether this filter hides anything.
     *
     * @return True unless this is {@link #ALL}.
     */
    public boolean isFiltered() {
        return label != null;
    }
}
