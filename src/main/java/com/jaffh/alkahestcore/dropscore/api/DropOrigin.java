package com.jaffh.alkahestcore.dropscore.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Represents what a dropscore drop came from: a mob (death or another trigger) or a broken block. Part of the public
 * API: other mods may read it from {@link com.jaffh.alkahestcore.dropscore.api.event.OnDropEvent}.
 */
public sealed interface DropOrigin {
    /**
     * Represents a drop of a mob.
     *
     * @param mob Mob that dropped, dead on a death drop. Never null.
     */
    record Mob(LivingEntity mob) implements DropOrigin {
    }

    /**
     * Represents a drop of a broken block.
     *
     * @param state State of the block before it broke. Never null.
     * @param pos   Position of the block. Never null.
     */
    record Block(BlockState state, BlockPos pos) implements DropOrigin {
    }
}
