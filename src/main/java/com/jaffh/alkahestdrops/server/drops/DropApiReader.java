package com.jaffh.alkahestdrops.server.drops;

import com.jaffh.alkahestdrops.api.AddedDrop;
import com.jaffh.alkahestdrops.api.DropRemoval;
import com.jaffh.alkahestdrops.api.DropTrigger;
import com.jaffh.alkahestdrops.api.TargetDrops;
import com.jaffh.alkahestdrops.common.drops.DropEntry;
import com.jaffh.alkahestdrops.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the read-only views of {@link com.jaffh.alkahestdrops.api.AlkahestDropsApi} from the drop tables and the
 * loaded files. Not a hot path: views are built on every call.
 */
public final class DropApiReader {

    /**
     * Prevents instantiation of this utility class.
     */
    private DropApiReader() {
    }

    /**
     * Builds the view of a mob for one trigger.
     *
     * @param type    Mob type. Never null.
     * @param trigger Trigger to read. Never null.
     * @return The view, empty when no server is running or the files are not loaded yet.
     * @throws IllegalStateException If called outside the server thread.
     */
    public static TargetDrops mobDrops(EntityType<?> type, DropTrigger trigger) {
        if (!canRead(MobDropManager.INSTANCE)) return TargetDrops.EMPTY;
        return new TargetDrops(addedDrops(MobDropManager.getTable(type, trigger)),
                removals(MobDropManager.INSTANCE, type, trigger));
    }

    /**
     * Builds the view of a block.
     *
     * @param block Block. Never null.
     * @return The view, empty when no server is running or the files are not loaded yet.
     * @throws IllegalStateException If called outside the server thread.
     */
    public static TargetDrops blockDrops(Block block) {
        if (!canRead(BlockDropManager.INSTANCE)) return TargetDrops.EMPTY;
        return new TargetDrops(addedDrops(BlockDropManager.getTable(block)),
                removals(BlockDropManager.INSTANCE, block, DropTrigger.DEATH));
    }

    /**
     * Tells whether the files of a module can be read now.
     *
     * @param manager Module to read. Never null.
     * @return False when no server is running or the files are not loaded yet.
     * @throws IllegalStateException If a server is running and this is not its thread.
     */
    private static boolean canRead(DropManager<?> manager) {
        return ServerThread.current() != null && manager.isLoaded();
    }

    /**
     * Lists the drops of a table, which already lacks the drops that removal files of other namespaces always remove.
     *
     * @param table Table of the target and trigger. Never null.
     * @return One view per drop, in file order.
     */
    private static List<AddedDrop> addedDrops(DropTable table) {
        List<AddedDrop> drops = new ArrayList<>();
        for (DropFile file : table.dropFiles()) {
            for (DropEntry entry : file.entries()) {
                drops.add(new AddedDrop(file.fileId(), entry.tracking().key(), entry.item(), entry.chance(),
                        entry.count().min(), entry.count().max(), entry.trigger().trigger(),
                        file.hasGlobalConditions() || entry.hasConditions(), !entry.enchantBonuses().isEmpty(),
                        entry.tracking().pity(), entry.tracking().isFirstDrop()));
            }
        }
        return drops;
    }

    /**
     * Lists every removal entry of the loaded removal files that apply to a target and trigger.
     *
     * @param manager Module of the target. Never null.
     * @param value   Mob type or block. Never null.
     * @param trigger Trigger to read. Never null.
     * @param <T>     Registry entry type of the module.
     * @return One view per removal entry, in load order.
     */
    private static <T> List<DropRemoval> removals(DropManager<T> manager, T value, DropTrigger trigger) {
        List<DropRemoval> removals = new ArrayList<>();
        for (ParsedDropFile file : manager.report().files()) {
            if (!(file instanceof ParsedDropFile.Removals removalFile)) continue;
            RemovalRule rule = removalFile.rule();
            if (rule.trigger() != trigger || !manager.doesFileApplyTo(rule.fileId(), value)) continue;
            for (RemovalEntry entry : rule.entries()) {
                removals.add(new DropRemoval(rule.fileId(), itemsOf(entry.target()), trigger,
                        rule.hasGlobalConditions() || entry.hasConditions()));
            }
        }
        return removals;
    }

    /**
     * Writes the items a removal entry names the way removal files do.
     *
     * @param target Items of the entry, or null for an experience entry.
     * @return An item id, {@code #tag} or {@code *}; null for experience.
     */
    @Nullable
    private static String itemsOf(@Nullable ItemTarget target) {
        if (target == null) return null;
        if (target instanceof ItemTarget.Exact exact) return String.valueOf(ForgeRegistries.ITEMS.getKey(exact.item()));
        if (target instanceof ItemTarget.Tagged tagged) return "#" + tagged.tag().location();
        return "*";
    }
}
