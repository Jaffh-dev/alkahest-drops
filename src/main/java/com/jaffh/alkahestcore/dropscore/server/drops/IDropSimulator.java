package com.jaffh.alkahestcore.dropscore.server.drops;

import net.minecraft.network.chat.Component;

/**
 * Rolls the drops of one target without breaking or killing anything, for {@code /dropscore simulate}.
 */
public interface IDropSimulator {

    /**
     * Rolls one kill or block break: the vanilla loot table, loot modifiers, removals, dropscore drops and the
     * experience, as the real event would.
     *
     * @return The items and experience of the roll. Never null.
     */
    SimulatedDrop simulateOnce();

    /**
     * Returns the name of the simulated mob or block.
     *
     * @return The translated name. Never null.
     */
    Component targetName();

    /**
     * Describes who kills or breaks, with which tool and enchantment level, for the header of the report.
     *
     * @return A short English description. Never null.
     */
    String describeSetup();
}
