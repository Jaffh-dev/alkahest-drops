package com.jaffh.alkahestdrops.api.event;

import com.jaffh.alkahestdrops.api.DropOrigin;
import com.jaffh.alkahestdrops.api.DropTrigger;
import javax.annotation.Nullable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired on the Forge event bus, on the server thread, when an item rolled by a drop file has spawned in the
 * world: at the end of the tick it spawned in, so the item is in the level and selectors find it. Fired for every
 * drop file item drop, with or without {@code on_drop}; never for experience drops, simulations, or stacks that never
 * spawned as an item (a grave mod cancelling the drops, an auto-pickup mod). The item already glows when its drop
 * asks for it. Cancelling the event skips the drop's {@code run_function}. Part of the public API.
 *
 * @since 1.0.0
 */
@Cancelable
public class OnDropEvent extends Event {
    private final ItemEntity item;
    private final DropOrigin origin;
    private final String dropKey;
    private final DropTrigger trigger;

    @Nullable
    private final Player player;

    /**
     * Creates the event of one spawned drop.
     *
     * @param item    Spawned item entity. Never null.
     * @param origin  Mob or block the drop came from. Never null.
     * @param dropKey Key of the drop that rolled the item. Never null.
     * @param trigger What made the drop happen. Never null.
     * @param player  Player the drop was rolled for, or null without a player.
     * @since 1.0.0
     */
    public OnDropEvent(
            ItemEntity item, DropOrigin origin, String dropKey, DropTrigger trigger, @Nullable Player player) {
        this.item = item;
        this.origin = origin;
        this.dropKey = dropKey;
        this.trigger = trigger;
        this.player = player;
    }

    /**
     * Returns the spawned item.
     *
     * @return The item entity, already in the level. Never null.
     * @since 1.0.0
     */
    public ItemEntity getItem() {
        return item;
    }

    /**
     * Returns what the drop came from.
     *
     * @return The mob or the broken block. Never null.
     * @since 1.0.0
     */
    public DropOrigin getOrigin() {
        return origin;
    }

    /**
     * Returns the key of the drop that rolled the item, stable across reloads and the same key the drop's pity and
     * one-time progress uses.
     *
     * @return {@code "<mob|block>:<file id>@<id>"} for a drop with an {@code id}, such as
     *         {@code "mob:mypack:dragon@legendary_scale"}, else {@code "<mob|block>:<file id>#<index in drops>"}.
     * @since 1.0.0
     */
    public String getDropKey() {
        return dropKey;
    }

    /**
     * Returns what made the drop happen.
     *
     * @return The trigger; always death for blocks. Never null.
     * @since 1.0.0
     */
    public DropTrigger getTrigger() {
        return trigger;
    }

    /**
     * Returns the player the drop was rolled for: the killer, the player who sheared or hit, the breaker, or the owner
     * of a per-player drop. Machines' fake players are returned too; check for {@code FakePlayer} to skip them.
     *
     * @return The player, or null without a player (lightning, periodic, explosions, kills by mobs).
     * @since 1.0.0
     */
    @Nullable
    public Player getPlayer() {
        return player;
    }
}
