package com.jaffh.alkahestdrops;

import com.jaffh.alkahestdrops.client.events.AlkahestDropsClientEvents;
import com.jaffh.alkahestdrops.common.events.AlkahestDropsCommonEvents;
import com.jaffh.alkahestdrops.server.events.AlkahestDropsServerEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Entry point of Alkahest Drops: registers the event handlers of each side and nothing else.
 */
@Mod(AlkahestDrops.ID)
public class AlkahestDrops {
    /** Mod id and namespace of every id the mod owns. */
    public static final String ID = "alkahestdrops";

    /**
     * Creates the mod and registers its common, server and (on the physical client) client handlers.
     *
     * @param context Loading context of the mod. Never null.
     */
    public AlkahestDrops(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        AlkahestDropsCommonEvents.register(context, modEventBus, ID);
        AlkahestDropsServerEvents.register(modEventBus, MinecraftForge.EVENT_BUS);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AlkahestDropsClientEvents.register(modEventBus, MinecraftForge.EVENT_BUS));
    }
}
