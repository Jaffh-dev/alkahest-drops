package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.server.lang.Localizer;
import net.minecraft.network.chat.Component;

/**
 * Signals that a simulation setup cannot be simulated, such as {@code sneaking} without anyone to sneak, with a
 * translatable message for the player.
 */
public class InvalidSimulationException extends IllegalArgumentException {
    private final transient Component text;

    /**
     * Creates the exception.
     *
     * @param text Translatable message for the player. Never null.
     */
    public InvalidSimulationException(Component text) {
        super(Localizer.english().text(text));
        this.text = text;
    }

    /**
     * Returns the message for the player.
     *
     * @return The translatable message.
     */
    public Component text() {
        return text;
    }
}
