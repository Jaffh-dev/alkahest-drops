package com.jaffh.alkahestcore.dropscore.gametest;

import com.jaffh.alkahestcore.AlkahestCore;
import com.jaffh.alkahestcore.dropscore.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestcore.dropscore.server.drops.DropIssue;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * GameTests for block drops, run headless with {@code ./gradlew runGameTestServer}. Rules come from the
 * {@code block_drops} folders of the {@code dstest_a} (drop files), {@code dstest_b} (removal files) and
 * {@code dstest_c} (invalid files) namespaces; every roll is deterministic (chance 1, fixed counts).
 */
@GameTestHolder(AlkahestCore.ID)
@PrefixGameTestTemplate(false)
public class BlockDropGameTests {

    /**
     * A drop file adds its drop to a block broken with a pickaxe, next to the vanilla drop.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void dropFileAddsDrop(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.SANDSTONE, new ItemStack(Items.IRON_PICKAXE), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.DIAMOND, 1);
            DropTestKit.assertItemCount(helper, Items.SANDSTONE, 1);
            helper.succeed();
        });
    }

    /**
     * {@code on_fortune.bonus_count} raises the count by one per Fortune level: 1 + 3 with Fortune III.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void fortuneRaisesCount(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.TERRACOTTA, enchantedPickaxe(Enchantments.BLOCK_FORTUNE, 3), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.AMETHYST_SHARD, 4);
            helper.succeed();
        });
    }

    /**
     * A local {@code match_tool} silk-touch condition passes with a Silk Touch pickaxe.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void silkTouchConditionPasses(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.GLASS, enchantedPickaxe(Enchantments.SILK_TOUCH, 1), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.EMERALD, 1);
            helper.succeed();
        });
    }

    /**
     * The same condition fails with a plain pickaxe.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void silkTouchConditionFails(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.GLASS, new ItemStack(Items.IRON_PICKAXE), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.EMERALD, 0);
            helper.succeed();
        });
    }

    /**
     * An explosion runs the block's loot table, so drop files apply without a player or tool.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void explosionAppliesRules(GameTestHelper helper) {
        DropTestKit.explodeBlock(helper, Blocks.DIRT);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.NAUTILUS_SHELL, 1);
            helper.succeed();
        });
    }

    /**
     * A {@code #minecraft:logs} target drops for a log.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void tagTargetDrops(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.OAK_LOG, new ItemStack(Items.IRON_AXE), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.PRISMARINE_SHARD, 1);
            helper.succeed();
        });
    }

    /**
     * A {@code minecraft:*} target drops for a block no file names, gated here by a global golden hoe condition.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void namespaceTargetDrops(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.HAY_BLOCK, new ItemStack(Items.GOLDEN_HOE), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.BARRIER, 1);
            helper.succeed();
        });
    }

    /**
     * A removal file removes a drop of the block's loot table.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void removalRemovesLootTableDrop(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.COAL_ORE, new ItemStack(Items.IRON_PICKAXE), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.COAL, 0);
            helper.succeed();
        });
    }

    /**
     * A removal file of {@code dstest_b} removes the gold added by {@code dstest_a} and keeps its iron.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void removalOfOtherNamespace(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.SMOOTH_STONE, new ItemStack(Items.IRON_PICKAXE), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.GOLD_INGOT, 0);
            DropTestKit.assertItemCount(helper, Items.IRON_INGOT, 1);
            helper.succeed();
        });
    }

    /**
     * An experience removal on diamond ore drops no experience; the diamond still drops.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void experienceRemovedFromDiamondOre(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.DIAMOND_ORE, new ItemStack(Items.IRON_PICKAXE), false);
        helper.runAfterDelay(1, () -> {
            int orbs = DropTestKit.countExperienceOrbs(helper);
            DropTestKit.check(orbs == 0, "expected no experience orbs, got " + orbs);
            DropTestKit.assertItemCount(helper, Items.DIAMOND, 1);
            helper.succeed();
        });
    }

    /**
     * An experience removal gated by a sneaking breaker (global condition) drops no experience when the player sneaks.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void experienceRemovedWhenBreakerSneaks(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.EMERALD_ORE, new ItemStack(Items.IRON_PICKAXE), true);
        helper.runAfterDelay(1, () -> {
            int orbs = DropTestKit.countExperienceOrbs(helper);
            DropTestKit.check(orbs == 0, "expected no experience orbs, got " + orbs);
            helper.succeed();
        });
    }

    /**
     * The same file keeps the experience when the player stands.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void experienceKeptWhenBreakerStands(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.EMERALD_ORE, new ItemStack(Items.IRON_PICKAXE), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.check(DropTestKit.countExperienceOrbs(helper) > 0, "expected experience orbs");
            helper.succeed();
        });
    }

    /**
     * An experience drop adds its experience to a block that gives none in vanilla.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void experienceDropAdded(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.BRICKS, new ItemStack(Items.IRON_PICKAXE), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertExperience(helper, 5);
            helper.succeed();
        });
    }

    /**
     * {@code on_fortune.bonus_count} raises the experience by two per Fortune level: 5 + 4 with Fortune II.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void fortuneRaisesExperience(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.BRICKS, enchantedPickaxe(Enchantments.BLOCK_FORTUNE, 2), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertExperience(helper, 9);
            helper.succeed();
        });
    }

    /**
     * Every experience drop rolls on its own and the results add up: 4 + 6 from one {@code dstest_a} file and 2
     * from a {@code dstest_b} file, while the 100 gated by a golden hoe {@code match_tool} is skipped.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void experienceDropsAddUp(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.BOOKSHELF, new ItemStack(Items.IRON_AXE), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertExperience(helper, 12);
            helper.succeed();
        });
    }

    /**
     * An explosion gives no block experience, as in vanilla, even with an experience drop.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void explosionGivesNoExperience(GameTestHelper helper) {
        DropTestKit.explodeBlock(helper, Blocks.BRICKS);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertExperience(helper, 0);
            helper.succeed();
        });
    }

    /**
     * The experience removal of {@code dstest_b} removes the vanilla experience and the 7 of {@code dstest_a},
     * and keeps the 3 of its own namespace: the "set the experience" pattern.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void experienceRemovalKeepsOwnNamespace(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.NETHER_QUARTZ_ORE, new ItemStack(Items.IRON_PICKAXE), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertExperience(helper, 3);
            helper.succeed();
        });
    }

    /**
     * {@code hardness} linear with divisor 10 gives obsidian (hardness 50) 5 flint.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void hardnessCountForObsidian(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.OBSIDIAN, new ItemStack(Items.DIAMOND_PICKAXE), false);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.FLINT, 5);
            helper.succeed();
        });
    }

    /**
     * A block whose loot table is {@code minecraft:empty} never runs loot modifiers, so the break fallback drops
     * its dropscore drops after the tick.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void blockWithoutLootTableDrops(GameTestHelper helper) {
        DropTestKit.check(Blocks.END_PORTAL_FRAME.getLootTable() == BuiltInLootTables.EMPTY,
                "expected the end portal frame to have no loot table");
        DropTestKit.breakBlock(helper, Blocks.END_PORTAL_FRAME, new ItemStack(Items.IRON_PICKAXE), false);
        helper.succeedWhen(() -> DropTestKit.assertItemCount(helper, Items.ENDER_EYE, 2));
    }

    /**
     * The invalid block files of {@code dstest_c} are reported with the right path and severity.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void loadReportsIssues(GameTestHelper helper) {
        List<DropIssue> issues = BlockDropManager.getIssues();
        DropTestKit.assertIssue(issues, "max_health_on_block", "drops[0].drop_behavior.type", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(issues, "unknown_block", "block", DropIssue.Severity.WARNING);
        DropTestKit.assertIssue(issues, "entity_key_in_block_file", "entity", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(issues, "on_looting_in_block_file", "drops[0].on_looting", DropIssue.Severity.WARNING);
        DropTestKit.assertIssue(issues, "experience_with_item", "drops[0].experience", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(issues, "experience_with_nbt", "drops[0].nbt", DropIssue.Severity.WARNING);
        helper.succeed();
    }

    /**
     * Creates an iron pickaxe with one enchantment.
     *
     * @param enchantment Enchantment to add. Never null.
     * @param level       Enchantment level.
     * @return The pickaxe.
     */
    private static ItemStack enchantedPickaxe(Enchantment enchantment, int level) {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.enchant(enchantment, level);
        return pickaxe;
    }
}
