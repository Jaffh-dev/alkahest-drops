package com.jaffh.alkahestdrops.common.drops;

import net.minecraft.util.RandomSource;

import java.util.Locale;
import java.util.Optional;

/**
 * Defines how the final count of a drop is rolled inside its count range.
 */
public enum DropBehaviorType {
    /** Picks a count uniformly between min and max. */
    UNIFORM(false, false) {
        @Override
        public int rollCount(DropCount range, DropBehavior behavior, float scaleValue, RandomSource random) {
            if (range.min() >= range.max()) return range.min();
            return range.min() + random.nextInt(range.max() - range.min() + 1);
        }
    },
    /** Starts at min and makes every roll independently, adding the increment on each success. */
    FIXED_ROLLS(true, false) {
        @Override
        public int rollCount(DropCount range, DropBehavior behavior, float scaleValue, RandomSource random) {
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
    CHAINED_ROLLS(true, false) {
        @Override
        public int rollCount(DropCount range, DropBehavior behavior, float scaleValue, RandomSource random) {
            int total = range.min();
            for (int i = 0; i < behavior.rolls() && total < range.max(); i++) {
                if (random.nextDouble() >= behavior.chancePerRoll()) break;
                total += behavior.incrementValue();
            }
            return Math.min(total, range.max());
        }
    },
    /** Scales the count with the mob's max health, clamped to the count range. Mobs only. */
    MAX_HEALTH(false, true) {
        @Override
        public int rollCount(DropCount range, DropBehavior behavior, float scaleValue, RandomSource random) {
            return behavior.scaleFormula().countFor(scaleValue, range);
        }
    },
    /** Scales the count with the block's hardness, clamped to the count range. Blocks only. */
    HARDNESS(false, true) {
        @Override
        public int rollCount(DropCount range, DropBehavior behavior, float scaleValue, RandomSource random) {
            return behavior.scaleFormula().countFor(scaleValue, range);
        }
    },
    /**
     * Count from the damage the mob took from a hit, after armor, Resistance and absorption and capped at the health
     * it had, with the {@code max_health} formula; mob hit drops only. No damage drops nothing, whatever
     * {@code count.min} is.
     */
    DAMAGE_TAKEN(false, true) {
        @Override
        public int rollCount(DropCount range, DropBehavior behavior, float scaleValue, RandomSource random) {
            if (scaleValue <= 0) return 0;
            return behavior.scaleFormula().countFor(scaleValue, range);
        }

        @Override
        public boolean readsDamageTaken() {
            return true;
        }
    };

    private final boolean needsRollSettings;
    private final boolean isScaled;

    /**
     * Creates a behavior type.
     *
     * @param needsRollSettings Whether the type reads {@code rolls}, {@code chance_per_roll} and {@code increment_value}.
     * @param isScaled          Whether the type reads {@code function}, {@code multiplier} and {@code divisor}.
     */
    DropBehaviorType(boolean needsRollSettings, boolean isScaled) {
        this.needsRollSettings = needsRollSettings;
        this.isScaled = isScaled;
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
     * Tells whether this type scales the count with a value of the target, using a {@link ScaleFormula}.
     *
     * @return True for {@code max_health} and {@code hardness}.
     */
    public boolean isScaled() {
        return isScaled;
    }

    /**
     * Tells whether this type reads the damage taken instead of the max health or hardness.
     *
     * @return True for {@link #DAMAGE_TAKEN}.
     */
    public boolean readsDamageTaken() {
        return false;
    }

    public abstract int rollCount(DropCount range, DropBehavior behavior, float scaleValue, RandomSource random);

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
