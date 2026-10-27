package com.jaffh.alkahestdrops.common.events;

import com.jaffh.alkahestdrops.common.AlkahestDropsConfig;
import com.jaffh.alkahestdrops.common.AlkahestDropsLootConditions;
import com.jaffh.alkahestdrops.common.AlkahestDropsLootModifiers;
import com.jaffh.alkahestdrops.common.network.AlkahestDropsNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Registers what Alkahest Drops needs on both sides: its server config, its loot modifier codec, its loot condition
 * types and its network channel.
 */
public final class AlkahestDropsCommonEvents {

    /**
     * Prevents instantiation of this utility class.
     */
    private AlkahestDropsCommonEvents() {
    }

    /**
     * Registers the config, the registry entries and the network channel of Alkahest Drops.
     *
     * @param context     Loading context of the mod. Never null.
     * @param modEventBus Event bus of the mod. Never null.
     * @param modId       Namespace of the registry entries and the channel. Never null.
     */
    public static void register(ModLoadingContext context, IEventBus modEventBus, String modId) {
        AlkahestDropsConfig.register(context);
        AlkahestDropsLootModifiers.register(modEventBus, modId);
        AlkahestDropsLootConditions.register(modEventBus);
        AlkahestDropsNetwork.register(modId);
    }
}
