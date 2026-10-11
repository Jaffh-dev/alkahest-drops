package com.jaffh.alkahestcore.dropscore.server.blockdrops;

import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalRule;
import com.jaffh.alkahestcore.dropscore.server.drops.RollContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a block's drop table to its drops, shared by the loot modifier, the fallback for blocks without a
 * loot table and the experience of a break.
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

        result.addAll(table.rollDrops(activeRemovals, context, rollContextOf(context)));
        return result;
    }

    /**
     * Builds the roll context of a block: the Fortune level of the tool and the block's hardness.
     *
     * @param context Loot context of the block, with a block state and an origin. Never null.
     * @return The roll context; Fortune is 0 when there is no tool (explosions, pistons) or no Fortune.
     */
    static RollContext rollContextOf(LootContext context) {
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        int fortuneLevel = tool != null ? EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool) : 0;
        return new RollContext(fortuneLevel, hardnessOf(context), context.getRandom());
    }

    /**
     * Returns the hardness of the block, the value the {@code hardness} behavior scales with.
     *
     * @param context Loot context of the block. Never null.
     * @return The hardness, such as 1.5 for stone and 50 for obsidian; -1 for unbreakable blocks, 0 without a
     *         block state or origin.
     */
    private static float hardnessOf(LootContext context) {
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (state == null || origin == null) return 0.0F;
        return state.getDestroySpeed(context.getLevel(), new BlockPos(origin));
    }
}
