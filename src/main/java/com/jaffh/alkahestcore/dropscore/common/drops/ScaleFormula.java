package com.jaffh.alkahestcore.dropscore.common.drops;

/**
 * Represents the settings of the scaled behaviors, {@code max_health} (mobs) and {@code hardness} (blocks):
 * {@code count = round(multiplier · function(value / divisor))}, clamped by the drop's count range.
 *
 * @param function   Function applied to the scaled value. Never null.
 * @param multiplier Factor applied to the function value, above 0.
 * @param divisor    Divisor of the target value, above 0.
 */
public record ScaleFormula(ScaleFunction function, double multiplier, double divisor) {
    /** The defaults: linear, multiplier 1, divisor 1. */
    public static final ScaleFormula DEFAULT = new ScaleFormula(ScaleFunction.LINEAR, 1.0, 1.0);

    /**
     * Computes the count for a target value, clamped to a range.
     *
     * @param value Max health of the mob or hardness of the block; 0 or less (unbreakable or instant-break
     *              blocks) gives {@code range.min()}.
     * @param range Count range with any enchantment bonus applied. Never null.
     * @return The count, between {@code range.min()} and {@code range.max()} when min ≤ max.
     */
    public int countFor(float value, DropCount range) {
        if (value <= 0) return range.min();
        double result = multiplier * function.apply(value / divisor);
        if (Double.isNaN(result)) return range.min();
        long rounded = Math.round(result);
        return (int) Math.max(range.min(), Math.min(rounded, range.max()));
    }
}
