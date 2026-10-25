package com.jaffh.alkahestcore.dropscore.client.compat.jei;

import com.jaffh.alkahestcore.dropscore.common.drops.SyncedDrop;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedTarget;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one JEI recipe: one target and a part of its drops. A target with more drops than its category's grid
 * holds is split into several parts.
 *
 * @param target   The mob type or block. Never null.
 * @param targetId Id of the target. Never null.
 * @param drops    Drops of this part. Immutable, never null.
 * @param part     Index of this part, from 0.
 * @param <T>      Target type, such as {@code EntityType<?>} or {@code Block}.
 */
record DropRecipe<T>(T target, ResourceLocation targetId, List<SyncedDrop> drops, int part) {

    /**
     * Creates a recipe and stores an immutable copy of its drops.
     */
    DropRecipe {
        drops = List.copyOf(drops);
    }

    /**
     * Builds the recipes of every synced target this client knows.
     *
     * @param targets      Synced targets of one module. Never null.
     * @param registry     Registry of the targets. Never null.
     * @param dropsPerPage Most drops one recipe shows, above 0.
     * @param <T>          Target type.
     * @return The recipes, in target order; targets unknown here are left out.
     */
    static <T> List<DropRecipe<T>> allOf(List<SyncedTarget> targets, IForgeRegistry<T> registry, int dropsPerPage) {
        List<DropRecipe<T>> recipes = new ArrayList<>();
        for (SyncedTarget synced : targets) {
            if (!registry.containsKey(synced.id())) continue;
            T target = registry.getValue(synced.id());
            if (target == null) continue;
            List<SyncedDrop> drops = synced.drops();
            for (int start = 0, part = 0; start < drops.size(); start += dropsPerPage, part++) {
                List<SyncedDrop> partDrops = drops.subList(start, Math.min(start + dropsPerPage, drops.size()));
                recipes.add(new DropRecipe<>(target, synced.id(), partDrops, part));
            }
        }
        return recipes;
    }

    /**
     * Returns a unique id of this recipe inside its category.
     *
     * @return The target id, with {@code /part_<n>} for every part after the first.
     */
    ResourceLocation id() {
        return part == 0 ? targetId : new ResourceLocation(targetId.getNamespace(), targetId.getPath() + "/part_" + part);
    }
}
