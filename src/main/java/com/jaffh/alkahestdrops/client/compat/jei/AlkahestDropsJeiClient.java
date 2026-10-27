package com.jaffh.alkahestdrops.client.compat.jei;

import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

/**
 * Client events the JEI integration needs outside JEI's own plugin lifecycle. It uses no JEI class, and is only
 * registered when JEI is loaded.
 */
public final class AlkahestDropsJeiClient {

    /**
     * Prevents instantiation of this utility class.
     */
    private AlkahestDropsJeiClient() {
    }

    /**
     * Registers how the custom tooltip lines of the drop pages (chance bar, icon lines) are drawn.
     *
     * @param event Tooltip factory registration event of the mod bus.
     */
    public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ChanceBar.class, ChanceBar.Renderer::new);
        event.register(IconLine.class, IconLine.Renderer::new);
    }
}
