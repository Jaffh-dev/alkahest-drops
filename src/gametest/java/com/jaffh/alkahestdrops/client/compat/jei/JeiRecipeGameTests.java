package com.jaffh.alkahestdrops.client.compat.jei;

import com.jaffh.alkahestdrops.AlkahestDrops;
import com.jaffh.alkahestdrops.common.drops.DropBehavior;
import com.jaffh.alkahestdrops.common.drops.DropCount;
import com.jaffh.alkahestdrops.common.drops.EnchantBonuses;
import com.jaffh.alkahestdrops.common.drops.SyncedDrop;
import com.jaffh.alkahestdrops.common.drops.SyncedDropSource;
import com.jaffh.alkahestdrops.common.drops.SyncedTarget;
import com.jaffh.alkahestdrops.api.DropTrigger;
import com.jaffh.alkahestdrops.common.drops.TriggerSettings;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * GameTests for the JEI recipes built from the synced summary: splitting long drop lists and the drop grids. They
 * only use registries, so they run on the GameTest server (JEI's own classes are not touched).
 */
@GameTestHolder(AlkahestDrops.ID)
@PrefixGameTestTemplate(false)
public class JeiRecipeGameTests {
    private static final String ARENA = "arena";

    /**
     * A target with 23 drops becomes three recipes of 10, 10 and 3 drops with distinct ids; a target this side does
     * not know becomes no recipe.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = ARENA)
    public static void longDropListsSplitIntoParts(GameTestHelper helper) {
        ResourceLocation stoneId = new ResourceLocation("stone");
        List<SyncedTarget> targets = List.of(
                new SyncedTarget(stoneId, drops(23)),
                new SyncedTarget(new ResourceLocation("nomod", "unknown"), drops(1)));

        List<DropRecipe<Block>> recipes = DropRecipe.allOf(targets, ForgeRegistries.BLOCKS, 10);
        check(recipes.size() == 3, "expected 3 recipes, got " + recipes.size());
        check(recipes.get(0).drops().size() == 10 && recipes.get(2).drops().size() == 3, "part sizes");
        check(recipes.get(0).id().equals(stoneId), "first part id");
        check(recipes.get(2).id().equals(new ResourceLocation("stone/part_2")), "third part id");
        check(recipes.get(1).target() == Blocks.STONE, "part target");
        helper.succeed();
    }

    /**
     * Each trigger of a target gets its own pages, death first whatever the drop order, with the trigger in the id.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = ARENA)
    public static void triggersGetTheirOwnPages(GameTestHelper helper) {
        ResourceLocation stoneId = new ResourceLocation("stone");
        List<SyncedDrop> drops = new ArrayList<>();
        drops.add(drop(new TriggerSettings(DropTrigger.SHEAR, 0)));
        drops.addAll(drops(2));
        drops.add(drop(new TriggerSettings(DropTrigger.PERIODIC, 100)));

        List<DropRecipe<Block>> recipes = DropRecipe.allOf(List.of(new SyncedTarget(stoneId, drops)), ForgeRegistries.BLOCKS, 10);
        check(recipes.size() == 3, "expected 3 recipes, got " + recipes.size());
        check(recipes.get(0).trigger() == DropTrigger.DEATH && recipes.get(0).drops().size() == 2, "death page first");
        check(recipes.get(0).id().equals(stoneId), "death page id");
        check(recipes.get(1).id().equals(new ResourceLocation("stone/shear")), "shear page id " + recipes.get(1).id());
        check(recipes.get(2).trigger() == DropTrigger.PERIODIC, "periodic page last");
        helper.succeed();
    }

    /**
     * Grid cells map to positions and back, and points outside the cells map to no cell.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = ARENA)
    public static void gridCellsMatchPositions(GameTestHelper helper) {
        DropGrid grid = new DropGrid(92, 11, 4, 5);
        check(grid.size() == 20 && grid.width() == 74 && grid.height() == 92, "grid size");
        check(grid.cellX(5) == 92 + 1 + 18 && grid.cellY(5) == 11 + 1 + 18, "cell 5 position");
        check(grid.cellAt(grid.cellX(5) + 3, grid.cellY(5) + 3) == 5, "cell at cell 5");
        check(grid.cellAt(91, 20) == -1 && grid.cellAt(100, 200) == -1, "outside the grid");
        helper.succeed();
    }

    /**
     * Creates simple diamond drops.
     *
     * @param count Number of drops.
     * @return The drops.
     */
    private static List<SyncedDrop> drops(int count) {
        List<SyncedDrop> drops = new ArrayList<>();
        for (int i = 0; i < count; i++) drops.add(drop(TriggerSettings.DEATH));
        return drops;
    }

    /**
     * Creates one simple diamond drop.
     *
     * @param trigger Trigger of the drop. Never null.
     * @return The drop.
     */
    private static SyncedDrop drop(TriggerSettings trigger) {
        return new SyncedDrop(new ItemStack(Items.DIAMOND), 1.0, new DropCount(1, 1), DropBehavior.UNIFORM,
                EnchantBonuses.NONE, SyncedDrop.UNTRACKED, List.of(), false,
                SyncedDropSource.lootTable(new ResourceLocation("blocks/stone")), trigger);
    }

    /**
     * Fails the test when a check is false.
     *
     * @param isTrue  Checked condition.
     * @param message What was checked.
     */
    private static void check(boolean isTrue, String message) {
        if (!isTrue) throw new GameTestAssertException(message);
    }
}
