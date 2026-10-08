package com.jaffh.alkahestcore.fluidscore.common.behaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Interface providing default behaviors for high-temperature fluids.
 * <p>Can be implemented by any hook class that needs to interact with
 * flammable surroundings or produce steam/fizzing effects.</p>
 */
public interface IHotFluidBehaviour {

    //constant to avoid creating a directions array everytime hasFlammableNeighbours is called
    Direction[] DIRECTIONS = Direction.values();

    /**
     * Checks all six adjacent faces for flammable blocks.
     *
     * @param level The current level.
     * @param pos The position of the hot fluid.
     * @return true if at least one neighbor is flammable.
     */
    default boolean hasFlammableNeighbours(Level level, BlockPos pos){
        for(Direction direction : DIRECTIONS){
            BlockPos neighbourPos = pos.relative(direction);
            BlockState neighbourState = level.getBlockState(neighbourPos);
            if(!neighbourState.isAir() && neighbourState.isFlammable(level, neighbourPos, direction.getOpposite())){
                return true;
            }
        }
        return false;
    }

    /**
     * Triggers the vanilla "fizz" sound and smoke particle effect.
     * Commonly used when hot fluids contact water or extinguish items.
     *
     * @param level The level accessor.
     * @param pos The position where the effect should play.
     */
    default void fizz(LevelAccessor level, BlockPos pos){
        level.levelEvent(1501, pos, 0);
    }
}
