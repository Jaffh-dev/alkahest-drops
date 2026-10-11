package com.jaffh.alkahestcore.dropscore.common.drops;

import java.util.Locale;
import java.util.Optional;

/**
 * Defines how the {@code max_health} behavior turns a mob's scaled max health into a count.
 */
public enum HealthFunction {
    /** Uses the scaled health as is. */
    LINEAR {
        @Override
        public double apply(double scaledHealth) {
            return scaledHealth;
        }
    },
    /** Uses the natural logarithm of the scaled health, so strong mobs do not drop huge stacks. */
    LOG {
        @Override
        public double apply(double scaledHealth) {
            return Math.log(scaledHealth);
        }
    };

    /**
     * Applies the function.
     *
     * @param scaledHealth Max health divided by the divisor.
     * @return The function value; may be negative or infinite for small inputs, the caller clamps it.
     */
    public abstract double apply(double scaledHealth);

    /**
     * Finds the function named by a JSON id such as {@code "log"}.
     *
     * @param id JSON id, case-insensitive. Never null.
     * @return The matching function, or empty when the id is unknown.
     */
    public static Optional<HealthFunction> fromId(String id) {
        try {
            return Optional.of(valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
