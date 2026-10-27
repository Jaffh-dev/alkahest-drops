package com.jaffh.alkahestdrops.common.drops;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Represents one fully resolved drop: every default is already applied. An experience drop has no item; its rolled
 * count is the amount of experience, so check {@link #isExperience()} before reading {@code item}.
 *
 * @param item         Dropped item, or null for an experience drop.
 * @param nbt          Tag copied onto every dropped stack, or null for none (always null for experience).
 * @param chance       Base chance (0–1) that the drop happens.
 * @param count        Base count range. Never null.
 * @param behavior     How the count is rolled. Never null.
 * @param enchantBonuses Per-level bonuses of each enchantment in {@code enchant_bonus}. Never null.
 * @param conditions   Vanilla loot conditions that must all pass. Immutable, never null.
 * @param functions    Vanilla loot functions applied in order to every dropped stack, after {@code nbt}. Always
 *                     empty for experience. Immutable, never null.
 * @param tracking     Per-player settings: pity, first kill and per-player rolls. Never null.
 * @param trigger      What makes the drop happen, death by default; always death for blocks. Never null.
 * @param onDrop       What happens when the dropped item spawns, {@link DropActions#NONE} for nothing (always none
 *                     for experience). Never null.
 */
public record DropEntry(
        @Nullable Item item,
        @Nullable CompoundTag nbt,
        double chance,
        DropCount count,
        DropBehavior behavior,
        EnchantBonuses enchantBonuses,
        List<LootItemCondition> conditions,
        List<LootItemFunction> functions,
        DropTracking tracking,
        TriggerSettings trigger,
        DropActions onDrop
) {
    /**
     * Creates a drop entry and stores immutable copies of the conditions and functions.
     */
    public DropEntry {
        conditions = List.copyOf(conditions);
        functions = List.copyOf(functions);
    }

    /**
     * Tells whether this drop gives experience instead of an item.
     *
     * @return True when the drop has no item.
     */
    public boolean isExperience() {
        return item == null;
    }

    /**
     * Tells whether this drop has loot conditions to test.
     *
     * @return True when at least one condition is set.
     */
    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    /**
     * Tells whether this drop has loot functions to apply.
     *
     * @return True when at least one function is set.
     */
    public boolean hasFunctions() {
        return !functions.isEmpty();
    }

    /**
     * Creates the stack dropped for a rolled count.
     *
     * @param amount Rolled count.
     * @return A new stack with a copy of the NBT, or {@link ItemStack#EMPTY} when the count is 0 or less or this is
     *         an experience drop.
     */
    public ItemStack createStack(int amount) {
        if (amount <= 0 || item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, amount);
        if (nbt != null) {
            stack.setTag(nbt.copy());
        }
        return stack;
    }
}
