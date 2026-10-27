package com.jaffh.alkahestdrops.common.drops;

import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Represents the bonus one enchantment gives to a drop: one entry of {@code enchant_bonus}.
 *
 * @param enchantment Enchantment whose level scales the bonus. Never null.
 * @param bonus       Per-level bonuses of that enchantment. Never null.
 */
public record EnchantBonusEntry(Enchantment enchantment, EnchantBonus bonus) {
}
