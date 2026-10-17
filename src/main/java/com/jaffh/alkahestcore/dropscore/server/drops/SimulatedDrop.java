package com.jaffh.alkahestcore.dropscore.server.drops;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Represents the result of one simulated kill or block break.
 *
 * @param items      Dropped stacks, none of them empty. Never null.
 * @param experience Dropped experience, 0 or more.
 */
public record SimulatedDrop(List<ItemStack> items, int experience) {
}
