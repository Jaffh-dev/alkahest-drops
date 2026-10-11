package com.jaffh.alkahestcore.dropscore.gametest;

import com.jaffh.alkahestcore.AlkahestCore;
import com.jaffh.alkahestcore.dropscore.server.drops.DropIssue;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.MobDropManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * GameTests for mob drops, run headless with {@code ./gradlew runGameTestServer}. Rules come from the
 * {@code dstest_a} (drop files), {@code dstest_b} (removal files) and {@code dstest_c} (invalid files)
 * namespaces in the gametest resources; every roll is deterministic (chance 1, fixed counts).
 */
@GameTestHolder(AlkahestCore.ID)
@PrefixGameTestTemplate(false)
public class MobDropGameTests {

    /**
     * {@code max_health} log/3/10 gives a 20-health zombie round(3 · ln 2) = 2 bones.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void maxHealthCountForZombie(GameTestHelper helper) {
        DropTestKit.killWithoutPlayer(DropTestKit.spawnMob(helper, EntityType.ZOMBIE));
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.BONE, 2);
            helper.succeed();
        });
    }

    /**
     * {@code max_health} log/3/10 gives a 300-health mob (wither health) round(3 · ln 30) = 10 bones.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void maxHealthCountForHighHealth(GameTestHelper helper) {
        Mob zombie = DropTestKit.spawnMob(helper, EntityType.ZOMBIE);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300.0);
        zombie.setHealth(300.0F);
        DropTestKit.killWithoutPlayer(zombie);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.BONE, 10);
            helper.succeed();
        });
    }

    /**
     * A removal file keeps drops of its own namespace (flesh replaced by exactly 3) and removes drops added by
     * other namespaces (the diamond removed by {@code dstest_b}).
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void removalNamespaceRule(GameTestHelper helper) {
        DropTestKit.killWithoutPlayer(DropTestKit.spawnMob(helper, EntityType.HUSK));
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.ROTTEN_FLESH, 3);
            DropTestKit.assertItemCount(helper, Items.DIAMOND, 0);
            helper.succeed();
        });
    }

    /**
     * A global {@code killed_by_player} condition lets the file drop on a player kill.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void globalConditionPassesOnPlayerKill(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.CREEPER), player);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.EMERALD, 1);
            helper.succeed();
        });
    }

    /**
     * A global {@code killed_by_player} condition blocks the file without a player.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void globalConditionFailsWithoutPlayer(GameTestHelper helper) {
        DropTestKit.killWithoutPlayer(DropTestKit.spawnMob(helper, EntityType.CREEPER));
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.EMERALD, 0);
            helper.succeed();
        });
    }

    /**
     * A {@code #tag} target drops for a mob in the tag.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void tagTargetDrops(GameTestHelper helper) {
        DropTestKit.killWithoutPlayer(DropTestKit.spawnMob(helper, EntityType.SKELETON));
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.PRISMARINE_SHARD, 1);
            helper.succeed();
        });
    }

    /**
     * {@code minecraft:*} and {@code *} targets drop for a mob no file names.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void namespaceAndAnyTargetsDrop(GameTestHelper helper) {
        DropTestKit.killWithoutPlayer(DropTestKit.spawnMob(helper, EntityType.SPIDER));
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.NAUTILUS_SHELL, 1);
            DropTestKit.assertItemCount(helper, Items.BARRIER, 1);
            helper.succeed();
        });
    }

    /**
     * A tag-targeted {@code "*"} removal gated by "not killed by a player" wipes vanilla drops and the drops of
     * other namespaces when its condition passes.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void conditionalWipeWithoutPlayer(GameTestHelper helper) {
        DropTestKit.killWithoutPlayer(DropTestKit.spawnMob(helper, EntityType.COW));
        helper.runAfterDelay(1, () -> {
            int items = DropTestKit.countItemEntities(helper);
            DropTestKit.check(items == 0, "expected no drops, got " + items + " item entities");
            helper.succeed();
        });
    }

    /**
     * The same removal leaves every drop when its condition fails.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void conditionalWipeSkippedOnPlayerKill(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.COW), player);
        helper.runAfterDelay(1, () -> {
            DropTestKit.check(DropTestKit.countItems(helper, Items.BEEF) >= 1, "expected vanilla beef");
            DropTestKit.assertItemCount(helper, Items.BARRIER, 1);
            DropTestKit.assertItemCount(helper, Items.NAUTILUS_SHELL, 1);
            helper.succeed();
        });
    }

    /**
     * {@code bonus_count} raises the count by one per Looting level: 1 + 3 with Looting III.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void lootingRaisesCount(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(Enchantments.MOB_LOOTING, 3);
        Player player = DropTestKit.playerHolding(helper, sword);
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.WITCH), player);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.AMETHYST_SHARD, 4);
            helper.succeed();
        });
    }

    /**
     * A removal file with an experience entry and an experience drop of the same namespace set the experience:
     * 4 + 2 per Looting level, so 10 with Looting III, and none of the chicken's vanilla experience.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void lootingRaisesExperience(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(Enchantments.MOB_LOOTING, 3);
        Player player = DropTestKit.playerHolding(helper, sword);
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.CHICKEN), player);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertExperience(helper, 10);
            helper.succeed();
        });
    }

    /**
     * The experience removal of {@code dstest_b} removes the 7 experience of {@code dstest_a} and keeps its own
     * {@code max_health} experience: 0.6 · 10 health = 6 for a pig.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void experienceRemovalKeepsOwnNamespace(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.PIG), player);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertExperience(helper, 6);
            helper.succeed();
        });
    }

    /**
     * An experience removal entry whose local condition is a sneaking killer drops no experience when the killer
     * sneaks.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void experienceRemovedWhenKillerSneaks(GameTestHelper helper) {
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.SHEEP), DropTestKit.sneakingPlayer(helper));
        helper.runAfterDelay(1, () -> {
            int orbs = DropTestKit.countExperienceOrbs(helper);
            DropTestKit.check(orbs == 0, "expected no experience orbs, got " + orbs);
            helper.succeed();
        });
    }

    /**
     * The same file keeps the experience when the killer stands.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void experienceKeptWhenKillerStands(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.SHEEP), player);
        helper.runAfterDelay(1, () -> {
            DropTestKit.check(DropTestKit.countExperienceOrbs(helper) > 0, "expected experience orbs");
            helper.succeed();
        });
    }

    /**
     * The invalid files of {@code dstest_c} are reported with the right path and severity.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void loadReportsIssues(GameTestHelper helper) {
        List<DropIssue> issues = MobDropManager.getIssues();
        DropTestKit.assertIssue(issues, "version_missing", "version", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(issues, "player_removal", "entity", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(issues, "both_kinds", "", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(issues, "experience_removal_with_item", "removals[0].experience", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(issues, "unknown_namespace", "entity", DropIssue.Severity.WARNING);
        DropTestKit.assertIssue(issues, "bad_max_health", "drops[0].drop_behavior.divisor", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(issues, "bad_max_health", "drops[1].drop_behavior.function", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(issues, "bad_max_health", "drops[2].drop_behavior.rolls", DropIssue.Severity.WARNING);
        DropTestKit.assertIssue(issues, "block_key_in_mob_file", "block", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(issues, "on_fortune_in_mob_file", "drops[0].on_fortune", DropIssue.Severity.WARNING);
        DropTestKit.assertIssue(issues, "hardness_on_mob", "drops[0].drop_behavior.type", DropIssue.Severity.ERROR);
        helper.succeed();
    }
}
