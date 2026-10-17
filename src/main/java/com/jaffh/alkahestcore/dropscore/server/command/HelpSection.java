package com.jaffh.alkahestcore.dropscore.server.command;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a titled part of a {@code /dropscore help} page, such as its usage or its arguments.
 *
 * @param title Section title, such as {@code Usage}. Never null.
 * @param lines Lines under the title. Never null.
 */
record HelpSection(String title, List<IHelpLine> lines) {
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
        return new HelpSection("Usage", Arrays.stream(usages).<IHelpLine>map(IHelpLine.Syntax::new).toList());
    }

    /**
     * Creates the arguments section.
     *
     * @param arguments Arguments, in command order. Never null.
     * @return The section.
     */
    static HelpSection arguments(IHelpLine.Argument... arguments) {
        return new HelpSection("Arguments", List.of(arguments));
    }

    /**
     * Creates the examples section.
     *
     * @param examples Example commands. Never null.
     * @return The section.
     */
    static HelpSection examples(IHelpLine.Example... examples) {
        return new HelpSection("Examples", List.of(examples));
    }

    /**
     * Creates an argument line.
     *
     * @param term    Argument as typed. Never null.
     * @param meaning What the argument does. Never null.
     * @return The line.
     */
    static IHelpLine.Argument argument(String term, String meaning) {
        return new IHelpLine.Argument(term, meaning);
    }

    /**
     * Creates an example line.
     *
     * @param command     Full command. Never null.
     * @param description What the example does. Never null.
     * @return The line.
     */
    static IHelpLine.Example example(String command, String description) {
        return new IHelpLine.Example(command, description);
    }
}
