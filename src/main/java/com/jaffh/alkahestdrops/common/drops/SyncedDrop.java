package com.jaffh.alkahestdrops.common.drops;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents what a client needs to show one drop of a target, such as in JEI. Chances of loot table drops are
 * computed from the table and may be approximate. Conditions are synced as {@link SyncedCondition}s; the ones clients
 * cannot describe only set {@code hasHiddenConditions}.
 *
 * @param stack               Dropped item with a count of 1, or {@link ItemStack#EMPTY} for experience. Never null.
 * @param chance              Chance (0–1) that the drop happens.
 * @param count               Count range (amount of experience for an experience drop). Never null.
 * @param behavior            How the count is rolled. Never null.
 * @param enchantBonuses      Per-level bonuses of each enchantment. Never null.
 * @param tracking            Pity, one-time drop and per-player settings. Never null.
 * @param conditions          Conditions the drop needs, including the negated conditions of removal files that may
 *                            remove it. Immutable, never null.
 * @param hasHiddenConditions Whether the drop has conditions (or conditional removals) that are not in
 *                            {@code conditions}.
 * @param source              Where the drop comes from. Never null.
 * @param trigger             What makes the drop happen; death for loot tables, vanilla experience and blocks.
 *                            Never null.
 */
public record SyncedDrop(
        ItemStack stack,
        double chance,
        DropCount count,
        DropBehavior behavior,
        EnchantBonuses enchantBonuses,
        DropTracking tracking,
        List<SyncedCondition> conditions,
        boolean hasHiddenConditions,
        SyncedDropSource source,
        TriggerSettings trigger
) {
    /** Settings of a drop that is not a drop file drop: no pity, no one-time or per-player roll. */
    public static final DropTracking UNTRACKED = new DropTracking("", 0, false, false);

    /**
     * Creates a synced drop and stores an immutable copy of its conditions.
     */
    public SyncedDrop {
        conditions = List.copyOf(conditions);
    }

    /**
     * Tells whether this drop gives experience instead of an item.
     *
     * @return True when the stack is empty.
     */
    public boolean isExperience() {
        return stack.isEmpty();
    }

    /**
     * Tells whether this drop needs anything beyond its chance.
     *
     * @return True when it has conditions, shown or hidden.
     */
    public boolean hasConditions() {
        return !conditions.isEmpty() || hasHiddenConditions;
    }

    /**
     * Returns a copy of this drop with more conditions, such as the negated conditions of a removal file.
     *
     * @param added    Conditions to add. Never null.
     * @param isHidden Whether conditions that cannot be described were added too.
     * @return This drop when nothing is added, otherwise a copy.
     */
    public SyncedDrop withConditions(List<SyncedCondition> added, boolean isHidden) {
        if (added.isEmpty() && !isHidden) return this;
        List<SyncedCondition> merged = new ArrayList<>(conditions);
        merged.addAll(added);
        return new SyncedDrop(stack, chance, count, behavior, enchantBonuses, tracking, merged,
                hasHiddenConditions || isHidden, source, trigger);
    }
}
