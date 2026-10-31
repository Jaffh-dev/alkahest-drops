package com.jaffh.alkahestdrops.server.drops;

import com.jaffh.alkahestdrops.api.event.DropFilesLoadedEvent;
import com.jaffh.alkahestdrops.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/**
 * Fires {@link DropFilesLoadedEvent} once the files are ready to read. The files themselves load at other moments
 * (before the server config on world open, before tags are bound during {@code /reload}), so the event waits for the
 * server start and for the end of each reload, when tags, config and files are all in place.
 */
public final class DropFilesLoadedNotifier {

    /**
     * Prevents instantiation of this utility class.
     */
    private DropFilesLoadedNotifier() {}

    /**
     * Fires the event when the server has started.
     *
     * @param event Server started event.
     */
    public static void onServerStarted(ServerStartedEvent event) {
        fire(event.getServer());
    }

    /**
     * Fires the event at the end of a {@code /reload}. Forge also sends this event to each joining player, which is
     * not a reload, so those are skipped.
     *
     * @param event Datapack sync event.
     */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) fire(event.getPlayerList().getServer());
    }

    /**
     * Fires the event when both modules have loaded their files.
     *
     * @param server Running server. Never null.
     */
    private static void fire(MinecraftServer server) {
        if (MobDropManager.INSTANCE.isLoaded() && BlockDropManager.INSTANCE.isLoaded()) {
            MinecraftForge.EVENT_BUS.post(new DropFilesLoadedEvent(server));
        }
    }
}
