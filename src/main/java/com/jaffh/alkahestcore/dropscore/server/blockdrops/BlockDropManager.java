package com.jaffh.alkahestcore.dropscore.server.blockdrops;

import com.jaffh.alkahestcore.dropscore.server.drops.DropFileFormat;
import com.jaffh.alkahestcore.dropscore.server.drops.DropIssue;
import com.jaffh.alkahestcore.dropscore.server.drops.DropManager;
import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.jaffh.alkahestcore.dropscore.server.drops.TargetMatcher;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Datapack reload listener for {@code data/<namespace>/block_drops/*.json}, with rules indexed by block.
 */
public final class BlockDropManager extends DropManager<Block> {
    /** The listener registered on every reload; it holds the rules of the current datapacks. */
    public static final BlockDropManager INSTANCE = new BlockDropManager();

    /**
     * Creates the listener for the {@code block_drops} datapack folder. Every block may match a pattern.
     */
    private BlockDropManager() {
        super("block_drops", DropFileFormat.BLOCK,
                new TargetMatcher<>(ForgeRegistries.BLOCKS, (block, tag) -> block.builtInRegistryHolder().is(tag), block -> true),
                "block");
    }

    /**
     * Returns the drop table of a block, built on its first lookup after a reload.
     *
     * @param block Broken block. Never null.
     * @return The table, {@link DropTable#EMPTY} when no file targets this block.
     */
    public static DropTable getTable(Block block) {
        return INSTANCE.tableOf(block);
    }

    /**
     * Returns the problems found by the last load of block drop files.
     *
     * @return An immutable list, empty when every file was valid.
     */
    public static List<DropIssue> getIssues() {
        return INSTANCE.issues();
    }
}
