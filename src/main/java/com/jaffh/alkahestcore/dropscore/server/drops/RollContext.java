package com.jaffh.alkahestcore.dropscore.server.drops;

import java.util.Random;

/**
 * Represents what one kill or block break gives to the roll of its drops.
 *
 * @param enchantLevel Looting or Fortune level, 0 for no bonus.
 * @param maxHealth    Max health of the dying mob, 0 for blocks.
 * @param random       Random source of the level. Never null.
 */
public record RollContext(int enchantLevel, float maxHealth, Random random) {
}
