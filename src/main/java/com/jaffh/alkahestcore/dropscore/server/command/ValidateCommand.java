package com.jaffh.alkahestcore.dropscore.server.command;

import com.jaffh.alkahestcore.dropscore.server.drops.DropIssue;
import com.jaffh.alkahestcore.dropscore.server.drops.DropManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /dropscore validate [errors|warnings] [namespace] [page <n>]}: lists the problems the last reload found in
 * the drop files, optionally only one severity and only one namespace, with error and warning totals.
 */
final class ValidateCommand {
    private static final String TITLE = "VALIDATE";
    private static final String NAMESPACE = "namespace";

    /**
     * Prevents instantiation of this utility class.
     */
    private ValidateCommand() {
    }

    /**
     * Builds the {@code validate} node.
     *
     * @return The node.
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return withNamespace(Commands.literal("validate"), null)
                .then(withNamespace(Commands.literal("errors"), DropIssue.Severity.ERROR))
                .then(withNamespace(Commands.literal("warnings"), DropIssue.Severity.WARNING));
    }

    /**
     * Makes a node list the problems of one severity, and adds the optional namespace after it.
     *
     * @param node     Node ending the severity part. Never null.
     * @param severity Severity to show, or null for both.
     * @param <T>      Builder type.
     * @return The node.
     */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withNamespace(T node, @Nullable DropIssue.Severity severity) {
        return PagedOutput.withPages(node, (context, page) -> run(context.getSource(), severity, null, page))
                .then(PagedOutput.withPages(
                        Commands.argument(NAMESPACE, StringArgumentType.word()).suggests(DropscoreCommand.NAMESPACES),
                        (context, page) -> run(context.getSource(), severity, StringArgumentType.getString(context, NAMESPACE), page)));
    }

    /**
     * Sends one page of the problems.
     *
     * @param source    Command source. Never null.
     * @param severity  Severity to show, or null for both.
     * @param namespace Namespace to show, or null for every namespace.
     * @param page      Page to send, 1 or more.
     * @return The number of problems shown.
     */
    private static int run(CommandSourceStack source, @Nullable DropIssue.Severity severity, @Nullable String namespace, int page) {
        List<Component> lines = new ArrayList<>();
        int errorCount = 0;
        int warningCount = 0;
        for (DropManager<?> manager : DropscoreCommand.MANAGERS) {
            for (DropIssue issue : manager.issues()) {
                if (namespace != null && !issue.fileId().getNamespace().equals(namespace)) continue;
                if (severity != null && issue.severity() != severity) continue;
                if (issue.severity() == DropIssue.Severity.ERROR) errorCount++; else warningCount++;
                lines.add(describe(issue, manager));
            }
        }

        String scope = namespace == null ? "the dropscore files" : "namespace " + namespace;
        String command = "/dropscore validate" + severityArgument(severity) + (namespace == null ? "" : " " + namespace);
        if (lines.isEmpty()) {
            String what = severity == null ? "problems" : severity == DropIssue.Severity.ERROR ? "errors" : "warnings";
            Component message = CommandText.text("No " + what + " in " + scope + ".", ChatFormatting.GREEN);
            PagedOutput.send(source, TITLE, List.of(message), List.of(), 1, command);
            return 0;
        }

        MutableComponent title = CommandText.text("Problems in " + scope + ": ", ChatFormatting.BLUE);
        if (severity != DropIssue.Severity.WARNING) {
            title.append(CommandText.bold(String.valueOf(errorCount), ChatFormatting.RED))
                    .append(CommandText.text(errorCount == 1 ? " error" : " errors", ChatFormatting.GRAY));
        }
        if (severity == null) title.append(CommandText.text(", ", ChatFormatting.GRAY));
        if (severity != DropIssue.Severity.ERROR) {
            title.append(CommandText.bold(String.valueOf(warningCount), ChatFormatting.YELLOW))
                    .append(CommandText.text(warningCount == 1 ? " warning" : " warnings", ChatFormatting.GRAY));
        }
        PagedOutput.send(source, TITLE, List.of(title), lines, page, command);
        return lines.size();
    }

    /**
     * Returns the severity part of the command, to repeat it in the page links.
     *
     * @param severity Severity shown, or null for both.
     * @return {@code " errors"}, {@code " warnings"} or an empty string.
     */
    private static String severityArgument(@Nullable DropIssue.Severity severity) {
        if (severity == null) return "";
        return severity == DropIssue.Severity.ERROR ? " errors" : " warnings";
    }

    /**
     * Describes one problem on a line: the bold tag and the underlined file id in the severity's color (red for
     * errors, yellow for warnings), the module in gray and the problem in white.
     *
     * @param issue   Problem. Never null.
     * @param manager Module of the file. Never null.
     * @return Such as {@code "[E] [mob] ns:file: drops[0].chance: must be 0 or more, drop skipped"}.
     */
    private static Component describe(DropIssue issue, DropManager<?> manager) {
        ChatFormatting color = issue.severity() == DropIssue.Severity.ERROR ? ChatFormatting.RED : ChatFormatting.YELLOW;
        String tag = issue.severity() == DropIssue.Severity.ERROR ? "[E]" : "[W]";
        return CommandText.text("")
                .append(CommandText.bold(tag, color))
                .append(CommandText.text(" [" + manager.kind() + "] ", ChatFormatting.GRAY))
                .append(CommandText.fileId(issue.fileId(), manager, color))
                .append(CommandText.text(": ", color))
                .append(CommandText.text(issue.describe(), ChatFormatting.WHITE));
    }
}
