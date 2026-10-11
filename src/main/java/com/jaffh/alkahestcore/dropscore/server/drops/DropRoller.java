package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.common.drops.DropBehavior;
import com.jaffh.alkahestcore.dropscore.common.drops.DropCount;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonus;
import net.minecraft.world.item.ItemStack;

/**
 * Rolls single drops, shared by mob and block drops. Conditions are tested by the caller.
 */
public final class DropRoller {

    /**
     * Prevents instantiation of this utility class.
     */
    private DropRoller() {
    }

    /**
     * Rolls one drop: chance first, then the count with the enchantment bonuses applied.
     *
     * @param entry   Drop to roll. Never null.
     * @param context Enchantment level, max health and random source of the roll. Never null.
     * @return The dropped stack, or {@link ItemStack#EMPTY} when the drop fails or rolls a count of 0.
     */
    public static ItemStack roll(DropEntry entry, RollContext context) {
        EnchantBonus bonus = entry.enchantBonus();
        int level = context.enchantLevel();
        double chance = bonus.chance().applyTo(entry.chance(), level);
        if (context.random().nextDouble() >= chance) return ItemStack.EMPTY;

        DropCount range = entry.count().withBonus(bonus.count(), level);
        DropBehavior behavior = entry.behavior().withBonus(bonus.roll(), level);
        return entry.createStack(behavior.type().rollCount(range, behavior, context.maxHealth(), context.random()));
    }
}
