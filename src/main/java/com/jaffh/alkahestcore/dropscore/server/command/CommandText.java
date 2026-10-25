package com.jaffh.alkahestcore.dropscore.server.command;

import com.jaffh.alkahestcore.dropscore.common.drops.DropNumbers;
import com.jaffh.alkahestcore.dropscore.server.drops.DropManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;


/**
 * Builds the chat text shared by the {@code /dropscore} commands: clickable file ids and items, and numbers.
 */
final class CommandText {

    /**
     * Prevents instantiation of this utility class.
     */
    private CommandText() {
    }

    /**
     * Creates plain text.
     *
     * @param text Text. Never null.
     * @return A new mutable component.
     */
    static MutableComponent text(String text) {
        return Component.literal(text);
    }

    /**
     * Creates colored text.
     *
     * @param text  Text. Never null.
     * @param color Chat color. Never null.
     * @return A new mutable component.
     */
    static MutableComponent text(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    /**
     * Creates bold colored text.
     *
     * @param text  Text. Never null.
     * @param color Chat color. Never null.
     * @return A new mutable component.
     */
    static MutableComponent bold(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color, ChatFormatting.BOLD);
    }

    /**
     * Creates an underlined file id that shows its datapack path on hover and copies the path when clicked.
     *
     * @param fileId  File id. Never null.
     * @param manager Module the file belongs to. Never null.
     * @param color   Color of the id. Never null.
     * @return The clickable file id.
     */
    static MutableComponent fileId(ResourceLocation fileId, DropManager<?> manager, ChatFormatting color) {
        String path = "data/" + fileId.getNamespace() + "/" + manager.folder() + "/" + fileId.getPath() + ".json";
        Style style = Style.EMPTY
                .withColor(color)
                .withUnderlined(true)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text(path + "\nClick to copy the path")))
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, path));
        return text(fileId.toString()).withStyle(style);
    }

    /**
     * Creates the name of an item, in bold yellow.
     *
     * @param item Item. Never null.
     * @return The item name.
     */
    static MutableComponent itemName(Item item) {
        return new ItemStack(item).getHoverName().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
    }

    /**
     * Creates text that runs a command when clicked.
     *
     * @param label   Shown text. Never null.
     * @param hint    Hover text. Never null.
     * @param command Command to run. Never null.
     * @return The clickable text, in green.
     */
    static MutableComponent runnable(String label, String hint, String command) {
        return runnable(label, hint, command, ChatFormatting.GREEN);
    }

    /**
     * Creates colored text that runs a command when clicked.
     *
     * @param label   Shown text. Never null.
     * @param hint    Hover text. Never null.
     * @param command Command to run. Never null.
     * @param color   Color of the text. Never null.
     * @return The clickable text.
     */
    static MutableComponent runnable(String label, String hint, String command, ChatFormatting color) {
        return clickable(label, hint, new ClickEvent(ClickEvent.Action.RUN_COMMAND, command), color);
    }

    /**
     * Creates colored text that puts a command in the chat input when clicked, without running it.
     *
     * @param label   Shown text. Never null.
     * @param hint    Hover text. Never null.
     * @param command Command to put in the chat input. Never null.
     * @param color   Color of the text. Never null.
     * @return The clickable text.
     */
    static MutableComponent suggestable(String label, String hint, String command, ChatFormatting color) {
        return clickable(label, hint, new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command), color);
    }

    /**
     * Creates colored text with a hover text and a click action.
     *
     * @param label Shown text. Never null.
     * @param hint  Hover text. Never null.
     * @param click Click action. Never null.
     * @param color Color of the text. Never null.
     * @return The clickable text.
     */
    private static MutableComponent clickable(String label, String hint, ClickEvent click, ChatFormatting color) {
        Style style = Style.EMPTY
                .withColor(color)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text(hint)))
                .withClickEvent(click);
        return text(label).withStyle(style);
    }

    /**
     * Formats a number with two decimals, dropping trailing zeros.
     *
     * @param value Number to format.
     * @return Such as {@code "1.5"} or {@code "3"}.
     */
    static String number(double value) {
        return DropNumbers.number(value);
    }

    /**
     * Formats a 0–1 ratio as a percentage.
     *
     * @param ratio Ratio between 0 and 1.
     * @return Such as {@code "12.5%"}.
     */
    static String percent(double ratio) {
        return DropNumbers.percent(ratio);
    }

    /**
     * Returns a word with a plural {@code s} when the count is not 1.
     *
     * @param count Count of things.
     * @param word  Singular word. Never null.
     * @return Such as {@code "1 file"} or {@code "3 files"}.
     */
    static String count(long count, String word) {
        return count + " " + word + (count == 1 ? "" : "s");
    }
}
