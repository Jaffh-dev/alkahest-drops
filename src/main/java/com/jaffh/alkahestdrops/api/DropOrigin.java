package com.jaffh.alkahestdrops.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Represents what a drop file drop came from: a mob (death or another trigger) or a broken block. Part of the public
 * API: other mods may read it from {@link com.jaffh.alkahestdrops.api.event.OnDropEvent}.
 *
 * @since 1.0.0
 */
public sealed interface DropOrigin {
    /**
     * Represents a drop of a mob.
     *
     * @param mob Mob that dropped, dead on a death drop. Never null.
     * @since 1.0.0
     */
    record Mob(LivingEntity mob) implements DropOrigin {}

    /**
     * Represents a drop of a broken block.
     *
     * @param state State of the block before it broke. Never null.
     * @param pos   Position of the block. Never null.
     * @since 1.0.0
     */
    record Block(BlockState state, BlockPos pos) implements DropOrigin {}
}
