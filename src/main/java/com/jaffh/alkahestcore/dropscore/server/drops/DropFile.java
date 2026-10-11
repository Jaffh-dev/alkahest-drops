package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;

/**
 * Represents the drops one drop file adds to its target, keeping its source file for namespace-scoped removals.
 *
 * @param fileId           Source file id. Never null.
 * @param globalConditions Conditions that gate every drop of the file. Immutable, never null.
 * @param entries          Resolved drops, each with its local conditions. Immutable, never null.
 */
public record DropFile(ResourceLocation fileId, List<LootItemCondition> globalConditions, List<DropEntry> entries) {
    /**
     * Creates a drop file and stores immutable copies of its lists.
     */
    public DropFile {
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
     * Tells whether rolling this file needs a loot context.
     *
     * @return True when the file or any of its drops has conditions, or any drop has loot functions.
     */
    public boolean needsLootContext() {
        if (hasGlobalConditions()) return true;
        for (DropEntry entry : entries) {
            if (entry.hasConditions() || entry.hasFunctions()) return true;
        }
        return false;
    }

    /**
     * Tells whether this file has experience drops.
     *
     * @return True when at least one drop gives experience.
     */
    public boolean hasExperienceDrops() {
        for (DropEntry entry : entries) {
            if (entry.isExperience()) return true;
        }
        return false;
    }

    /**
     * Returns a copy of this file with other drops.
     *
     * @param keptEntries Drops to keep. Never null.
     * @return A new drop file with the same id and global conditions.
     */
    public DropFile withEntries(List<DropEntry> keptEntries) {
        return new DropFile(fileId, globalConditions, keptEntries);
    }
}
