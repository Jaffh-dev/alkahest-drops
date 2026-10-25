package com.jaffh.alkahestcore.dropscore.client.compat.jei;

import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

/**
 * Client events the JEI integration needs outside JEI's own plugin lifecycle. It uses no JEI class, and is only
 * registered when JEI is loaded.
 */
public final class DropscoreJeiClient {

    /**
     * Prevents instantiation of this utility class.
     */
    private DropscoreJeiClient() {
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
