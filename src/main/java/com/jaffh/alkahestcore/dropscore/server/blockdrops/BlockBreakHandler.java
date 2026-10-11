package com.jaffh.alkahestcore.dropscore.server.blockdrops;

import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Handles what a loot modifier cannot do on {@link BlockEvent.BreakEvent}, which fires before the block is removed:
 * the experience of the break (turned off by experience removal entries, raised by experience drops), and the drops
 * of blocks whose loot table is {@code minecraft:empty} ({@code noDrops()}), since those never run loot modifiers.
 * Only player breaks give block experience, so this event covers every experience case; the fallback drops cover
 * player breaks only, not explosions or pistons.
 */
public class BlockBreakHandler {

    /**
     * Sets the experience of a block break: zeroed by passing experience removal entries, raised by experience
     * drops. Like vanilla experience, it is skipped in creative and when the player cannot harvest the block. Runs at
     * LOW priority so changes of other mods at NORMAL priority are overridden.
     *
     * @param event Break event of the block.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getWorld() instanceof ServerLevel level)) return;

        BlockState state = event.getState();
        DropTable table = BlockDropManager.getTable(state.getBlock());
        if (!table.affectsExperience()) return;

        Player player = event.getPlayer();
        BlockPos pos = event.getPos();
        if (player.isCreative() || !state.canHarvestBlock(level, pos, player)) return;

        LootContext context = createLootContext(level, pos, state, player, player.getMainHandItem());
        event.setExpToDrop(table.applyExperience(event.getExpToDrop(), context, BlockDropApplier.rollContextOf(context)));
    }

    /**
     * Schedules the drops of a block without a loot table, as vanilla would drop them: not in creative and only
     * when the player can harvest the block. Runs at LOWEST priority so canceled breaks are skipped, and drops
     * after the tick, once the block is really gone.
     *
     * @param event Break event of the block.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreakWithoutLootTable(BlockEvent.BreakEvent event) {
        if (!(event.getWorld() instanceof ServerLevel level)) return;

        BlockState state = event.getState();
        Block block = state.getBlock();
        if (block.getLootTable() != BuiltInLootTables.EMPTY) return;

        DropTable table = BlockDropManager.getTable(block);
        if (table.dropFiles().isEmpty()) return;

        Player player = event.getPlayer();
        BlockPos pos = event.getPos();
        if (player.isCreative() || !state.canHarvestBlock(level, pos, player)) return;

        LootContext context = createLootContext(level, pos, state, player, player.getMainHandItem().copy());
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> dropIfBroken(level, pos, state, table, context)));
    }

    /**
     * Rolls and pops the drops of a block without a loot table, unless the break did not happen.
     *
     * @param level   Level of the block. Never null.
     * @param pos     Position of the block. Never null.
     * @param state   State of the block when the break started. Never null.
     * @param table   Table of the block. Never null.
     * @param context Loot context built when the break started. Never null.
     */
    private static void dropIfBroken(ServerLevel level, BlockPos pos, BlockState state, DropTable table, LootContext context) {
        if (level.getBlockState(pos) == state) return;

        List<ItemStack> drops = BlockDropApplier.apply(table, List.of(), context);
        for (ItemStack stack : drops) {
            Block.popResource(level, pos, stack);
        }
    }

    /**
     * Builds the loot context the block's loot table would get, to test the conditions of drop and removal files.
     *
     * @param level  Level of the block. Never null.
     * @param pos    Position of the block. Never null.
     * @param state  State of the block. Never null.
     * @param player Player breaking the block. Never null.
     * @param tool   Tool breaking the block. Never null.
     * @return A loot context with the block state, block entity, origin, tool and player.
     */
    private static LootContext createLootContext(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        return new LootContext.Builder(level)
                .withRandom(level.getRandom())
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, tool)
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, level.getBlockEntity(pos))
                .create(LootContextParamSets.BLOCK);
    }
}
