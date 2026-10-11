package com.jaffh.alkahestcore.dropscore.server.drops;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;

/**
 * Represents one removal file: entries it removes from a target's drops, gated by the file's global conditions.
 * It removes matching event drops (vanilla and other mods) and matching drops added by drop files of
 * other namespaces, never drops added by its own namespace.
 *
 * @param fileId                 Source file id. Never null.
 * @param globalConditions       Conditions that gate every entry and {@code remove_experience}. Immutable, never null.
 * @param shouldRemoveExperience Whether the file turns off experience when its global conditions pass.
 * @param entries                Items to remove, each with its local conditions. Immutable, never null.
 */
public record RemovalRule(
        ResourceLocation fileId,
        List<LootItemCondition> globalConditions,
        boolean shouldRemoveExperience,
        List<RemovalEntry> entries
) {
    /**
     * Creates a removal rule and stores immutable copies of its lists.
     */
    public RemovalRule {
        globalConditions = List.copyOf(globalConditions);
        entries = List.copyOf(entries);
    }

    /**
     * Tells whether this rule has global conditions to test.
     *
     * @return True when at least one global condition is set.
     */
    public boolean hasGlobalConditions() {
        return !globalConditions.isEmpty();
    }

    /**
     * Tells whether testing this rule needs a loot context.
     *
     * @return True when the rule or any of its entries has conditions.
     */
    public boolean hasAnyConditions() {
        if (hasGlobalConditions()) return true;
        for (RemovalEntry entry : entries) {
            if (entry.hasConditions()) return true;
        }
        return false;
    }

    /**
     * Returns a copy of this rule with other entries, used to split a rule at reload and to keep only the
     * entries that passed their conditions on a kill.
     *
     * @param keptEntries Entries of the copy. Never null.
     * @return A new rule with the same file id, global conditions and experience flag.
     */
    public RemovalRule withEntries(List<RemovalEntry> keptEntries) {
        return new RemovalRule(fileId, globalConditions, shouldRemoveExperience, keptEntries);
    }

    /**
     * Tells whether an entry of this rule names an item, ignoring local conditions.
     *
     * @param item Item to check. Never null.
     * @return True when an entry names the item.
     */
    public boolean removes(Item item) {
        for (RemovalEntry entry : entries) {
            if (entry.target().matches(item)) return true;
        }
        return false;
    }

    /**
     * Tells whether this rule removes a drop added by a dropscore drop file, ignoring local conditions.
     *
     * @param dropFileId Id of the drop file that added the drop. Never null.
     * @param item       Dropped item. Never null.
     * @return True when an entry names the item and the drop file belongs to another namespace.
     */
    public boolean shouldRemoveDropOf(ResourceLocation dropFileId, Item item) {
        return isOtherNamespace(dropFileId) && removes(item);
    }

    /**
     * Tells whether this rule removes the experience drops of a dropscore drop file, ignoring conditions.
     *
     * @param dropFileId Id of the drop file that adds the experience. Never null.
     * @return True when the rule has {@code remove_experience} and the drop file belongs to another namespace.
     */
    public boolean shouldRemoveExperienceOf(ResourceLocation dropFileId) {
        return shouldRemoveExperience && isOtherNamespace(dropFileId);
    }

    /**
     * Tells whether a drop file belongs to another namespace than this rule, so this rule may remove its drops.
     *
     * @param dropFileId Id of the drop file. Never null.
     * @return True when the namespaces differ.
     */
    private boolean isOtherNamespace(ResourceLocation dropFileId) {
        return !dropFileId.getNamespace().equals(fileId.getNamespace());
    }
}
