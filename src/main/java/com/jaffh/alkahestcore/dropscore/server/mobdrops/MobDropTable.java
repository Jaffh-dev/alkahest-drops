package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.jaffh.alkahestcore.dropscore.server.drops.DropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalRule;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Set;

/**
 * Represents everything dropscore does for one entity type, precomputed at reload.
 *
 * @param dropFiles        Drop files left after unconditional single-item removals. Immutable, never null.
 * @param removedItems     Items every unconditional single-item removal entry removes from the event drops.
 *                         Immutable, never null.
 * @param perKillRemovals  Removal files tested per kill: entries with conditions, and tag or {@code *} entries
 *                         (tags are only bound after reload). Immutable, never null.
 * @param needsLootContext Whether any file or entry has conditions, so a kill must build a loot context.
 */
public record MobDropTable(
        List<DropFile> dropFiles,
        Set<Item> removedItems,
        List<RemovalRule> perKillRemovals,
        boolean needsLootContext
) {
    /**
     * Creates a table from its rules, storing immutable copies and computing whether a loot context is needed.
     *
     * @param dropFiles       Drop files left after unconditional single-item removals. Never null.
     * @param removedItems    Items removed unconditionally from the event drops. Never null.
     * @param perKillRemovals Removal files tested per kill. Never null.
     * @return The new table.
     */
    public static MobDropTable of(List<DropFile> dropFiles, Set<Item> removedItems, List<RemovalRule> perKillRemovals) {
        boolean needsLootContext = dropFiles.stream().anyMatch(DropFile::hasAnyConditions)
                || perKillRemovals.stream().anyMatch(RemovalRule::hasAnyConditions);
        return new MobDropTable(
                List.copyOf(dropFiles),
                Set.copyOf(removedItems),
                List.copyOf(perKillRemovals),
                needsLootContext
        );
    }
}
