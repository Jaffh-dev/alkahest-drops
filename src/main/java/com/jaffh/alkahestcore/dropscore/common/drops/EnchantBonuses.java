package com.jaffh.alkahestcore.dropscore.common.drops;

import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Represents every enchantment bonus of one drop. The bonuses of different enchantments add up; each keeps its own
 * caps, and chances stay at most 1.
 *
 * @param entries One entry per enchantment, in file order. Immutable, never null.
 */
public record EnchantBonuses(List<EnchantBonusEntry> entries) {
    /** No bonus from any enchantment. */
    public static final EnchantBonuses NONE = new EnchantBonuses(List.of());

    /**
     * Creates the bonuses and stores an immutable copy of the entries.
     */
    public EnchantBonuses {
        entries = List.copyOf(entries);
    }

    /**
     * Tells whether no enchantment gives a bonus.
     *
     * @return True when there is no entry.
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Applies every chance bonus to a base chance.
     *
     * @param baseChance Chance without bonus (0–1).
     * @param levels     Level of each enchantment, 0 or more. Never null.
     * @return The raised chance, at most 1.
     */
    public double applyToChance(double baseChance, ToIntFunction<Enchantment> levels) {
        double chance = baseChance;
        for (EnchantBonusEntry entry : entries) {
            chance = entry.bonus().chance().applyTo(chance, levels.applyAsInt(entry.enchantment()));
        }
        return chance;
    }

    /**
     * Applies every count bonus to a count range.
     *
     * @param count  Range without bonus. Never null.
     * @param levels Level of each enchantment, 0 or more. Never null.
     * @return The raised range.
     */
    public DropCount applyToCount(DropCount count, ToIntFunction<Enchantment> levels) {
        DropCount range = count;
        for (EnchantBonusEntry entry : entries) {
            range = range.withBonus(entry.bonus().count(), levels.applyAsInt(entry.enchantment()));
        }
        return range;
    }

    /**
     * Applies every roll bonus to a behavior.
     *
     * @param behavior Behavior without bonus. Never null.
     * @param levels   Level of each enchantment, 0 or more. Never null.
     * @return The raised behavior; unchanged for types without roll settings.
     */
    public DropBehavior applyToBehavior(DropBehavior behavior, ToIntFunction<Enchantment> levels) {
        DropBehavior result = behavior;
        for (EnchantBonusEntry entry : entries) {
            result = result.withBonus(entry.bonus().roll(), levels.applyAsInt(entry.enchantment()));
        }
        return result;
    }

    /**
     * Tells whether any enchantment raises the drop chance.
     *
     * @return True when an entry has a chance bonus above 0.
     */
    public boolean hasChanceBonus() {
        return entries.stream().anyMatch(entry -> entry.bonus().chance().bonusPerLevel() > 0);
    }

    /**
     * Tells whether one enchantment gives a bonus.
     *
     * @param enchantment Enchantment to look for. Never null.
     * @return True when an entry uses it.
     */
    public boolean has(Enchantment enchantment) {
        return entries.stream().anyMatch(entry -> entry.enchantment() == enchantment);
    }
}
