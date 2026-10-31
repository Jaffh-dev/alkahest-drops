package com.jaffh.alkahestdrops.server.progress;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/**
 * Holds the drop progress of one player, attached as a capability: the misses in a row of every pity drop and the
 * one-time drops ({@code first_kill}, {@code first_break}) already received. Keys are the progress keys of the drops
 * ({@code "<mob|block>:<fileId>#<dropIndex>"}).
 */
public final class DropProgress {
    /** Capability of the drop progress, attached to every player on the server. */
    public static final Capability<DropProgress> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    private static final String MISSES_KEY = "misses";
    private static final String FIRST_DROPS_KEY = "first_drops";

    private final Map<String, Integer> missesByRule = new HashMap<>();
    private final Set<String> firstDropsDone = new HashSet<>();

    /**
     * Returns the drop progress of a player.
     *
     * @param player Player, or null for none.
     * @return The progress, or null when there is no player or the player has no progress (client side).
     */
    @Nullable
    public static DropProgress of(@Nullable Player player) {
        if (player == null) return null;
        return player.getCapability(CAPABILITY).resolve().orElse(null);
    }

    /**
     * Returns how many times in a row a pity drop missed.
     *
     * @param key Progress key of the drop. Never null.
     * @return The misses since the last drop, 0 when none.
     */
    public int missesOf(String key) {
        return missesByRule.getOrDefault(key, 0);
    }

    /**
     * Counts one more miss of a pity drop.
     *
     * @param key Progress key of the drop. Never null.
     */
    public void recordMiss(String key) {
        missesByRule.merge(key, 1, Integer::sum);
    }

    /**
     * Resets the misses of a pity drop after it dropped.
     *
     * @param key Progress key of the drop. Never null.
     */
    public void resetMisses(String key) {
        missesByRule.remove(key);
    }

    /**
     * Marks a one-time drop as received, if it was not yet.
     *
     * @param key Progress key of the drop. Never null.
     * @return True when the player had not received the drop before, so it drops now.
     */
    public boolean claimFirstDrop(String key) {
        return firstDropsDone.add(key);
    }

    /**
     * Tells whether a one-time drop was already received.
     *
     * @param key Progress key of the drop. Never null.
     * @return True when the drop happened for this player.
     */
    public boolean hasReceivedFirstDrop(String key) {
        return firstDropsDone.contains(key);
    }

    /**
     * Forgets the one-time drops received whose key matches, so they can drop again.
     *
     * @param keys Which progress keys to forget. Never null.
     * @return How many one-time drops were forgotten.
     */
    public int resetFirstDrops(Predicate<String> keys) {
        int before = firstDropsDone.size();
        firstDropsDone.removeIf(keys);
        return before - firstDropsDone.size();
    }

    /**
     * Clears the misses of every pity drop whose key matches.
     *
     * @param keys Which progress keys to clear. Never null.
     * @return How many pity counters were cleared.
     */
    public int resetMisses(Predicate<String> keys) {
        int before = missesByRule.size();
        missesByRule.keySet().removeIf(keys);
        return before - missesByRule.size();
    }

    /**
     * Replaces this progress with a copy of another, used when a player respawns or returns from the End.
     *
     * @param other Progress of the old player entity. Never null.
     */
    public void copyFrom(DropProgress other) {
        missesByRule.clear();
        missesByRule.putAll(other.missesByRule);
        firstDropsDone.clear();
        firstDropsDone.addAll(other.firstDropsDone);
    }

    /**
     * Writes this progress to NBT.
     *
     * @return A tag with the misses per drop and the list of one-time drops received.
     */
    public CompoundTag save() {
        CompoundTag misses = new CompoundTag();
        missesByRule.forEach(misses::putInt);
        ListTag firstDrops = new ListTag();
        for (String key : firstDropsDone) {
            firstDrops.add(StringTag.valueOf(key));
        }

        CompoundTag tag = new CompoundTag();
        tag.put(MISSES_KEY, misses);
        tag.put(FIRST_DROPS_KEY, firstDrops);
        return tag;
    }

    /**
     * Replaces this progress with the one stored in NBT.
     *
     * @param tag Tag written by {@link #save()}. Never null.
     */
    public void load(CompoundTag tag) {
        missesByRule.clear();
        CompoundTag misses = tag.getCompound(MISSES_KEY);
        for (String key : misses.getAllKeys()) {
            missesByRule.put(key, misses.getInt(key));
        }
        firstDropsDone.clear();
        ListTag firstDrops = tag.getList(FIRST_DROPS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < firstDrops.size(); i++) {
            firstDropsDone.add(firstDrops.getString(i));
        }
    }
}
