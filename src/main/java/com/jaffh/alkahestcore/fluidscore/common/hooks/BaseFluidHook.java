package com.jaffh.alkahestcore.fluidscore.common.hooks;


import com.jaffh.alkahestcore.fluidscore.common.fluids.BaseFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.Random;

/**
 * The primary extensibility point for fluid behaviors within the AlkahestCore framework.
 * <p>By extending this class and registering it to a fluid, developers can intercept, override,
 * and heavily customize world interactions, spreading physics, and simulation logic without
 * directly subclassing or modifying core registry blocks.</p>
 *
 * @see BaseFluid
 */
public class BaseFluidHook {

    /**
     * The fluid instance that owns and executes this hook pipeline.
     */
    protected final BaseFluid hookOwner;

    public BaseFluidHook(BaseFluid hookOwner){
        this.hookOwner = hookOwner;
    }

    /**
     * Determines the schedule delay (in ticks) before a flowing stream attempts to expand into neighboring positions.
     * <p><b>Use Cases:</b> Can be used to implement contextual viscosity shifts. For example, making a fluid
     * flow faster when exposed to open daylight, or significantly slowing down a thick sludge as it cools down.</p>
     *
     * @param level              The active world context.
     * @param pos                The origin block position of the fluid block.
     * @param state              The current fluid state at the origin position.
     * @param newState           The target state the fluid is preparing to transition into.
     * @param defaultSpreadDelay The default tick delay configured by the fluid's properties (Water = 5, Lava = 30).
     * @return The custom delay in game ticks. Returning {@code defaultSpreadDelay} preserves default behavior.
     */
    public int getCustomSpreadDelay(Level level, BlockPos pos, FluidState state, FluidState newState, int defaultSpreadDelay){
        return defaultSpreadDelay;
    }

    /**
     * Evaluates whether this fluid can be replaced or overridden by another incoming fluid or block state.
     * <p><b>Use Cases:</b> Excellent for designing unmixable or hydrophobic liquids (e.g., oil staying separate
     * from water), constructing highly "stubborn" fluids that block other liquid currents, or initializing custom
     * chemical reactions before a replacement occurs.</p>
     *
     * @param state                  The current fluid state at the given position.
     * @param level                  The block getter providing world access.
     * @param pos                    The target coordinate where the replacement is happening.
     * @param fluid                  The incoming fluid attempting to occupy this block space.
     * @param direction              The direction vector from which the incoming fluid is flowing.
     * @param defaultCanReplaceFluid The vanilla system's standard evaluation (usually returns true if fluids match).
     * @return {@code true} if the incoming fluid is allowed to overwrite this position; {@code false} otherwise.
     */
    public boolean canReplaceFluid(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction, boolean defaultCanReplaceFluid){
        return defaultCanReplaceFluid;
    }

    /**
     * Overrides the explosion resistance capability rating of the fluid block.
     * <p><b>Use Cases:</b> Standard fluids (Water/Lava) return 100.0F to safely absorb shockwaves. You can lower
     * this value to design volatile explosive chemicals that instantly detonate or vanish when caught in a blast radius,
     * or raise it to create hyper-dense kinetic dampening shield barriers.</p>
     *
     * @return The scalar explosion resistance value. Defaults to 100.0F.
     */
    public float getCustomExplosionResistance(){
        return 100.0f;
    }

    /**
     * Toggles whether this fluid qualifies to receive random block updates from the game engine.
     * <p><b>Use Cases:</b> Return {@code true} if your liquid needs to process low-frequency environmental checks.
     * Examples include puddles evaporating under hot biomes, toxic runoff poisoning adjacent dirt blocks, or
     * radioactive waste slowly decaying over time.</p>
     * <p><i>Warning: Enable this judiciously; keeping it active across massive oceans can impact server tick performance.</i></p>
     *
     * @return {@code true} to sign up for random block updates; {@code false} to skip them. Default is {@code false}.
     */
    public boolean isRandomlyTicking(){return false;}

    /**
     * Processes sporadic, low-frequency tick events. Only triggers if {@link #isRandomlyTicking()} returns true.
     * <p><b>Use Cases:</b> Ideal for background mechanics that do not require aggressive per-tick execution,
     * such as random block mutation, spreading corruption, checking for weather interactions, or spawning passive steam particles.</p>
     *
     * @param level  The active world context.
     * @param pos    The coordinate position of the fluid block.
     * @param state  The active fluid state.
     * @param random The pseudo-random number generator used for safe chance scaling calculations.
     */
    public void onRandomTick(Level level, BlockPos pos, FluidState state, Random random){}

    /**
     * Main simulation loop execution hook, evaluated every single game tick at the fluid's position.
     * <p><b>Use Cases:</b> Perfect for managing aggressive real-time logic. Use this to apply potion effects or
     * custom damage to players standing inside it, melt surrounding ice blocks, or run sub-tick particle engines.</p>
     *
     * @param level The active world context level.
     * @param pos   The coordinates of the ticking fluid.
     * @param state The current fluid state.
     */
    public void onTick(Level level, BlockPos pos, FluidState state){}

    /**
     * Dictates the maximum radius range (in blocks) the fluid can pathfind horizontally when searching for downslopes.
     * <p><b>Use Cases:</b> Controls spreading intelligence. Lowering this value (e.g., 1 or 2) simulates heavy muddy sludges
     * that blindly pool up instead of flowing towards nearby drops. Raising this simulates smart, hyper-fluid mercury that
     * actively searches far ahead to find crevices to cascade into.</p>
     *
     * @param level The reader world context.
     * @return Maximum pathfinding search block range. Default is 4 (matching vanilla Water).
     */
    public int getCustomSlopeFindDistance(LevelReader level){
        return 4;
    }

    /**
     * Determines how much height level allocation the fluid loses for each horizontal block it spreads.
     * <p><b>Use Cases:</b> Regulates the maximum horizontal stream length. Leaving this at 1 allows the fluid to flow
     * out up to 8 blocks away from its source. Setting this to 2 or 4 creates thick, highly viscous liquids (like honey
     * or wet concrete) that travel a very short distance before stopping completely.</p>
     *
     * @param level The reader world context.
     * @return The height value decremented per block traveled. Default is 1.
     */
    public int getCustomDropOff(LevelReader level){
        return 1;
    }

    /**
     * Sets the base scheduled tick scheduling delay intervals assigned to this fluid block type.
     * <p><b>Use Cases:</b> Dictates general simulation speeds. Lowering this value makes the liquid respond to block
     * updates and cascade down cliffs nearly instantaneously. Increasing this makes updates feel heavy and delayed.</p>
     *
     * @param level The reader world context.
     * @return Scheduled update tick rate interval. Default is 5.
     */
    public int getCustomTickDelay(LevelReader level){
        return 5;
    }

    /**
     * Triggers immediately before the fluid flows over and destroys an existing block in the world.
     * <p><b>Use Cases:</b> By default, this drops the target block's items safely (like breaking torches or tall grass).
     * You can override this to implement destructive traits, such as an acid fluid completely vaporizing and incinerating drops
     * instead of letting the player collect them, or playing specific sizzling sound effects.</p>
     *
     * @param level The world accessor.
     * @param pos   The block coordinate position being overridden.
     * @param state The block state about to be destroyed.
     */
    public void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state){
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        Block.dropResources(state, level, pos, blockEntity);
    }

    /**
     * Controls whether individual streams of this fluid can merge together to produce new infinite source blocks.
     * <p><b>Use Cases:</b> Set to {@code true} to duplicate vanilla Water mechanics (e.g., 2x2 pools forming infinite wells).
     * Return {@code false} to match rare resource behavior like Lava, preventing players from exploiting or duplicating
     * precious custom metals or chemical fluids.</p>
     *
     * @return {@code true} if infinite source regeneration mechanics are allowed; {@code false} otherwise.
     */
    public boolean canConvertToSource(){
        return true;
    }
}
