package com.jaffh.alkahestcore.dropscore.server.drops;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;

/**
 * Represents one entry of a removal file's {@code removals} list.
 *
 * @param item       Item to remove. Never null.
 * @param conditions Local loot conditions of this entry, tested after the file's global ones. Immutable, never null.
 */
public record RemovalEntry(Item item, List<LootItemCondition> conditions) {
    /**
     * Creates a removal entry and stores an immutable copy of the conditions.
     */
    public RemovalEntry {
        conditions = List.copyOf(conditions);
    }

    /**
     * Tells whether this entry has local conditions to test.
     *
     * @return True when at least one local condition is set.
     */
    public boolean hasConditions() {
        return !conditions.isEmpty();
    }
}
