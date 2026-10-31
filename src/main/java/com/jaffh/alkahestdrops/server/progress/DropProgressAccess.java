package com.jaffh.alkahestdrops.server.progress;

import com.jaffh.alkahestdrops.server.drops.RealPlayers;
import com.jaffh.alkahestdrops.server.drops.ServerThread;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Reads and resets the drop progress of a player for {@link com.jaffh.alkahestdrops.api.AlkahestDropsApi}. Players
 * without progress (machines' fake players, client-side players) read as having none, and resetting them changes
 * nothing.
 */
public final class DropProgressAccess {

    /**
     * Prevents instantiation of this utility class.
     */
    private DropProgressAccess() {
    }

    /**
     * Returns how many times in a row a pity drop missed for a player.
     *
     * @param player  Player. Never null.
     * @param dropKey Key of the drop. Never null.
     * @return The misses since the drop last dropped, 0 when none or without progress.
     * @throws IllegalStateException If called outside the server thread.
     */
    public static int pityMisses(Player player, String dropKey) {
        DropProgress progress = progressOf(player);
        return progress != null ? progress.missesOf(dropKey) : 0;
    }

    /**
     * Tells whether a player already received a one-time drop.
     *
     * @param player  Player. Never null.
     * @param dropKey Key of the drop. Never null.
     * @return True when the drop happened for the player; false without progress.
     * @throws IllegalStateException If called outside the server thread.
     */
    public static boolean hasReceivedFirstDrop(Player player, String dropKey) {
        DropProgress progress = progressOf(player);
        return progress != null && progress.hasReceivedFirstDrop(dropKey);
    }

    /**
     * Clears a player's pity misses of matching drops.
     *
     * @param player   Player. Never null.
     * @param dropKeys Which drop keys to clear. Never null.
     * @return How many pity counters were cleared; 0 without progress.
     * @throws IllegalStateException If called outside the server thread.
     */
    public static int resetPity(Player player, Predicate<String> dropKeys) {
        DropProgress progress = progressOf(player);
        return progress != null ? progress.resetMisses(dropKeys) : 0;
    }

    /**
     * Forgets the one-time drops of matching drops a player received, so they can drop again.
     *
     * @param player   Player. Never null.
     * @param dropKeys Which drop keys to forget. Never null.
     * @return How many one-time drops were forgotten; 0 without progress.
     * @throws IllegalStateException If called outside the server thread.
     */
    public static int resetFirstDrops(Player player, Predicate<String> dropKeys) {
        DropProgress progress = progressOf(player);
        return progress != null ? progress.resetFirstDrops(dropKeys) : 0;
    }

    /**
     * Returns the progress of a player that has one, checking the thread.
     *
     * @param player Player. Never null.
     * @return The progress, or null for machines' fake players, client-side players or when no server runs.
     * @throws IllegalStateException If called outside the server thread.
     */
    @Nullable
    private static DropProgress progressOf(Player player) {
        if (ServerThread.current() == null || !RealPlayers.isReal(player)) return null;
        return DropProgress.of(player);
    }
}
