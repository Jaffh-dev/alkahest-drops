package com.jaffh.alkahestdrops.server.sync;

import com.jaffh.alkahestdrops.common.drops.SyncedDropRules;
import com.jaffh.alkahestdrops.common.network.AlkahestDropsNetwork;
import com.jaffh.alkahestdrops.common.network.DropRulesSyncPacket;
import com.jaffh.alkahestdrops.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestdrops.server.drops.DropLoadReport;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropManager;
import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import org.slf4j.Logger;

/**
 * Sends the drop summary to clients that have the Alkahest Drops channel (clients with JEI), on login and after every
 * reload. The summary is built once per datapack load (every reload makes new load reports) and reused for every
 * player; nothing is built when no client can receive it.
 */
public final class DropRulesSender {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private CachedRules cached;

    /**
     * Sends the summary to the joining player, or to every online player after a reload. Forge fires the event
     * before tags and recipes are sent, so the summary arrives before JEI rebuilds its recipes.
     *
     * @param event Datapack sync event: a player on login, none after a reload.
     */
    public void onDatapackSync(OnDatapackSyncEvent event) {
        if (!AlkahestDropsNetwork.isEnabled()) return;
        ServerPlayer joining = event.getPlayer();
        List<ServerPlayer> players =
                joining != null ? List.of(joining) : event.getPlayerList().getPlayers();
        List<DropRulesSyncPacket> packets = null;
        for (ServerPlayer player : players) {
            if (!AlkahestDropsNetwork.canReceive(player)) continue;
            if (packets == null) packets = currentPackets(event.getPlayerList().getServer());
            for (DropRulesSyncPacket packet : packets) AlkahestDropsNetwork.sendTo(player, packet);
        }
    }

    /**
     * Returns the batches of the current summary, building them when the datapacks have loaded since the last call.
     *
     * @param server Running server. Never null.
     * @return The batches, in send order. Never null.
     */
    private List<DropRulesSyncPacket> currentPackets(MinecraftServer server) {
        DropLoadReport mobs = MobDropManager.INSTANCE.report();
        DropLoadReport blocks = BlockDropManager.INSTANCE.report();
        CachedRules current = cached;
        if (current == null || current.mobs() != mobs || current.blocks() != blocks) {
            long start = System.nanoTime();
            SyncedDropRules rules = SyncedDropFactory.rulesOf(server);
            LOGGER.debug(
                    "[alkahestdrops] Built the JEI drop summary of {} mobs and {} blocks in {} ms.",
                    rules.mobs().size(),
                    rules.blocks().size(),
                    (System.nanoTime() - start) / 1_000_000);
            current = new CachedRules(
                    mobs, blocks, DropRulesSyncPacket.split(rules, DropRulesSyncPacket.MAX_BATCH_BYTES));
            cached = current;
        }
        return current.packets();
    }

    /**
     * Represents the summary built from two load reports, compared by identity to detect a new load.
     *
     * @param mobs    Mob report the summary was built from. Never null.
     * @param blocks  Block report the summary was built from. Never null.
     * @param packets Batches of the built summary. Never null.
     */
    private record CachedRules(DropLoadReport mobs, DropLoadReport blocks, List<DropRulesSyncPacket> packets) {}
}
