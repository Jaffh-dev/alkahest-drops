package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.common.drops.DropTracking;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Represents one stack of a simulated roll and the pity or one-time drop it came from.
 *
 * @param stack    Dropped stack. Never null.
 * @param tracking Settings of the pity or one-time drop that rolled the stack, or null for any other drop.
 */
public record SimulatedStack(ItemStack stack, @Nullable DropTracking tracking) {
}
