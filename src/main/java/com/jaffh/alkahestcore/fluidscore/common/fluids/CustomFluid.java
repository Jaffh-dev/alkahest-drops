package com.jaffh.alkahestcore.fluidscore.common.fluids;

import com.jaffh.alkahestcore.fluidscore.common.hooks.CustomFluidHook;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * A configuration-driven implementation of {@link BaseFluid}.
 * This class serves as the foundation for registering custom, data-driven fluids
 * (such as those loaded via JSON files) by externalizing physics, simulation,
 * and hazard properties into an immutable configuration record.
 */
public abstract class CustomFluid extends BaseFluid {

    // Configuration properties defining the physical behavior and environmental hazards of this fluid
    protected final Configs configs;

    /**
     * Constructs a new CustomFluid instance, automatically binding it to the standard
     * data-driven {@link CustomFluidHook} pipeline.
     */
    public CustomFluid(Properties properties, Attributes attributes, Configs configs) {
        super(properties, attributes, CustomFluidHook::new);
        this.configs = configs;
    }

    /**
     * @return The immutable {@link Configs} record assigned to this fluid instance.
     */
    public Configs getConfigs(){
        return configs;
    }

    /**
     * Immutable property container holding parameters for world fluid simulation,
     * entity environmental hazards, and inventory handling behavior.
     *
     * @param isHot                Determines if the fluid exhibits high-temperature traits (e.g., burning adjacent blocks).
     * @param tickDelay            The update frequency rate of the fluid in world game ticks.
     * @param slopeFindDistance    The horizontal search distance check used to calculate down-slope flow routing paths.
     * @param dropOff              The decrement height value subtracted from the fluid level for each block it flows horizontally.
     * @param canConvertToSource   Allows flowing streams to form an infinite source block cluster matrix (like Water).
     * @param fireSpreadIntensity  The rate or chance intensity at which this fluid spawns fire blocks on flammable borders.
     * @param entityDamage         The direct flat damage value dealt to an entity submerged inside this fluid per tick.
     * @param damageSource         The semantic classification of damage applied to entities (e.g., LAVA, MAGIC, GENERIC).
     * @param bucketStackSize      The maximum inventory stack allocation size allowed for this fluid's filled bucket container.
     * @param secondsOnFire        The duration penalty in seconds an entity remains burning after exiting the fluid.
     * @param deletesItems         If true, any dropped item entities touching this fluid are immediately destroyed
     *                             (unless the fluid is hot and the item possesses explicit fire immunity traits).
     */
    public record Configs(
            boolean isHot,
            int tickDelay,
            int slopeFindDistance,
            int dropOff,
            boolean canConvertToSource,
            int fireSpreadIntensity,
            float entityDamage,
            DamageSource damageSource,
            int bucketStackSize,
            int secondsOnFire,
            boolean deletesItems
    ){

        public Configs{
            if(tickDelay <= 0) tickDelay = 5;
            if(slopeFindDistance <= 0) slopeFindDistance = 4;
            if(dropOff <= 0 || dropOff >= 8) dropOff = 1;
            if(fireSpreadIntensity < 0) fireSpreadIntensity = 0;
            if(entityDamage < 0.0f) entityDamage = 0.0f;
            if(damageSource == null) damageSource = DamageSource.LAVA;
            if(bucketStackSize < 1 || bucketStackSize > 64) bucketStackSize = 1;
            if(secondsOnFire < 0) secondsOnFire = 0;
        }
    }

    /**
     * Represents the flowing variant of the base fluid instance.
     */
    public static class Flowing extends CustomFluid{

        public Flowing(Properties properties, Attributes attributes, Configs configs){
            super(properties, attributes, configs);
        }

        /**
         * Defines the state properties for this fluid.
         *
         * @param builder The {@link StateDefinition.Builder} used to add properties to the fluid.
         */
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder){
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        /**
         * Retrieves the current fluid level of this state.
         * <p>In the Minecraft fluid system, the level dictates the height and flow intensity
         * of the fluid block. For a flowing fluid, this is a value between 1 and 8.</p>
         *
         * @param state The current {@link FluidState} being queried.
         * @return An integer representing the fluid level (1-8).
         */
        @Override
        public final int getAmount(FluidState state){
            return state.getValue(LEVEL);
        }

        /**
         * Determines whether this fluid state represents a source block.
         * <p>A source block is the "origin" of the fluid, usually represented as a
         * full, stable block (level 8). Flowing states return false.</p>
         *
         * @param state The current {@link FluidState} being queried.
         * @return True if the state is a source, false if it is a flowing/transitional state.
         */
        @Override
        public final boolean isSource(FluidState state){
            return false;
        }
    }

    /**
     * Represents the still (source origin) variant of the base fluid instance.
     */
    public static class Source extends CustomFluid{

        public Source(Properties properties, Attributes attributes, Configs configs){
            super(properties, attributes, configs);
        }

        /**
         * Retrieves the current fluid level of this state.
         * <p>In the Minecraft fluid system, the level dictates the height and flow intensity
         * of the fluid block. For a flowing fluid, this is a value between 1 and 8.</p>
         *
         * * @param state The current {@link FluidState} being queried.
         * @return An integer representing the fluid level (1-8).
         */
        @Override
        public final int getAmount(FluidState state){
            return 8;
        }

        /**
         * Determines whether this fluid state represents a source block.
         * <p>A source block is the "origin" of the fluid, usually represented as a
         * full, stable block (level 8). Flowing states return false.</p>
         *
         * @param state The current {@link FluidState} being queried.
         * @return True if the state is a source, false if it is a flowing/transitional state.
         */
        @Override
        public final boolean isSource(FluidState state){
            return true;
        }
    }
}
