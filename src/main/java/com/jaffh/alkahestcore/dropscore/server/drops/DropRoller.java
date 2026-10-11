package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.common.drops.DropBehavior;
import com.jaffh.alkahestcore.dropscore.common.drops.DropCount;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

import javax.annotation.Nullable;

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
     * Rolls one item drop and applies its loot functions to the stack.
     *
     * @param entry       Drop to roll. Never null.
     * @param context     Enchantment level, scale value and random source of the roll. Never null.
     * @param lootContext Loot context of the drop, non-null whenever the drop has functions.
     * @return The dropped stack, or {@link ItemStack#EMPTY} when the drop fails, rolls a count of 0, gives
     *         experience or a function empties it.
     */
    public static ItemStack roll(DropEntry entry, RollContext context, @Nullable LootContext lootContext) {
        ItemStack stack = entry.createStack(rollCount(entry, context));
        if (stack.isEmpty() || !entry.hasFunctions()) return stack;
        return applyFunctions(entry, stack, lootContext);
    }

    /**
     * Applies the loot functions of a drop in file order, each to the result of the previous one.
     *
     * @param entry       Drop whose functions to apply. Never null.
     * @param stack       Rolled stack, with the count and NBT already set. Never null, not empty.
     * @param lootContext Loot context of the drop. Never null.
     * @return The stack the last function returns.
     */
    private static ItemStack applyFunctions(DropEntry entry, ItemStack stack, LootContext lootContext) {
        for (LootItemFunction function : entry.functions()) {
            stack = function.apply(stack, lootContext);
        }
        return stack;
    }

    /**
     * Rolls the count of one drop: chance first, then the count with the enchantment bonuses applied. For an
     * experience drop the count is the amount of experience.
     *
     * @param entry   Drop to roll. Never null.
     * @param context Enchantment level, scale value and random source of the roll. Never null.
     * @return The rolled count, 0 when the drop fails.
     */
    public static int rollCount(DropEntry entry, RollContext context) {
        EnchantBonus bonus = entry.enchantBonus();
        int level = context.enchantLevel();
        double chance = bonus.chance().applyTo(entry.chance(), level);
        if (context.random().nextDouble() >= chance) return 0;

        DropCount range = entry.count().withBonus(bonus.count(), level);
        DropBehavior behavior = entry.behavior().withBonus(bonus.roll(), level);
        return behavior.type().rollCount(range, behavior, context.scaleValue(), context.random());
    }
}
