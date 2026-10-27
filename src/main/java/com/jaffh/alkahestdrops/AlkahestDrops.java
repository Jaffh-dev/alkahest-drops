package com.jaffh.alkahestdrops;


import com.jaffh.alkahestdrops.client.events.AlkahestDropsClientEvents;
import com.jaffh.alkahestdrops.common.events.AlkahestDropsCommonEvents;
import com.jaffh.alkahestdrops.server.events.AlkahestDropsServerEvents;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AlkahestDrops.ID)
public class AlkahestDrops
{
    public static final String ID = "alkahestdrops";
    private static final Logger LOGGER = LogUtils.getLogger();

    public AlkahestDrops(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        AlkahestDropsCommonEvents.register(context, modEventBus, ID);
        AlkahestDropsServerEvents.register(modEventBus, MinecraftForge.EVENT_BUS);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AlkahestDropsClientEvents.register(modEventBus, MinecraftForge.EVENT_BUS));

        // Register the setup method for modloading
        modEventBus.addListener(this::setup);
        // Register the enqueueIMC method for modloading
        modEventBus.addListener(this::enqueueIMC);
        // Register the processIMC method for modloading
        modEventBus.addListener(this::processIMC);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event)
    {
        // some preinit code
    }

    private void enqueueIMC(final InterModEnqueueEvent event)
    {
        // Some example code to dispatch IMC to another mod
    }

    private void processIMC(final InterModProcessEvent event)
    {
        // Some example code to receive and process InterModComms from other mods
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
    }
}
