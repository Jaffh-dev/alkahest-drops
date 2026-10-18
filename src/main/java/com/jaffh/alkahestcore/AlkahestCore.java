package com.jaffh.alkahestcore;


import com.jaffh.alkahestcore.dropscore.common.DropscoreConfig;
import com.jaffh.alkahestcore.dropscore.common.DropscoreLootConditions;
import com.jaffh.alkahestcore.dropscore.common.DropscoreLootModifiers;
import com.jaffh.alkahestcore.dropscore.server.blockdrops.BlockBreakHandler;
import com.jaffh.alkahestcore.dropscore.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestcore.dropscore.server.command.DropscoreCommand;
import com.jaffh.alkahestcore.dropscore.server.progress.DropProgressEvents;
import com.jaffh.alkahestcore.dropscore.server.progress.ProgressPruner;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.HitTracker;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.LootingOverride;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.MobDropInjector;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.MobDropManager;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AlkahestCore.ID)
public class AlkahestCore
{
    public static final String ID = "alkahestcore";
    private static final Logger LOGGER = LogUtils.getLogger();

    public AlkahestCore()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        DropscoreConfig.register(ModLoadingContext.get());
        modEventBus.addListener(MobDropManager.INSTANCE::onConfigLoading);
        MinecraftForge.EVENT_BUS.register(new MobDropInjector());
        MinecraftForge.EVENT_BUS.addListener(MobDropManager.INSTANCE::onTagsUpdated);
        DropscoreLootModifiers.register(modEventBus, ID);
        DropscoreLootConditions.register(modEventBus);
        modEventBus.addListener(BlockDropManager.INSTANCE::onConfigLoading);
        MinecraftForge.EVENT_BUS.register(new BlockBreakHandler());
        MinecraftForge.EVENT_BUS.addListener(BlockDropManager.INSTANCE::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, LootingOverride.INSTANCE::onLootingLevel);
        MinecraftForge.EVENT_BUS.addListener(DropscoreCommand::onRegisterCommands);
        MinecraftForge.EVENT_BUS.register(new HitTracker());
        modEventBus.addListener(DropProgressEvents::onRegisterCapabilities);
        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, DropProgressEvents::onAttachCapabilities);
        MinecraftForge.EVENT_BUS.addListener(DropProgressEvents::onPlayerClone);
        MinecraftForge.EVENT_BUS.addListener(ProgressPruner::onDatapackSync);

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
