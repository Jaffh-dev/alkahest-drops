package com.jaffh.alkahestcore.dropscore.server.command;

import com.jaffh.alkahestcore.dropscore.server.drops.IDropSimulator;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulatedDrop;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs a simulator many times and counts what it dropped, per item and for the experience. Only what the filter
 * shows is counted; every run is still rolled in full, so removals and conditions behave as in a real drop.
 */
public final class DropSimulation {
    private final SimulationFilter filter;
    private final boolean shouldKeepStacks;
    private final Map<Item, ItemTally> tallies = new IdentityHashMap<>();
    private final List<ItemStack> keptStacks = new ArrayList<>();
    private int runCount;
    private long experience;
    private int runsWithExperience;

    /**
     * Creates an empty simulation.
     *
     * @param filter           What to count. Never null.
     * @param shouldKeepStacks Whether to keep the counted stacks, merged, to drop them afterward.
     */
    private DropSimulation(SimulationFilter filter, boolean shouldKeepStacks) {
        this.filter = filter;
        this.shouldKeepStacks = shouldKeepStacks;
    }

    /**
     * Rolls a simulator a number of times.
     *
     * @param simulator        Simulator of the target. Never null.
     * @param runs             Number of kills or breaks, 1 or more.
     * @param filter           What to count. Never null.
     * @param shouldKeepStacks Whether to keep the counted stacks to drop them afterward.
     * @return The counts of every run.
     */
    public static DropSimulation run(IDropSimulator simulator, int runs, SimulationFilter filter, boolean shouldKeepStacks) {
        DropSimulation simulation = new DropSimulation(filter, shouldKeepStacks);
        for (int i = 0; i < runs; i++) {
            simulation.record(simulator.simulateOnce());
        }
        return simulation;
    }

    /**
     * Counts the shown part of one run.
     *
     * @param drop Result of the run. Never null.
     */
    private void record(SimulatedDrop drop) {
        runCount++;
        Set<Item> itemsOfRun = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ItemStack stack : drop.items()) {
            if (stack.isEmpty() || !filter.matchesItem().test(stack)) continue;
            ItemTally tally = tallies.computeIfAbsent(stack.getItem(), item -> new ItemTally());
            tally.total += stack.getCount();
            if (itemsOfRun.add(stack.getItem())) tally.runsWithDrop++;
            if (shouldKeepStacks) keep(stack);
        }
        if (filter.showsExperience() && drop.experience() > 0) {
            experience += drop.experience();
            runsWithExperience++;
        }
    }

    /**
     * Adds a stack to the kept stacks, merged with an equal stack (same item and tags) when there is one.
     *
     * @param stack Counted stack. Never null.
     */
    private void keep(ItemStack stack) {
        for (ItemStack kept : keptStacks) {
            if (ItemStack.isSameItemSameTags(kept, stack)) {
                kept.grow(stack.getCount());
                return;
            }
        }
        keptStacks.add(stack.copy());
    }

    /**
     * Returns the number of runs.
     *
     * @return The number of simulated kills or breaks.
     */
    public int runCount() {
        return runCount;
    }

    /**
     * Returns the counted items, most dropped first.
     *
     * @return One result per item. Never null.
     */
    public List<ItemResult> itemResults() {
        List<ItemResult> results = new ArrayList<>(tallies.size());
        tallies.forEach((item, tally) -> results.add(new ItemResult(item, tally.total, tally.runsWithDrop)));
        results.sort(Comparator.comparingLong(ItemResult::total).reversed());
        return results;
    }

    /**
     * Returns the experience of every run.
     *
     * @return The summed experience, 0 when the filter hides it.
     */
    public long experience() {
        return experience;
    }

    /**
     * Returns the number of runs that dropped experience.
     *
     * @return The run count, 0 when the filter hides the experience.
     */
    public int runsWithExperience() {
        return runsWithExperience;
    }

    /**
     * Tells whether nothing shown dropped in any run.
     *
     * @return True when no item and no experience was counted.
     */
    public boolean isEmpty() {
        return tallies.isEmpty() && experience == 0;
    }

    /**
     * Returns the counted stacks merged by item and tags, with counts that may exceed the stack size.
     *
     * @return The kept stacks; empty unless the simulation keeps stacks. Never null.
     */
    public List<ItemStack> keptStacks() {
        return keptStacks;
    }

    /**
     * Represents what one item dropped over every run.
     *
     * @param item         Dropped item. Never null.
     * @param total        Summed count over every run.
     * @param runsWithDrop Number of runs that dropped at least one.
     */
    public record ItemResult(Item item, long total, int runsWithDrop) {
    }

    /**
     * Counts one item while the simulation runs.
     */
    private static final class ItemTally {
        private long total;
        private int runsWithDrop;
    }
}
