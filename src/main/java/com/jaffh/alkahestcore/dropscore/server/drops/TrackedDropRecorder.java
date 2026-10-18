package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.common.drops.DropTracking;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Remembers which stacks and how much experience of one simulated roll came from a pity or one-time drop, so
 * {@code /dropscore simulate} can report them on their own lines. Stacks are matched by identity: the loot table and
 * loot modifiers hand back the same stack objects dropscore rolled. Records nothing outside a simulation. Server
 * thread only.
 */
public final class TrackedDropRecorder {
    /** The single recorder. */
    public static final TrackedDropRecorder INSTANCE = new TrackedDropRecorder();

    private boolean isRecording;
    /** Stacks of tracked drops in the running roll; created on the first one, since most rolls have none. */
    private Map<ItemStack, DropTracking> recordedStacks = Map.of();
    /** Experience of tracked drops in the running roll; created on the first one. */
    private Map<DropTracking, Integer> recordedExperience = Map.of();

    /**
     * Creates the single recorder.
     */
    private TrackedDropRecorder() {
    }

    /**
     * Records the settings of the drop a stack was rolled from, while a simulated roll is running.
     *
     * @param stack    Rolled stack. Never null.
     * @param tracking Settings of a pity or one-time drop. Never null.
     */
    void recordStack(ItemStack stack, DropTracking tracking) {
        if (!isRecording) return;
        if (recordedStacks.isEmpty()) recordedStacks = new IdentityHashMap<>();
        recordedStacks.put(stack, tracking);
    }

    /**
     * Records experience rolled by a pity or one-time drop, while a simulated roll is running.
     *
     * @param tracking Settings of the drop. Never null.
     * @param amount   Rolled experience, above 0.
     */
    void recordExperience(DropTracking tracking, int amount) {
        if (!isRecording) return;
        if (recordedExperience.isEmpty()) recordedExperience = new HashMap<>();
        recordedExperience.merge(tracking, amount, Integer::sum);
    }

    /**
     * Runs one simulated roll, items first and then experience, and labels what pity and one-time drops gave.
     *
     * @param items      Roll of the items. Never null.
     * @param experience Roll of the experience, run after the items. Never null.
     * @return The stacks with their drop settings, the total experience and the part of it each tracked drop gave.
     */
    public SimulatedDrop recordRoll(Supplier<List<ItemStack>> items, IntSupplier experience) {
        isRecording = true;
        List<ItemStack> rolledItems;
        int rolledExperience;
        Map<ItemStack, DropTracking> stacks;
        Map<DropTracking, Integer> trackedExperience;
        try {
            rolledItems = items.get();
            rolledExperience = experience.getAsInt();
        } finally {
            isRecording = false;
            stacks = recordedStacks;
            trackedExperience = recordedExperience;
            recordedStacks = Map.of();
            recordedExperience = Map.of();
        }
        List<SimulatedStack> labeled = new ArrayList<>(rolledItems.size());
        for (ItemStack stack : rolledItems) {
            labeled.add(new SimulatedStack(stack, stacks.get(stack)));
        }
        return new SimulatedDrop(labeled, rolledExperience, trackedExperience);
    }
}
