package com.jaffh.alkahestdrops.server.drops;

import com.jaffh.alkahestdrops.common.AlkahestDropsConfig;

/**
 * Represents the limits drop files are clamped to. The roll loops run on every kill or block break,
 * so these bound the per-drop cost, and they keep the count math far from int overflow.
 *
 * @param maxRolls     Highest rolls and roll bonus, 1 or more.
 * @param maxCount     Highest count and count bonus, 1 or more.
 * @param maxIncrement Highest increment value and increment bonus, 1 or more.
 */
public record DropLimits(int maxRolls, int maxCount, int maxIncrement) {

    /**
     * Reads the limits from the loaded server config.
     *
     * @return The configured limits.
     */
    public static DropLimits fromConfig() {
        return new DropLimits(
                AlkahestDropsConfig.MAX_ROLLS.get(),
                AlkahestDropsConfig.MAX_COUNT.get(),
                AlkahestDropsConfig.MAX_INCREMENT.get());
    }
}
