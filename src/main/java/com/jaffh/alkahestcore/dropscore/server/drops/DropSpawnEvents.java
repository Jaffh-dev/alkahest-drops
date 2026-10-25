package com.jaffh.alkahestcore.dropscore.server.drops;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Follows queued dropscore stacks as they spawn: applies the glow right away, and at the end of the tick fires the
 * drop event and runs the {@code on_drop} function. Then it empties the stack queue.
 */
public class DropSpawnEvents {
    /**
     * Spawned items waiting for the end of the tick. The join event fires before the item is added to the level, so a
     * function or listener run there could not find the item with selectors or level queries.
     */
    private final List<SpawnedDrop> spawnedThisTick = new ArrayList<>();

    /**
     * Picks up a spawning item whose stack is queued. Runs at LOWEST priority, skipping spawns other mods cancel, and
     * costs one check per spawning entity while nothing is queued.
     *
     * @param event Join event of the entity.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        SpawningDropQueue queue = SpawningDropQueue.INSTANCE;
        if (queue.isEmpty() || event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item)) return;
        SpawningDropQueue.RolledFrom rolled = queue.take(item.getItem());
        if (rolled == null) return;
        DropActionRunner.applyGlow(item, rolled.entry().onDrop());
        spawnedThisTick.add(new SpawnedDrop(item, rolled));
    }

    /**
     * Finishes the drops spawned this tick, then forgets the stacks rolled this tick that never spawned. The list is
     * copied first, since a function or listener may spawn more drops; those are finished at the end of the next tick.
     *
     * @param event Server tick event.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!spawnedThisTick.isEmpty()) {
            List<SpawnedDrop> due = List.copyOf(spawnedThisTick);
            spawnedThisTick.clear();
            for (SpawnedDrop spawned : due) DropActionRunner.finishDrop(spawned.item(), spawned.rolled());
        }
        if (!SpawningDropQueue.INSTANCE.isEmpty()) SpawningDropQueue.INSTANCE.clear();
    }

    /**
     * Represents a spawned item waiting for the end of the tick.
     *
     * @param item   Spawned item entity. Never null.
     * @param rolled Drop and roll of its stack. Never null.
     */
    private record SpawnedDrop(ItemEntity item, SpawningDropQueue.RolledFrom rolled) {
    }
}
