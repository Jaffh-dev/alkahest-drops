package com.jaffh.alkahestdrops.common.drops;

/**
 * Represents how a drop rolls its count.
 *
 * @param type           Roll algorithm. Never null.
 * @param rolls          Number of rolls for the roll-based types.
 * @param chancePerRoll  Chance (0–1) that one roll succeeds.
 * @param incrementValue Items added per successful roll.
 * @param scaleFormula   Settings of the scaled types, {@link ScaleFormula#DEFAULT} for other types. Never null.
 */
public record DropBehavior(DropBehaviorType type, int rolls, double chancePerRoll, int incrementValue, ScaleFormula scaleFormula) {
    /** The {@code uniform} behavior, which has no roll settings. */
    public static final DropBehavior UNIFORM = new DropBehavior(DropBehaviorType.UNIFORM, 0, 0.0, 0, ScaleFormula.DEFAULT);

    /**
     * Creates a roll-based behavior.
     *
     * @param type           {@link DropBehaviorType#FIXED_ROLLS} or {@link DropBehaviorType#CHAINED_ROLLS}. Never null.
     * @param rolls          Number of rolls.
     * @param chancePerRoll  Chance (0–1) that one roll succeeds.
     * @param incrementValue Items added per successful roll.
     * @return The new behavior.
     */
    public static DropBehavior ofRolls(DropBehaviorType type, int rolls, double chancePerRoll, int incrementValue) {
        return new DropBehavior(type, rolls, chancePerRoll, incrementValue, ScaleFormula.DEFAULT);
    }

    /**
     * Creates a scaled behavior.
     *
     * @param type         {@link DropBehaviorType#MAX_HEALTH} or {@link DropBehaviorType#HARDNESS}. Never null.
     * @param scaleFormula Settings of the formula. Never null.
     * @return The new behavior.
     */
    public static DropBehavior ofScale(DropBehaviorType type, ScaleFormula scaleFormula) {
        return new DropBehavior(type, 0, 0.0, 0, scaleFormula);
    }

    /**
     * Returns this behavior with an enchantment roll bonus applied.
     *
     * @param bonus Roll bonus of the drop. Never null.
     * @param level Enchantment level, 0 or more.
     * @return This instance when the level is 0 or the type has no roll settings, otherwise a copy with
     *         more rolls, a higher roll chance and a higher increment value.
     */
    public DropBehavior withBonus(EnchantBonus.RollBonus bonus, int level) {
        if (level <= 0 || !type.needsRollSettings()) return this;
        return new DropBehavior(
                type,
                bonus.applyToRolls(rolls, level),
                bonus.applyToChance(chancePerRoll, level),
                bonus.applyToIncrement(incrementValue, level),
                scaleFormula
        );
    }
}
