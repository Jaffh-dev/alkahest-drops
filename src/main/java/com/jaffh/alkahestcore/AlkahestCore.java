package com.jaffh.alkahestcore;


import com.jaffh.alkahestcore.dropscore.common.mobdrops.MobDropInjector;
import com.jaffh.alkahestcore.fluidscore.AlkahestConfig;
import com.jaffh.alkahestcore.fluidscore.common.FluidManager;
import com.jaffh.alkahestcore.fluidscore.common.jsonfluids.CustomFluidJsonLoader;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(AlkahestCore.ID)
public class AlkahestCore
{
    public static final String ID = "alkahestcore";
    private static final Logger LOGGER = LogUtils.getLogger();

    public AlkahestCore()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        AlkahestConfig.loadEarly();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AlkahestConfig.SPEC);

        FluidManager FLUID_MANAGER = new FluidManager(ID, modEventBus);

        CustomFluidJsonLoader.init(FLUID_MANAGER);

        MinecraftForge.EVENT_BUS.register(new MobDropInjector());

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

    // You can use EventBusSubscriber to automatically subscribe events on the contained class (this is subscribing to the MOD
    // Event bus for receiving Registry Events)
    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents
    {
        @SubscribeEvent
        public static void onBlocksRegistry(final RegistryEvent.Register<Block> blockRegistryEvent)
        {
            // Register a new block here
        }
    }
}
