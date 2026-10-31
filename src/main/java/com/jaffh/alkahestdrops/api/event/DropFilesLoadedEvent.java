package com.jaffh.alkahestdrops.api.event;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired on the Forge event bus, on the server thread, once the drop and removal files are loaded and ready to read:
 * when the server has started, and after every {@code /reload}. Rebuild anything cached from
 * {@link com.jaffh.alkahestdrops.api.AlkahestDropsApi} here; from this point the read API returns the new files.
 * Part of the public API.
 *
 * @since 1.0.0
 */
public class DropFilesLoadedEvent extends Event {
    private final MinecraftServer server;

    /**
     * Creates the event of one load.
     *
     * @param server Server whose files were loaded. Never null.
     * @since 1.0.0
     */
    public DropFilesLoadedEvent(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Returns the server whose files were loaded.
     *
     * @return The server. Never null.
     * @since 1.0.0
     */
    public MinecraftServer getServer() {
        return server;
    }
}
