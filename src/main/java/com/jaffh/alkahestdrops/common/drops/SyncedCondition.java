package com.jaffh.alkahestdrops.common.drops;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents one loot condition of a drop that clients can describe, such as "Killed by player" or "Requires Silk
 * Touch". A negated condition comes from {@code inverted} or from a conditional removal: the drop only happens when the
 * condition does not pass ("Not sneaking").
 *
 * @param kind      What the condition checks. Never null.
 * @param isNegated Whether the drop needs the condition to fail.
 * @param ids       Items, item tag or enchantments of a tool condition; empty for other kinds. Immutable, never null.
 */
public record SyncedCondition(Kind kind, boolean isNegated, List<ResourceLocation> ids) {
    /**
     * Creates a condition and stores an immutable copy of its ids.
     */
    public SyncedCondition {
        ids = List.copyOf(ids);
    }

    /**
     * Creates a condition without ids.
     *
     * @param kind      What the condition checks. Never null.
     * @param isNegated Whether the drop needs the condition to fail.
     * @return The condition.
     */
    public static SyncedCondition of(Kind kind, boolean isNegated) {
        return new SyncedCondition(kind, isNegated, List.of());
    }

    /**
     * Returns the opposite condition.
     *
     * @return A copy with {@code isNegated} flipped.
     */
    public SyncedCondition negate() {
        return new SyncedCondition(kind, !isNegated, ids);
    }

    /**
     * Defines the conditions clients can describe.
     */
    public enum Kind {
        /** A player killed the mob ({@code killed_by_player}). */
        KILLED_BY_PLAYER,
        /** A real player, not a machine's fake player, killed the mob ({@code killed_by_real_player}). */
        KILLED_BY_REAL_PLAYER,
        /** A real player, not a machine's fake player, broke the block ({@code broken_by_real_player}). */
        BROKEN_BY_REAL_PLAYER,
        /** The player is sneaking ({@code entity_properties} flag {@code is_sneaking}). */
        SNEAKING,
        /** The dying mob is on fire ({@code entity_properties} flag {@code is_on_fire}). */
        BURNING,
        /** The tool is one of the items in {@code ids} ({@code match_tool}). */
        TOOL_ITEMS,
        /** The tool is in the item tag in {@code ids} ({@code match_tool}). */
        TOOL_TAG,
        /** The tool has the enchantments in {@code ids} ({@code match_tool}). */
        TOOL_ENCHANTMENTS
    }
}
