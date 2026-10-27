package com.jaffh.alkahestdrops.server.command;

import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a titled part of a {@code /alkdrops help} page, such as its usage or its arguments.
 *
 * @param title Section title, such as "Usage". Never null.
 * @param lines Lines under the title. Never null.
 */
record HelpSection(Component title, List<IHelpLine> lines) {
    /**
     * Copies the lines so the section stays immutable.
     */
    HelpSection {
        lines = List.copyOf(lines);
    }

    /**
     * Creates the usage section.
     *
     * @param usages Syntax of each form of the command. Never null.
     * @return The section.
     */
    static HelpSection usage(String... usages) {
        return new HelpSection(CommandText.lang("help.section.usage"),
                Arrays.stream(usages).<IHelpLine>map(IHelpLine.Syntax::new).toList());
    }

    /**
     * Creates the arguments section.
     *
     * @param arguments Arguments, in command order. Never null.
     * @return The section.
     */
    static HelpSection arguments(IHelpLine.Argument... arguments) {
        return new HelpSection(CommandText.lang("help.section.arguments"), List.of(arguments));
    }

    /**
     * Creates the examples section.
     *
     * @param examples Example commands. Never null.
     * @return The section.
     */
    static HelpSection examples(IHelpLine.Example... examples) {
        return new HelpSection(CommandText.lang("help.section.examples"), List.of(examples));
    }

    /**
     * Creates an argument line whose term is typed as shown.
     *
     * @param term       Argument as typed, such as {@code page <n>}. Never null.
     * @param meaningKey Key of what the argument does, after {@code help.}, such as {@code validate.arg.errors}.
     *                   Never null.
     * @return The line.
     */
    static IHelpLine.Argument argument(String term, String meaningKey) {
        return new IHelpLine.Argument(CommandText.text(term), CommandText.lang("help." + meaningKey));
    }

    /**
     * Creates an argument line whose term is a description, such as "no namespace".
     *
     * @param termKey    Key of the term, after {@code help.term.}, such as {@code no_namespace}. Never null.
     * @param meaningKey Key of what the argument does, after {@code help.}. Never null.
     * @return The line.
     */
    static IHelpLine.Argument describedArgument(String termKey, String meaningKey) {
        return new IHelpLine.Argument(CommandText.lang("help.term." + termKey), CommandText.lang("help." + meaningKey));
    }

    /**
     * Creates an example line.
     *
     * @param command        Full command. Never null.
     * @param descriptionKey Key of what the example does, after {@code help.}, such as {@code validate.example.all}.
     *                       Never null.
     * @return The line.
     */
    static IHelpLine.Example example(String command, String descriptionKey) {
        return new IHelpLine.Example(command, CommandText.lang("help." + descriptionKey));
    }
}
