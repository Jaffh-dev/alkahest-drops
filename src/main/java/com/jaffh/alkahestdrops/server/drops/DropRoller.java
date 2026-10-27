package com.jaffh.alkahestdrops.server.drops;

import com.jaffh.alkahestdrops.common.drops.DropBehavior;
import com.jaffh.alkahestdrops.common.drops.DropCount;
import com.jaffh.alkahestdrops.common.drops.DropEntry;
import com.jaffh.alkahestdrops.common.drops.DropTracking;
import com.jaffh.alkahestdrops.common.drops.EnchantBonuses;
import com.jaffh.alkahestdrops.server.progress.DropProgress;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

import javax.annotation.Nullable;

/**
 * Rolls single drops, shared by mob and block drops, updating the player's pity and one-time drop progress.
 * Conditions are tested by the caller.
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
     * @param context     Enchantment levels, scale value and random source of the roll. Never null.
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
     * Rolls the count of one drop, with the player's progress: a one-time drop happens once per player, ignoring
     * its chance; a pity drop is forced on the miss that reaches its pity, and any drop resets its misses. Without a
     * player, pity is ignored and one-time drops never happen. For an experience drop the count is the amount of
     * experience.
     *
     * @param entry   Drop to roll. Never null.
     * @param context Enchantment levels, scale value, random source and player of the roll. Never null.
     * @return The rolled count, 0 when the drop fails.
     */
    public static int rollCount(DropEntry entry, RollContext context) {
        DropTracking tracking = entry.tracking();
        if (!tracking.needsProgress()) return rollChanceAndCount(entry, context);

        DropProgress progress = context.progress();
        if (tracking.isFirstDrop()) {
            return progress != null && progress.claimFirstDrop(tracking.key()) ? rollForcedCount(entry, context) : 0;
        }
        if (progress == null) return rollChanceAndCount(entry, context);
        return rollWithPity(entry, context, progress);
    }

    /**
     * Rolls a pity drop: a miss is counted, unless it is the miss that reaches the pity, which forces the drop.
     *
     * @param entry    Drop with pity. Never null.
     * @param context  Roll context. Never null.
     * @param progress Progress of the roll's player. Never null.
     * @return The rolled count, 0 on a counted miss.
     */
    private static int rollWithPity(DropEntry entry, RollContext context, DropProgress progress) {
        String key = entry.tracking().key();
        int count = rollChanceAndCount(entry, context);
        if (count > 0) {
            progress.resetMisses(key);
            return count;
        }
        if (progress.missesOf(key) + 1 >= entry.tracking().pity()) {
            progress.resetMisses(key);
            return rollForcedCount(entry, context);
        }
        progress.recordMiss(key);
        return 0;
    }

    /**
     * Rolls the count of a drop that must happen: the chance is skipped and the count is at least 1.
     *
     * @param entry   Drop to roll. Never null.
     * @param context Roll context. Never null.
     * @return The rolled count, 1 or more.
     */
    private static int rollForcedCount(DropEntry entry, RollContext context) {
        return Math.max(1, rollCountOnly(entry, context));
    }

    /**
     * Rolls the chance of a drop, then its count.
     *
     * @param entry   Drop to roll. Never null.
     * @param context Roll context. Never null.
     * @return The rolled count, 0 when the chance fails.
     */
    private static int rollChanceAndCount(DropEntry entry, RollContext context) {
        double chance = entry.enchantBonuses().applyToChance(entry.chance(), context.enchantLevels()::levelOf);
        if (context.random().nextDouble() >= chance) return 0;
        return rollCountOnly(entry, context);
    }

    /**
     * Rolls the count of a drop with the enchantment bonuses applied, without its chance.
     *
     * @param entry   Drop to roll. Never null.
     * @param context Roll context. Never null.
     * @return The rolled count, 0 or more.
     */
    private static int rollCountOnly(DropEntry entry, RollContext context) {
        EnchantBonuses bonuses = entry.enchantBonuses();
        EnchantLevels levels = context.enchantLevels();
        DropCount range = bonuses.applyToCount(entry.count(), levels::levelOf);
        DropBehavior behavior = bonuses.applyToBehavior(entry.behavior(), levels::levelOf);
        float scaleValue = behavior.type().readsDamageTaken() ? context.damageTaken() : context.scaleValue();
        return behavior.type().rollCount(range, behavior, scaleValue, context.random());
    }
}
