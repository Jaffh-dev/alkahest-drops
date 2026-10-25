package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.jaffh.alkahestcore.AlkahestCore;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.common.drops.DropTrigger;
import com.jaffh.alkahestcore.dropscore.server.drops.DropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Keeps the tick timers of a mob's periodic and hit drops in its persistent data (saved with the mob), as the game
 * time each drop may drop again, by progress key. Game time is shared by every dimension, so timers survive
 * portals. A periodic drop first drops one interval after the timer starts; a hit drop drops on the first hit, then
 * waits its cooldown.
 */
final class DropTimers {
    private static final String TAG = AlkahestCore.ID + ":drop_timers";

    /**
     * Prevents instantiation of this utility class.
     */
    private DropTimers() {
    }

    /**
     * Collects the drops of a table whose timer has run out and restarts their timers. A drop without a tick
     * setting (a hit drop without cooldown) is always due. Allocates only when a drop is due, so it is cheap to
     * call every tick.
     *
     * @param mob   Mob holding the timers. Never null.
     * @param table Table of the trigger. Never null.
     * @param now   Current game time.
     * @return The due drops, compared by identity; an empty immutable set when none is due.
     */
    static Set<DropEntry> collectDue(LivingEntity mob, DropTable table, long now) {
        CompoundTag timers = null;
        Set<DropEntry> due = null;
        for (DropFile file : table.dropFiles()) {
            for (DropEntry entry : file.entries()) {
                if (entry.trigger().ticks() > 0) {
                    if (timers == null) timers = timersOf(mob);
                    if (!restartIfDue(timers, entry, now)) continue;
                }
                if (due == null) due = Collections.newSetFromMap(new IdentityHashMap<>());
                due.add(entry);
            }
        }
        return due == null ? Set.of() : due;
    }

    /**
     * Tells whether a timed drop is due and, when it is, starts its next wait. A timer seen for the first time
     * starts now; only a hit drop drops on that first check.
     *
     * @param timers Timers of the mob. Never null.
     * @param entry  Drop with a tick setting above 0. Never null.
     * @param now    Current game time.
     * @return True when the drop may roll now.
     */
    private static boolean restartIfDue(CompoundTag timers, DropEntry entry, long now) {
        String key = entry.tracking().key();
        long next = now + entry.trigger().ticks();
        if (!timers.contains(key, Tag.TAG_LONG)) {
            timers.putLong(key, next);
            return entry.trigger().trigger() != DropTrigger.PERIODIC;
        }
        if (now < timers.getLong(key)) return false;
        timers.putLong(key, next);
        return true;
    }

    /**
     * Returns the timer tag of a mob, adding it on first use.
     *
     * @param mob Mob. Never null.
     * @return The tag inside the mob's persistent data.
     */
    private static CompoundTag timersOf(LivingEntity mob) {
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(TAG, Tag.TAG_COMPOUND)) data.put(TAG, new CompoundTag());
        return data.getCompound(TAG);
    }
}
