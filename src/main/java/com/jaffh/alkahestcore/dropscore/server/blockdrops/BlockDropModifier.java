package com.jaffh.alkahestcore.dropscore.server.blockdrops;

import com.google.gson.JsonObject;
import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.GlobalLootModifierSerializer;
import net.minecraftforge.common.loot.LootModifier;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Global Loot Modifier that applies the block drop tables: it removes drops of the block's loot table first,
 * then rolls and adds dropscore drops. Runs on every loot table and ignores the ones that are not a block break.
 * Blocks whose loot table is {@code minecraft:empty} never run loot modifiers; {@link BlockBreakHandler} covers
 * their player breaks.
 */
public class BlockDropModifier extends LootModifier {

    /**
     * Creates the modifier.
     *
     * @param conditions Conditions of the modifier file; dropscore's own file has none.
     */
    public BlockDropModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    /**
     * Applies the table of the broken block.
     *
     * @param generatedLoot Drops of the loot table and earlier modifiers.
     * @param context       Loot context of the loot table.
     * @return The drops after removals, with dropscore drops added.
     */
    @Nonnull
    @Override
    protected List<ItemStack> doApply(List<ItemStack> generatedLoot, LootContext context) {
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state == null) return generatedLoot;

        DropTable table = BlockDropManager.getTable(state.getBlock());
        if (table.isEmpty()) return generatedLoot;

        return BlockDropApplier.apply(table, generatedLoot, context);
    }

    /**
     * Reads and writes {@link BlockDropModifier} files; the modifier has no settings besides its conditions.
     */
    public static class Serializer extends GlobalLootModifierSerializer<BlockDropModifier> {

        /**
         * Creates the modifier of a loot modifier file.
         *
         * @param location   Id of the file.
         * @param object     JSON of the file.
         * @param conditions Parsed conditions of the file.
         * @return The modifier.
         */
        @Override
        public BlockDropModifier read(ResourceLocation location, JsonObject object, LootItemCondition[] conditions) {
            return new BlockDropModifier(conditions);
        }

        /**
         * Writes a modifier back to JSON.
         *
         * @param instance Modifier to write.
         * @return The JSON with the conditions of the modifier.
         */
        @Override
        public JsonObject write(BlockDropModifier instance) {
            return makeConditions(instance.conditions);
        }
    }
}
