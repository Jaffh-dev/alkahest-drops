package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.server.progress.DropProgress;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Represents what one kill or block break gives to the roll of its drops.
 *
 * @param enchantLevel Looting or Fortune level, 0 for no bonus.
 * @param scaleValue   Value the scaled behaviors read: max health of the dying mob or hardness of the broken block.
 * @param random       Random source of the level. Never null.
 * @param player       Player whose pity and one-time drop progress the roll uses and who owns per-player drops, or
 *                     null without a player (pity is then ignored and one-time drops never happen).
 */
public record RollContext(int enchantLevel, float scaleValue, Random random, @Nullable Player player) {
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
