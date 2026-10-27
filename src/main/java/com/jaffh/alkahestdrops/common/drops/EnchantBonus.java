package com.jaffh.alkahestdrops.common.drops;

/**
 * Represents the per-level bonuses one enchantment gives to one drop (the {@code on_enchant} object).
 *
 * @param chance Bonus to the drop chance. Never null.
 * @param roll   Bonus to the rolls of roll-based behaviors. Never null.
 * @param count  Bonus to the count range. Never null.
 */
public record EnchantBonus(ChanceBonus chance, RollBonus roll, CountBonus count) {
    /** No bonus at any level. */
    public static final EnchantBonus NONE = new EnchantBonus(ChanceBonus.NONE, RollBonus.NONE, CountBonus.NONE);

    /**
     * Computes a per-level bonus capped at a maximum. Uses long math because modded or command-given
     * enchantment levels can be large enough to overflow an int product.
     *
     * @param bonusPerLevel Bonus added per level, 0 or more.
     * @param level         Enchantment level, 0 or more.
     * @param maxBonus      Cap of the total bonus, 0 or more.
     * @return The total bonus, between 0 and maxBonus.
     */
    private static int cappedBonus(int bonusPerLevel, int level, int maxBonus) {
        return (int) Math.min((long) bonusPerLevel * level, maxBonus);
    }

    /**
     * Represents a bonus to the drop chance.
     *
     * @param bonusPerLevel Chance added per level.
     * @param maxBonus      Cap of the total added chance.
     */
    public record ChanceBonus(double bonusPerLevel, double maxBonus) {
        /** No chance bonus. */
        public static final ChanceBonus NONE = new ChanceBonus(0.0, 0.0);

        /**
         * Applies this bonus to a base chance.
         *
         * @param baseChance Chance without bonus (0–1).
         * @param level      Enchantment level, 0 or more.
         * @return The raised chance, at most 1.
         */
        public double applyTo(double baseChance, int level) {
            return Math.min(baseChance + Math.min(bonusPerLevel * level, maxBonus), 1.0);
        }
    }

    /**
     * Represents a bonus to the rolls of roll-based behaviors.
     *
     * @param bonusRollsPerLevel      Rolls added per level.
     * @param bonusRollChancePerLevel Roll chance added per level.
     * @param maxBonusRolls           Cap of the total added rolls.
     * @param maxBonusRollChance      Cap of the total added roll chance.
     * @param bonusIncrementPerLevel  Increment value added per level.
     * @param maxBonusIncrement       Cap of the total added increment value.
     */
    public record RollBonus(
            int bonusRollsPerLevel,
            double bonusRollChancePerLevel,
            int maxBonusRolls,
            double maxBonusRollChance,
            int bonusIncrementPerLevel,
            int maxBonusIncrement
    ) {
        /** No roll bonus. */
        public static final RollBonus NONE = new RollBonus(0, 0.0, 0, 0.0, 0, 0);

        /**
         * Applies this bonus to a base number of rolls.
         *
         * @param baseRolls Rolls without bonus.
         * @param level     Enchantment level, 0 or more.
         * @return The raised number of rolls.
         */
        public int applyToRolls(int baseRolls, int level) {
            return baseRolls + cappedBonus(bonusRollsPerLevel, level, maxBonusRolls);
        }

        /**
         * Applies this bonus to a base roll chance.
         *
         * @param baseChance Roll chance without bonus (0–1).
         * @param level      Enchantment level, 0 or more.
         * @return The raised roll chance, at most 1.
         */
        public double applyToChance(double baseChance, int level) {
            return Math.min(baseChance + Math.min(bonusRollChancePerLevel * level, maxBonusRollChance), 1.0);
        }

        /**
         * Applies this bonus to a base increment value.
         *
         * @param baseIncrement Items added per successful roll without bonus.
         * @param level         Enchantment level, 0 or more.
         * @return The raised increment value.
         */
        public int applyToIncrement(int baseIncrement, int level) {
            return baseIncrement + cappedBonus(bonusIncrementPerLevel, level, maxBonusIncrement);
        }
    }

    /**
     * Represents a bonus to the count range.
     *
     * @param bonusMinPerLevel Min added per level.
     * @param bonusMaxPerLevel Max added per level.
     * @param maxBonusMin      Cap of the total added min.
     * @param maxBonusMax      Cap of the total added max.
     */
    public record CountBonus(int bonusMinPerLevel, int bonusMaxPerLevel, int maxBonusMin, int maxBonusMax) {
        /** No count bonus. */
        public static final CountBonus NONE = new CountBonus(0, 0, 0, 0);

        /**
         * Applies this bonus to a base min.
         *
         * @param baseMin Min without bonus.
         * @param level   Enchantment level, 0 or more.
         * @return The raised min.
         */
        public int applyToMin(int baseMin, int level) {
            return baseMin + cappedBonus(bonusMinPerLevel, level, maxBonusMin);
        }

        /**
         * Applies this bonus to a base max.
         *
         * @param baseMax Max without bonus.
         * @param level   Enchantment level, 0 or more.
         * @return The raised max.
         */
        public int applyToMax(int baseMax, int level) {
            return baseMax + cappedBonus(bonusMaxPerLevel, level, maxBonusMax);
        }
    }
}
