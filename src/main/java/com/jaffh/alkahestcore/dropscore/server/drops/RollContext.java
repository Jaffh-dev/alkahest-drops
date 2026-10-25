package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.server.progress.DropProgress;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Represents what one kill or block break gives to the roll of its drops.
 *
 * @param enchantLevels Enchantment levels the {@code enchant_bonus} entries read. Never null.
 * @param scaleValue    Value the scaled behaviors read: max health of the dying mob or hardness of the broken block.
 * @param damageTaken   Damage the mob took from the hit, read by {@code damage_taken}; 0 outside hit drops.
 * @param random        Random source of the level. Never null.
 * @param player        Player whose pity and one-time drop progress the roll uses and who owns per-player drops, or
 *                      null without a player (pity is then ignored and one-time drops never happen).
 */
public record RollContext(EnchantLevels enchantLevels, float scaleValue, float damageTaken, RandomSource random,
                          @Nullable Player player) {
    /**
     * Creates the context of a roll that took no damage (every trigger but hit).
     *
     * @param enchantLevels Enchantment levels of the roll. Never null.
     * @param scaleValue    Max health of the mob or hardness of the block.
     * @param random        Random source of the level. Never null.
     * @param player        Player of the roll, or null.
     */
    public RollContext(EnchantLevels enchantLevels, float scaleValue, RandomSource random, @Nullable Player player) {
        this(enchantLevels, scaleValue, 0.0F, random, player);
    }

    /**
     * Returns the drop progress of the roll's player.
     *
     * @return The progress, or null without a player.
     */
    @Nullable
    public DropProgress progress() {
        return DropProgress.of(player);
    }
}
