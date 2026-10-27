package com.jaffh.alkahestdrops.server.command;

import com.jaffh.alkahestdrops.common.drops.DropTracking;
import com.jaffh.alkahestdrops.server.drops.IDropSimulator;
import com.jaffh.alkahestdrops.server.drops.SimulatedDrop;
import com.jaffh.alkahestdrops.server.drops.SimulatedStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    private final Map<TallyKey, Tally> tallies = new HashMap<>();
    /** Experience lines: null for the experience of other drops, else one per pity or one-time drop. */
    private final Map<DropTracking, Tally> experienceTallies = new HashMap<>();
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
        if (drop.items().isEmpty() && drop.experience() <= 0) return;
        Set<TallyKey> keysOfRun = new HashSet<>();
        for (SimulatedStack simulated : drop.items()) {
            ItemStack stack = simulated.stack();
            if (stack.isEmpty() || !filter.matchesItem().test(stack)) continue;
            TallyKey key = new TallyKey(stack.getItem(), simulated.tracking());
            Tally tally = tallies.computeIfAbsent(key, ignored -> new Tally());
            tally.total += stack.getCount();
            if (keysOfRun.add(key)) tally.runsWithDrop++;
            if (shouldKeepStacks) keep(stack);
        }
        if (filter.showsExperience() && drop.experience() > 0) {
            experience += drop.experience();
            runsWithExperience++;
            recordExperience(drop);
        }
    }

    /**
     * Counts the experience of one run per line: each pity or one-time drop on its own, the rest together.
     *
     * @param drop Result of the run, with experience above 0. Never null.
     */
    private void recordExperience(SimulatedDrop drop) {
        int plain = drop.experience();
        for (Map.Entry<DropTracking, Integer> tracked : drop.trackedExperience().entrySet()) {
            countExperience(tracked.getKey(), tracked.getValue());
            plain -= tracked.getValue();
        }
        if (plain > 0) countExperience(null, plain);
    }

    /**
     * Adds experience to one experience line.
     *
     * @param tracking Pity or one-time drop of the line, or null for the other drops.
     * @param amount   Experience of the run for this line, above 0.
     */
    private void countExperience(@Nullable DropTracking tracking, int amount) {
        Tally tally = experienceTallies.computeIfAbsent(tracking, ignored -> new Tally());
        tally.total += amount;
        tally.runsWithDrop++;
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
     * Returns the counted items, most dropped first. Stacks of each pity or one-time drop are counted on their own,
     * apart from other drops of the same item.
     *
     * @return One result per item, plus one per pity or one-time drop. Never null.
     */
    public List<ItemResult> itemResults() {
        List<ItemResult> results = new ArrayList<>(tallies.size());
        tallies.forEach((key, tally) -> results.add(new ItemResult(key.item(), key.tracking(), tally.total, tally.runsWithDrop)));
        results.sort(Comparator.comparingLong(ItemResult::total).reversed());
        return results;
    }

    /**
     * Returns the experience lines: the experience of other drops first, then each pity or one-time drop, most
     * dropped first.
     *
     * @return One result per line; empty when no experience was counted. Never null.
     */
    public List<ExperienceResult> experienceResults() {
        List<ExperienceResult> results = new ArrayList<>(experienceTallies.size());
        experienceTallies.forEach((tracking, tally) -> results.add(new ExperienceResult(tracking, tally.total, tally.runsWithDrop)));
        results.sort(Comparator.comparing((ExperienceResult result) -> result.tracking() != null)
                .thenComparing(Comparator.comparingLong(ExperienceResult::total).reversed()));
        return results;
    }

    /**
     * Returns the experience of every run, every line together.
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
     * @param tracking     Settings of the pity or one-time drop these stacks came from, or null for the other drops.
     * @param total        Summed count over every run.
     * @param runsWithDrop Number of runs that dropped at least one.
     */
    public record ItemResult(Item item, @Nullable DropTracking tracking, long total, int runsWithDrop) {
    }

    /**
     * Represents what one experience line dropped over every run.
     *
     * @param tracking     Settings of the pity or one-time drop of the line, or null for the other drops.
     * @param total        Summed experience over every run.
     * @param runsWithDrop Number of runs that dropped experience on this line.
     */
    public record ExperienceResult(@Nullable DropTracking tracking, long total, int runsWithDrop) {
    }

    /**
     * Identifies one line of the report: an item, alone or from one pity or one-time drop.
     *
     * @param item     Dropped item. Never null.
     * @param tracking Settings of the pity or one-time drop, or null for the other drops.
     */
    private record TallyKey(Item item, @Nullable DropTracking tracking) {
    }

    /**
     * Counts one line while the simulation runs.
     */
    private static final class Tally {
        private long total;
        private int runsWithDrop;
    }
}
