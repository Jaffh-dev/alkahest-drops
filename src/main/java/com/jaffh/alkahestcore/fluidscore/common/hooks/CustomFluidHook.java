package com.jaffh.alkahestcore.fluidscore.common.hooks;

import com.jaffh.alkahestcore.fluidscore.common.fluids.BaseFluid;
import com.jaffh.alkahestcore.fluidscore.common.fluids.CustomFluid;
import com.jaffh.alkahestcore.fluidscore.common.behaviour.IHotFluidBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.Random;

/**
 * Internal implementation of {@link BaseFluidHook} acting as an adapter pipeline
 * for {@link CustomFluid}.
 * Maps the data-driven parameters from the fluid's {@link CustomFluid.Configs} record
 * directly into the simulation, physics, and world interaction engine.
 */
public class CustomFluidHook extends BaseFluidHook implements IHotFluidBehaviour {

    /**
     * Constructs a new configuration adapter hook for the specified fluid instance.
     *
     * @param hookOwner The {@link BaseFluid} instance executing this hook pipeline.
     */
    public CustomFluidHook(BaseFluid hookOwner) {
        super(hookOwner);
    }

    /**
     * Internal cast helper to access the owning {@link CustomFluid} instance safely.
     *
     * @return The cast {@link CustomFluid} instance managing this hook.
     */
    private CustomFluid getCustomOwner() {
        return (CustomFluid) this.hookOwner;
    }

    /**
     * References the configured horizontal slope pathfinding boundary value.
     *
     * @param level The reader world context.
     * @return The pathfind limit distance specified in the fluid configuration.
     */
    @Override
    public int getCustomSlopeFindDistance(LevelReader level) {
        return getCustomOwner().getConfigs().slopeFindDistance();
    }

    /**
     * References the configured level height dropoff decrement rating.
     *
     * @param level The reader world context.
     * @return The fluid drop-off decrement step rate specified in the fluid configuration.
     */
    @Override
    public int getCustomDropOff(LevelReader level) {
        return getCustomOwner().getConfigs().dropOff();
    }

    /**
     * References the configured scheduled engine base update delay value.
     *
     * @param level The reader world context.
     * @return The update fluid interval rate specified in the fluid configuration.
     */
    @Override
    public int getCustomTickDelay(LevelReader level) {
        return getCustomOwner().getConfigs().tickDelay();
    }

    /**
     * References whether this fluid configuration allows infinite stream merging.
     *
     * @return {@code true} if infinite source block generation is allowed by the configuration.
     */
    @Override
    public boolean canConvertToSource() {
        return getCustomOwner().getConfigs().canConvertToSource();
    }

    /**
     * Intercepts environmental block destruction updates to apply temperature conditional logic.
     * If the configuration defines this liquid as hot, it triggers a fizzing sound reaction profile;
     * otherwise, drops standard loot resources.
     *
     * @param level The world accessor.
     * @param pos   The target position being consumed by the fluid stream.
     * @param state The active block state about to be overridden.
     */
    @Override
    public void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        CustomFluid fluid = getCustomOwner();

        if (!fluid.isSame(state.getFluidState().getType())) {
            if (fluid.getConfigs().isHot()) {
                this.fizz(level, pos);
            } else {
                BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
                Block.dropResources(state, level, pos, blockEntity);
            }
        }
    }

    /**
     * Links random block tick subscription status to the fluid's heat configuration.
     * Hot fluids register for ticks to simulate environmental fire hazards.
     *
     * @return {@code true} if the fluid is hot and requires random ticking loops; {@code false} otherwise.
     */
    @Override
    public boolean isRandomlyTicking(){
        return getCustomOwner().getConfigs().isHot();
    }

    /**
     * Simulates thermal spread behaviors across adjacent air blocks.
     * Evaluates fire spread intensity iterations at random nearby intervals if the
     * world game rules authorize block fire ticking updates.
     *
     * @param level  The active world instance.
     * @param pos    The fluid block coordinate source.
     * @param state  The active fluid state.
     * @param random The random instance used for chance iteration directions.
     */
    @Override
    public void onRandomTick(Level level, BlockPos pos, FluidState state, Random random){

        if (level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            int attempts = getCustomOwner().getConfigs().fireSpreadIntensity();

            for (int i=0; i<attempts; i++){
                Direction dir = Direction.getRandom(random);
                BlockPos targetPos = pos.relative(dir);

                if (level.isEmptyBlock(targetPos)) {
                    if (this.hasFlammableNeighbours(level, targetPos)) {
                        level.setBlockAndUpdate(targetPos, BaseFireBlock.getState(level, targetPos));
                    }
                }
            }
        }
    }
}
