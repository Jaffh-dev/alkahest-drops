package com.jaffh.alkahestcore.fluidscore.common.fluids;

import com.jaffh.alkahestcore.fluidscore.common.hooks.BaseFluidHook;
import com.jaffh.alkahestcore.fluidscore.common.interactions.FluidInteractionManager;
import com.mojang.math.Vector4f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.FluidAttributes;
import org.jetbrains.annotations.Range;

import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Abstract base class for all custom fluids within the AlkahestCore framework.
 * Bridges Minecraft's vanilla fluid physics with a flexible, decoupled hook system.
 */
public abstract class BaseFluid extends FlowingFluid {

    // Fluid custom attributes (density, temperature, viscosity, etc.)
    protected final Attributes attributes;

    // Registry properties suppliers (still fluid, flowing fluid, block, bucket)
    protected final Properties properties;

    // Hook system for decoupling fluid logic from registries
    protected final BaseFluidHook cachedBaseHook;

    protected BaseFluid(Properties properties, Attributes attributes, Function<BaseFluid, BaseFluidHook> baseHookFactory){
        this.properties = properties;
        this.attributes = attributes;

        this.cachedBaseHook = (baseHookFactory != null) ? baseHookFactory.apply(this) : new BaseFluidHook(this);
    }

    /**
     * @return The active {@link BaseFluidHook} instance managing this fluid's base behaviors.
     */
    public final BaseFluidHook getBaseHook(){
        return this.cachedBaseHook;
    }

    /**
     * @return The {@link Attributes} of the fluid.
     */
    public final Attributes attributes(){
        return this.attributes;
    }

    /**
     * @return The {@link Properties} of the fluid.
     */
    public final Properties properties(){
        return this.properties;
    }

    /**
     * @return  The static (still) version of this fluid.
     */
    @Override
    public final Fluid getSource(){
        return this.properties.stillFluid.get();
    }

    /**
     * @return   The moving (flowing) version of this fluid.
     */
    @Override
    public final Fluid getFlowing(){
        return this.properties.flowingFluid.get();
    }

    /**
     * @return  The bucket item associated with this fluid.
     */
    @Override
    public final Item getBucket(){
        return this.properties.bucket.get();
    }

    /**
     * Maps the fluid state to its corresponding block representation in the world.
     *
     * @param state The current {@link FluidState}.
     * @return The {@link BlockState} used for world rendering and physical block updates.
     */
    @Override
    protected final BlockState createLegacyBlock(FluidState state){
        return this.properties.block.get().defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    /**
     * Checks if the provided fluid belongs to this fluid instance family (Still or Flowing).
     *
     * @param fluid The fluid to compare against.
     * @return {@code true} if the fluid matches either the source or flowing variant.
     */
    @Override
    public final boolean isSame(Fluid fluid){
        return this.getSource() == fluid || this.getFlowing() == fluid;
    }

    /**
     * Calculates the delay in ticks before the fluid spreads to adjacent positions.
     *
     * @param level    The world context.
     * @param pos      The target position for spreading.
     * @param state    The current state of the fluid.
     * @param newState The state the fluid will transition into.
     * @return Delay in game ticks.
     */
    @Override
    public int getSpreadDelay(Level level, BlockPos pos, FluidState state, FluidState newState){
        return getBaseHook().getCustomSpreadDelay(level, pos, state, newState, this.getTickDelay(level));
    }

    /**
     * Determines whether the current fluid state can be replaced by another fluid flowing into it.
     * Often used to handle fluid merging logic.
     *
     * @return {@code true} if the fluid can be replaced; {@code false} otherwise.
     */
    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction){
        boolean defaultCanReplaceFluid = fluid.isSame(this);

        return getBaseHook().canReplaceFluid(state, level, pos, fluid, direction, defaultCanReplaceFluid);
    }

    /**
     * Core update logic loop for the fluid. Handles movement ticks and world interactions.
     * The frequency of this call is determined by {@link #getTickDelay}.
     *
     * @param level The {@link Level} (surroundings of the fluid) where the fluid is ticking.
     * @param pos   The current {@link BlockPos} of the fluid.
     * @param state The current {@link FluidState}.
     * @see net.minecraft.world.level.material.FlowingFluid#tick(Level, BlockPos, FluidState)
     */
    @Override
    public void tick(Level level, BlockPos pos, FluidState state) {
        if (FluidInteractionManager.checkInteractions(level, pos, state)) {
            return;
        }
        getBaseHook().onTick(level, pos, state);
        super.tick(level, pos, state);
    }

    /**
     * Determines whether this fluid should receive random world ticks.
     *
     * @return {@code true} if the fluid updates on random ticks; {@code false} otherwise.
     */
    @Override
    protected boolean isRandomlyTicking(){
        return getBaseHook().isRandomlyTicking();
    }

    /**
     * Processes random tick behavior for the fluid.
     * This is only called by the engine if {@link #isRandomlyTicking()} returns true.
     *
     * @param level  The world context level.
     * @param pos    The block position of the ticking fluid.
     * @param state  The current fluid state.
     * @param random The pseudo-random number generator instance.
     */
    @Override
    public void randomTick(Level level, BlockPos pos, FluidState state, Random random){
        getBaseHook().onRandomTick(level, pos, state, random);
    }

    /**
     * @return The explosion resistance value. Reference values: Water/Lava = 100.0F.
     */
    @Override
    protected float getExplosionResistance(){
        return getBaseHook().getCustomExplosionResistance();
    }

    /**
     * Defines the maximum horizontal distance the fluid searches for a downward path.
     *
     * @return Search distance in blocks. Reference values: Water = 4, Lava = 2.
     */
    @Override
    public int getSlopeFindDistance(LevelReader level){
        return getBaseHook().getCustomSlopeFindDistance(level);
    }

    /**
     * Determines the fluid height/level loss per horizontal block traveled.
     * Minecraft fluids operate on a scale from 8 (Source) down to 1.
     *
     * @param level     The {@link LevelReader} providing world context.
     * @return The amount of height lost per block. Reference: Water/Lava = 1.
     */
    @Override
    public int getDropOff(LevelReader level){
        return getBaseHook().getCustomDropOff(level);
    }

    /**
     * Sets the tick execution frequency rate for scheduling ticks on this fluid..
     *
     * @param level     The {@link LevelReader} providing world context.
     * @return Delay in ticks between updates. Reference: Water = 5, Lava (Overworld) = 30.
     */
    @Override
    public int getTickDelay(LevelReader level){
        return getBaseHook().getCustomTickDelay(level);
    }

    /**
     * Triggers immediately before the fluid replaces/breaks a block in the world.
     *.
     * @param level     The {@link LevelAccessor} where the destruction occurs.
     * @param pos       The {@link BlockPos} being occupied by the fluid.
     * @param state     The {@link BlockState} of the block about to be destroyed.
     */
    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state){
        getBaseHook().beforeDestroyingBlock(level, pos, state);
    }

    /**
     * * Determines whether this fluid can form infinite source blocks (like water mechanics).
     *
     * @return  {@code true} if the fluid can create new source blocks; {@code false} otherwise.
     */
    @Override
    protected boolean canConvertToSource(){
        return getBaseHook().canConvertToSource();
    }

    /**
     * Converts Alkahest custom attributes into a standard Forge FluidAttributes instance.
     */
    @Override
    protected FluidAttributes createAttributes(){
        return FluidAttributes.builder(
                        new ResourceLocation(properties.modId, "block/fluid/"+this.attributes.fluidName+"_still"),
                        new ResourceLocation(properties.modId, "block/fluid/"+this.attributes.fluidName+"_flow")
                )
                .density(this.attributes.density)
                .viscosity(this.attributes.viscosity)
                .temperature(this.attributes.temperature)
                .luminosity(this.attributes.luminosity)
                .build(this);
    }

    /**
     * Data container record holding Forge-specific simulation properties and parameters.
     */
    public record Attributes(
            int temperature,
            int viscosity,
            int density,
            int luminosity,
            String fluidName,
            Vector4f fogColor
    ){

        public Attributes{
            luminosity = Mth.clamp(luminosity, 0, 15);
        }

        public Attributes(int temperature, int viscosity, int density, int luminosity, String fluidName) {
            this(temperature, viscosity, density, luminosity, fluidName, new Vector4f(0f, 0f, 0f, 0f));
        }

        /**
         * Sets the fog color using standard integer RGB values (0-255) and a float alpha value (0-1).
         *
         * @param r Red component channel (0 - 255).
         * @param g Green component channel (0 - 255).
         * @param b Blue component channel (0 - 255).
         * @param a Alpha opacity density channel (0.0F - 1.0F).
         * @return A new Attributes instance with the updated fog color configuration.
         */
        public Attributes withFogColor(@Range(from = 0, to = 255)int r, @Range(from = 0, to = 255)int g, @Range(from = 0, to = 255)int b, @Range(from = 0, to = 1)float a) {
            return withFogColor(
                    (Mth.clamp(r, 0, 255)/255.0f),
                    (Mth.clamp(g, 0, 255)/255.0f),
                    (Mth.clamp(b, 0, 255)/255.0f),
                    Mth.clamp(a, 0.0f, 1.0f));
        }

        /**
         * Sets the fog color using normalized float RGBA values ranging from 0.0F to 1.0F.
         *
         * @param r Red component value (0.0F - 1.0F).
         * @param g Green component value (0.0F - 1.0F).
         * @param b Blue component value (0.0F - 1.0F).
         * @param a Alpha opacity density value (0.0F - 1.0F).
         * @return A new Attributes instance with the updated fog color configuration.
         */
        public Attributes withFogColor(@Range(from = 0, to = 1)float r, @Range(from = 0, to = 1)float g, @Range(from = 0, to = 1)float b, @Range(from = 0, to = 1)float a) {
            Vector4f newColor= new Vector4f(
                    Mth.clamp(r, 0.0f, 1.0f),
                    Mth.clamp(g, 0.0f, 1.0f),
                    Mth.clamp(b, 0.0f, 1.0f),
                    Mth.clamp(a, 0.0f, 1.0f));

            return new Attributes(this.temperature, this.viscosity, this.density, this.luminosity, this.fluidName, newColor);
        }

        /**
         * Sets the fog color using an encoded hexadecimal integer (e.g., 0x00FFF0) and a float alpha value.
         *
         * @param hex The color map encoded as a hexadecimal integer.
         * @param a   Alpha opacity density value (0.0F - 1.0F).
         * @return A new Attributes instance with the updated fog color configuration.
         */
        public Attributes withFogColor(int hex, @Range(from = 0, to = 1)float a){
            float r = Mth.clamp(((hex >> 16) & 0xFF) / 255.0f, 0.0f, 1.0f);
            float g = Mth.clamp(((hex >> 8) & 0xFF) / 255.0f, 0.0f, 1.0f);
            float b = Mth.clamp((hex & 0xFF) / 255.0f, 0.0f, 1.0f);

            return withFogColor(r, g, b, Mth.clamp(a, 0.0f, 1.0f));
        }
    }

    /**
     * Data container record hosting safe RegistryObject suppliers required for framework stitching.
     */
    public record Properties(
            String modId,
            Supplier<? extends Fluid> stillFluid,
            Supplier<? extends Fluid> flowingFluid,
            Supplier<? extends Item> bucket,
            Supplier<? extends LiquidBlock> block
    ){

        public Properties(String modId,
                          Supplier<? extends Fluid> stillFluid,
                          Supplier<? extends Fluid> flowingFluid,
                          Supplier<? extends Item> bucket) {
            this(modId, stillFluid, flowingFluid, bucket, ()->null);
        }

        public Properties withBlock(Supplier<? extends LiquidBlock> blockSupplier){
            return new Properties(this.modId, this.stillFluid, this.flowingFluid, this.bucket, blockSupplier);
        }
    }

    /**
     * Represents the flowing variant of the base fluid instance.
     */
    public static class Flowing extends BaseFluid{

        public Flowing(Properties properties, Attributes attributes, Function<BaseFluid, BaseFluidHook> baseHookFactory){
            super(properties, attributes, baseHookFactory);
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
    public static class Source extends BaseFluid{

        public Source(Properties properties, Attributes attributes, Function<BaseFluid, BaseFluidHook> baseHookFactory){
            super(properties, attributes, baseHookFactory);
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