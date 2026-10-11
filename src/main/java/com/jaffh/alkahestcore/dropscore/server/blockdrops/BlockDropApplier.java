package com.jaffh.alkahestcore.dropscore.server.blockdrops;

import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalRule;
import com.jaffh.alkahestcore.dropscore.server.drops.RollContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a block's drop table to its drops, shared by the loot modifier and the fallback for blocks without a
 * loot table.
 */
final class BlockDropApplier {

    /**
     * Prevents instantiation of this utility class.
     */
    private BlockDropApplier() {
    }

    /**
     * Removes drops named by removal files, then rolls and adds dropscore drops. Conditions are tested against the
     * block's loot context, so {@code match_tool} (silk touch), {@code survives_explosion} and other block
     * conditions work.
     *
     * @param table   Table of the broken block. Never null.
     * @param drops   Drops already there (loot table and earlier modifiers). Never null, not modified.
     * @param context Loot context of the block. Never null.
     * @return A new list with the drops after removals and the added drops.
     */
    static List<ItemStack> apply(DropTable table, List<ItemStack> drops, LootContext context) {
        List<RemovalRule> activeRemovals = table.collectActiveRemovals(context);
        List<ItemStack> result = new ArrayList<>(drops);
        result.removeIf(stack -> table.removesEventDrop(stack.getItem(), activeRemovals));

        RollContext rollContext = new RollContext(fortuneLevelOf(context), 0.0F, context.getRandom());
        result.addAll(table.rollDrops(activeRemovals, context, rollContext));
        return result;
    }

    /**
     * Returns the Fortune level of the tool that broke the block.
     *
     * @param context Loot context of the block. Never null.
     * @return The level, 0 when there is no tool (explosions, pistons) or no Fortune.
     */
    private static int fortuneLevelOf(LootContext context) {
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (tool == null) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
    }
}
