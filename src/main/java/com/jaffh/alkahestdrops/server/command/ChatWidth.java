package com.jaffh.alkahestdrops.server.command;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/**
 * Estimates on the server how wide a text is in the client's chat, with the advances of the default Minecraft font,
 * so pages can count wrapped chat lines and bars can fill a line. The real width depends on each player's chat
 * width setting and resource pack; this assumes the default (320 pixels, default font).
 */
final class ChatWidth {
    /** Width of a chat line in pixels with the default chat width setting. */
    static final int LINE_WIDTH = 320;

    private static final int DEFAULT_ADVANCE = 6;

    /**
     * Prevents instantiation of this utility class.
     */
    private ChatWidth() {
    }

    /**
     * Returns the width of a text.
     *
     * @param text Text. Never null.
     * @return The width in pixels.
     */
    static int of(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += advanceOf(text.charAt(i));
        }
        return width;
    }

    /**
     * Returns how many chat lines a message takes once the client wraps it. Bold text is one pixel wider per character.
     *
     * @param message Message. Never null.
     * @return The number of lines, 1 or more.
     */
    static int linesOf(Component message) {
        int[] width = {0};
        message.visit((style, text) -> {
            width[0] += of(text) + (style.isBold() ? text.length() : 0);
            return Optional.empty();
        }, Style.EMPTY);
        return Math.max(1, (width[0] + LINE_WIDTH - 1) / LINE_WIDTH);
    }

    /**
     * Returns the advance of one character of the default font (glyph width plus one pixel of spacing).
     *
     * @param character Character.
     * @return The advance in pixels; 6 for characters not in the table.
     */
    private static int advanceOf(char character) {
        return switch (character) {
            case '!', ',', '.', ':', ';', 'i', '|', '\'' -> 2;
            case '`', 'l' -> 3;
            case ' ', 'I', '[', ']', 't' -> 4;
            case '"', '(', ')', '*', '<', '>', 'f', 'k', '{', '}' -> 5;
            case '@', '~' -> 7;
            default -> DEFAULT_ADVANCE;
        };
    }
}
