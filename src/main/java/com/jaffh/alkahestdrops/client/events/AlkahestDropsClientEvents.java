package com.jaffh.alkahestdrops.client.events;

import com.jaffh.alkahestdrops.client.compat.jei.AlkahestDropsJeiClient;
import com.jaffh.alkahestdrops.client.drops.ClientDropRules;
import com.jaffh.alkahestdrops.common.network.AlkahestDropsNetwork;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Registers the event handlers of the mod's physical client. Call only on the physical client, after the network
 * channel is registered.
 */
public final class AlkahestDropsClientEvents {

    /**
     * Prevents instantiation of this utility class.
     */
    private AlkahestDropsClientEvents() {}

    /**
     * Registers every client handler of the module. Without JEI the client has no channel and receives no drop
     * summary, so nothing is registered.
     *
     * @param modEventBus   Event bus of the mod. Never null.
     * @param forgeEventBus Forge event bus of gameplay events. Never null.
     */
    public static void register(IEventBus modEventBus, IEventBus forgeEventBus) {
        if (!AlkahestDropsNetwork.isEnabled()) return;
        forgeEventBus.addListener(ClientDropRules::onLoggingOut);
        modEventBus.addListener(AlkahestDropsJeiClient::onRegisterTooltipFactories);
    }
}
