package com.jaffh.alkahestcore.dropscore.server.drops;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Represents one entry of a removal file's {@code removals} list: items to remove, or the experience
 * ({@code "experience": true}). Check {@link #isExperience()} before reading {@code target}.
 *
 * @param target     Items to remove: one item, an item tag or every item; null for an experience entry.
 * @param conditions Local loot conditions of this entry, tested after the file's global ones. Immutable, never null.
 */
public record RemovalEntry(@Nullable ItemTarget target, List<LootItemCondition> conditions) {
    /**
     * Creates a removal entry and stores an immutable copy of the conditions.
     */
    public RemovalEntry {
        conditions = List.copyOf(conditions);
    }

    /**
     * Tells whether this entry removes experience instead of items.
     *
     * @return True when the entry has no item target.
     */
    public boolean isExperience() {
        return target == null;
    }

    /**
     * Tells whether this entry names an item, ignoring local conditions.
     *
     * @param item Item to check. Never null.
     * @return True when this is an item entry whose target matches the item.
     */
    public boolean removes(Item item) {
        return target != null && target.matches(item);
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
