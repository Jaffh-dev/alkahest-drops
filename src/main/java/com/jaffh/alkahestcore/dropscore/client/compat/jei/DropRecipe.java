package com.jaffh.alkahestcore.dropscore.client.compat.jei;

import com.jaffh.alkahestcore.dropscore.common.drops.DropTrigger;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedDrop;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedTarget;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Represents one JEI recipe: one target, one trigger and a part of the drops of that trigger. Each trigger of a
 * target gets its own pages, death first; a trigger with more drops than its category's grid holds is split into
 * several parts.
 *
 * @param target   The mob type or block. Never null.
 * @param targetId Id of the target. Never null.
 * @param trigger  What makes these drops happen. Never null.
 * @param drops    Drops of this part. Immutable, never null.
 * @param part     Index of this part within its trigger, from 0.
 * @param <T>      Target type, such as {@code EntityType<?>} or {@code Block}.
 */
record DropRecipe<T>(T target, ResourceLocation targetId, DropTrigger trigger, List<SyncedDrop> drops, int part) {

    /**
     * Creates a recipe and stores an immutable copy of its drops.
     */
    DropRecipe {
        drops = List.copyOf(drops);
    }

    /**
     * Creates the recipes of every synced target whose id is registered on this client.
     *
     * @param targets      Synced targets. Never null.
     * @param registry     Registry of the targets. Never null.
     * @param dropsPerPage Drops one recipe holds, 1 or more.
     * @param <T>          Target type.
     * @return The recipes, per target: each trigger in declaration order, each in parts.
     */
    static <T> List<DropRecipe<T>> allOf(List<SyncedTarget> targets, IForgeRegistry<T> registry, int dropsPerPage) {
        List<DropRecipe<T>> recipes = new ArrayList<>();
        for (SyncedTarget synced : targets) {
            if (!registry.containsKey(synced.id())) continue;
            T target = registry.getValue(synced.id());
            if (target == null) continue;
            for (Map.Entry<DropTrigger, List<SyncedDrop>> byTrigger : groupByTrigger(synced.drops()).entrySet()) {
                List<SyncedDrop> drops = byTrigger.getValue();
                for (int start = 0, part = 0; start < drops.size(); start += dropsPerPage, part++) {
                    List<SyncedDrop> partDrops = drops.subList(start, Math.min(start + dropsPerPage, drops.size()));
                    recipes.add(new DropRecipe<>(target, synced.id(), byTrigger.getKey(), partDrops, part));
                }
            }
        }
        return recipes;
    }

    /**
     * Groups drops by trigger, keeping their order inside each trigger.
     *
     * @param drops Drops of one target. Never null.
     * @return The drops of every trigger that has some, in declaration order of the triggers.
     */
    private static Map<DropTrigger, List<SyncedDrop>> groupByTrigger(List<SyncedDrop> drops) {
        Map<DropTrigger, List<SyncedDrop>> grouped = new EnumMap<>(DropTrigger.class);
        for (SyncedDrop drop : drops) {
            grouped.computeIfAbsent(drop.trigger().trigger(), trigger -> new ArrayList<>()).add(drop);
        }
        return grouped;
    }

    /**
     * Returns the id JEI shows for this recipe in advanced tooltips and uses for bookmarks.
     *
     * @return The target id for the first death part, such as {@code minecraft:zombie}; otherwise with the trigger
     *         and part, such as {@code minecraft:sheep/shear} or {@code minecraft:zombie/part_1}.
     */
    ResourceLocation id() {
        String path = targetId.getPath();
        if (trigger != DropTrigger.DEATH) path += "/" + trigger.id();
        if (part > 0) path += "/part_" + part;
        return new ResourceLocation(targetId.getNamespace(), path);
    }
}
