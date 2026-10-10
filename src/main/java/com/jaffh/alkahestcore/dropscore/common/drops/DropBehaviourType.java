package com.jaffh.alkahestcore.dropscore.common.drops;

import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * Defines how the final count of a drop is rolled inside its count range.
 */
public enum DropBehaviourType {
    /** Picks a count uniformly between min and max. */
    UNIFORM {
        @Override
        public int rollCount(DropCount range, DropBehaviour behaviour, Random random) {
            if (range.min() >= range.max()) return range.min();
            return range.min() + random.nextInt(range.max() - range.min() + 1);
        }
    },
    /** Starts at min and makes every roll independently, adding the increment on each success. */
    FIXED_ROLLS {
        @Override
        public int rollCount(DropCount range, DropBehaviour behaviour, Random random) {
            int total = range.min();
            for (int i = 0; i < behaviour.rolls() && total < range.max(); i++) {
                if (random.nextDouble() < behaviour.chancePerRoll()) {
                    total += behaviour.incrementValue();
                }
            }
            return Math.min(total, range.max());
        }
    },
    /** Starts at min and keeps rolling while rolls succeed, stopping at the first failure. */
    CHAINED_ROLLS {
        @Override
        public int rollCount(DropCount range, DropBehaviour behaviour, Random random) {
            int total = range.min();
            for (int i = 0; i < behaviour.rolls() && total < range.max(); i++) {
                if (random.nextDouble() >= behaviour.chancePerRoll()) break;
                total += behaviour.incrementValue();
            }
            return Math.min(total, range.max());
        }
    };

    /**
     * Rolls the item count of one drop.
     *
     * @param range     Count range with any enchantment bonus already applied. Never null.
     * @param behaviour Roll settings with any enchantment bonus already applied. Never null.
     * @param random    Random source of the level.
     * @return The rolled count, between {@code range.min()} and {@code range.max()} when min ≤ max.
     */
    public abstract int rollCount(DropCount range, DropBehaviour behaviour, Random random);

    /**
     * Finds the behavior type named by a JSON id such as {@code "fixed_rolls"}.
     *
     * @param id JSON id, case-insensitive. Never null.
     * @return The matching type, or empty when the id is unknown.
     */
    public static Optional<DropBehaviourType> fromId(String id) {
        try {
            return Optional.of(valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
