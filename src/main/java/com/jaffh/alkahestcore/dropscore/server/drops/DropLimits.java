package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.common.DropscoreConfig;

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
                DropscoreConfig.MAX_ROLLS.get(),
                DropscoreConfig.MAX_COUNT.get(),
                DropscoreConfig.MAX_INCREMENT.get()
        );
    }
}
