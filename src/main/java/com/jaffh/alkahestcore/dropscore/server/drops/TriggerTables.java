package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.common.drops.DropTrigger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds the drop tables of one target, one per trigger that has rules, so each event only reads the rules of its
 * own trigger. A removal file only removes drops of its own trigger.
 */
public final class TriggerTables {
    /** Tables of a target without rules. */
    public static final TriggerTables EMPTY = new TriggerTables(new EnumMap<>(DropTrigger.class));

    private final Map<DropTrigger, DropTable> tables;

    /**
     * Creates the tables.
     *
     * @param tables Table of every trigger with rules. Never null, not modified afterward.
     */
    private TriggerTables(EnumMap<DropTrigger, DropTable> tables) {
        this.tables = tables;
    }

    /**
     * Builds the tables of one target from every rule that matches it.
     *
     * @param dropFiles Drop files matching the target. Never null.
     * @param removals  Removal files matching the target. Never null.
     * @return The tables.
     */
    public static TriggerTables build(List<DropFile> dropFiles, List<RemovalRule> removals) {
        EnumMap<DropTrigger, DropTable> tables = new EnumMap<>(DropTrigger.class);
        for (DropTrigger trigger : DropTrigger.values()) {
            List<DropFile> files = filesOf(dropFiles, trigger);
            List<RemovalRule> rules = removals.stream().filter(rule -> rule.trigger() == trigger).toList();
            if (!files.isEmpty() || !rules.isEmpty()) tables.put(trigger, DropTable.build(files, rules));
        }
        return new TriggerTables(tables);
    }

    /**
     * Keeps the drops of one trigger in each drop file, leaving out files without any.
     *
     * @param dropFiles Drop files of the target. Never null.
     * @param trigger   Trigger to keep. Never null.
     * @return The files holding only that trigger's drops.
     */
    private static List<DropFile> filesOf(List<DropFile> dropFiles, DropTrigger trigger) {
        List<DropFile> files = new ArrayList<>();
        for (DropFile file : dropFiles) {
            List<DropEntry> entries = file.entries().stream()
                    .filter(entry -> entry.trigger().trigger() == trigger)
                    .toList();
            if (entries.size() == file.entries().size()) files.add(file);
            else if (!entries.isEmpty()) files.add(file.withEntries(entries));
        }
        return files;
    }

    /**
     * Returns the table of one trigger.
     *
     * @param trigger Trigger. Never null.
     * @return The table, {@link DropTable#EMPTY} when no rule has this trigger.
     */
    public DropTable of(DropTrigger trigger) {
        DropTable table = tables.get(trigger);
        return table != null ? table : DropTable.EMPTY;
    }

    /**
     * Tells whether no trigger of the target has a rule.
     *
     * @return True when every table is empty.
     */
    public boolean isEmpty() {
        for (DropTable table : tables.values()) {
            if (!table.isEmpty()) return false;
        }
        return true;
    }

    /**
     * Returns the progress keys of the pity and one-time drops of every trigger.
     *
     * @return The keys, empty when no drop is tracked.
     */
    public Set<String> progressKeys() {
        Set<String> keys = new HashSet<>();
        for (DropTable table : tables.values()) keys.addAll(table.progressKeys());
        return keys;
    }
}
