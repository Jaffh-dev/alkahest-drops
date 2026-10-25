package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.AlkahestCore;
import com.jaffh.alkahestcore.dropscore.server.lang.Localizer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Rolls the drops of one target without breaking or killing anything, for {@code /dropscore simulate}.
 */
public interface IDropSimulator {
    /** Prefix of the translation keys of setup descriptions and setup errors. */
    String KEY_PREFIX = "command." + AlkahestCore.ID + ".dropscore.simulate.setup.";

    /**
     * Creates a translatable part of a setup description or a setup error.
     *
     * @param key  Key without {@link #KEY_PREFIX}, such as {@code killer}. Never null.
     * @param args Values of the key's placeholders. Never null.
     * @return The text.
     */
    static MutableComponent setupText(String key, Object... args) {
        return Localizer.translatable(KEY_PREFIX + key, args);
    }

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
     * @return A short translatable description. Never null.
     */
    Component describeSetup();
}
