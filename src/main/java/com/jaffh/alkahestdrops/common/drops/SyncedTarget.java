package com.jaffh.alkahestdrops.common.drops;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents everything one mob or block drops, as sent to clients: its loot table, its experience and every
 * drop file drop that reaches it (by id, tag, namespace or {@code *}), after removals.
 *
 * @param id    Id of the entity type or block. Never null.
 * @param drops Drops, loot table first, then experience, then drop file drops. Immutable, never null.
 */
public record SyncedTarget(ResourceLocation id, List<SyncedDrop> drops) {
    /**
     * Creates a synced target and stores an immutable copy of its drops.
     */
    public SyncedTarget {
        drops = List.copyOf(drops);
    }
}
