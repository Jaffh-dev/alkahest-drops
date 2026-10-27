package com.jaffh.alkahestdrops.server.command;

import com.jaffh.alkahestdrops.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestdrops.server.drops.TriggerTables;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropManager;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Represents the mob and the block one {@code <entity | block>} command argument names. An id can name both, such as
 * {@code minecraft:tnt}.
 *
 * @param id     Looked-up id. Never null.
 * @param entity Mob type with this id, or null.
 * @param block  Block with this id, or null. At least one of {@code entity} and {@code block} is set.
 */
record TargetLookup(ResourceLocation id, @Nullable EntityType<?> entity, @Nullable Block block) {
    private static final DynamicCommandExceptionType UNKNOWN_TARGET = new DynamicCommandExceptionType(
            id -> CommandText.lang("error.unknown_target", id));

    /**
     * Finds the mob and the block of an id.
     *
     * @param id Id typed in the command. Never null.
     * @return The lookup.
     * @throws CommandSyntaxException If no mob or block has this id.
     */
    static TargetLookup of(ResourceLocation id) throws CommandSyntaxException {
        EntityType<?> entity = ForgeRegistries.ENTITY_TYPES.containsKey(id) ? ForgeRegistries.ENTITY_TYPES.getValue(id) : null;
        Block block = ForgeRegistries.BLOCKS.containsKey(id) ? ForgeRegistries.BLOCKS.getValue(id) : null;
        if (entity == null && block == null) throw UNKNOWN_TARGET.create(id);
        return new TargetLookup(id, entity, block);
    }

    /**
     * Creates a suggestion provider of every mob and block whose drop tables pass a test.
     *
     * @param tableTest Test of the tables of every trigger of a target. Never null.
     * @return The provider, suggesting the ids sorted.
     */
    static SuggestionProvider<CommandSourceStack> suggestingTables(Predicate<TriggerTables> tableTest) {
        return (context, builder) -> SharedSuggestionProvider.suggestResource(idsWithTable(tableTest), builder);
    }

    /**
     * Collects the ids of every mob and block whose drop tables pass a test.
     *
     * @param tableTest Test of the tables of every trigger of a target. Never null.
     * @return The ids, sorted.
     */
    private static Set<ResourceLocation> idsWithTable(Predicate<TriggerTables> tableTest) {
        Set<ResourceLocation> ids = new TreeSet<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            if (tableTest.test(MobDropManager.INSTANCE.triggerTablesOf(type))) ids.add(ForgeRegistries.ENTITY_TYPES.getKey(type));
        }
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            if (tableTest.test(BlockDropManager.INSTANCE.triggerTablesOf(block))) ids.add(ForgeRegistries.BLOCKS.getKey(block));
        }
        return ids;
    }
}
