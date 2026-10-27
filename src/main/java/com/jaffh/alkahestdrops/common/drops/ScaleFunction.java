package com.jaffh.alkahestdrops.common.drops;

import java.util.Locale;
import java.util.Optional;

/**
 * Defines how the scaled behaviors ({@code max_health}, {@code hardness}) turn the target's scaled value into a count.
 */
public enum ScaleFunction {
    /** Uses the scaled value as is. */
    LINEAR {
        @Override
        public double apply(double scaledValue) {
            return scaledValue;
        }
    },
    /** Uses the natural logarithm of the scaled value, so strong targets do not drop huge stacks. */
    LOG {
        @Override
        public double apply(double scaledValue) {
            return Math.log(scaledValue);
        }
    };

    /**
     * Applies the function.
     *
     * @param scaledValue Target value divided by the divisor, above 0.
     * @return The function value; may be negative for small inputs, the caller clamps it.
     */
    public abstract double apply(double scaledValue);

    /**
     * Finds the function named by a JSON id such as {@code "log"}.
     *
     * @param id JSON id, case-insensitive. Never null.
     * @return The matching function, or empty when the id is unknown.
     */
    public static Optional<ScaleFunction> fromId(String id) {
        try {
            return Optional.of(valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
