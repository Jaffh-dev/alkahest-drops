package com.jaffh.alkahestcore.dropscore.server.drops;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Fake player that stands in for a real player in {@code /dropscore simulate}. Unlike the fake players of machine
 * mods, it counts as a real player ({@link RealPlayers#isReal}): it keeps its own drop progress, so pity and
 * one-time drops show in the report, and the {@code killed_by_real_player} / {@code broken_by_real_player}
 * conditions pass for it.
 */
public class SimulatedPlayer extends FakePlayer {

    /**
     * Creates a simulated player that is never added to the level.
     *
     * @param level   Level of the simulation. Never null.
     * @param profile Name and id of the player. Never null.
     */
    public SimulatedPlayer(ServerLevel level, GameProfile profile) {
        super(level, profile);
    }
}
