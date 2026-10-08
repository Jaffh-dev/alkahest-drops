package com.jaffh.alkahestcore.fluidscore.common.hooks;

import com.jaffh.alkahestcore.fluidscore.common.fluids.AdvancedFluid;
import com.jaffh.alkahestcore.fluidscore.common.fluids.BaseFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The premium extensibility pipeline for complex fluid mechanics within the AlkahestCore framework.
 * <p>By extending this class, developers gain absolute control over the fluid's microphysics,
 * allowing for the modification of rendering bounds, custom vector flow forces, solid boundary layers,
 * and surgical flow-direction constraints.</p>
 *
 * @see AdvancedFluid
 */
public class AdvancedFluidHook{

    /**
     * The advanced fluid instance that owns and executes this hook pipeline.
     */
    protected final AdvancedFluid hookOwner;

    public AdvancedFluidHook(AdvancedFluid hookOwner){
        this.hookOwner = hookOwner;
    }

    /**
     * Intercepts and modifies the physics flow vector, altering how entities are pushed while submerged.
     * <p><b>Use Cases:</b> Perfect for creating complex current mechanics. Use this to implement magnetic whirlpools
     * that pull entities toward a central point, high-velocity rapids that launch players, reverse-gravity anomalies
     * that carry entities upward, or localized conveyor-belt fluids.</p>
     *
     * @param level       The block getter providing world access.
     * @param pos         The coordinate position of the fluid block.
     * @param state       The current fluid state.
     * @param defaultFlow The default directional flow velocity vector computed by the vanilla engine.
     * @return The modified {@link Vec3} vector force. Returning {@code defaultFlow} maintains standard behavior.
     */
    public Vec3 getCustomFlow(BlockGetter level, BlockPos pos, FluidState state, Vec3 defaultFlow){
        return defaultFlow;
    }

    /**
     * Dictates whether a specific side/face of this fluid block behaves as a solid surface boundary.
     * <p><b>Use Cases:</b> Ideal for designing non-Newtonian or hyper-viscous fluids. This can be used to create
     * a layer of quicksand (solid when approached from the side, but non-solid from the top), or a walking-on-water
     * mechanic where players can run across the top face of the liquid if they maintain a certain velocity.</p>
     *
     * @param level              The block getter providing world access.
     * @param pos                The coordinate position of the fluid block.
     * @param direction          The specific block face being evaluated.
     * @param defaultIsSolidFace The default evaluation supplied by the engine (typically false for liquids).
     * @return {@code true} to make this specific face act as a solid wall/floor; {@code false} otherwise.
     */
    public boolean isSolidFace(BlockGetter level, BlockPos pos, Direction direction, boolean defaultIsSolidFace){
        return defaultIsSolidFace;
    }

    /**
     * Calculates and mutates the fluid's state profile, typically triggered by neighboring block updates.
     * <p><b>Use Cases:</b> Useful for real-time chemical compounding or reactive simulation. For example, forcing
     * a volatile fluid to instantly change its density state when exposed to nearby heat blocks, or preventing a fluid
     * from losing its source state properties when interacting with specific block types.</p>
     *
     * @param level        The reader world context.
     * @param pos          The target block position being updated.
     * @param blockState   The current block state occupying that coordinate.
     * @param defaultState The standard resulting state intended by the vanilla engine calculation.
     * @return The chosen {@link FluidState} configuration. Returning {@code defaultState} maintains standard logic.
     */
    public FluidState getNewCustomLiquidState(LevelReader level, BlockPos pos, BlockState blockState, FluidState defaultState){
        return defaultState;
    }

    /**
     * Customizes the render-mesh height calculation of the fluid block, directly affecting visual appearance and buoyancy calculations.
     * <p><b>Use Cases:</b> Can be used to implement custom fluid animation effects, such as a fluid that rhythmically expands
     * and contracts to simulate breathing/tides, or a highly dense sludge that physically collapses down visually under pressure.</p>
     *
     * @param state         The current fluid state.
     * @param level         The block getter providing world access.
     * @param pos           The coordinate position of the fluid block.
     * @param defaultHeight The default height scalar calculated from the fluid's level property (ranging from 0.0 to 1.0).
     * @return The modified float height level. Returning {@code defaultHeight} maintains standard behavior.
     */
    public float getCustomHeight(FluidState state, BlockGetter level, BlockPos pos, float defaultHeight){
        return defaultHeight;
    }

    /**
     * Defines a custom structural collision and selection boundary hitbox (VoxelShape) for the fluid block.
     * <p><b>Use Cases:</b> Great for adding physical interaction depth. Use this to construct sticky tar pits with complex
     * stepped hitboxes that capture entities, or to make a magic liquid clickable/selectable like a normal solid block
     * while retaining fluid visuals.</p>
     *
     * @param state        The current fluid state.
     * @param level        The block getter providing world access.
     * @param pos          The coordinate position of the fluid block.
     * @param defaultShape The default voxel outline provided by the underlying fluid engine.
     * @return The customized {@link VoxelShape} layout rule.
     */
    public VoxelShape getCustomShape(FluidState state, BlockGetter level, BlockPos pos, VoxelShape defaultShape){
        return defaultShape;
    }

    /**
     * Gatekeeps and validates whether a fluid current is physically allowed to spread into a targeted adjacent block.
     * <p><b>Use Cases:</b> Gives you precise pathfinding restriction control. You can use this to engineer directional fluids
     * (e.g., a polarized liquid that can only expand along the North-South axis), or build protective runic containment blocks
     * that custom fluids are absolutely forbidden from breaking through or spilling into.</p>
     *
     * @param level      The world accessor processing block updates.
     * @param pos        The destination coordinates the fluid wants to expand into.
     * @param blockState The current block state residing at the destination coordinates.
     * @param direction  The outward direction vector from the source block to the destination block.
     * @param fluidState The incoming fluid state preparing to occupy the target location.
     * @return {@code true} to allow expansion; {@code false} to cancel the flow action completely.
     */
    public boolean canSpreadTo(LevelAccessor level, BlockPos pos, BlockState blockState, Direction direction, FluidState fluidState) {
        return true;
    }

    /**
     * Pre-spread interceptor execution phase. Triggers instantly before the fluid schedules or spreads an update to its surroundings.
     * <p><b>Use Cases:</b> Excellent for synchronization, tracking systems, or heavy sound/particle choreography. Use this to
     * count and limit how far a single fluid chain can branch off from its original parent source, or to play a low-frequency
     * rumbling effect right before a massive wave of liquid advances.</p>
     *
     * @param level The world accessor processing block updates.
     * @param pos   The origin coordinate position of the fluid executing the spread.
     * @param state The current fluid state at the origin position.
     */
    public void beforeSpread(LevelAccessor level, BlockPos pos, FluidState state) {
    }

    /**
     * Defines the intrinsic base height property of the fluid, independent of external world constraints or state factors.
     * <p><b>Use Cases:</b> Primarily leveraged by internal inventory management hooks or specialized multi-block fluid storage tanks.
     * Overriding this ensures that external pipes or tank displays interpret the volume capacity or fluid density profile
     * exactly as your mod intended, regardless of environment layout.</p>
     *
     * @param state            The current fluid state.
     * @param defaultOwnHeight The base default height metric computed by the engine.
     * @return The final float value representing the fluid's own static height trait.
     */
    public float getCustomOwnHeight(FluidState state, float defaultOwnHeight) {
        return defaultOwnHeight;
    }
}
