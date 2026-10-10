package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.jaffh.alkahestcore.dropscore.server.drops.RemovalRule;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Set;

/**
 * Represents everything dropscore does for one entity type, precomputed at reload.
 *
 * @param dropFiles           Drop files left after unconditional removals. Immutable, never null.
 * @param removedItems        Items every unconditional removal entry removes from the event drops. Immutable, never null.
 * @param conditionalRemovals Removal files with global conditions or conditional entries, tested per kill.
 *                            Immutable, never null.
 * @param needsLootContext    Whether any file or entry has conditions, so a kill must build a loot context.
 */
public record MobDropTable(
        List<MobDropFile> dropFiles,
        Set<Item> removedItems,
        List<RemovalRule> conditionalRemovals,
        boolean needsLootContext
) {
    /**
     * Creates a table from its rules, storing immutable copies and computing whether a loot context is needed.
     *
     * @param dropFiles           Drop files left after unconditional removals. Never null.
     * @param removedItems        Items removed unconditionally from the event drops. Never null.
     * @param conditionalRemovals Removal files tested per kill. Never null.
     * @return The new table.
     */
    public static MobDropTable of(List<MobDropFile> dropFiles, Set<Item> removedItems, List<RemovalRule> conditionalRemovals) {
        boolean hasConditionalDrop = dropFiles.stream().anyMatch(file -> file.hasGlobalConditions()
                || file.entries().stream().anyMatch(entry -> entry.hasConditions()));
        return new MobDropTable(
                List.copyOf(dropFiles),
                Set.copyOf(removedItems),
                List.copyOf(conditionalRemovals),
                hasConditionalDrop || !conditionalRemovals.isEmpty()
        );
    }
}
