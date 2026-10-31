package com.jaffh.alkahestdrops.gametest;

import com.jaffh.alkahestdrops.AlkahestDrops;
import com.jaffh.alkahestdrops.api.DropOrigin;
import com.jaffh.alkahestdrops.api.event.OnDropEvent;
import com.jaffh.alkahestdrops.api.DropTrigger;
import com.jaffh.alkahestdrops.server.drops.DropActionRunner;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * GameTests for {@code on_drop} and {@link OnDropEvent}: the dropped item of a drop with actions glows and runs its
 * function as the item, with the drop's player tagged; other drops are left alone; the event fires for every Alkahest Drops
 * item drop and cancelling it skips the function. The silverfish's function {@code dstest_a:on_drop/mark} tags the
 * item {@code dstest_rare}, and {@code dstest_with_player} when a tagged player is online. Event listeners only look
 * at drops of their own test's mob or block, since tests run at the same time.
 */
@GameTestHolder(AlkahestDrops.ID)
@PrefixGameTestTemplate(false)
public class OnDropGameTests {
    private static final String RARE_TAG = "dstest_rare";
    private static final String WITH_PLAYER_TAG = "dstest_with_player";

    /**
     * The silverfish's emerald glows and runs its function; its gold nugget and the {@code *} barrier do neither. The
     * mock player is not in the level, so the function finds no tagged player.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void onDropAppliesToMobDrop(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.SILVERFISH), player);
        helper.runAfterDelay(1, () -> {
            assertAll(helper, Items.EMERALD, ItemEntity::hasGlowingTag, "must glow");
            assertAll(helper, Items.EMERALD, item -> item.getTags().contains(RARE_TAG), "must run its function");
            assertAll(helper, Items.EMERALD, item -> !item.getTags().contains(WITH_PLAYER_TAG), "must find no tagged player");
            for (Item plain : List.of(Items.GOLD_NUGGET, Items.BARRIER)) {
                assertAll(helper, plain, item -> !item.hasGlowingTag() && !item.getTags().contains(RARE_TAG), "must have no action");
            }
            helper.succeed();
        });
    }

    /**
     * An online real player carries the drop-player tag while the function runs, and loses it afterward. The player
     * is put in the player list only, which is all {@code @a} reads, through its private list, since
     * {@code getPlayers()} is read-only and {@code makeMockServerPlayerInLevel} fails on Forge's network setup in the
     * GameTest server.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void onDropTagsDropPlayer(GameTestHelper helper) {
        ServerPlayer player = DropTestKit.fakePlayer(helper);
        List<ServerPlayer> online = ObfuscationReflectionHelper.getPrivateValue(PlayerList.class,
                helper.getLevel().getServer().getPlayerList(), "f_11196_");
        online.add(player);
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.SILVERFISH), player);
        helper.runAfterDelay(1, () -> {
            online.remove(player);
            assertAll(helper, Items.EMERALD, item -> item.getTags().contains(WITH_PLAYER_TAG), "must see the tagged player");
            DropTestKit.check(!player.getTags().contains(DropActionRunner.PLAYER_TAG), "the player must lose the tag");
            helper.succeed();
        });
    }

    /**
     * The amethyst block's emerald glows when mined with a pickaxe; the block's own drop does not. The event names the
     * block and its position.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void onDropAppliesToBlockDrop(GameTestHelper helper) {
        List<OnDropEvent> events = new ArrayList<>();
        Consumer<OnDropEvent> listener = event -> {
            if (event.getOrigin() instanceof DropOrigin.Block block && block.state().is(Blocks.AMETHYST_BLOCK)
                    && block.pos().equals(helper.absolutePos(DropTestKit.CENTER))) {
                events.add(event);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, OnDropEvent.class, listener);
        DropTestKit.breakBlock(helper, Blocks.AMETHYST_BLOCK, new ItemStack(Items.IRON_PICKAXE), false);
        helper.runAfterDelay(1, () -> {
            MinecraftForge.EVENT_BUS.unregister(listener);
            assertGlowing(helper, Items.EMERALD, true);
            assertGlowing(helper, Items.AMETHYST_BLOCK, false);
            DropTestKit.check(events.size() == 1 && events.get(0).getItem().getItem().is(Items.EMERALD),
                    "expected one event, for the emerald; got " + events.size());
            DropTestKit.check(events.get(0).getDropKey().equals("block:dstest_a:amethyst_block_on_drop#0"),
                    "drop key " + events.get(0).getDropKey());
            helper.succeed();
        });
    }

    /**
     * The event fires once per drop file item drop, also for drops without {@code on_drop}, with the mob, the drop
     * key, the trigger and the player; it does not fire for the vanilla drops of the mob.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void onDropEventFiresForEveryDropFileDrop(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        Mob silverfish = DropTestKit.spawnMob(helper, EntityType.SILVERFISH);
        List<OnDropEvent> events = new ArrayList<>();
        Consumer<OnDropEvent> listener = event -> {
            if (event.getOrigin() instanceof DropOrigin.Mob mob && mob.mob() == silverfish) events.add(event);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, OnDropEvent.class, listener);
        DropTestKit.killByPlayer(silverfish, player);
        helper.runAfterDelay(1, () -> {
            MinecraftForge.EVENT_BUS.unregister(listener);
            List<String> keys = events.stream().map(OnDropEvent::getDropKey).sorted().toList();
            DropTestKit.check(keys.equals(List.of("mob:dstest_a:any_barrier#0", "mob:dstest_a:minecraft_namespace_shell#0", "mob:dstest_a:silverfish_on_drop#0",
                    "mob:dstest_a:silverfish_on_drop#1")), "drop keys " + keys);
            DropTestKit.check(events.stream().allMatch(event -> event.getTrigger() == DropTrigger.DEATH && event.getPlayer() == player),
                    "every event must be a death drop of the killer");
            helper.succeed();
        });
    }

    /**
     * Cancelling the event skips the drop's function; the glow, applied as the item spawns, stays.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void cancelledOnDropEventSkipsFunction(GameTestHelper helper) {
        Mob silverfish = DropTestKit.spawnMob(helper, EntityType.SILVERFISH);
        Consumer<OnDropEvent> listener = event -> {
            if (event.getOrigin() instanceof DropOrigin.Mob mob && mob.mob() == silverfish) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, OnDropEvent.class, listener);
        DropTestKit.killByPlayer(silverfish, DropTestKit.playerHolding(helper, ItemStack.EMPTY));
        helper.runAfterDelay(1, () -> {
            MinecraftForge.EVENT_BUS.unregister(listener);
            assertAll(helper, Items.EMERALD, ItemEntity::hasGlowingTag, "must still glow");
            assertAll(helper, Items.EMERALD, item -> !item.getTags().contains(RARE_TAG), "must not run its function");
            helper.succeed();
        });
    }

    /**
     * A function defined by a datapack of the same reload is found when the drop files are read, so it gets no warning.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void existingFunctionIsAccepted(GameTestHelper helper) {
        DropTestKit.check(MobDropManager.getIssues().stream().noneMatch(issue -> issue.fileId().getPath().equals("silverfish_on_drop")),
                "silverfish_on_drop must load without issues");
        helper.succeed();
    }

    /**
     * Fails the test unless every dropped item of a kind glows, or none does, and at least one dropped.
     *
     * @param helper     Helper of the running test. Never null.
     * @param item       Item to check. Never null.
     * @param shouldGlow Whether the items must glow.
     */
    static void assertGlowing(GameTestHelper helper, Item item, boolean shouldGlow) {
        assertAll(helper, item, entity -> entity.hasGlowingTag() == shouldGlow, shouldGlow ? "must glow" : "must not glow");
    }

    /**
     * Fails the test unless at least one item of a kind dropped and every one passes a check.
     *
     * @param helper  Helper of the running test. Never null.
     * @param item    Item to check. Never null.
     * @param check   Check every dropped item must pass. Never null.
     * @param message End of the failure message, such as "must glow". Never null.
     */
    private static void assertAll(GameTestHelper helper, Item item, Predicate<ItemEntity> check, String message) {
        List<ItemEntity> items = DropTestKit.droppedItems(helper, item);
        DropTestKit.check(!items.isEmpty(), "expected a dropped " + item);
        DropTestKit.check(items.stream().allMatch(check), item + " " + message);
    }
}
