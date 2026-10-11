package com.jaffh.alkahestcore.dropscore.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Spawns, kills and inspects mobs inside the {@code dropscore_arena} template for the dropscore GameTests.
 */
final class DropTestKit {
    /** Template every dropscore test runs in: a 9×5×9 stone floor. */
    static final String ARENA = "dropscore_arena";

    private static final BlockPos CENTER = new BlockPos(4, 1, 4);
    private static final BlockPos FAR_CORNER = new BlockPos(9, 5, 9);
    private static final float LETHAL_DAMAGE = 10_000.0F;

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
