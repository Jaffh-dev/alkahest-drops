package com.jaffh.alkahestcore.dropscore.server.blockdrops;

import com.google.common.base.Suppliers;
import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Global Loot Modifier that applies the block drop tables: it removes drops of the block's loot table first,
 * then rolls and adds dropscore drops. Runs on every loot table and ignores the ones that are not a block break.
 * Blocks whose loot table is {@code minecraft:empty} never run loot modifiers; {@link BlockBreakHandler} covers
 * their player breaks.
 */
public class BlockDropModifier extends LootModifier {
    /** Reads and writes modifier files; the modifier has no settings besides its conditions. */
    public static final Supplier<Codec<BlockDropModifier>> CODEC = Suppliers.memoize(
            () -> RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, BlockDropModifier::new)));

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
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state == null) return generatedLoot;

        DropTable table = BlockDropManager.getTable(state.getBlock());
        if (table.isEmpty()) return generatedLoot;

        return new ObjectArrayList<>(BlockDropApplier.apply(table, generatedLoot, context));
    }

    /**
     * Returns the codec of this modifier, registered as {@code <modId>:block_drops}.
     *
     * @return The codec.
     */
    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
