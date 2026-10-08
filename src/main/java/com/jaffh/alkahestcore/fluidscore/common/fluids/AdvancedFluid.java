package com.jaffh.alkahestcore.fluidscore.common.fluids;

import com.jaffh.alkahestcore.fluidscore.common.hooks.AdvancedFluidHook;
import com.jaffh.alkahestcore.fluidscore.common.hooks.BaseFluidHook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.function.Function;

/**
 * An extended implementation of {@link BaseFluid} that opens up advanced physics
 * and movement logic simulation hooks.
 */
public abstract class AdvancedFluid extends BaseFluid{

    // Hook instance for advanced fluid customization and physics interception
    protected AdvancedFluidHook cachedAdvancedHook;

    protected AdvancedFluid(
            Properties properties,
            Attributes attributes,
            Function<BaseFluid, BaseFluidHook> baseHookFactory,
            Function<AdvancedFluid, AdvancedFluidHook> advancedHookFactory) {
        super(properties, attributes, baseHookFactory);

        this.cachedAdvancedHook = (advancedHookFactory != null) ? advancedHookFactory.apply(this) : new AdvancedFluidHook(this);
    }

    /**
     * @return The active {@link AdvancedFluidHook} instance managing this fluid's advanced behaviors.
     */
    public final AdvancedFluidHook getAdvancedHook(){
        return this.cachedAdvancedHook;
    }

    /**
     * Handles the logic of moving the fluid into a neighboring block.
     *
     * @param level      The {@link LevelAccessor} providing world access.
     * @param pos        The target destination {@link BlockPos}.
     * @param blockState The current {@link BlockState} occupying the destination.
     * @param direction  The {@link Direction} the fluid is flowing towards.
     * @param fluidState The incoming {@link FluidState} that will occupy the target position.
     */
    @Override
    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState blockState, Direction direction, FluidState fluidState){
        if (getAdvancedHook().canSpreadTo(level, pos, blockState, direction, fluidState)) {
            super.spreadTo(level, pos, blockState, direction, fluidState);
        }
    }

    /**
     * Calculates the flow vector, which dictates the direction and drag velocity strength
     * pushing entities immersed within this fluid.
     *
     * @param level The {@link BlockGetter} world context.
     * @param pos   The {@link BlockPos} of the ticking fluid state.
     * @param state The current {@link FluidState}.
     * @return A {@link Vec3} representing the direction and scalar force of the current flow.
     */
    @Override
    public Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state){
        return getAdvancedHook().getCustomFlow(level, pos, state, super.getFlow(level, pos, state));
    }

    /**
     * Determines whether a specific face of this fluid acts as a solid boundary block surface.
     *
     * @param level     The {@link BlockGetter} world context.
     * @param pos       The {@link BlockPos} of the fluid.
     * @param direction The target boundary {@link Direction} side being verified.
     * @return {@code true} if the evaluated face behaves as a solid boundary layer.
     */
    @Override
    protected boolean isSolidFace(BlockGetter level, BlockPos pos, Direction direction){
        return getAdvancedHook().isSolidFace(level, pos, direction, super.isSolidFace(level, pos, direction));
    }

    /**
     * Main expansion driver. Coordinates and schedules flow spreading steps into adjacent neighboring positions.
     *
     * @param level The {@link LevelAccessor} processing block updates.
     * @param pos   The origin {@link BlockPos} of the fluid source.
     * @param state The current {@link FluidState}.
     */
    @Override
    protected void spread(LevelAccessor level, BlockPos pos, FluidState state){
        getAdvancedHook().beforeSpread(level, pos, state);
        super.spread(level, pos, state);
    }

    /**
     * Evaluates and updates the height layout profile for new liquid transitions when a block update occurs.
     *
     * @param level      The {@link LevelReader} inspecting environmental block surroundings.
     * @param pos        The target {@link BlockPos} being simulated.
     * @param blockState The active {@link BlockState} at the target coordinate.
     * @return The updated resulting {@link FluidState}.
     */
    @Override
    protected FluidState getNewLiquid(LevelReader level, BlockPos pos, BlockState blockState){
        return getAdvancedHook().getNewCustomLiquidState(level, pos, blockState, super.getNewLiquid(level, pos, blockState));
    }

    /**
     * Retrieves the proportional rendering height of the fluid mesh layer inside the world space bounds.
     *
     * @param state The current {@link FluidState}.
     * @param level The {@link BlockGetter} world context.
     * @param pos   The {@link BlockPos} of the liquid.
     * @return A float value ranging between {@code 0.0F} and {@code 1.0F}.
     */
    @Override
    public float getHeight(FluidState state, BlockGetter level, BlockPos pos){
        return getAdvancedHook().getCustomHeight(state, level, pos, super.getHeight(state, level, pos));
    }

    /**
     * Computes the intrinsic static height level properties of the fluid state, completely independent of world conditions.
     *
     * @param state The current {@link FluidState}.
     * @return A float value representing the fluid's own base height property.
     */
    @Override
    public float getOwnHeight(FluidState state){
        return getAdvancedHook().getCustomOwnHeight(state, super.getOwnHeight(state));
    }

    /**
     * Returns the exact physical collision bounding box shape footprints assigned to this fluid instance.
     *
     * @param state The current {@link FluidState}.
     * @param level The {@link BlockGetter} world context.
     * @param pos   The {@link BlockPos} coordinate of the fluid.
     * @return The corresponding bounding {@link VoxelShape} configuration.
     */
    @Override
    public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos){
        return getAdvancedHook().getCustomShape(state, level, pos, super.getShape(state, level, pos));
    }

    /**
     * Represents the flowing variant of the advanced fluid instance.
     */
    public static class Flowing extends AdvancedFluid{

        public Flowing(Properties properties,
                       Attributes attributes,
                       Function<BaseFluid, BaseFluidHook> baseHookFactory,
                       Function<AdvancedFluid, AdvancedFluidHook> advancedHookFactory){
            super(properties, attributes, baseHookFactory, advancedHookFactory);
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
     * Represents the still (source origin) variant of the advanced fluid instance.
     */
    public static class Source extends AdvancedFluid{

        public Source(Properties properties,
                      Attributes attributes,
                      Function<BaseFluid, BaseFluidHook> baseHookFactory,
                      Function<AdvancedFluid, AdvancedFluidHook> advancedHookFactory){
            super(properties, attributes, baseHookFactory, advancedHookFactory);
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
