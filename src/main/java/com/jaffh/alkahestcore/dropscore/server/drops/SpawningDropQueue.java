package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Remembers the dropscore stacks rolled this tick until they spawn as item entities, for {@code on_drop} and
 * {@link com.jaffh.alkahestcore.dropscore.api.event.OnDropEvent}. Stacks are matched by identity: mob drops, trigger
 * drops and block loot all spawn the same stack objects dropscore rolled. Every drop spawns in the tick it is rolled,
 * so the queue is emptied at the end of each tick and a stack that never spawned (an auto-pickup mod moved it into an
 * inventory) is forgotten. Simulated rolls are never queued. Server thread only.
 */
public final class SpawningDropQueue {
    /** The single queue. */
    public static final SpawningDropQueue INSTANCE = new SpawningDropQueue();

    private final Map<ItemStack, RolledFrom> pending = new IdentityHashMap<>();

    /**
     * Creates the single queue.
     */
    private SpawningDropQueue() {
    }

    /**
     * Queues a rolled stack, unless the roll is simulated or has no origin.
     *
     * @param stack Rolled stack. Never null, never empty.
     * @param entry Drop that rolled it. Never null.
     * @param roll  Roll context of the stack, with its player and origin. Never null.
     */
    void add(ItemStack stack, DropEntry entry, RollContext roll) {
        if (roll.origin() == null || TrackedDropRecorder.INSTANCE.isRecording()) return;
        pending.put(stack, new RolledFrom(entry, roll));
    }

    /**
     * Tells whether no stack waits to spawn, the cheap check made before looking a spawned item up.
     *
     * @return True when the queue is empty.
     */
    public boolean isEmpty() {
        return pending.isEmpty();
    }

    /**
     * Removes and returns the drop a stack was rolled from.
     *
     * @param stack Stack of a spawning item entity. Never null.
     * @return The drop and roll of the stack, or null when the stack was not rolled by dropscore this tick.
     */
    @Nullable
    public RolledFrom take(ItemStack stack) {
        return pending.remove(stack);
    }

    /**
     * Forgets every queued stack.
     */
    public void clear() {
        pending.clear();
    }

    /**
     * Represents the drop and roll a queued stack came from.
     *
     * @param entry Drop that rolled the stack. Never null.
     * @param roll  Roll context of the stack, with a non-null origin. Never null.
     */
    public record RolledFrom(DropEntry entry, RollContext roll) {
    }
}
