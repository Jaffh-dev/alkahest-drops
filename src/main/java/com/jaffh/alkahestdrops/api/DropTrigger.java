package com.jaffh.alkahestdrops.api;

import java.util.Locale;
import java.util.Optional;

/**
 * Lists what can make a drop happen: the {@code "on"} key of a drop. Blocks only use {@link #DEATH} (a break).
 *
 * @since 1.0.0
 */
public enum DropTrigger {
    /** The mob dies, or the block is broken. The default. */
    DEATH(true),
    /** A player shears the mob with shears. */
    SHEAR(true),
    /** A player hurts the mob. */
    HIT(true),
    /** Lightning strikes the mob. */
    LIGHTNING(false),
    /** Time passes while the mob is alive. */
    PERIODIC(false);

    private final boolean hasPlayer;

    /**
     * Creates a trigger.
     *
     * @param hasPlayer Whether a player can cause the trigger.
     */
    DropTrigger(boolean hasPlayer) {
        this.hasPlayer = hasPlayer;
    }

    /**
     * Finds a trigger by its id.
     *
     * @param id Id as written in drop files, such as {@code "shear"}. Never null.
     * @return The trigger, or empty when no trigger has that id.
     * @since 1.0.0
     */
    public static Optional<DropTrigger> fromId(String id) {
        for (DropTrigger trigger : values()) {
            if (trigger.id().equals(id)) return Optional.of(trigger);
        }
        return Optional.empty();
    }

    /**
     * Returns the id of this trigger, as written in drop files.
     *
     * @return Such as {@code "death"} or {@code "periodic"}.
     * @since 1.0.0
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Tells whether a player can cause this trigger, so its drops may have a player.
     *
     * @return True for death, shear and hit; false for lightning and periodic drops, which never have a player.
     * @since 1.0.0
     */
    public boolean hasPlayer() {
        return hasPlayer;
    }
}
