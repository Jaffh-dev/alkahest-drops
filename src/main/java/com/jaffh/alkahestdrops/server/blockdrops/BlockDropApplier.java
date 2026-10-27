package com.jaffh.alkahestdrops.server.blockdrops;

import com.jaffh.alkahestdrops.api.DropOrigin;
import com.jaffh.alkahestdrops.server.drops.DropTable;
import com.jaffh.alkahestdrops.server.drops.EnchantLevels;
import com.jaffh.alkahestdrops.server.drops.RollContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

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
     * Removes drops named by removal files, then rolls and adds drop file drops. Conditions are tested against the
     * block's loot context, so {@code match_tool} (silk touch), {@code survives_explosion} and other block
     * conditions work.
     *
     * @param table   Table of the broken block. Never null.
     * @param drops   Drops already there (loot table and earlier modifiers). Never null, not modified.
     * @param context Loot context of the block. Never null.
     * @return A new list with the drops after removals and the added drops.
     */
    static List<ItemStack> apply(DropTable table, List<ItemStack> drops, LootContext context) {
        return table.applyTo(drops, context, rollContextOf(context));
    }

    /**
     * Builds the roll context of a block: the enchantments of the tool, the block's hardness, the breaking player and
     * the block the drops spawn from.
     *
     * @param context Loot context of the block, with a block state and an origin. Never null.
     * @return The roll context; no enchantment without a tool (explosions, pistons), the player is null without a
     *         breaking player, and the origin is null without a block state or position.
     */
    static RollContext rollContextOf(LootContext context) {
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        EnchantLevels levels = tool != null ? EnchantLevels.ofItem(tool) : EnchantLevels.NONE;
        Player player = context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof Player breaker ? breaker : null;
        RollContext roll = new RollContext(levels, hardnessOf(context), context.getRandom(), player);
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        return state != null && origin != null ? roll.withOrigin(new DropOrigin.Block(state, BlockPos.containing(origin))) : roll;
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
        return state.getDestroySpeed(context.getLevel(), BlockPos.containing(origin));
    }
}
