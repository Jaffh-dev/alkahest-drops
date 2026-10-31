package com.jaffh.alkahestdrops.server.drops;

import com.jaffh.alkahestdrops.common.drops.DropTracking;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of one simulated kill or block break.
 *
 * @param items             Dropped stacks, each with the pity or one-time drop it came from. Never null.
 * @param experience        Dropped experience, 0 or more, including {@code trackedExperience}.
 * @param trackedExperience Part of the experience each pity or one-time drop gave. Never null.
 */
public record SimulatedDrop(List<SimulatedStack> items, int experience, Map<DropTracking, Integer> trackedExperience) {
    /** A roll that dropped nothing. */
    public static final SimulatedDrop NOTHING = new SimulatedDrop(List.of(), 0, Map.of());
}
