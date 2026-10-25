package com.jaffh.alkahestcore.dropscore.common.drops;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Represents everything one mob or block drops, as sent to clients: its loot table, its experience and every
 * dropscore drop that reaches it (by id, tag, namespace or {@code *}), after removals.
 *
 * @param id    Id of the entity type or block. Never null.
 * @param drops Drops, loot table first, then experience, then dropscore drops. Immutable, never null.
 */
public record SyncedTarget(ResourceLocation id, List<SyncedDrop> drops) {
    /**
     * Creates a synced target and stores an immutable copy of its drops.
     */
    public SyncedTarget {
        drops = List.copyOf(drops);
    }
}
