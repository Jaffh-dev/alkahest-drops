package com.jaffh.alkahestcore.dropscore.server.drops;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Represents the items one removal entry names: a single item, an item tag or every item ({@code "*"}).
 * Tags are matched when a drop happens, because tags are not bound yet while datapack files are read.
 */
public sealed interface ItemTarget {
    /** Matches every item. */
    ItemTarget ANY = new Any();

    /**
     * Tells whether this target names an item.
     *
     * @param item Item to check. Never null.
     * @return True when the item is named.
     */
    boolean matches(Item item);

    /**
     * Targets one item.
     *
     * @param item Named item. Never null.
     */
    record Exact(Item item) implements ItemTarget {
        /**
         * Tells whether the item is the named item.
         *
         * @param item Item to check. Never null.
         * @return True on the same item.
         */
        @Override
        public boolean matches(Item item) {
            return this.item == item;
        }
    }

    /**
     * Targets every item in an item tag.
     *
     * @param tag Named tag. Never null.
     */
    record Tagged(TagKey<Item> tag) implements ItemTarget {
        /**
         * Tells whether the item is in the tag.
         *
         * @param item Item to check. Never null.
         * @return True when the bound tag contains the item.
         */
        // Mojang marks the holder internal, not obsolete; vanilla's ItemStack.is(TagKey) uses the same call.
        @SuppressWarnings("deprecation")
        @Override
        public boolean matches(Item item) {
            return item.builtInRegistryHolder().is(tag);
        }
    }

    /**
     * Targets every item.
     */
    record Any() implements ItemTarget {
        /**
         * Matches any item.
         *
         * @param item Item to check. Never null.
         * @return Always true.
         */
        @Override
        public boolean matches(Item item) {
            return true;
        }
    }
}
