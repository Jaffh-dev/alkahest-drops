package com.jaffh.alkahestcore.events;

import com.jaffh.alkahestcore.AlkahestCore;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.MobDropManager;
import com.jaffh.alkahestcore.fluidscore.common.interactions.FluidInteractionManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = AlkahestCore.ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEvents {

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event){
        event.addListener(new FluidInteractionManager());
        event.addListener(new MobDropManager());
    }
}
