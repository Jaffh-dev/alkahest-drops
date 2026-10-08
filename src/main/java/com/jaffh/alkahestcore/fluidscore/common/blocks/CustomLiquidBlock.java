package com.jaffh.alkahestcore.fluidscore.common.blocks;

import com.jaffh.alkahestcore.fluidscore.common.fluids.CustomFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

import java.util.function.Supplier;

/**
 * The physical block representation of a {@link CustomFluid} in the world.
 * This class handles environmental logic when entities interact or collide
 * with the liquid block space, directly interpreting the parameters defined
 * in {@link CustomFluid.Configs}.
 */
public class CustomLiquidBlock extends LiquidBlock {
    // Configuration rules governing hazards, damage, and item handling behaviors
    private final CustomFluid.Configs configs;

    public CustomLiquidBlock(Supplier<Fluid> fluidSupplier, Properties properties , CustomFluid.Configs configs) {
        super(() -> (FlowingFluid) fluidSupplier.get(), properties);
        this.configs = configs;
    }

    /**
     * Overrides the collision event handled when an entity is inside the block boundary layer.
     * Processes server-side hazard logic, splitting behaviors between floating item drops
     * and living/motive entities.
     *
     * @param state The active block state.
     * @param level The current level instance.
     * @param pos   The position coordinate of the block.
     * @param entity The entity intersecting the block space.
     */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity){
        if(!level.isClientSide){
            if(entity instanceof ItemEntity){
                if(configs.deletesItems()){
                    if(!configs.isHot() || !entity.fireImmune()) {
                        // Items are vaporized if the fluid deletes items, unless the fluid is hot
                        // and the item possesses explicit fire immunity traits (e.g., Netherite items).
                        entity.discard();
                    }
                }
            }
            else {
                burnEntity(entity);
                applyDamage(entity);
            }
        }
    }

    /**
     * Inflicts direct environmental hazard damage to an entity based on configuration parameters.
     *
     * @param entity The entity inside the block space.
     */
    private void applyDamage(Entity entity){
        if(this.configs.entityDamage() > 0.0f){
            entity.hurt(this.configs.damageSource(), this.configs.entityDamage());
        }
    }

    /**
     * Ignites an entity, scheduling a sustained burning duration if the fluid is configured as hot.
     *
     * @param entity The entity inside the block space.
     */
    private void burnEntity(Entity entity){
        if(this.configs.isHot()){
            entity.setSecondsOnFire(configs.secondsOnFire());
        }
    }
}