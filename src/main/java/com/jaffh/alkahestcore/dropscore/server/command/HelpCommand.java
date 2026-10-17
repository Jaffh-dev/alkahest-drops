package com.jaffh.alkahestcore.dropscore.server.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /dropscore help [files|simulate|sources|validate] [page <n>]}: without a topic, lists the subcommands;
 * with one, explains its syntax, each argument and examples.
 */
final class HelpCommand {
    private static final String TITLE = "HELP";
    private static final String COMMAND = "/dropscore help";

    /**
     * Prevents instantiation of this utility class.
     */
    private HelpCommand() {
    }

    /**
     * Builds the {@code help} node.
     *
     * @return The node.
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> help =
                PagedOutput.withPages(Commands.literal("help"), (context, page) -> runIndex(context.getSource(), page));
        for (HelpTopic topic : HelpTopic.values()) {
            help.then(PagedOutput.withPages(Commands.literal(topic.keyword()),
                    (context, page) -> runTopic(context.getSource(), topic, page)));
        }
        return help;
    }

    /**
     * Sends one page of the subcommand list, each subcommand linking to its help.
     *
     * @param source Command source. Never null.
     * @param page   Page to send, 1 or more.
     * @return The number of topics.
     */
    private static int runIndex(CommandSourceStack source, int page) {
        Component intro = CommandText.text("Dropscore commands, click one to see how it works:", ChatFormatting.BLUE);
        List<Component> lines = new ArrayList<>();
        for (HelpTopic topic : HelpTopic.values()) {
            lines.add(CommandText.text("")
                    .append(CommandText.runnable(topic.keyword(), "Show the help of /dropscore " + topic.keyword(),
                            COMMAND + " " + topic.keyword()))
                    .append(CommandText.text(": ", ChatFormatting.GREEN))
                    .append(CommandText.text(topic.summary(), ChatFormatting.WHITE)));
        }
        PagedOutput.send(source, TITLE, List.of(intro), lines, page, COMMAND);
        return lines.size();
    }

    /**
     * Sends one page of a topic: its summary, then each section title in blue followed by its lines.
     *
     * @param source Command source. Never null.
     * @param topic  Topic to explain. Never null.
     * @param page   Page to send, 1 or more.
     * @return The number of body lines.
     */
    private static int runTopic(CommandSourceStack source, HelpTopic topic, int page) {
        Component intro = CommandText.text("")
                .append(CommandText.text("Help of ", ChatFormatting.BLUE))
                .append(CommandText.bold("/dropscore " + topic.keyword(), ChatFormatting.BLUE));
        List<Component> lines = new ArrayList<>();
        lines.add(CommandText.text(topic.summary(), ChatFormatting.WHITE));
        for (HelpSection section : topic.sections()) {
            lines.add(CommandText.text(section.title() + ":", ChatFormatting.BLUE));
            for (IHelpLine line : section.lines()) lines.add(line.render());
        }
        PagedOutput.send(source, TITLE, List.of(intro), lines, page, COMMAND + " " + topic.keyword());
        return lines.size();
    }
}
