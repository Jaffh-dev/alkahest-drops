package com.jaffh.alkahestcore.dropscore.client.compat.jei;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.common.ForgeSpawnEggItem;

import javax.annotation.Nullable;

/**
 * Tells JEI how to name, identify and search {@link MobIngredient}s. In cheat mode, a mob gives its spawn egg.
 */
final class MobIngredientHelper implements IIngredientHelper<MobIngredient> {

    /**
     * Returns the ingredient type.
     *
     * @return {@link MobIngredient#TYPE}.
     */
    @Override
    public IIngredientType<MobIngredient> getIngredientType() {
        return MobIngredient.TYPE;
    }

    /**
     * Returns the name of a mob, used by JEI's search.
     *
     * @param ingredient Mob. Never null.
     * @return The translated name, such as "Zombie".
     */
    @Override
    public String getDisplayName(MobIngredient ingredient) {
        return ingredient.type().getDescription().getString();
    }

    /**
     * Returns a unique id of a mob.
     *
     * @param ingredient Mob. Never null.
     * @param context    Where the id is used. Never null.
     * @return Such as {@code "mob:minecraft:zombie"}.
     */
    @Override
    public String getUniqueId(MobIngredient ingredient, UidContext context) {
        return "mob:" + ingredient.id();
    }

    /**
     * Returns the registry id of a mob, which JEI uses for its mod name.
     *
     * @param ingredient Mob. Never null.
     * @return The id of the mob type.
     */
    @Override
    public ResourceLocation getResourceLocation(MobIngredient ingredient) {
        return ingredient.id();
    }

    /**
     * Returns the item cheat mode gives for a mob.
     *
     * @param ingredient Mob. Never null.
     * @return Its spawn egg, or an empty stack when it has none.
     */
    @Override
    public ItemStack getCheatItemStack(MobIngredient ingredient) {
        SpawnEggItem egg = ForgeSpawnEggItem.fromEntityType(ingredient.type());
        return egg != null ? new ItemStack(egg) : ItemStack.EMPTY;
    }

    /**
     * Copies a mob ingredient.
     *
     * @param ingredient Mob. Never null.
     * @return The same ingredient, which is immutable.
     */
    @Override
    public MobIngredient copyIngredient(MobIngredient ingredient) {
        return ingredient;
    }

    /**
     * Describes a mob for JEI's error logs.
     *
     * @param ingredient Mob, or null.
     * @return The mob's id, or {@code "null"}.
     */
    @Override
    public String getErrorInfo(@Nullable MobIngredient ingredient) {
        return ingredient == null ? "null" : ingredient.id().toString();
    }
}
