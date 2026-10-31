package com.jaffh.alkahestdrops.gametest;

import com.jaffh.alkahestdrops.AlkahestDrops;
import com.jaffh.alkahestdrops.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestdrops.server.drops.DropIssue;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropManager;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * GameTests for mob drops on triggers other than death ({@code on}): shear, hit with cooldown, lightning once per
 * bolt, periodic with interval, removal files of one trigger, and the trigger validation. Rules: {@code dstest_a}
 * sheep (shear: 2 string + emerald), cave spider (hit: flint, feather with a long cooldown), iron golem (lightning:
 * prismarine crystals) and turtle (periodic every 10 ticks: slime ball + 3 experience); {@code dstest_b} removes the
 * sheep's emerald on shear and its string on death; {@code dstest_c} holds the invalid files.
 */
@GameTestHolder(AlkahestDrops.ID)
@PrefixGameTestTemplate(false)
public class TriggerGameTests {

    /**
     * Shearing drops the shear drops; a shear removal of another namespace removes the emerald, and the death
     * removal of string does not touch shear drops.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void shearDropsFollowShearRemovals(GameTestHelper helper) {
        Sheep sheep = DropTestKit.spawnMob(helper, EntityType.SHEEP);
        shear(helper, sheep);
        helper.runAfterDelay(1, () -> {
            DropTestKit.check(sheep.isSheared(), "sheep was not sheared");
            DropTestKit.assertItemCount(helper, Items.STRING, 2);
            DropTestKit.assertItemCount(helper, Items.EMERALD, 0);
            helper.succeed();
        });
    }

    /**
     * Shear drops read the enchantments of the shears: 1 echo shard + 1 per Unbreaking level, so 4 with Unbreaking III.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void shearReadsShearsEnchantments(GameTestHelper helper) {
        ItemStack shears = new ItemStack(Items.SHEARS);
        shears.enchant(Enchantments.UNBREAKING, 3);
        shear(helper, DropTestKit.spawnMob(helper, EntityType.MOOSHROOM), shears);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.ECHO_SHARD, 4);
            helper.succeed();
        });
    }

    /**
     * A sheared sheep cannot be sheared again, so it drops nothing.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void shearedSheepDropsNothing(GameTestHelper helper) {
        Sheep sheep = DropTestKit.spawnMob(helper, EntityType.SHEEP);
        sheep.setSheared(true);
        shear(helper, sheep);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.STRING, 0);
            helper.succeed();
        });
    }

    /**
     * Shear drops never drop on death.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void deathIgnoresShearDrops(GameTestHelper helper) {
        DropTestKit.killWithoutPlayer(DropTestKit.spawnMob(helper, EntityType.SHEEP));
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.STRING, 0);
            DropTestKit.assertItemCount(helper, Items.EMERALD, 0);
            helper.succeed();
        });
    }

    /**
     * Every player hit drops the flint; the feather waits its 1000-tick cooldown, so it drops on the first hit only.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void hitDropsWaitTheirCooldown(GameTestHelper helper) {
        Mob spider = DropTestKit.spawnMob(helper, EntityType.CAVE_SPIDER);
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        hit(spider, player);
        hit(spider, player);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.FLINT, 2);
            DropTestKit.assertItemCount(helper, Items.FEATHER, 1);
            helper.succeed();
        });
    }

    /**
     * A {@code damage_taken} hit drop counts the damage the mob took: 5 damage on an unarmored vindicator gives 5
     * gold ingots (linear, multiplier 1).
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void damageTakenFollowsTheDamage(GameTestHelper helper) {
        Mob vindicator = DropTestKit.spawnMob(helper, EntityType.VINDICATOR);
        vindicator.hurt(
                vindicator.damageSources().playerAttack(DropTestKit.playerHolding(helper, ItemStack.EMPTY)), 5.0F);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.GOLD_INGOT, 5);
            helper.succeed();
        });
    }

    /**
     * A mob cannot lose more health than it has: 5 damage on a vindicator with 2 health gives 2 gold ingots.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void damageTakenIsCappedAtHealth(GameTestHelper helper) {
        Mob vindicator = DropTestKit.spawnMob(helper, EntityType.VINDICATOR);
        vindicator.setHealth(2.0F);
        vindicator.hurt(
                vindicator.damageSources().playerAttack(DropTestKit.playerHolding(helper, ItemStack.EMPTY)), 5.0F);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.GOLD_INGOT, 2);
            helper.succeed();
        });
    }

    /**
     * Resistance V blocks all damage, so a {@code damage_taken} drop gives nothing.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void damageTakenGivesNothingWithoutDamage(GameTestHelper helper) {
        Mob vindicator = DropTestKit.spawnMob(helper, EntityType.VINDICATOR);
        vindicator.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4));
        vindicator.hurt(
                vindicator.damageSources().playerAttack(DropTestKit.playerHolding(helper, ItemStack.EMPTY)), 5.0F);
        helper.runAfterDelay(1, () -> {
            DropTestKit.check(vindicator.getHealth() == vindicator.getMaxHealth(), "the vindicator took damage");
            DropTestKit.assertItemCount(helper, Items.GOLD_INGOT, 0);
            helper.succeed();
        });
    }

    /**
     * Damage without a player is not a hit.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void hitWithoutPlayerDropsNothing(GameTestHelper helper) {
        Mob spider = DropTestKit.spawnMob(helper, EntityType.CAVE_SPIDER);
        spider.hurt(spider.damageSources().generic(), 1.0F);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.FLINT, 0);
            helper.succeed();
        });
    }

    /**
     * A bolt strikes on several ticks, but the lightning drop drops once.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void lightningDropsOncePerBolt(GameTestHelper helper) {
        Mob golem = DropTestKit.spawnMob(helper, EntityType.IRON_GOLEM);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
        DropTestKit.check(bolt != null, "no lightning bolt");
        bolt.moveTo(golem.position());
        helper.getLevel().addFreshEntity(bolt);
        helper.runAfterDelay(10, () -> {
            DropTestKit.assertItemCount(helper, Items.PRISMARINE_CRYSTALS, 1);
            helper.succeed();
        });
    }

    /**
     * A periodic drop every 10 ticks drops twice in 25 ticks: items and experience alike.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void periodicDropsEveryInterval(GameTestHelper helper) {
        DropTestKit.spawnMob(helper, EntityType.TURTLE);
        helper.runAfterDelay(25, () -> {
            DropTestKit.assertItemCount(helper, Items.SLIME_BALL, 2);
            DropTestKit.assertExperience(helper, 6);
            helper.succeed();
        });
    }

    /**
     * Invalid triggers are reported: unknown ids, keys of another trigger, one-time and pity drops without player,
     * per-player drops off death, an interval of 0, enchantment bonuses on lightning, {@code damage_taken} off hit and
     * in block files, {@code on} on a drop file, and {@code on} in block files.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void triggerIssuesAreReported(GameTestHelper helper) {
        List<DropIssue> mobIssues = MobDropManager.getIssues();
        DropTestKit.assertIssue(mobIssues, "trigger_issues", "on", DropIssue.Severity.WARNING);
        DropTestKit.assertIssue(mobIssues, "trigger_issues", "drops[0].on", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(mobIssues, "trigger_issues", "drops[1].interval", DropIssue.Severity.WARNING);
        DropTestKit.assertIssue(mobIssues, "trigger_issues", "drops[2].first_kill", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(mobIssues, "trigger_issues", "drops[3].pity", DropIssue.Severity.WARNING);
        DropTestKit.assertIssue(mobIssues, "trigger_issues", "drops[4].per_player", DropIssue.Severity.WARNING);
        DropTestKit.assertIssue(mobIssues, "trigger_issues", "drops[5].interval", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(mobIssues, "trigger_issues", "drops[6].enchant_bonus", DropIssue.Severity.WARNING);
        DropTestKit.assertIssue(mobIssues, "trigger_issues", "drops[7].drop_behavior.type", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(mobIssues, "removal_bad_trigger", "on", DropIssue.Severity.ERROR);
        List<DropIssue> blockIssues = BlockDropManager.getIssues();
        DropTestKit.assertIssue(blockIssues, "trigger_in_block_file", "drops[0].on", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(
                blockIssues, "trigger_in_block_file", "drops[1].drop_behavior.type", DropIssue.Severity.ERROR);
        DropTestKit.assertIssue(blockIssues, "removal_trigger_in_block_file", "on", DropIssue.Severity.ERROR);
        helper.succeed();
    }

    /**
     * Shears a mob as a real player would, through the entity interaction.
     *
     * @param helper Helper of the running test. Never null.
     * @param mob    Mob to shear. Never null.
     */
    private static void shear(GameTestHelper helper, Mob mob) {
        shear(helper, mob, new ItemStack(Items.SHEARS));
    }

    /**
     * Shears a mob as a player holding the given shears.
     *
     * @param helper Helper of the test. Never null.
     * @param mob    Mob to shear. Never null.
     * @param shears Shears to hold, possibly enchanted. Never null.
     */
    private static void shear(GameTestHelper helper, Mob mob, ItemStack shears) {
        ServerPlayer player = DropTestKit.fakePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, shears);
        player.interactOn(mob, InteractionHand.MAIN_HAND);
    }

    /**
     * Hurts a mob once by a player for 1 damage, clearing its hurt cooldown first so every call counts.
     *
     * @param mob    Mob to hit. Never null.
     * @param player Attacking player. Never null.
     */
    private static void hit(Mob mob, Player player) {
        mob.invulnerableTime = 0;
        mob.hurt(mob.damageSources().playerAttack(player), 1.0F);
    }
}
