package com.jaffh.alkahestcore.dropscore.client.compat.jei;

import com.jaffh.alkahestcore.AlkahestCore;
import com.jaffh.alkahestcore.dropscore.client.drops.ClientDropRules;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedDropRules;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

/**
 * JEI entry point of dropscore, found by JEI through {@link JeiPlugin}; without JEI this class is never loaded.
 * Shows the drops the server synced for every mob and block, and lists every mob as an ingredient: JEI rebuilds its recipes after
 * the server sends tags and recipes, which happens right after the drop summary on login and on every reload.
 */
@JeiPlugin
public final class DropscoreJeiPlugin implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation UID = new ResourceLocation(AlkahestCore.ID, "dropscore");

    /** Mobs drawn by the ingredient list and the mob category, created once per level. */
    private final EntityPreviews previews = new EntityPreviews();
    @Nullable
    private MobDropCategory mobCategory;
    @Nullable
    private BlockDropCategory blockCategory;

    /**
     * Returns the id of this plugin.
     *
     * @return {@code alkahestcore:dropscore}.
     */
    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    /**
     * Registers mobs as JEI ingredients, so every mob is listed and can be clicked to open its drops.
     *
     * @param registration Ingredient registration of JEI. Never null.
     */
    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        registration.register(MobIngredient.TYPE, MobIngredient.all(), new MobIngredientHelper(),
                MobIngredientRenderer.icon(previews));
    }

    /**
     * Registers the mob and block drop categories.
     *
     * @param registration Category registration of JEI. Never null.
     */
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        mobCategory = new MobDropCategory(guiHelper, previews);
        blockCategory = new BlockDropCategory(guiHelper);
        registration.addRecipeCategories(mobCategory, blockCategory);
    }

    /**
     * Registers one recipe per synced mob and block (or per part of a long drop list).
     *
     * @param registration Recipe registration of JEI. Never null.
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (mobCategory == null || blockCategory == null) return;
        SyncedDropRules rules = ClientDropRules.current();
        List<DropRecipe<EntityType<?>>> mobRecipes = DropRecipe.allOf(rules.mobs(), ForgeRegistries.ENTITY_TYPES,
                mobCategory.dropsPerPage());
        List<DropRecipe<Block>> blockRecipes = DropRecipe.allOf(rules.blocks(), ForgeRegistries.BLOCKS,
                blockCategory.dropsPerPage());
        registration.addRecipes(MobDropCategory.TYPE, mobRecipes);
        registration.addRecipes(BlockDropCategory.TYPE, blockRecipes);
        LOGGER.debug("[dropscore] Added {} mob drop and {} block drop recipes to JEI.", mobRecipes.size(), blockRecipes.size());
    }

    /**
     * Forgets the drawn mobs when JEI stops, so they do not keep the level that was left.
     */
    @Override
    public void onRuntimeUnavailable() {
        previews.clear();
    }
}
