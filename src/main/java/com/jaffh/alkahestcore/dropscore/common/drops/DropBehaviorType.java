package com.jaffh.alkahestcore.dropscore.common.drops;

import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * Defines how the final count of a drop is rolled inside its count range.
 */
public enum DropBehaviorType {
    /** Picks a count uniformly between min and max. */
    UNIFORM(false) {
        @Override
        public int rollCount(DropCount range, DropBehavior behavior, Random random) {
            if (range.min() >= range.max()) return range.min();
            return range.min() + random.nextInt(range.max() - range.min() + 1);
        }
    },
    /** Starts at min and makes every roll independently, adding the increment on each success. */
    FIXED_ROLLS(true) {
        @Override
        public int rollCount(DropCount range, DropBehavior behavior, Random random) {
            int total = range.min();
            for (int i = 0; i < behavior.rolls() && total < range.max(); i++) {
                if (random.nextDouble() < behavior.chancePerRoll()) {
                    total += behavior.incrementValue();
                }
            }
            return Math.min(total, range.max());
        }
    },
    /** Starts at min and keeps rolling while rolls succeed, stopping at the first failure. */
    CHAINED_ROLLS(true) {
        @Override
        public int rollCount(DropCount range, DropBehavior behavior, Random random) {
            int total = range.min();
            for (int i = 0; i < behavior.rolls() && total < range.max(); i++) {
                if (random.nextDouble() >= behavior.chancePerRoll()) break;
                total += behavior.incrementValue();
            }
            return Math.min(total, range.max());
        }
    };

    private final boolean needsRollSettings;

    /**
     * Creates a behavior type.
     *
     * @param needsRollSettings Whether the type reads {@code rolls}, {@code chance_per_roll} and {@code increment_value}.
     */
    DropBehaviorType(boolean needsRollSettings) {
        this.needsRollSettings = needsRollSettings;
    }

    /**
     * Tells whether this type reads the roll settings and the {@code bonus_roll} bonus.
     *
     * @return True for the roll-based types.
     */
    public boolean needsRollSettings() {
        return needsRollSettings;
    }

    /**
     * Rolls the item count of one drop.
     *
     * @param range     Count range with any enchantment bonus already applied. Never null.
     * @param behavior Roll settings with any enchantment bonus already applied. Never null.
     * @param random    Random source of the level.
     * @return The rolled count, between {@code range.min()} and {@code range.max()} when min ≤ max.
     */
    public abstract int rollCount(DropCount range, DropBehavior behavior, Random random);

    /**
     * Finds the behavior type named by a JSON id such as {@code "fixed_rolls"}.
     *
     * @param id JSON id, case-insensitive. Never null.
     * @return The matching type, or empty when the id is unknown.
     */
    public static Optional<DropBehaviorType> fromId(String id) {
        try {
            return Optional.of(valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
