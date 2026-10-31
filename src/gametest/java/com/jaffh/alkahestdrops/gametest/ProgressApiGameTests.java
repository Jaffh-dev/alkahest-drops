package com.jaffh.alkahestdrops.gametest;

import com.jaffh.alkahestdrops.AlkahestDrops;
import com.jaffh.alkahestdrops.api.AddedDrop;
import com.jaffh.alkahestdrops.api.AlkahestDropsApi;
import com.jaffh.alkahestdrops.api.DropTrigger;
import com.jaffh.alkahestdrops.api.event.DropFilesLoadedEvent;
import com.jaffh.alkahestdrops.server.drops.DropFilesLoadedNotifier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * GameTests for the progress part of {@link AlkahestDropsApi} (pity misses, one-time drops, resets) and for
 * {@link DropFilesLoadedEvent}. Uses the rabbit's pity 5 drop and the fox's first-kill totem of {@code dstest_a}.
 */
@GameTestHolder(AlkahestDrops.ID)
@PrefixGameTestTemplate(false)
public class ProgressApiGameTests {
    private static final String RABBIT_PITY_KEY = "mob:dstest_a:rabbit_pity#0";
    private static final String FOX_FIRST_KILL_KEY = "mob:dstest_a:fox_first_kill@totem";

    /**
     * Two missed rabbit kills read as 2 misses of a pity 5 drop; resetting the drop's one-time progress leaves them,
     * resetting its pity clears them.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void progressApiReadsAndResetsPity(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        for (int i = 0; i < 2; i++) DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.RABBIT), player);
        helper.runAfterDelay(1, () -> {
            AddedDrop drop = AlkahestDropsApi.getMobDrops(EntityType.RABBIT, DropTrigger.DEATH).drops().stream()
                    .filter(added -> added.dropKey().equals(RABBIT_PITY_KEY)).findFirst().orElseThrow();
            DropTestKit.check(drop.pity() == 5 && !drop.isFirstDrop(), "rabbit drop view " + drop);
            DropTestKit.check(AlkahestDropsApi.getPityMisses(player, RABBIT_PITY_KEY) == 2, "expected 2 misses");
            DropTestKit.check(!AlkahestDropsApi.resetFirstDrop(player, RABBIT_PITY_KEY), "no one-time drop to forget");
            DropTestKit.check(AlkahestDropsApi.getPityMisses(player, RABBIT_PITY_KEY) == 2, "first drop reset touched pity");
            DropTestKit.check(AlkahestDropsApi.resetPity(player, RABBIT_PITY_KEY), "expected misses to clear");
            DropTestKit.check(AlkahestDropsApi.getPityMisses(player, RABBIT_PITY_KEY) == 0, "misses not cleared");
            helper.succeed();
        });
    }

    /**
     * The fox's first kill marks its one-time drop as received; resetting all pity leaves it, resetting all one-time
     * drops makes it available again, and one drop can be forgotten on its own.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void progressApiReadsAndResetsFirstDrops(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        DropTestKit.check(!AlkahestDropsApi.hasReceivedFirstDrop(player, FOX_FIRST_KILL_KEY), "received before the kill");
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.FOX), player);
        helper.runAfterDelay(1, () -> {
            DropTestKit.check(AlkahestDropsApi.hasReceivedFirstDrop(player, FOX_FIRST_KILL_KEY), "first kill not recorded");
            AlkahestDropsApi.resetAllPity(player);
            DropTestKit.check(AlkahestDropsApi.hasReceivedFirstDrop(player, FOX_FIRST_KILL_KEY), "pity reset touched the first kill");
            DropTestKit.check(AlkahestDropsApi.resetAllFirstDrops(player) >= 1, "expected one-time drops forgotten");
            DropTestKit.check(!AlkahestDropsApi.hasReceivedFirstDrop(player, FOX_FIRST_KILL_KEY), "first kill not cleared");
            DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.FOX), player);
            helper.runAfterDelay(1, () -> {
                DropTestKit.check(AlkahestDropsApi.resetFirstDrop(player, FOX_FIRST_KILL_KEY), "expected the fox drop forgotten");
                DropTestKit.check(!AlkahestDropsApi.resetFirstDrop(player, FOX_FIRST_KILL_KEY), "forgot it twice");
                helper.succeed();
            });
        });
    }

    /**
     * Machines' fake players have no progress: they read as none and resetting them clears nothing.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void progressApiIgnoresFakePlayers(GameTestHelper helper) {
        Player machine = DropTestKit.machinePlayer(helper);
        DropTestKit.killByPlayer(DropTestKit.spawnMob(helper, EntityType.RABBIT), machine);
        helper.runAfterDelay(1, () -> {
            DropTestKit.check(AlkahestDropsApi.getPityMisses(machine, RABBIT_PITY_KEY) == 0, "machine counted misses");
            DropTestKit.check(AlkahestDropsApi.resetAllPity(machine) == 0 && AlkahestDropsApi.resetAllFirstDrops(machine) == 0,
                    "machine had progress");
            helper.succeed();
        });
    }

    /**
     * The files-loaded event fires at the end of a reload (a datapack sync without a player) and not when a player
     * joins (a sync with that player).
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void filesLoadedEventFiresAfterReloadOnly(GameTestHelper helper) {
        AtomicInteger fired = new AtomicInteger();
        Consumer<DropFilesLoadedEvent> listener = event -> fired.incrementAndGet();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, DropFilesLoadedEvent.class, listener);
        try {
            PlayerList players = helper.getLevel().getServer().getPlayerList();
            DropFilesLoadedNotifier.onDatapackSync(new OnDatapackSyncEvent(players, DropTestKit.fakePlayer(helper)));
            DropTestKit.check(fired.get() == 0, "fired on a player join");
            DropFilesLoadedNotifier.onDatapackSync(new OnDatapackSyncEvent(players, null));
            DropTestKit.check(fired.get() == 1, "expected one event after a reload, got " + fired.get());
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }
}
