package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.AlkahestCore;
import com.jaffh.alkahestcore.dropscore.common.drops.DropTracking;
import com.jaffh.alkahestcore.dropscore.server.lang.Localizer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Map;
import java.util.OptionalInt;

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
     * Describes the enchantment levels a simulation reads, with plain numbers since vanilla has no Roman numeral for
     * levels such as 255.
     *
     * @param levels Levels above 0, in display order. Never null.
     * @return Such as "enchantments: Looting 3, Sharpness 5", or "no enchantments" when empty.
     */
    static MutableComponent enchantmentsText(Map<Enchantment, Integer> levels) {
        if (levels.isEmpty()) return setupText("no_enchantments");
        MutableComponent list = Component.empty();
        boolean isFirst = true;
        for (Map.Entry<Enchantment, Integer> entry : levels.entrySet()) {
            if (!isFirst) list.append(", ");
            list.append(Component.translatable(entry.getKey().getDescriptionId())).append(" " + entry.getValue());
            isFirst = false;
        }
        return setupText("enchantments", list);
    }

    /**
     * Rolls one kill or block break: the vanilla loot table, loot modifiers, removals, dropscore drops and the
     * experience, as the real event would.
     *
     * @return The items and experience of the roll. Never null.
     */
    SimulatedDrop simulateOnce();

    /**
     * Names one run of the simulation, the word the report's keys use.
     *
     * @return Such as {@code kill}, {@code break} or {@code shear}.
     */
    String runNoun();

    /**
     * Returns how many runs one rate of the report covers, so rates of tiny per-run values stay readable.
     *
     * @return 1 (rates per run) unless overridden.
     */
    default int runsPerRate() {
        return 1;
    }

    /**
     * Returns the interval of a periodic drop the report shows on its own line.
     *
     * @param tracking Drop of a result line. Never null.
     * @return The interval in runs (ticks), or empty when the drop is not periodic.
     */
    default OptionalInt intervalOf(DropTracking tracking) {
        return OptionalInt.empty();
    }

    /**
     * Tells whether a drop the report shows on its own line counts per damage dealt ({@code damage_taken}).
     *
     * @param tracking Drop of a result line. Never null.
     * @return False unless overridden.
     */
    default boolean isPerDamage(DropTracking tracking) {
        return false;
    }

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
