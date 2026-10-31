package com.jaffh.alkahestdrops.api;

import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Represents one entry of an Alkahest Drops removal file that applies to a mob or block. It removes matching drops of
 * the loot table, of other mods, and of drop files in other namespaces. Instances are created by Alkahest Drops only;
 * read them through {@link AlkahestDropsApi}.
 *
 * @param fileId        Id of the removal file. Never null.
 * @param items         Items removed, as written in the file: an item id, {@code #tag} or {@code *}; null when the
 *                      entry removes experience.
 * @param trigger       Trigger whose drops the entry removes. Never null.
 * @param isConditional Whether loot conditions (of the file or the entry) decide if it removes.
 * @since 1.0.0
 */
public record DropRemoval(ResourceLocation fileId, @Nullable String items, DropTrigger trigger, boolean isConditional) {
    /**
     * Tells whether this entry removes experience instead of items.
     *
     * @return True when {@link #items()} is null.
     * @since 1.0.0
     */
    public boolean isExperience() {
        return items == null;
    }

    /**
     * Tells whether this entry removes an item, reading tags as they are bound now.
     *
     * @param item Item to check. Never null.
     * @return True when the entry names the item, one of its tags, or {@code *}; false for experience entries.
     * @since 1.0.0
     */
    public boolean removes(Item item) {
        if (items == null) return false;
        if (items.equals("*")) return true;
        if (items.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(items.substring(1));
            return tag != null && item.builtInRegistryHolder().is(TagKey.create(Registries.ITEM, tag));
        }
        return items.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(item)));
    }
}
