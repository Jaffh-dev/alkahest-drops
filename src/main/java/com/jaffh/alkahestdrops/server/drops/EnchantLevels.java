package com.jaffh.alkahestdrops.server.drops;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Gives the enchantment levels a roll reads for {@code enchant_bonus}: the levels of the held item (weapon, shears or
 * tool), with some levels replaced, such as the Looting of {@code LootingLevelEvent} or the levels forced by
 * {@code /alkdrops simulate}. The item's enchantments are read once (Forge's {@code getAllEnchantments}), on the first
 * lookup that needs them.
 * Server thread only.
 */
public final class EnchantLevels {
    /** No enchantment at any level. */
    public static final EnchantLevels NONE = new EnchantLevels(ItemStack.EMPTY, Map.of());

    private final ItemStack item;
    private final Map<Enchantment, Integer> overrides;

    @Nullable
    private Map<Enchantment, Integer> itemLevels;

    /**
     * Creates the levels of an item with replaced levels.
     *
     * @param item      Held item whose enchantments are read. Never null, may be empty.
     * @param overrides Levels that replace the item's own. Never null.
     */
    private EnchantLevels(ItemStack item, Map<Enchantment, Integer> overrides) {
        this.item = item;
        this.overrides = Map.copyOf(overrides);
    }

    /**
     * Creates the levels of a held item.
     *
     * @param item Held item. Never null, may be empty.
     * @return The levels, {@link #NONE} for an empty item.
     */
    public static EnchantLevels ofItem(ItemStack item) {
        return item.isEmpty() ? NONE : new EnchantLevels(item, Map.of());
    }

    /**
     * Returns these levels with the level of one enchantment replaced.
     *
     * @param enchantment Enchantment to replace. Never null.
     * @param level       Its new level, 0 or more.
     * @return A copy with the replaced level.
     */
    public EnchantLevels withOverride(Enchantment enchantment, int level) {
        return withOverrides(Map.of(enchantment, level));
    }

    /**
     * Returns these levels with the levels of some enchantments replaced.
     *
     * @param levels Levels to replace, 0 or more. Never null.
     * @return This instance when there is nothing to replace, otherwise a copy.
     */
    public EnchantLevels withOverrides(Map<Enchantment, Integer> levels) {
        if (levels.isEmpty()) return this;
        Map<Enchantment, Integer> merged = new HashMap<>(overrides);
        merged.putAll(levels);
        return new EnchantLevels(item, merged);
    }

    /**
     * Returns the level of one enchantment.
     *
     * @param enchantment Enchantment to read. Never null.
     * @return The replaced level, else the held item's level, 0 when it has none.
     */
    public int levelOf(Enchantment enchantment) {
        Integer level = overrides.get(enchantment);
        if (level != null) return level;
        if (item.isEmpty()) return 0;
        if (itemLevels == null) itemLevels = item.getAllEnchantments();
        return itemLevels.getOrDefault(enchantment, 0);
    }
}
