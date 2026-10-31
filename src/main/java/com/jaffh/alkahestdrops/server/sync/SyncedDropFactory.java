package com.jaffh.alkahestdrops.server.sync;

import com.jaffh.alkahestdrops.common.drops.DropCount;
import com.jaffh.alkahestdrops.api.DropTrigger;
import com.jaffh.alkahestdrops.common.drops.SyncedDrop;
import com.jaffh.alkahestdrops.common.drops.SyncedDropRules;
import com.jaffh.alkahestdrops.common.drops.SyncedTarget;
import com.jaffh.alkahestdrops.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Builds the drop summary sent to clients: one entry per mob that can die and per block, each with its full drop list
 * (see {@link TargetDropsBuilder}). Targets without any drop are left out.
 */
public final class SyncedDropFactory {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Experience samples per target; vanilla rolls mob and ore experience at random. */
    private static final int EXPERIENCE_SAMPLES = 32;

    /**
     * Prevents instantiation of this utility class.
     */
    private SyncedDropFactory() {
    }

    /**
     * Builds the summary of the loaded drop files and loot tables.
     *
     * @param server Running server. Never null.
     * @return The summary sent to clients.
     */
    public static SyncedDropRules rulesOf(MinecraftServer server) {
        return new SyncedDropRules(mobsOf(server), blocksOf(server));
    }

    /**
     * Builds the targets of every mob that can die and has at least one drop.
     *
     * @param server Running server. Never null.
     * @return The mob targets, in registry order.
     */
    private static List<SyncedTarget> mobsOf(MinecraftServer server) {
        TargetDropsBuilder builder = builderOf(server, false);
        ServerLevel level = server.overworld();
        List<SyncedTarget> targets = new ArrayList<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            if (type == EntityType.PLAYER || !DefaultAttributes.hasSupplier(type)) continue;
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            List<SyncedDrop> drops = new ArrayList<>(builder.build(id, type.getDefaultLootTable(), MobDropManager.getTable(type),
                    mobExperienceOf(type, level)));
            for (DropTrigger trigger : DropTrigger.values()) {
                if (trigger != DropTrigger.DEATH) drops.addAll(builder.buildTriggered(MobDropManager.getTable(type, trigger)));
            }
            if (!drops.isEmpty()) targets.add(new SyncedTarget(id, drops));
        }
        return targets;
    }

    /**
     * Builds the targets of every block that has at least one drop.
     *
     * @param server Running server. Never null.
     * @return The block targets, in registry order.
     */
    private static List<SyncedTarget> blocksOf(MinecraftServer server) {
        TargetDropsBuilder builder = builderOf(server, true);
        ServerLevel level = server.overworld();
        List<SyncedTarget> targets = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            if (block.defaultBlockState().isAir()) continue;
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            List<SyncedDrop> drops = builder.build(id, block.getLootTable(), BlockDropManager.getTable(block),
                    blockExperienceOf(block.defaultBlockState(), level));
            if (!drops.isEmpty()) targets.add(new SyncedTarget(id, drops));
        }
        return targets;
    }

    /**
     * Creates the target builder of one module.
     *
     * @param server  Running server. Never null.
     * @param isBlock Whether the module is block drops.
     * @return The builder.
     */
    private static TargetDropsBuilder builderOf(MinecraftServer server, boolean isBlock) {
        ConditionDescriber describer = new ConditionDescriber(isBlock);
        return new TargetDropsBuilder(new LootTableAnalyzer(server.getLootData(), describer), describer);
    }

    /**
     * Samples the vanilla experience of a mob, from a mob created for it and never added to the level.
     *
     * @param type  Mob type. Never null.
     * @param level Level to create the mob in. Never null.
     * @return The experience range, or null when it gives none or cannot be created.
     */
    @Nullable
    private static DropCount mobExperienceOf(EntityType<?> type, ServerLevel level) {
        Entity entity;
        try {
            entity = type.create(level);
        } catch (RuntimeException e) {
            LOGGER.warn("[alkahestdrops] Cannot create {} to read its experience for JEI: {}", ForgeRegistries.ENTITY_TYPES.getKey(type), e.toString());
            return null;
        }
        if (!(entity instanceof LivingEntity living)) return null;
        return sampleExperience(living::getExperienceReward);
    }

    /**
     * Samples the vanilla experience of a block broken without Fortune or Silk Touch.
     *
     * @param state Default state of the block. Never null.
     * @param level Level the experience is read in. Never null.
     * @return The experience range, or null when it gives none.
     */
    @Nullable
    private static DropCount blockExperienceOf(BlockState state, ServerLevel level) {
        RandomSource random = RandomSource.create(0);
        return sampleExperience(() -> state.getExpDrop(level, random, BlockPos.ZERO, 0, 0));
    }

    /**
     * Samples an experience reward several times.
     *
     * @param reward Rolls one reward. Never null.
     * @return The lowest and highest sampled values, or null when every sample is 0 or the reward cannot be read.
     */
    @Nullable
    private static DropCount sampleExperience(IntSupplier reward) {
        int min = Integer.MAX_VALUE;
        int max = 0;
        try {
            for (int i = 0; i < EXPERIENCE_SAMPLES; i++) {
                int value = reward.getAsInt();
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        } catch (RuntimeException e) {
            return null;
        }
        return max > 0 ? new DropCount(Math.max(0, min), max) : null;
    }
}
