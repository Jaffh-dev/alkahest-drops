package com.jaffh.alkahestdrops.server.drops;

import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Guards the public API calls that read or change server state, which only the server thread may touch.
 */
public final class ServerThread {

    /**
     * Prevents instantiation of this utility class.
     */
    private ServerThread() {}

    /**
     * Returns the running server, checking that the caller is on its thread.
     *
     * @return The server, or null when no server is running.
     * @throws IllegalStateException If a server is running and this is not its thread.
     */
    @Nullable
    public static MinecraftServer current() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !server.isSameThread()) {
            throw new IllegalStateException("The Alkahest Drops API must be called on the server thread");
        }
        return server;
    }
}
