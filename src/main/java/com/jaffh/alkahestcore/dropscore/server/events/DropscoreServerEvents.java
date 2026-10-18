package com.jaffh.alkahestcore.dropscore.server.events;

import com.jaffh.alkahestcore.dropscore.server.blockdrops.BlockBreakHandler;
import com.jaffh.alkahestcore.dropscore.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestcore.dropscore.server.command.DropscoreCommand;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.HitTracker;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.LootingOverride;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.MobDropInjector;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.MobDropManager;
import com.jaffh.alkahestcore.dropscore.server.progress.DropProgressEvents;
import com.jaffh.alkahestcore.dropscore.server.progress.ProgressPruner;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Registers the event handlers of dropscore's logical server: datapack loading, mob and block drops, the
 * {@code /dropscore} command and player progress. They are registered on both physical sides, because singleplayer
 * runs the logical server inside the client.
 */
public final class DropscoreServerEvents {

    /**
     * Prevents instantiation of this utility class.
     */
    private DropscoreServerEvents() {
    }

    /**
     * Registers every handler of the module.
     *
     * @param modEventBus   Event bus of the mod. Never null.
     * @param forgeEventBus Forge event bus of gameplay events. Never null.
     */
    public static void register(IEventBus modEventBus, IEventBus forgeEventBus) {
        modEventBus.addListener(MobDropManager.INSTANCE::onConfigLoading);
        modEventBus.addListener(BlockDropManager.INSTANCE::onConfigLoading);
        modEventBus.addListener(DropProgressEvents::onRegisterCapabilities);

        forgeEventBus.addListener(DropscoreServerEvents::onAddReloadListener);
        forgeEventBus.register(new MobDropInjector());
        forgeEventBus.addListener(MobDropManager.INSTANCE::onTagsUpdated);
        forgeEventBus.register(new BlockBreakHandler());
        forgeEventBus.addListener(BlockDropManager.INSTANCE::onTagsUpdated);
        forgeEventBus.addListener(EventPriority.LOWEST, LootingOverride.INSTANCE::onLootingLevel);
        forgeEventBus.addListener(DropscoreCommand::onRegisterCommands);
        forgeEventBus.register(new HitTracker());
        forgeEventBus.addGenericListener(Entity.class, DropProgressEvents::onAttachCapabilities);
        forgeEventBus.addListener(DropProgressEvents::onPlayerClone);
        forgeEventBus.addListener(ProgressPruner::onDatapackSync);
    }

    /**
     * Adds the datapack reload listeners of the mob and block drop files.
     *
     * @param event Reload listener event of the Forge bus.
     */
    private static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(MobDropManager.INSTANCE);
        event.addListener(BlockDropManager.INSTANCE);
    }
}
