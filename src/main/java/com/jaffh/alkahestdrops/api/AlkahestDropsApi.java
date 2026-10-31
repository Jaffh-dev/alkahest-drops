package com.jaffh.alkahestdrops.api;

import com.jaffh.alkahestdrops.server.drops.DropApiReader;
import com.jaffh.alkahestdrops.server.progress.DropProgressAccess;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

/**
 * Reads what the loaded drop and removal files do to a mob or block, and reads or resets the drop progress of players
 * (pity misses, one-time drops): for wiki mods, quest mods, recipe viewers and server tools. Works on the running
 * server (singleplayer included), so call it on the server thread. Drop views follow the last datapack load; listen to
 * {@link com.jaffh.alkahestdrops.api.event.DropFilesLoadedEvent} to refresh anything cached from them. Progress keys
 * are the drop keys of {@link AddedDrop#dropKey()}; machines' fake players have no progress.
 *
 * @since 1.0.0
 */
public final class AlkahestDropsApi {

    /**
     * Prevents instantiation of this utility class.
     */
    private AlkahestDropsApi() {}

    /**
     * Returns what Alkahest Drops does to the drops of a mob for one trigger: drop files that name it by id, tag,
     * {@code mod:*} or {@code *}.
     *
     * @param type    Mob type. Never null.
     * @param trigger Trigger whose drops to read; {@link DropTrigger#DEATH} for the death drops. Never null.
     * @return The added drops and the removals; {@link TargetDrops#EMPTY} when no server is running.
     * @throws IllegalStateException If called outside the server thread.
     * @since 1.0.0
     */
    public static TargetDrops getMobDrops(EntityType<?> type, DropTrigger trigger) {
        return DropApiReader.mobDrops(type, trigger);
    }

    /**
     * Returns what Alkahest Drops does to the drops of a block when it breaks.
     *
     * @param block Block. Never null.
     * @return The added drops and the removals; {@link TargetDrops#EMPTY} when no server is running.
     * @throws IllegalStateException If called outside the server thread.
     * @since 1.0.0
     */
    public static TargetDrops getBlockDrops(Block block) {
        return DropApiReader.blockDrops(block);
    }

    /**
     * Returns how many times in a row a drop with pity missed for a player. The drop is forced on the roll that
     * reaches {@link AddedDrop#pity()}, so {@code pity - misses} rolls are left at most.
     *
     * @param player  Player. Never null.
     * @param dropKey Key of the drop, from {@link AddedDrop#dropKey()}. Never null.
     * @return The misses since the drop last dropped; 0 when none, for a fake player or without a server.
     * @throws IllegalStateException If called outside the server thread.
     * @since 1.0.0
     */
    public static int getPityMisses(Player player, String dropKey) {
        return DropProgressAccess.pityMisses(player, dropKey);
    }

    /**
     * Tells whether a player already received a one-time drop ({@link AddedDrop#isFirstDrop()}).
     *
     * @param player  Player. Never null.
     * @param dropKey Key of the drop, from {@link AddedDrop#dropKey()}. Never null.
     * @return True when the drop happened for the player; false for a fake player or without a server.
     * @throws IllegalStateException If called outside the server thread.
     * @since 1.0.0
     */
    public static boolean hasReceivedFirstDrop(Player player, String dropKey) {
        return DropProgressAccess.hasReceivedFirstDrop(player, dropKey);
    }

    /**
     * Clears a player's pity misses of one drop, so its bad-luck protection starts over (like
     * {@code /alkdrops reset <player> pity}). The one-time drop progress is left alone.
     *
     * @param player  Player. Never null.
     * @param dropKey Key of the drop, from {@link AddedDrop#dropKey()}. Never null.
     * @return True when the player had misses of the drop.
     * @throws IllegalStateException If called outside the server thread.
     * @since 1.0.0
     */
    public static boolean resetPity(Player player, String dropKey) {
        return DropProgressAccess.resetPity(player, dropKey::equals) > 0;
    }

    /**
     * Forgets that a player received a one-time drop, so it can drop for them again (like
     * {@code /alkdrops reset <player> first}). Pity misses are left alone.
     *
     * @param player  Player. Never null.
     * @param dropKey Key of the drop, from {@link AddedDrop#dropKey()}. Never null.
     * @return True when the player had received the drop.
     * @throws IllegalStateException If called outside the server thread.
     * @since 1.0.0
     */
    public static boolean resetFirstDrop(Player player, String dropKey) {
        return DropProgressAccess.resetFirstDrops(player, dropKey::equals) > 0;
    }

    /**
     * Clears the pity misses of every drop for a player. One-time drops are left alone.
     *
     * @param player Player. Never null.
     * @return How many pity counters were cleared.
     * @throws IllegalStateException If called outside the server thread.
     * @since 1.0.0
     */
    public static int resetAllPity(Player player) {
        return DropProgressAccess.resetPity(player, key -> true);
    }

    /**
     * Forgets every one-time drop a player received, mobs ({@code first_kill}) and blocks ({@code first_break}). Pity
     * misses are left alone.
     *
     * @param player Player. Never null.
     * @return How many one-time drops were forgotten.
     * @throws IllegalStateException If called outside the server thread.
     * @since 1.0.0
     */
    public static int resetAllFirstDrops(Player player) {
        return DropProgressAccess.resetFirstDrops(player, key -> true);
    }
}
