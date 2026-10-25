package com.jaffh.alkahestcore.dropscore.server.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Represents one line of a {@code /dropscore help} page, which knows how to render itself in chat.
 */
sealed interface IHelpLine permits IHelpLine.Syntax, IHelpLine.Argument, IHelpLine.Example {
    /** Indentation of the lines under a section title. */
    String INDENT = "  ";

    /**
     * Renders the line.
     *
     * @return The chat line.
     */
    Component render();

    /**
     * Represents the syntax of a command, in yellow; clicking it puts its fixed start in the chat input.
     *
     * @param usage Syntax, such as {@code /dropscore files [namespace] [page <n>]}. Never null.
     */
    record Syntax(String usage) implements IHelpLine {
        @Override
        public Component render() {
            return CommandText.text(INDENT).append(CommandText.suggestable(usage, CommandText.lang("hint.start_typing"),
                    fixedStart(), ChatFormatting.YELLOW));
        }

        /**
         * Returns the start of the syntax before its first argument.
         *
         * @return Such as {@code "/dropscore simulate entity "}.
         */
        private String fixedStart() {
            int end = usage.length();
            for (int i = 0; i < usage.length(); i++) {
                char c = usage.charAt(i);
                if (c == '<' || c == '[') {
                    end = i;
                    break;
                }
            }
            return usage.substring(0, end).trim() + " ";
        }
    }

    /**
     * Represents an argument and what it means: the term (bold) and its colon in yellow, the meaning in white.
     *
     * @param term    Argument as typed, such as {@code page <n>}. Never null.
     * @param meaning What the argument does. Never null.
     */
    record Argument(Component term, Component meaning) implements IHelpLine {
        @Override
        public Component render() {
            return CommandText.text(INDENT, ChatFormatting.WHITE)
                    .append(term.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(CommandText.text(": ", ChatFormatting.YELLOW))
                    .append(meaning.copy().withStyle(ChatFormatting.WHITE));
        }
    }

    /**
     * Represents an example command in aqua, put in the chat input when clicked, followed by what it does in gray.
     *
     * @param command     Full command. Never null.
     * @param description What the example does. Never null.
     */
    record Example(String command, Component description) implements IHelpLine {
        @Override
        public Component render() {
            return CommandText.text(INDENT, ChatFormatting.WHITE)
                    .append(CommandText.suggestable(command, CommandText.lang("hint.put_in_chat"), command, ChatFormatting.AQUA))
                    .append(CommandText.text(" ", ChatFormatting.GRAY))
                    .append(description.copy().withStyle(ChatFormatting.GRAY));
        }
    }
}
