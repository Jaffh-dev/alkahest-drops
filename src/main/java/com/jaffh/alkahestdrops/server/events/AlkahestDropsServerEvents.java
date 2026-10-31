package com.jaffh.alkahestdrops.server.events;

import com.jaffh.alkahestdrops.common.network.AlkahestDropsNetwork;
import com.jaffh.alkahestdrops.server.blockdrops.BlockBreakHandler;
import com.jaffh.alkahestdrops.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestdrops.server.command.AlkahestDropsCommand;
import com.jaffh.alkahestdrops.server.drops.DropFilesLoadedNotifier;
import com.jaffh.alkahestdrops.server.drops.DropSpawnEvents;
import com.jaffh.alkahestdrops.server.mobdrops.HitTracker;
import com.jaffh.alkahestdrops.server.mobdrops.LootingOverride;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropInjector;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropManager;
import com.jaffh.alkahestdrops.server.mobdrops.TriggerDropInjector;
import com.jaffh.alkahestdrops.server.progress.DropProgressEvents;
import com.jaffh.alkahestdrops.server.progress.ProgressPruner;
import com.jaffh.alkahestdrops.server.sync.DropRulesSender;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Registers the event handlers of the mod's logical server: datapack loading, mob and block drops, the {@code
 * /alkdrops} command, player progress and the drop summary sent to clients. They are registered on both physical sides,
 * because singleplayer runs the logical server inside the client.
 */
public final class AlkahestDropsServerEvents {

    /**
     * Prevents instantiation of this utility class.
     */
    private AlkahestDropsServerEvents() {}

    /**
     * Registers every handler of the module. The drop summary sender is only registered when the network channel
     * exists (call after the channel is registered).
     *
     * @param modEventBus   Event bus of the mod. Never null.
     * @param forgeEventBus Forge event bus of gameplay events. Never null.
     */
    public static void register(IEventBus modEventBus, IEventBus forgeEventBus) {
        modEventBus.addListener(MobDropManager.INSTANCE::onConfigLoading);
        modEventBus.addListener(BlockDropManager.INSTANCE::onConfigLoading);
        modEventBus.addListener(DropProgressEvents::onRegisterCapabilities);

        forgeEventBus.addListener(AlkahestDropsServerEvents::onAddReloadListener);
        forgeEventBus.register(new MobDropInjector());
        forgeEventBus.register(new TriggerDropInjector());
        forgeEventBus.register(new DropSpawnEvents());
        forgeEventBus.addListener(MobDropManager.INSTANCE::onTagsUpdated);
        forgeEventBus.register(new BlockBreakHandler());
        forgeEventBus.addListener(BlockDropManager.INSTANCE::onTagsUpdated);
        forgeEventBus.addListener(EventPriority.LOWEST, LootingOverride.INSTANCE::onLootingLevel);
        forgeEventBus.addListener(AlkahestDropsCommand::onRegisterCommands);
        forgeEventBus.register(new HitTracker());
        forgeEventBus.addGenericListener(Entity.class, DropProgressEvents::onAttachCapabilities);
        forgeEventBus.addListener(DropProgressEvents::onPlayerClone);
        forgeEventBus.addListener(ProgressPruner::onDatapackSync);
        forgeEventBus.addListener(DropFilesLoadedNotifier::onServerStarted);
        forgeEventBus.addListener(DropFilesLoadedNotifier::onDatapackSync);
        if (AlkahestDropsNetwork.isEnabled()) {
            forgeEventBus.addListener(new DropRulesSender()::onDatapackSync);
        }
    }

    /**
     * Adds the datapack reload listeners of the mob and block drop files.
     *
     * @param event Reload listener event of the Forge bus.
     */
    private static void onAddReloadListener(AddReloadListenerEvent event) {
        MobDropManager.INSTANCE.useFunctionLibrary(event.getServerResources().getFunctionLibrary());
        BlockDropManager.INSTANCE.useFunctionLibrary(event.getServerResources().getFunctionLibrary());
        event.addListener(MobDropManager.INSTANCE);
        event.addListener(BlockDropManager.INSTANCE);
    }
}
