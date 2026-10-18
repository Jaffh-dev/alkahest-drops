package com.jaffh.alkahestcore.dropscore.common.events;

import com.jaffh.alkahestcore.dropscore.common.DropscoreConfig;
import com.jaffh.alkahestcore.dropscore.common.DropscoreLootConditions;
import com.jaffh.alkahestcore.dropscore.common.DropscoreLootModifiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Registers what dropscore needs on both sides: its server config, its loot modifier codec and its loot condition
 * types.
 */
public final class DropscoreCommonEvents {

    /**
     * Prevents instantiation of this utility class.
     */
    private DropscoreCommonEvents() {
    }

    /**
     * Registers the config and the registry entries of dropscore.
     *
     * @param context     Loading context of the mod. Never null.
     * @param modEventBus Event bus of the mod. Never null.
     * @param modId       Namespace of the registry entries. Never null.
     */
    public static void register(ModLoadingContext context, IEventBus modEventBus, String modId) {
        DropscoreConfig.register(context);
        DropscoreLootModifiers.register(modEventBus, modId);
        DropscoreLootConditions.register(modEventBus);
    }
}
