package com.jaffh.alkahestdrops.client.drops;

import com.jaffh.alkahestdrops.common.drops.SyncedDropRules;
import com.jaffh.alkahestdrops.common.network.DropRulesAssembler;
import com.jaffh.alkahestdrops.common.network.DropRulesSyncPacket;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

/**
 * Holds the drop summary the connected server sent, for recipe viewers. Only the client's main thread writes it
 * (the packet handler and the logout event); it is published through a {@code volatile} field as an immutable record.
 */
public final class ClientDropRules {
    private static final DropRulesAssembler ASSEMBLER = new DropRulesAssembler();
    private static volatile SyncedDropRules current = SyncedDropRules.EMPTY;

    /**
     * Prevents instantiation of this holder class.
     */
    private ClientDropRules() {
    }

    /**
     * Returns the summary of the connected server.
     *
     * @return The last complete summary, {@link SyncedDropRules#EMPTY} before the first one or after leaving.
     */
    public static SyncedDropRules current() {
        return current;
    }

    /**
     * Adds a received batch, and replaces the summary once its last batch arrives.
     *
     * @param packet Received batch. Never null.
     */
    public static void receive(DropRulesSyncPacket packet) {
        SyncedDropRules rules = ASSEMBLER.accept(packet);
        if (rules != null) current = rules;
    }

    /**
     * Forgets the summary when the player leaves, so a server without Alkahest Drops never shows the last server's drops.
     *
     * @param event Logout event of the Forge bus.
     */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        current = SyncedDropRules.EMPTY;
    }
}
