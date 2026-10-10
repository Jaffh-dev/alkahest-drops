package com.jaffh.alkahestcore.dropscore.common.drops;

/**
 * Represents how a drop rolls its count.
 *
 * @param type           Roll algorithm. Never null.
 * @param rolls          Number of rolls for the roll-based types.
 * @param chancePerRoll  Chance (0–1) that one roll succeeds.
 * @param incrementValue Items added per successful roll.
 */
public record DropBehavior(DropBehaviorType type, int rolls, double chancePerRoll, int incrementValue) {
    /** The {@code uniform} behavior, which has no roll settings. */
    public static final DropBehavior UNIFORM = new DropBehavior(DropBehaviorType.UNIFORM, 0, 0.0, 0);

    /**
     * Returns this behavior with an enchantment roll bonus applied.
     *
     * @param bonus Roll bonus of the drop. Never null.
     * @param level Enchantment level, 0 or more.
     * @return This instance when the level is 0, otherwise a copy with more rolls, a higher roll chance
     *         and a higher increment value.
     */
    public DropBehavior withBonus(EnchantBonus.RollBonus bonus, int level) {
        if (level <= 0) return this;
        return new DropBehavior(
                type,
                bonus.applyToRolls(rolls, level),
                bonus.applyToChance(chancePerRoll, level),
                bonus.applyToIncrement(incrementValue, level)
        );
    }
}
