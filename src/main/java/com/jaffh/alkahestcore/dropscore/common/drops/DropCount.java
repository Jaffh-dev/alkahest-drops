package com.jaffh.alkahestcore.dropscore.common.drops;

/**
 * Represents the item count range of a drop.
 *
 * @param min Smallest count.
 * @param max Largest count.
 */
public record DropCount(int min, int max) {

    /**
     * Returns this range with an enchantment count bonus applied.
     *
     * @param bonus Count bonus of the drop. Never null.
     * @param level Enchantment level, 0 or more.
     * @return This instance when the level is 0, otherwise a copy with the raised min and max.
     */
    public DropCount withBonus(EnchantBonus.CountBonus bonus, int level) {
        if (level <= 0) return this;
        return new DropCount(bonus.applyToMin(min, level), bonus.applyToMax(max, level));
    }
}
