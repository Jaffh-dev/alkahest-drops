package com.jaffh.alkahestcore.dropscore.client.events;

import com.jaffh.alkahestcore.dropscore.client.compat.jei.DropscoreJeiClient;
import com.jaffh.alkahestcore.dropscore.client.drops.ClientDropRules;
import com.jaffh.alkahestcore.dropscore.common.network.DropscoreNetwork;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Registers the event handlers of dropscore's physical client. Call only on the physical client, after the network
 * channel is registered.
 */
public final class DropscoreClientEvents {

    /**
     * Prevents instantiation of this utility class.
     */
    private DropscoreClientEvents() {
    }

    /**
     * Registers every client handler of the module. Without JEI the client has no channel and receives no drop
     * summary, so nothing is registered.
     *
     * @param modEventBus   Event bus of the mod. Never null.
     * @param forgeEventBus Forge event bus of gameplay events. Never null.
     */
    public static void register(IEventBus modEventBus, IEventBus forgeEventBus) {
        if (!DropscoreNetwork.isEnabled()) return;
        forgeEventBus.addListener(ClientDropRules::onLoggingOut);
        modEventBus.addListener(DropscoreJeiClient::onRegisterTooltipFactories);
    }
}
