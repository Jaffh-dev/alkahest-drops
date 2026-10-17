package com.jaffh.alkahestcore.dropscore.server.drops;

import java.util.Locale;

/**
 * Defines the conditions a simulation can force, so drops gated by them can be tested from chat.
 */
public enum SimulationFlag {
    /** The killer or breaker crouches ({@code is_sneaking}). Needs a player or an entity as actor. */
    SNEAKING,
    /** The dying mob is on fire ({@code is_on_fire}). Mob kills only. */
    BURNING;

    /**
     * Returns the command keyword of this flag.
     *
     * @return The lower-case name, such as {@code sneaking}.
     */
    public String keyword() {
        return name().toLowerCase(Locale.ROOT);
    }
}
