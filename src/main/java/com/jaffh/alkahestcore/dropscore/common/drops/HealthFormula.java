package com.jaffh.alkahestcore.dropscore.common.drops;

/**
 * Represents the settings of the {@code max_health} behavior:
 * {@code count = round(multiplier · function(maxHealth / divisor))}, clamped by the drop's count range.
 *
 * @param function   Function applied to the scaled health. Never null.
 * @param multiplier Factor applied to the function value, above 0.
 * @param divisor    Divisor of the max health, above 0.
 */
public record HealthFormula(HealthFunction function, double multiplier, double divisor) {
    /** The defaults: linear, multiplier 1, divisor 1. */
    public static final HealthFormula DEFAULT = new HealthFormula(HealthFunction.LINEAR, 1.0, 1.0);

    /**
     * Computes the count for a max health, clamped to a range.
     *
     * @param maxHealth Max health of the mob.
     * @param range     Count range with any enchantment bonus applied. Never null.
     * @return The count, between {@code range.min()} and {@code range.max()} when min ≤ max.
     */
    public int countFor(float maxHealth, DropCount range) {
        double value = multiplier * function.apply(maxHealth / divisor);
        if (Double.isNaN(value)) return range.min();
        long rounded = Math.round(value);
        return (int) Math.max(range.min(), Math.min(rounded, range.max()));
    }
}
