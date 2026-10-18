package com.jaffh.alkahestcore.dropscore.server.drops;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Represents where, by whom and under which forced conditions a simulated kill or block break happens.
 *
 * @param level        Level of the simulation. Never null.
 * @param origin       Position of the simulated mob or block. Never null.
 * @param actor        Who kills or breaks. Never null.
 * @param flags        Conditions forced on the simulation. Immutable, never null.
 * @param enchantLevel Looting or Fortune level forced on the simulation; empty to read it from the actor's main hand.
 */
public record SimulationSetup(ServerLevel level, Vec3 origin, SimulationActor actor, Set<SimulationFlag> flags,
                              OptionalInt enchantLevel) {
    private static final String BLANK_PLAYER_NAME = "Simulator";

    /**
     * Creates a setup and stores an immutable copy of the flags.
     */
    public SimulationSetup {
        flags = Set.copyOf(flags);
    }

    /**
     * Tells whether a condition is forced.
     *
     * @param flag Condition to check. Never null.
     * @return True when the flag is set.
     */
    public boolean has(SimulationFlag flag) {
        return flags.contains(flag);
    }

    /**
     * Creates the player of an {@link SimulationActor.AsPlayer} actor: a copy of the template's name, equipment and
     * position, or a blank player at the origin, crouching when {@link SimulationFlag#SNEAKING} is set. It is a
     * {@link SimulatedPlayer}, so it counts as a real player with its own drop progress; it is never added to the
     * level, so the real player and their progress are never changed.
     *
     * @param actor Player actor. Never null.
     * @return The fake player.
     */
    public Player createPlayer(SimulationActor.AsPlayer actor) {
        Player template = actor.template();
        String name = template != null ? template.getGameProfile().getName() : BLANK_PLAYER_NAME;
        SimulatedPlayer player = new SimulatedPlayer(level, new GameProfile(UUID.randomUUID(), name));
        if (template != null) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                player.setItemSlot(slot, template.getItemBySlot(slot).copy());
            }
            player.moveTo(template.getX(), template.getY(), template.getZ(), template.getYRot(), template.getXRot());
        } else {
            player.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        }
        if (has(SimulationFlag.SNEAKING)) player.setPose(Pose.CROUCHING);
        return player;
    }
}
