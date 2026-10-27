package com.jaffh.alkahestdrops.common.drops;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Optional;

/**
 * Defines what makes a mob drop: its death (the default) or an action on the living mob. Block drops always drop on
 * break.
 */
public enum DropTrigger {
    /** The mob dies. */
    DEATH(true, true, null, 0, 0),
    /** A player shears the mob. */
    SHEAR(true, true, null, 0, 0),
    /** A player hurts the mob; {@code cooldown} ticks must pass between two drops of the same mob. */
    HIT(true, true, "cooldown", 0, 0),
    /** Lightning strikes the mob, once per bolt. */
    LIGHTNING(false, false, null, 0, 0),
    /** Every {@code interval} ticks while the mob is alive. */
    PERIODIC(false, false, "interval", 6000, 1);

    private final boolean hasPlayer;
    private final boolean hasEnchantBonus;
    @Nullable
    private final String ticksKey;
    private final int defaultTicks;
    private final int minTicks;

    /**
     * Creates a trigger.
     *
     * @param hasPlayer       Whether a player can cause the drop, so pity and one-time drops have someone to count for.
     * @param hasEnchantBonus Whether the drop reads the enchantments of the held item: killing, hitting and shearing
     *                        use one.
     * @param ticksKey        JSON key of the trigger's tick setting, or null when it has none.
     * @param defaultTicks    Value of the tick setting when the key is missing.
     * @param minTicks        Lowest valid value of the tick setting.
     */
    DropTrigger(boolean hasPlayer, boolean hasEnchantBonus, @Nullable String ticksKey, int defaultTicks, int minTicks) {
        this.hasPlayer = hasPlayer;
        this.hasEnchantBonus = hasEnchantBonus;
        this.ticksKey = ticksKey;
        this.defaultTicks = defaultTicks;
        this.minTicks = minTicks;
    }

    /**
     * Finds a trigger by its JSON id.
     *
     * @param id Id as written in {@code on}, such as {@code shear}. Never null.
     * @return The trigger, or empty when the id is unknown.
     */
    public static Optional<DropTrigger> fromId(String id) {
        for (DropTrigger trigger : values()) {
            if (trigger.id().equals(id)) return Optional.of(trigger);
        }
        return Optional.empty();
    }

    /**
     * Returns the JSON id of this trigger.
     *
     * @return Such as {@code death} or {@code periodic}.
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Tells whether a player can cause this trigger.
     *
     * @return True for death, shear and hit.
     */
    public boolean hasPlayer() {
        return hasPlayer;
    }

    /**
     * Tells whether drops of this trigger read the enchantments of the held item, so {@code enchant_bonus} applies.
     *
     * @return True for death, hit and shear.
     */
    public boolean hasEnchantBonus() {
        return hasEnchantBonus;
    }

    /**
     * Returns the JSON key of this trigger's tick setting.
     *
     * @return {@code cooldown} for hit, {@code interval} for periodic, or null.
     */
    @Nullable
    public String ticksKey() {
        return ticksKey;
    }

    /**
     * Returns the tick setting used when its key is missing.
     *
     * @return 6000 for periodic, 0 otherwise.
     */
    public int defaultTicks() {
        return defaultTicks;
    }

    /**
     * Returns the lowest valid value of the tick setting.
     *
     * @return 1 for periodic, 0 otherwise.
     */
    public int minTicks() {
        return minTicks;
    }
}
