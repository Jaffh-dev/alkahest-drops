package com.jaffh.alkahestdrops.server.progress;

import com.jaffh.alkahestdrops.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestdrops.server.drops.DropManager;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropManager;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import org.slf4j.Logger;

/**
 * Removes from players' drop progress the entries of drops that no longer exist, so deleted drops leave nothing
 * behind. Runs when a player logs in and after every reload; entries of skipped files and of files that lost some
 * drops are kept (see {@link DropManager#shouldKeepProgressKey}), and nothing is pruned until both modules have loaded.
 */
public final class ProgressPruner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<DropManager<?>> MANAGERS = List.of(MobDropManager.INSTANCE, BlockDropManager.INSTANCE);

    /**
     * Prevents instantiation of this utility class.
     */
    private ProgressPruner() {}

    /**
     * Prunes the joining player on login, or every online player after a reload.
     *
     * @param event Datapack sync event: a player on login, none after a reload.
     */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (!canPrune()) return;
        ServerPlayer joining = event.getPlayer();
        List<ServerPlayer> players =
                joining != null ? List.of(joining) : event.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            DropProgress progress = DropProgress.of(player);
            if (progress == null) continue;
            int pruned = prune(progress);
            if (pruned > 0) {
                LOGGER.info(
                        "[alkahestdrops] Removed {} progress entries of deleted drops from {}.",
                        pruned,
                        player.getGameProfile().getName());
            }
        }
    }

    /**
     * Tells whether the drop files are loaded, so their progress keys can be trusted.
     *
     * @return True when every module has loaded the files of the current world.
     */
    public static boolean canPrune() {
        return MANAGERS.stream().allMatch(DropManager::isLoaded);
    }

    /**
     * Removes the entries whose drop is certainly gone. Keys that belong to no module are removed too.
     *
     * @param progress Progress of one player. Never null.
     * @return The number of one-time drops and pity counters removed.
     */
    public static int prune(DropProgress progress) {
        Predicate<String> isGone = key -> !shouldKeep(key);
        return progress.resetFirstDrops(isGone) + progress.resetMisses(isGone);
    }

    /**
     * Tells whether a progress entry must be kept.
     *
     * @param key Progress key. Never null.
     * @return True when the module that owns the key keeps it.
     */
    private static boolean shouldKeep(String key) {
        for (DropManager<?> manager : MANAGERS) {
            if (manager.format().ownsProgressKey(key)) return manager.shouldKeepProgressKey(key);
        }
        return false;
    }
}
