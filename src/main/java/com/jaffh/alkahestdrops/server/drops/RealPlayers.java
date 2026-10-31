package com.jaffh.alkahestdrops.server.drops;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Tells real players apart from the fake players machine mods use to kill mobs and break blocks, so farms get no drop
 * progress and fail the real-player conditions.
 */
public final class RealPlayers {

    /**
     * Prevents instantiation of this utility class.
     */
    private RealPlayers() {}

    /**
     * Tells whether an entity is a real player: any player except fake players, with the simulator's player counted as
     * real since it stands in for one.
     *
     * @param entity Entity to check, or null.
     * @return True for a player that is not a machine's fake player.
     */
    public static boolean isReal(@Nullable Entity entity) {
        if (!(entity instanceof Player)) return false;
        return !(entity instanceof FakePlayer) || entity instanceof SimulatedPlayer;
    }
}
