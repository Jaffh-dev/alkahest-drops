package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;

/**
 * Represents the drops one drop file adds to a mob, keeping its source file for namespace-scoped removals.
 *
 * @param fileId           Source file id. Never null.
 * @param globalConditions Conditions that gate every drop of the file. Immutable, never null.
 * @param entries          Resolved drops, each with its local conditions. Immutable, never null.
 */
public record MobDropFile(ResourceLocation fileId, List<LootItemCondition> globalConditions, List<DropEntry> entries) {
    /**
     * Creates a drop file and stores immutable copies of its lists.
     */
    public MobDropFile {
        globalConditions = List.copyOf(globalConditions);
        entries = List.copyOf(entries);
    }

    /**
     * Tells whether this file has global conditions to test.
     *
     * @return True when at least one global condition is set.
     */
    public boolean hasGlobalConditions() {
        return !globalConditions.isEmpty();
    }

    /**
     * Returns a copy of this file with other drops.
     *
     * @param keptEntries Drops to keep. Never null.
     * @return A new drop file with the same id and global conditions.
     */
    public MobDropFile withEntries(List<DropEntry> keptEntries) {
        return new MobDropFile(fileId, globalConditions, keptEntries);
    }
}
