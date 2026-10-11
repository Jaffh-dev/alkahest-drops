package com.jaffh.alkahestcore.dropscore.server.drops;

import java.util.Random;

/**
 * Represents what one kill or block break gives to the roll of its drops.
 *
 * @param enchantLevel Looting or Fortune level, 0 for no bonus.
 * @param scaleValue   Value the scaled behaviors read: max health of the dying mob or hardness of the broken block.
 * @param random       Random source of the level. Never null.
 */
public record RollContext(int enchantLevel, float scaleValue, Random random) {
}
