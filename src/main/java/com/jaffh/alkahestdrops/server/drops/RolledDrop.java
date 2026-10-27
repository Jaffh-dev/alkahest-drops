package com.jaffh.alkahestdrops.server.drops;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Represents one rolled drop file stack and the player it is locked to.
 *
 * @param stack Rolled stack. Never null, never empty.
 * @param owner Only player who can pick the stack up (a per-player roll), or null for anyone.
 */
public record RolledDrop(ItemStack stack, @Nullable UUID owner) {
}
