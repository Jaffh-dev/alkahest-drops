package com.jaffh.alkahestdrops.server.drops;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Represents what one trigger other than death dropped.
 *
 * @param items      Rolled stacks, none of them empty. Never null.
 * @param experience Rolled experience, 0 or more.
 */
public record TriggeredDrops(List<ItemStack> items, int experience) {
    /**
     * Tells whether nothing dropped.
     *
     * @return True when there is no stack and no experience.
     */
    public boolean isEmpty() {
        return items.isEmpty() && experience <= 0;
    }
}
