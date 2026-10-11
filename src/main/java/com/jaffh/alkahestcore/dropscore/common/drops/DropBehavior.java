package com.jaffh.alkahestcore.dropscore.common.drops;

/**
 * Represents how a drop rolls its count.
 *
 * @param type           Roll algorithm. Never null.
 * @param rolls          Number of rolls for the roll-based types.
 * @param chancePerRoll  Chance (0–1) that one roll succeeds.
 * @param incrementValue Items added per successful roll.
 * @param healthFormula  Settings of the {@code max_health} type, {@link HealthFormula#DEFAULT} for other types.
 *                       Never null.
 */
public record DropBehavior(DropBehaviorType type, int rolls, double chancePerRoll, int incrementValue, HealthFormula healthFormula) {
    /** The {@code uniform} behavior, which has no roll settings. */
    public static final DropBehavior UNIFORM = new DropBehavior(DropBehaviorType.UNIFORM, 0, 0.0, 0, HealthFormula.DEFAULT);

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
        return new DropBehavior(type, rolls, chancePerRoll, incrementValue, HealthFormula.DEFAULT);
    }

    /**
     * Creates a {@code max_health} behavior.
     *
     * @param healthFormula Settings of the formula. Never null.
     * @return The new behavior.
     */
    public static DropBehavior ofMaxHealth(HealthFormula healthFormula) {
        return new DropBehavior(DropBehaviorType.MAX_HEALTH, 0, 0.0, 0, healthFormula);
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
                healthFormula
        );
    }
}
