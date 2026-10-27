package com.jaffh.alkahestdrops.server.drops;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Represents who kills the mob or breaks the block in a simulation ({@code by ...} in {@code /alkdrops simulate}).
 */
public sealed interface SimulationActor {

    /**
     * A fake player: a copy of a real player (name, equipment, position), or a blank one from the console.
     *
     * @param template Player to copy, or null for a blank player with empty hands.
     */
    record AsPlayer(@Nullable Player template) implements SimulationActor {
    }

    /**
     * A mob of a type, created for the simulation and never added to the level. Mob kills only.
     *
     * @param type Type of the killer. Never null.
     */
    record AsEntity(EntityType<?> type) implements SimulationActor {
    }

    /**
     * Nobody: generic damage for mobs, a break without a player (netherite pickaxe as tool) for blocks.
     */
    record Nobody() implements SimulationActor {
    }

    /**
     * A TNT explosion: no player, no tool, and an explosion radius for {@code survives_explosion}. Block breaks only.
     */
    record Explosion() implements SimulationActor {
    }
}
