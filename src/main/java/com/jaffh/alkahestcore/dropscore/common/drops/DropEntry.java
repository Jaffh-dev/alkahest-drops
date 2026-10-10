package com.jaffh.alkahestcore.dropscore.common.drops;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Represents one fully resolved drop: every default is already applied, so readers never null-check.
 *
 * @param item         Dropped item. Never null.
 * @param nbt          Tag copied onto every dropped stack, or null for none.
 * @param chance       Base chance (0–1) that the drop happens.
 * @param count        Base count range. Never null.
 * @param behavior    How the count is rolled. Never null.
 * @param enchantBonus Per-level bonuses from Looting. Never null.
 * @param conditions   Vanilla loot conditions that must all pass. Immutable, never null.
 */
public record DropEntry(
        Item item,
        @Nullable CompoundTag nbt,
        double chance,
        DropCount count,
        DropBehavior behavior,
        EnchantBonus enchantBonus,
        List<LootItemCondition> conditions
) {
    /**
     * Creates a drop entry and stores an immutable copy of the conditions.
     */
    public DropEntry {
        conditions = List.copyOf(conditions);
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
     * Creates the stack dropped for a rolled count.
     *
     * @param amount Rolled count.
     * @return A new stack with a copy of the NBT, or {@link ItemStack#EMPTY} when the count is 0 or less.
     */
    public ItemStack createStack(int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, amount);
        if (nbt != null) {
            stack.setTag(nbt.copy());
        }
        return stack;
    }
}
