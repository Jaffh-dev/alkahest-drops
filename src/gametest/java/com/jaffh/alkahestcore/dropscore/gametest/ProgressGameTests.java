package com.jaffh.alkahestcore.dropscore.gametest;

import com.jaffh.alkahestcore.AlkahestCore;
import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.jaffh.alkahestcore.dropscore.server.drops.RollContext;
import com.jaffh.alkahestcore.dropscore.server.drops.RolledDrop;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.HitTracker;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.MobDropManager;
import com.jaffh.alkahestcore.dropscore.server.progress.DropProgress;
import com.jaffh.alkahestcore.dropscore.server.progress.ProgressPruner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GameTests for bad-luck protection ({@code pity}), one-time drops ({@code first_kill}) and per-player drops
 * ({@code per_player}), and {@code /dropscore reset}. Rules live in {@code dstest_a}: a rabbit dragon's breath with
 * chance 0 and pity 5, a clay nautilus shell with pity 3, a fox totem on the first kill (with an {@code id}, so its key uses it), a melon totem on the first
 * break and a polar bear heart of the sea per player.
 */
@GameTestHolder(AlkahestCore.ID)
@PrefixGameTestTemplate(false)
public class ProgressGameTests {
    private static final String RABBIT_PITY_KEY = "mob:dstest_a:rabbit_pity#0";
    private static final String FOX_FIRST_KILL_KEY = "mob:dstest_a:fox_first_kill@totem";
    private static final String MELON_FIRST_BREAK_KEY = "block:dstest_a:melon_first_break#0";

    /**
     * Pity 5 on a chance-0 drop misses four kills by the same player and drops on the fifth, then resets.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void pityForcesDropOnFifthKill(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        killRabbits(helper, player, 4);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.DRAGON_BREATH, 0);
            DropTestKit.check(progressOf(player).missesOf(RABBIT_PITY_KEY) == 4, "expected 4 counted misses");
            killRabbits(helper, player, 1);
            helper.runAfterDelay(1, () -> {
                DropTestKit.assertItemCount(helper, Items.DRAGON_BREATH, 1);
                DropTestKit.check(progressOf(player).missesOf(RABBIT_PITY_KEY) == 0, "misses not reset after the drop");
                helper.succeed();
            });
        });
    }

    /**
     * Pity needs a player: five kills without one never force the drop.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void pityIgnoredWithoutPlayer(GameTestHelper helper) {
        for (int i = 0; i < 5; i++) {
            DropTestKit.killWithoutPlayer(DropTestKit.spawnMob(helper, EntityType.RABBIT));
        }
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.DRAGON_BREATH, 0);
            helper.succeed();
        });
    }

    /**
     * Pity counts the breaks of the same player: pity 3 drops on the third clay block.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void pityCountsBlockBreaks(GameTestHelper helper) {
        ServerPlayer player = DropTestKit.fakePlayer(helper);
        DropTestKit.breakBlock(helper, Blocks.CLAY, player);
        DropTestKit.breakBlock(helper, Blocks.CLAY, player);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.NAUTILUS_SHELL, 0);
            DropTestKit.breakBlock(helper, Blocks.CLAY, player);
            helper.runAfterDelay(1, () -> {
                DropTestKit.assertItemCount(helper, Items.NAUTILUS_SHELL, 1);
                helper.succeed();
            });
        });
    }

    /**
     * A first-kill drop ignores its chance of 0 and drops once per player: two kills by one player and one by
     * another give two totems.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void firstKillDropsOncePerPlayer(GameTestHelper helper) {
        Player first = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        Player second = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.FOX), first);
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.FOX), first);
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.FOX), second);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.TOTEM_OF_UNDYING, 2);
            helper.succeed();
        });
    }

    /**
     * A first-kill drop never happens without a player.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void firstKillNeedsPlayer(GameTestHelper helper) {
        DropTestKit.killWithoutPlayer(DropTestKit.spawnMob(helper, EntityType.FOX));
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.TOTEM_OF_UNDYING, 0);
            helper.succeed();
        });
    }

    /**
     * A first-break drop ignores its chance of 0 and drops once per player: two melons broken by one player give
     * one totem.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void firstBreakDropsOncePerPlayer(GameTestHelper helper) {
        ServerPlayer player = DropTestKit.fakePlayer(helper);
        DropTestKit.breakBlock(helper, Blocks.MELON, player);
        DropTestKit.breakBlock(helper, Blocks.MELON, player);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.TOTEM_OF_UNDYING, 1);
            helper.succeed();
        });
    }

    /**
     * {@code reset <entity>} resets only the drops that mob can give: the fox first kill, not the rabbit pity or the
     * melon first break.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void resetTargetResetsOnlyItsDrops(GameTestHelper helper) {
        ServerPlayer player = DropTestKit.fakePlayer(helper);
        DropProgress progress = givenFullProgress(player);
        DropTestKit.check(runCommandAs(helper, player, "dropscore reset @s minecraft:fox") == 1, "expected 1 reset");
        DropTestKit.check(progress.claimFirstDrop(FOX_FIRST_KILL_KEY), "fox first kill not reset");
        DropTestKit.check(progress.missesOf(RABBIT_PITY_KEY) == 1, "rabbit pity reset by a fox reset");
        DropTestKit.check(!progress.claimFirstDrop(MELON_FIRST_BREAK_KEY), "melon first break reset by a fox reset");
        helper.succeed();
    }

    /**
     * {@code reset entities} resets every mob drop and keeps block drops; {@code reset blocks} the other way around.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void resetEntitiesAndBlocksSplitModules(GameTestHelper helper) {
        ServerPlayer player = DropTestKit.fakePlayer(helper);
        DropProgress progress = givenFullProgress(player);
        DropTestKit.check(runCommandAs(helper, player, "dropscore reset @s entities") == 2, "expected 2 mob resets");
        DropTestKit.check(progress.missesOf(RABBIT_PITY_KEY) == 0, "rabbit pity not reset");
        DropTestKit.check(runCommandAs(helper, player, "dropscore reset @s blocks") == 1, "expected 1 block reset");
        DropTestKit.check(progress.claimFirstDrop(MELON_FIRST_BREAK_KEY), "melon first break not reset");
        helper.succeed();
    }

    /**
     * {@code reset <player>} without a target resets everything; an unknown id and a missing player argument fail.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void resetEverythingAndFailures(GameTestHelper helper) {
        ServerPlayer player = DropTestKit.fakePlayer(helper);
        givenFullProgress(player);
        DropTestKit.check(runCommandAs(helper, player, "dropscore reset @s minecraft:not_a_thing") == 0, "unknown id accepted");
        DropTestKit.check(runCommandAs(helper, player, "dropscore reset") == 0, "reset without a player argument accepted");
        DropTestKit.check(runCommandAs(helper, player, "dropscore reset @s") == 3, "expected 3 resets");
        helper.succeed();
    }

    /**
     * {@code reset first} resets only one-time drops and keeps pity counters.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void resetFirstKeepsPity(GameTestHelper helper) {
        ServerPlayer player = DropTestKit.fakePlayer(helper);
        DropProgress progress = givenFullProgress(player);
        DropTestKit.check(runCommandAs(helper, player, "dropscore reset @s first") == 2, "expected 2 one-time drop resets");
        DropTestKit.check(progress.missesOf(RABBIT_PITY_KEY) == 1, "pity reset by 'first'");
        DropTestKit.check(progress.claimFirstDrop(FOX_FIRST_KILL_KEY), "fox first kill not reset");
        helper.succeed();
    }

    /**
     * {@code reset <player> pity entities} resets only the mob pity counters of the selected player. Fake players
     * are not in the player list, so every reset test selects the player with {@code @s}.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void resetPlayerPityOfEntities(GameTestHelper helper) {
        ServerPlayer player = DropTestKit.fakePlayer(helper);
        DropProgress progress = givenFullProgress(player);
        DropTestKit.check(runCommandAs(helper, player, "dropscore reset @s pity entities") == 1, "expected 1 pity reset");
        DropTestKit.check(progress.missesOf(RABBIT_PITY_KEY) == 0, "rabbit pity not reset");
        DropTestKit.check(!progress.claimFirstDrop(FOX_FIRST_KILL_KEY), "fox first kill reset by 'pity'");
        helper.succeed();
    }

    /**
     * A player argument that matches nobody fails.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void resetUnknownPlayerFails(GameTestHelper helper) {
        DropTestKit.check(runCommandAs(helper, null, "dropscore reset Nobody first") == 0, "unknown player accepted");
        helper.succeed();
    }

    /**
     * Pruning removes only progress of drops that are certainly gone: a missing position in a fully loaded file, a
     * deleted file and a key of no module. It keeps live drops, files that lost some drops ({@code tracking_issues})
     * and skipped files ({@code version_missing}).
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void pruneRemovesOnlyDeletedDrops(GameTestHelper helper) {
        DropTestKit.check(ProgressPruner.canPrune(), "drop files not loaded");
        DropProgress progress = new DropProgress();
        progress.recordMiss(RABBIT_PITY_KEY);
        progress.claimFirstDrop(MELON_FIRST_BREAK_KEY);
        progress.recordMiss("mob:dstest_a:rabbit_pity#7");
        progress.claimFirstDrop("mob:dstest_a:deleted_file#0");
        progress.claimFirstDrop("nonsense");
        progress.claimFirstDrop("mob:dstest_c:tracking_issues@ghost");
        progress.recordMiss("mob:dstest_c:version_missing#0");

        int pruned = ProgressPruner.prune(progress);
        DropTestKit.check(pruned == 3, "expected 3 pruned entries, got " + pruned);
        DropTestKit.check(progress.missesOf(RABBIT_PITY_KEY) == 1, "live pity counter pruned");
        DropTestKit.check(!progress.claimFirstDrop(MELON_FIRST_BREAK_KEY), "live first break pruned");
        DropTestKit.check(!progress.claimFirstDrop("mob:dstest_c:tracking_issues@ghost"), "file with skipped drops pruned");
        DropTestKit.check(progress.missesOf("mob:dstest_c:version_missing#0") == 1, "skipped file pruned");
        helper.succeed();
    }

    /**
     * Gives a player a fox first kill, a melon first break and one rabbit miss.
     *
     * @param player Player. Never null.
     * @return The player's progress.
     */
    private static DropProgress givenFullProgress(Player player) {
        DropProgress progress = progressOf(player);
        progress.claimFirstDrop(FOX_FIRST_KILL_KEY);
        progress.claimFirstDrop(MELON_FIRST_BREAK_KEY);
        progress.recordMiss(RABBIT_PITY_KEY);
        return progress;
    }

    /**
     * Runs a command without chat output, as a player or as the console.
     *
     * @param helper  Helper of the test. Never null.
     * @param player  Player running the command, or null for the console.
     * @param command Command without the slash. Never null.
     * @return The command result, 0 when it failed.
     */
    private static int runCommandAs(GameTestHelper helper, ServerPlayer player, String command) {
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel())
                .withSuppressedOutput();
        if (player != null) source = source.withEntity(player);
        return helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
    }

    /**
     * A machine's fake player has no drop progress: pity never builds up over its block breaks and it never gets a
     * first-kill drop.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void machinePlayerGetsNoProgress(GameTestHelper helper) {
        ServerPlayer machine = DropTestKit.machinePlayer(helper);
        DropTestKit.check(DropProgress.of(machine) == null, "a machine's fake player has drop progress");
        for (int i = 0; i < 3; i++) {
            DropTestKit.breakBlock(helper, Blocks.CLAY, machine);
        }
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.NAUTILUS_SHELL, 0);
            DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.FOX), machine);
            helper.runAfterDelay(1, () -> {
                DropTestKit.assertItemCount(helper, Items.TOTEM_OF_UNDYING, 0);
                helper.succeed();
            });
        });
    }

    /**
     * {@code killed_by_real_player} passes for a player and fails for a machine's fake player and without a player.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void killedByRealPlayerCondition(GameTestHelper helper) {
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.DONKEY), DropTestKit.playerHolding(helper, ItemStack.EMPTY));
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.DONKEY), DropTestKit.machinePlayer(helper));
        DropTestKit.killWithoutPlayer(DropTestKit.spawnMob(helper, EntityType.DONKEY));
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.DIAMOND_BLOCK, 1);
            helper.succeed();
        });
    }

    /**
     * {@code broken_by_real_player} passes for a player and fails for a machine's fake player and an explosion.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void brokenByRealPlayerCondition(GameTestHelper helper) {
        DropTestKit.breakBlock(helper, Blocks.HAY_BLOCK, DropTestKit.fakePlayer(helper));
        DropTestKit.breakBlock(helper, Blocks.HAY_BLOCK, DropTestKit.machinePlayer(helper));
        DropTestKit.explodeBlock(helper, Blocks.HAY_BLOCK);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.DIAMOND_BLOCK, 1);
            helper.succeed();
        });
    }

    /**
     * The progress survives a dimension change: the player entity is kept, and Forge invalidates and then revives its
     * capabilities ({@code removePlayerImmediately} then {@code revive}, as in {@code ServerPlayer.changeDimension}).
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void progressSurvivesDimensionChange(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        progressOf(player).recordMiss(RABBIT_PITY_KEY);
        player.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        player.revive();
        DropTestKit.check(progressOf(player).missesOf(RABBIT_PITY_KEY) == 1, "pity lost after a dimension change");
        helper.succeed();
    }

    /**
     * The progress is copied to the respawned player through the real clone event, after the old player was removed
     * (its capabilities invalidated) as the respawn does.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void progressCopiedOnRespawn(GameTestHelper helper) {
        Player dead = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        progressOf(dead).recordMiss(RABBIT_PITY_KEY);
        progressOf(dead).claimFirstDrop(FOX_FIRST_KILL_KEY);
        dead.remove(Entity.RemovalReason.KILLED);
        Player respawned = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, dead, true));

        DropProgress progress = progressOf(respawned);
        DropTestKit.check(progress.missesOf(RABBIT_PITY_KEY) == 1, "pity not copied on respawn");
        DropTestKit.check(!progress.claimFirstDrop(FOX_FIRST_KILL_KEY), "first kill not copied on respawn");
        helper.succeed();
    }

    /**
     * The progress survives saving (relog) and copying to a new player entity (respawn).
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void progressSurvivesSaveAndCopy(GameTestHelper helper) {
        DropProgress original = new DropProgress();
        original.recordMiss(RABBIT_PITY_KEY);
        original.recordMiss(RABBIT_PITY_KEY);
        original.claimFirstDrop(FOX_FIRST_KILL_KEY);

        DropProgress loaded = new DropProgress();
        loaded.load(original.save());
        DropProgress copied = new DropProgress();
        copied.copyFrom(loaded);

        DropTestKit.check(copied.missesOf(RABBIT_PITY_KEY) == 2, "misses lost");
        DropTestKit.check(!copied.claimFirstDrop(FOX_FIRST_KILL_KEY), "first kill lost");
        helper.succeed();
    }

    /**
     * A per-player drop is rolled once per attacker, each stack locked to its attacker.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void perPlayerRollsOncePerAttacker(GameTestHelper helper) {
        Player first = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        Player second = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        DropTable table = MobDropManager.getTable(EntityType.POLAR_BEAR);
        List<RolledDrop> drops = table.rollDrops(List.of(), null, rollOf(helper, null), List.of(rollOf(helper, first), rollOf(helper, second)))
                .stream().filter(drop -> drop.stack().is(Items.HEART_OF_THE_SEA)).toList();

        Set<UUID> owners = drops.stream().map(RolledDrop::owner).collect(Collectors.toSet());
        DropTestKit.check(drops.size() == 2, "expected 2 hearts, got " + drops.size());
        DropTestKit.check(owners.equals(Set.of(first.getUUID(), second.getUUID())), "hearts not locked to their attackers");
        helper.succeed();
    }

    /**
     * Without an online attacker (a mock player is never in the player list), a per-player drop gets one shared,
     * unlocked roll.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void perPlayerSharedRollWithoutAttackers(GameTestHelper helper) {
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.POLAR_BEAR), DropTestKit.playerHolding(helper, ItemStack.EMPTY));
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.HEART_OF_THE_SEA, 1);
            List<ItemEntity> hearts = DropTestKit.droppedItems(helper, Items.HEART_OF_THE_SEA);
            DropTestKit.check(hearts.stream().allMatch(item -> item.getOwner() == null), "shared roll is locked to a player");
            helper.succeed();
        });
    }

    /**
     * Every player who damages a mob with per-player drops is recorded once on the mob.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void hitTrackerRecordsAttackers(GameTestHelper helper) {
        Mob bear = DropTestKit.spawnMob(helper, EntityType.POLAR_BEAR);
        Player first = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        Player second = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        hitOnce(bear, first);
        hitOnce(bear, second);
        hitOnce(bear, first);

        List<UUID> attackers = HitTracker.attackerIdsOf(bear);
        DropTestKit.check(attackers.equals(List.of(first.getUUID(), second.getUUID())), "unexpected attackers " + attackers);
        helper.succeed();
    }

    /**
     * Kills rabbits with a player attack.
     *
     * @param helper Helper of the test. Never null.
     * @param player Killing player. Never null.
     * @param kills  Number of rabbits.
     */
    private static void killRabbits(GameTestHelper helper, Player player, int kills) {
        for (int i = 0; i < kills; i++) {
            DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.RABBIT), player);
        }
    }

    /**
     * Deals one small hit, ignoring the mob's invulnerability ticks so every hit counts.
     *
     * @param mob    Mob to hit. Never null.
     * @param player Attacking player. Never null.
     */
    private static void hitOnce(Mob mob, Player player) {
        mob.invulnerableTime = 0;
        mob.hurt(mob.damageSources().playerAttack(player), 1.0F);
    }

    /**
     * Returns the drop progress of a player, failing when it is missing.
     *
     * @param player Player. Never null.
     * @return The progress.
     */
    private static DropProgress progressOf(Player player) {
        DropProgress progress = DropProgress.of(player);
        DropTestKit.check(progress != null, "player has no drop progress");
        return progress;
    }

    /**
     * Creates the roll context of a polar bear kill without Looting.
     *
     * @param helper Helper of the test. Never null.
     * @param player Player of the roll, or null for none.
     * @return The roll context.
     */
    private static RollContext rollOf(GameTestHelper helper, Player player) {
        return new RollContext(0, 30.0F, helper.getLevel().getRandom(), player);
    }
}
