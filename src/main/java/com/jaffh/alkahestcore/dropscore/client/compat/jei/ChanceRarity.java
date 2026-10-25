package com.jaffh.alkahestcore.dropscore.client.compat.jei;

/**
 * Defines how rare a drop chance is, and the color it is shown in (chance bar and chance values in tooltips).
 */
enum ChanceRarity {
    /** 50% or more. */
    COMMON(0.5, 0x6BE36B),
    /** 10% or more. */
    UNCOMMON(0.1, 0xF2E15C),
    /** 1% or more. */
    RARE(0.01, 0xFFA23A),
    /** Below 1%. */
    VERY_RARE(0.0, 0xC879FF);

    private final double minChance;
    private final int color;

    /**
     * Creates a rarity.
     *
     * @param minChance Lowest chance (0–1) of the rarity.
     * @param color     RGB color of the rarity.
     */
    ChanceRarity(double minChance, int color) {
        this.minChance = minChance;
        this.color = color;
    }

    /**
     * Returns the RGB color of the rarity.
     *
     * @return The color, without alpha.
     */
    int color() {
        return color;
    }

    /**
     * Finds the rarity of a chance.
     *
     * @param chance Chance, 0–1.
     * @return The rarest rarity whose minimum the chance reaches.
     */
    static ChanceRarity of(double chance) {
        for (ChanceRarity rarity : values()) {
            if (chance >= rarity.minChance) return rarity;
        }
        return VERY_RARE;
    }
}
