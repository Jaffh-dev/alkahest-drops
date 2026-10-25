package com.jaffh.alkahestcore.dropscore.common.drops;

import java.util.List;

/**
 * Represents the drops of every mob and block, as the server sends them to clients for display, such as in JEI.
 *
 * @param mobs   Mob targets with at least one drop. Immutable, never null.
 * @param blocks Block targets with at least one drop. Immutable, never null.
 */
public record SyncedDropRules(List<SyncedTarget> mobs, List<SyncedTarget> blocks) {
    /** No targets, before the first sync and after leaving a server. */
    public static final SyncedDropRules EMPTY = new SyncedDropRules(List.of(), List.of());

    /**
     * Creates the rules and stores immutable copies of the target lists.
     */
    public SyncedDropRules {
        mobs = List.copyOf(mobs);
        blocks = List.copyOf(blocks);
    }
}
