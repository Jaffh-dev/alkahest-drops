package com.jaffh.alkahestdrops.server.drops;

import com.jaffh.alkahestdrops.common.drops.DropTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;

/**
 * Represents one removal file: entries it removes from a target's drops of one trigger, gated by the file's global
 * conditions. On death it removes matching event drops (vanilla and other mods); on every trigger it removes
 * matching drops added by drop files of other namespaces, never drops added by its own namespace.
 *
 * @param fileId           Source file id. Never null.
 * @param globalConditions Conditions that gate every entry. Immutable, never null.
 * @param entries          Items or experience to remove, each with its local conditions. Immutable, never null.
 * @param trigger          Trigger whose drops the rule removes, death by default. Never null.
 */
public record RemovalRule(ResourceLocation fileId, List<LootItemCondition> globalConditions, List<RemovalEntry> entries,
                          DropTrigger trigger) {
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
     * @return A new rule with the same file id and global conditions.
     */
    public RemovalRule withEntries(List<RemovalEntry> keptEntries) {
        return new RemovalRule(fileId, globalConditions, keptEntries, trigger);
    }

    /**
     * Tells whether an entry of this rule removes experience, ignoring local conditions.
     *
     * @return True when at least one entry is an experience entry.
     */
    public boolean removesExperience() {
        for (RemovalEntry entry : entries) {
            if (entry.isExperience()) return true;
        }
        return false;
    }

    /**
     * Tells whether an entry of this rule names an item, ignoring local conditions.
     *
     * @param item Item to check. Never null.
     * @return True when an entry names the item.
     */
    public boolean removes(Item item) {
        for (RemovalEntry entry : entries) {
            if (entry.removes(item)) return true;
        }
        return false;
    }

    /**
     * Tells whether this rule removes a drop added by a drop file, ignoring local conditions.
     *
     * @param dropFileId Id of the drop file that added the drop. Never null.
     * @param item       Dropped item. Never null.
     * @return True when an entry names the item and the drop file belongs to another namespace.
     */
    public boolean shouldRemoveDropOf(ResourceLocation dropFileId, Item item) {
        return isOtherNamespace(dropFileId) && removes(item);
    }

    /**
     * Tells whether this rule removes the experience drops of a drop file, ignoring conditions.
     *
     * @param dropFileId Id of the drop file that adds the experience. Never null.
     * @return True when an entry removes experience and the drop file belongs to another namespace.
     */
    public boolean shouldRemoveExperienceOf(ResourceLocation dropFileId) {
        return isOtherNamespace(dropFileId) && removesExperience();
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
