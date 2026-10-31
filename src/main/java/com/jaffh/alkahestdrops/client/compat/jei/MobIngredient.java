package com.jaffh.alkahestdrops.client.compat.jei;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A mob as a JEI ingredient, so every mob is listed in JEI (drawn as a small 3D model) and clicking it opens its drop
 * page, also for mobs without a spawn egg such as the Ender Dragon. A wrapper instead of {@code EntityType} itself, so
 * another mod registering entity types as ingredients never clashes with this type.
 *
 * @param type The mob type. Never null.
 */
record MobIngredient(EntityType<?> type) {
    /** JEI ingredient type of mobs. */
    static final IIngredientType<MobIngredient> TYPE = () -> MobIngredient.class;

    /**
     * Returns the id of the mob type.
     *
     * @return The registry id, such as {@code minecraft:zombie}.
     */
    ResourceLocation id() {
        return ForgeRegistries.ENTITY_TYPES.getKey(type);
    }

    /**
     * Lists every mob that can die, the mobs that can have drop pages.
     *
     * @return One ingredient per living mob type except players, in registry order.
     */
    static List<MobIngredient> all() {
        List<MobIngredient> mobs = new ArrayList<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            if (type != EntityType.PLAYER && DefaultAttributes.hasSupplier(type)) mobs.add(new MobIngredient(type));
        }
        return mobs;
    }
}
