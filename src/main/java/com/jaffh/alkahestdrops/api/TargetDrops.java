package com.jaffh.alkahestdrops.api;

import java.util.List;

/**
 * Represents what Alkahest Drops does to the drops of one mob (for one trigger) or one block.
 *
 * @param drops    Drops the drop files add, without those that removal files of other namespaces always remove (as in
 *                 game). Conditional removals are not applied here; they are listed in {@code removals}. Immutable,
 *                 never null.
 * @param removals Removal entries that apply, conditional or not. Immutable, never null.
 * @since 1.0.0
 */
public record TargetDrops(List<AddedDrop> drops, List<DropRemoval> removals) {
    /** Nothing added and nothing removed. */
    public static final TargetDrops EMPTY = new TargetDrops(List.of(), List.of());

    /**
     * Creates the drops of a target and stores immutable copies of the lists.
     */
    public TargetDrops {
        drops = List.copyOf(drops);
        removals = List.copyOf(removals);
    }

    /**
     * Tells whether Alkahest Drops leaves the target's drops alone.
     *
     * @return True when nothing is added or removed.
     * @since 1.0.0
     */
    public boolean isEmpty() {
        return drops.isEmpty() && removals.isEmpty();
    }
}
