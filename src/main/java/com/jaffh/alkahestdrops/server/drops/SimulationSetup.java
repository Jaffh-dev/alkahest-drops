package com.jaffh.alkahestdrops.server.drops;

import com.mojang.authlib.GameProfile;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/**
 * Represents where, by whom and under which forced conditions a simulated kill or block break happens.
 *
 * @param level        Level of the simulation. Never null.
 * @param origin       Position of the simulated mob or block. Never null.
 * @param actor        Who kills or breaks. Never null.
 * @param flags        Conditions forced on the simulation. Immutable, never null.
 * @param enchantments Enchantment levels forced on the simulation, in command order; they replace the levels of the
 *                     actor's held item, and every other enchantment is still read from it. Immutable, never null.
 */
public record SimulationSetup(
        ServerLevel level,
        Vec3 origin,
        SimulationActor actor,
        Set<SimulationFlag> flags,
        Map<Enchantment, Integer> enchantments) {
    private static final String BLANK_PLAYER_NAME = "Simulator";

    /**
     * Creates a setup and stores immutable copies of the flags and the enchantments, keeping their order.
     */
    public SimulationSetup {
        flags = Set.copyOf(flags);
        enchantments = Collections.unmodifiableMap(new LinkedHashMap<>(enchantments));
    }

    /**
     * Returns the forced Looting level, which vanilla {@code looting_enchant} functions must read too.
     *
     * @return The level, or empty when Looting is not forced.
     */
    public OptionalInt forcedLooting() {
        Integer level = enchantments.get(Enchantments.MOB_LOOTING);
        return level != null ? OptionalInt.of(level) : OptionalInt.empty();
    }

    /**
     * Returns the enchantment levels a roll reads: those of the held item, with the forced ones replacing them.
     *
     * @param heldItem Item the actor holds. Never null, may be empty.
     * @return The levels.
     */
    public EnchantLevels levelsOf(ItemStack heldItem) {
        return EnchantLevels.ofItem(heldItem).withOverrides(enchantments);
    }

    /**
     * Lists the enchantments a roll reads, for the report: those of the held item, with the forced ones replacing
     * them; levels of 0 are left out.
     *
     * @param heldItem Item the actor holds. Never null, may be empty.
     * @return The levels above 0, the held item's first, in a mutable ordered map.
     */
    public Map<Enchantment, Integer> effectiveEnchantments(ItemStack heldItem) {
        Map<Enchantment, Integer> levels = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(heldItem));
        levels.putAll(enchantments);
        levels.values().removeIf(level -> level <= 0);
        return levels;
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
