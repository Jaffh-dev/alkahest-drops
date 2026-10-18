package com.jaffh.alkahestcore.dropscore.gametest;

import com.jaffh.alkahestcore.dropscore.server.drops.DropIssue;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulatedPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.util.List;
import java.util.UUID;

/**
 * Spawns, kills, breaks and inspects mobs and blocks inside the {@code dropscore_arena} template for the
 * dropscore GameTests.
 */
final class DropTestKit {
    /** Template every dropscore test runs in: a 9×5×9 stone floor. */
    static final String ARENA = "dropscore_arena";

    private static final BlockPos CENTER = new BlockPos(4, 1, 4);
    private static final BlockPos FAR_CORNER = new BlockPos(9, 5, 9);
    private static final float LETHAL_DAMAGE = 10_000.0F;
    private static final float SMALL_EXPLOSION_POWER = 1.0F;

    /**
     * Prevents instantiation of this utility class.
     */
    private DropTestKit() {
    }

    /**
     * Spawns a mob without AI at the arena center.
     *
     * @param helper Helper of the running test. Never null.
     * @param type   Mob type. Never null.
     * @param <T>    Mob class.
     * @return The spawned mob.
     */
    static <T extends Mob> T spawnMob(GameTestHelper helper, EntityType<T> type) {
        return helper.spawnWithNoFreeWill(type, CENTER);
    }

    /**
     * Creates a mock player holding an item, standing up.
     *
     * @param helper   Helper of the running test. Never null.
     * @param mainHand Item in the main hand, {@link ItemStack#EMPTY} for none. Never null.
     * @return The mock player; it is not added to the level.
     */
    static Player playerHolding(GameTestHelper helper, ItemStack mainHand) {
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
        return player;
    }

    /**
     * Creates a crouching mock player with an empty hand, matching the {@code is_sneaking} entity flag.
     *
     * @param helper Helper of the running test. Never null.
     * @return The mock player.
     */
    static Player sneakingPlayer(GameTestHelper helper) {
        Player player = playerHolding(helper, ItemStack.EMPTY);
        player.setPose(Pose.CROUCHING);
        return player;
    }

    /**
     * Kills a mob with a player attack, so {@code killed_by_player}, looting and experience apply.
     *
     * @param mob    Mob to kill. Never null.
     * @param player Attacking player. Never null.
     */
    static void killByPlayer(LivingEntity mob, Player player) {
        mob.hurt(DamageSource.playerAttack(player), LETHAL_DAMAGE);
    }

    /**
     * Kills a mob with generic damage, with no killer.
     *
     * @param mob Mob to kill. Never null.
     */
    static void killWithoutPlayer(LivingEntity mob) {
        mob.hurt(DamageSource.GENERIC, LETHAL_DAMAGE);
    }

    /**
     * Places a block at the arena center and breaks it like a survival player would: the break event, the loot
     * table with the tool and player, and the experience. Uses a server player that counts as real, since a mock player
     * cannot break.
     *
     * @param helper     Helper of the running test. Never null.
     * @param block      Block to place and break. Never null.
     * @param tool       Item in the player's main hand, {@link ItemStack#EMPTY} for none. Never null.
     * @param isSneaking Whether the player crouches, matching the {@code is_sneaking} entity flag.
     */
    static void breakBlock(GameTestHelper helper, Block block, ItemStack tool, boolean isSneaking) {
        ServerPlayer player = fakePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        if (isSneaking) player.setPose(Pose.CROUCHING);
        breakBlock(helper, block, player);
    }

    /**
     * Places a block at the arena center and breaks it with a given fake player, so several breaks share the
     * player's drop progress.
     *
     * @param helper Helper of the running test. Never null.
     * @param block  Block to place and break. Never null.
     * @param player Fake player breaking the block, from {@link #fakePlayer}. Never null.
     */
    static void breakBlock(GameTestHelper helper, Block block, ServerPlayer player) {
        helper.setBlock(CENTER, block);
        player.gameMode.destroyBlock(helper.absolutePos(CENTER));
    }

    /**
     * Creates a player that can break blocks and counts as a real player with its own drop progress (the simulator's
     * player type); it is not added to the level.
     *
     * @param helper Helper of the running test. Never null.
     * @return The player.
     */
    static ServerPlayer fakePlayer(GameTestHelper helper) {
        return new SimulatedPlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "dropscore_test"));
    }

    /**
     * Creates a plain fake player, as machine mods use to kill mobs and break blocks: it has no drop progress and fails
     * the real-player conditions. It is not added to the level.
     *
     * @param helper Helper of the running test. Never null.
     * @return The machine's fake player.
     */
    static ServerPlayer machinePlayer(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "dropscore_machine"));
    }

    /**
     * Returns the center of the arena block where mobs spawn and blocks are broken, in world coordinates.
     *
     * @param helper Helper of the running test. Never null.
     * @return The center of the block.
     */
    static Vec3 arenaCenter(GameTestHelper helper) {
        return Vec3.atCenterOf(helper.absolutePos(CENTER));
    }

    /**
     * Places a block at the arena center and blows it up with an explosion too weak to break the stone floor.
     *
     * @param helper Helper of the running test. Never null.
     * @param block  Block to place and blow up. Never null.
     */
    static void explodeBlock(GameTestHelper helper, Block block) {
        helper.setBlock(CENTER, block);
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(CENTER));
        helper.getLevel().explode(null, center.x, center.y, center.z, SMALL_EXPLOSION_POWER, Explosion.BlockInteraction.BREAK);
    }

    /**
     * Counts the dropped items of one kind in the arena.
     *
     * @param helper Helper of the running test. Never null.
     * @param item   Item to count. Never null.
     * @return The summed stack sizes.
     */
    static int countItems(GameTestHelper helper, Item item) {
        int total = 0;
        for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, arenaBounds(helper))) {
            if (drop.getItem().is(item)) total += drop.getItem().getCount();
        }
        return total;
    }

    /**
     * Returns the dropped stacks of one kind in the arena.
     *
     * @param helper Helper of the running test. Never null.
     * @param item   Item to find. Never null.
     * @return The stacks of the item entities holding the item, in no order.
     */
    static List<ItemStack> droppedStacks(GameTestHelper helper, Item item) {
        return droppedItems(helper, item).stream().map(ItemEntity::getItem).toList();
    }

    /**
     * Returns every item entity of an item in the arena.
     *
     * @param helper Helper of the running test. Never null.
     * @param item   Item to look for. Never null.
     * @return The item entities, in no particular order.
     */
    static List<ItemEntity> droppedItems(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, arenaBounds(helper), entity -> entity.getItem().is(item));
    }

    /**
     * Counts every dropped item entity in the arena.
     *
     * @param helper Helper of the running test. Never null.
     * @return The number of item entities.
     */
    static int countItemEntities(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, arenaBounds(helper)).size();
    }

    /**
     * Counts the experience orbs in the arena.
     *
     * @param helper Helper of the running test. Never null.
     * @return The number of orbs.
     */
    static int countExperienceOrbs(GameTestHelper helper) {
        return helper.getLevel().getEntities(EntityType.EXPERIENCE_ORB, arenaBounds(helper), orb -> true).size();
    }

    /**
     * Sums the experience of the orbs in the arena. Orbs of the same value merge into one entity with a count,
     * which has no getter, so it is read from the saved orb.
     *
     * @param helper Helper of the running test. Never null.
     * @return The total experience.
     */
    static int sumExperience(GameTestHelper helper) {
        int total = 0;
        for (ExperienceOrb orb : helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, arenaBounds(helper))) {
            CompoundTag saved = orb.saveWithoutId(new CompoundTag());
            total += orb.getValue() * Math.max(saved.getInt("Count"), 1);
        }
        return total;
    }

    /**
     * Fails the test when the experience in the arena differs from the expected amount.
     *
     * @param helper   Helper of the running test. Never null.
     * @param expected Expected total experience.
     * @throws GameTestAssertException If the total differs.
     */
    static void assertExperience(GameTestHelper helper, int expected) {
        int actual = sumExperience(helper);
        check(actual == expected, "expected " + expected + " experience, got " + actual);
    }

    /**
     * Fails the test when a dropped item count differs from the expected one.
     *
     * @param helper   Helper of the running test. Never null.
     * @param item     Item to count. Never null.
     * @param expected Expected summed stack size.
     * @throws GameTestAssertException If the count differs.
     */
    static void assertItemCount(GameTestHelper helper, Item item, int expected) {
        int actual = countItems(helper, item);
        if (actual != expected) {
            throw new GameTestAssertException("expected " + expected + " " + item.getRegistryName() + ", got " + actual);
        }
    }

    /**
     * Fails the test unless a {@code dstest_c} file reported a problem at a path with a severity.
     *
     * @param issues   Issues of the last load. Never null.
     * @param file     File name in {@code dstest_c/mob_drops} or {@code dstest_c/block_drops}, without extension.
     *                 Never null.
     * @param field    JSON path of the problem, {@code ""} for the file root. Never null.
     * @param severity Expected severity. Never null.
     * @throws GameTestAssertException If no such problem was reported.
     */
    static void assertIssue(List<DropIssue> issues, String file, String field, DropIssue.Severity severity) {
        ResourceLocation fileId = ResourceLocation.tryParse("dstest_c:" + file);
        boolean isReported = issues.stream().anyMatch(issue -> issue.fileId().equals(fileId)
                && issue.field().equals(field) && issue.severity() == severity);
        check(isReported, "missing " + severity + " for " + fileId + " at '" + field + "'");
    }

    /**
     * Fails the test when a condition is false.
     *
     * @param isTrue  Condition to check.
     * @param message Failure message. Never null.
     * @throws GameTestAssertException If the condition is false.
     */
    static void check(boolean isTrue, String message) {
        if (!isTrue) throw new GameTestAssertException(message);
    }

    /**
     * Returns the arena box, a little wider than the template so drops that bounce stay inside.
     *
     * @param helper Helper of the running test. Never null.
     * @return The box in world coordinates.
     */
    private static AABB arenaBounds(GameTestHelper helper) {
        return new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(FAR_CORNER)).inflate(2.0);
    }
}
