package com.jaffh.alkahestdrops.server.command;

import com.jaffh.alkahestdrops.server.lang.Localizer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends command results in a frame, one page at a time, so chat is not flooded:
 * <pre>
 * ===========[TITLE]===========
 * intro lines (on every page)
 * body lines of the page
 * ====[&lt;]==[PAGE X/Y]==[&gt;]====
 * </pre>
 * Every line is first translated into the language of whoever runs the command, so the layout measures the text
 * the player will read. A page holds at most {@link #MAX_LINES} chat lines, frame included, counting the lines a
 * long message wraps into; an entry that does not fit moves to the next page. The bars are padded with {@code =} to fill a chat line, with
 * their label centered. The arrows run the command for the other pages.
 */
final class PagedOutput {
    /** Chat lines of one page, frame included. */
    static final int MAX_LINES = 16;

    private static final int FRAME_LINES = 2;
    private static final ChatFormatting FRAME_COLOR = ChatFormatting.GOLD;
    private static final String FILL = "=";

    /**
     * Prevents instantiation of this utility class.
     */
    private PagedOutput() {
    }

    /**
     * Runs a command for one page of its output.
     */
    @FunctionalInterface
    interface PageAction {
        /**
         * Sends one page.
         *
         * @param context Command context. Never null.
         * @param page    Page to send, 1 or more.
         * @return The command result.
         * @throws CommandSyntaxException If an argument is invalid.
         */
        int run(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException;
    }

    /**
     * Makes a node run its action on page 1, and adds the {@code page <number>} suffix for the other pages.
     *
     * @param node   Node whose command is paged. Never null.
     * @param action Action sending one page. Never null.
     * @param <T>    Builder type.
     * @return The node.
     */
    static <T extends ArgumentBuilder<CommandSourceStack, T>> T withPages(T node, PageAction action) {
        return node.executes(context -> action.run(context, 1))
                .then(Commands.literal("page")
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> action.run(context, IntegerArgumentType.getInteger(context, "page")))));
    }

    /**
     * Sends one framed page, translated for the source: the title bar, the intro lines, the page's body lines and
     * the page bar.
     *
     * @param source  Command source. Never null.
     * @param title   Title shown in the top bar, such as "SIMULATION". Never null.
     * @param intro   Lines repeated on top of every page. Never null.
     * @param lines   Body lines, split into pages. Never null.
     * @param page    Requested page, clamped to the last page.
     * @param command Command that sends this output, without the page suffix, such as {@code /alkdrops validate}.
     *                Never null.
     */
    static void send(CommandSourceStack source, Component title, List<? extends Component> intro,
                     List<? extends Component> lines, int page, String command) {
        Localizer lang = Localizer.forSource(source);
        List<Component> shownIntro = intro.stream().<Component>map(lang::localize).toList();
        List<Component> shownLines = lines.stream().<Component>map(lang::localize).toList();
        List<Integer> pageStarts = splitIntoPages(shownLines, MAX_LINES - FRAME_LINES - countChatLines(shownIntro));
        int pageCount = pageStarts.size();
        int shownPage = Math.min(page, pageCount);
        int start = pageStarts.get(shownPage - 1);
        int end = shownPage < pageCount ? pageStarts.get(shownPage) : shownLines.size();

        Component header = titleBar(lang.text(title));
        source.sendSuccess(() -> header, false);
        for (Component line : shownIntro) source.sendSuccess(() -> line, false);
        for (Component line : shownLines.subList(start, end)) source.sendSuccess(() -> line, false);
        Component footer = pageBar(shownPage, pageCount, command, lang);
        source.sendSuccess(() -> footer, false);
    }

    /**
     * Splits body lines into pages, filling each page up to the available chat lines. A line taller than a whole
     * page still gets a page of its own.
     *
     * @param lines     Body lines. Never null.
     * @param available Chat lines left for the body on each page.
     * @return The index of the first line of each page; one page starting at 0 when there are no lines.
     */
    private static List<Integer> splitIntoPages(List<? extends Component> lines, int available) {
        List<Integer> pageStarts = new ArrayList<>();
        pageStarts.add(0);
        int used = 0;
        for (int i = 0; i < lines.size(); i++) {
            int height = ChatWidth.linesOf(lines.get(i));
            if (used > 0 && used + height > available) {
                pageStarts.add(i);
                used = 0;
            }
            used += height;
        }
        return pageStarts;
    }

    /**
     * Counts the chat lines of messages once wrapped.
     *
     * @param messages Messages. Never null.
     * @return The total number of chat lines.
     */
    private static int countChatLines(List<? extends Component> messages) {
        int total = 0;
        for (Component message : messages) total += ChatWidth.linesOf(message);
        return total;
    }

    /**
     * Builds the top bar.
     *
     * @param title Translated title. Never null.
     * @return Such as {@code "===========[SIMULATION]==========="}, filling a chat line.
     */
    private static Component titleBar(String title) {
        String label = "[" + title + "]";
        int fillCount = fillCount(ChatWidth.of(label));
        int left = fillCount / 2;
        return CommandText.text(FILL.repeat(left) + label + FILL.repeat(fillCount - left), FRAME_COLOR);
    }

    /**
     * Builds the bottom bar with the page number and the arrows, centered; an arrow is gray when there is no page
     * that way.
     *
     * @param page      Shown page.
     * @param pageCount Number of pages, 1 or more.
     * @param command   Command without the page suffix. Never null.
     * @param lang      Language of the source. Never null.
     * @return Such as {@code "========[<]==[PAGE 2/5]==[>]========"}, filling a chat line.
     */
    private static Component pageBar(int page, int pageCount, String command, Localizer lang) {
        String pageLabel = "==[" + lang.text(CommandText.lang("page.label", page, pageCount)) + "]==";
        int fillCount = fillCount(ChatWidth.of("[<]" + pageLabel + "[>]"));
        int left = fillCount / 2;
        return CommandText.text(FILL.repeat(left), FRAME_COLOR)
                .append(arrow("[<]", page - 1, pageCount, command, lang))
                .append(CommandText.text(pageLabel, FRAME_COLOR))
                .append(arrow("[>]", page + 1, pageCount, command, lang))
                .append(CommandText.text(FILL.repeat(fillCount - left), FRAME_COLOR));
    }

    /**
     * Returns how many fill characters complete a chat line around a label.
     *
     * @param labelWidth Width of the label in pixels.
     * @return The number of fill characters, 0 or more, that keep the bar within one line.
     */
    private static int fillCount(int labelWidth) {
        return Math.max(0, (ChatWidth.LINE_WIDTH - labelWidth) / ChatWidth.of(FILL));
    }

    /**
     * Builds one arrow of the page bar.
     *
     * @param label      Arrow text. Never null.
     * @param targetPage Page the arrow leads to.
     * @param pageCount  Number of pages.
     * @param command    Command without the page suffix. Never null.
     * @param lang       Language of the source. Never null.
     * @return A clickable arrow, or a gray one when the target page does not exist.
     */
    private static MutableComponent arrow(String label, int targetPage, int pageCount, String command, Localizer lang) {
        if (targetPage < 1 || targetPage > pageCount) return CommandText.text(label, ChatFormatting.DARK_GRAY);
        return lang.localize(CommandText.runnable(label, CommandText.lang("page.show", targetPage), command + " page " + targetPage));
    }
}
