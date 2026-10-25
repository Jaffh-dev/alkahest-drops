package com.jaffh.alkahestcore.dropscore.server.command;

import com.jaffh.alkahestcore.dropscore.server.drops.DropLoadReport;
import com.jaffh.alkahestcore.dropscore.server.drops.DropManager;
import com.jaffh.alkahestcore.dropscore.server.drops.ParsedDropFile;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.MobDropManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /dropscore files [namespace] [page <n>]}: without a namespace, counts the loaded and skipped files of every
 * namespace; with one, lists each of its files with its kind and target.
 */
final class FilesCommand {
    private static final Component TITLE = CommandText.lang("files.title");

    /**
     * Prevents instantiation of this utility class.
     */
    private FilesCommand() {
    }

    /**
     * Builds the {@code files} node.
     *
     * @return The node.
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return PagedOutput.withPages(Commands.literal("files"), (context, page) -> runSummary(context.getSource(), page))
                .then(PagedOutput.withPages(
                        Commands.argument("namespace", StringArgumentType.word()).suggests(DropscoreCommand.NAMESPACES),
                        (context, page) -> runNamespace(context.getSource(), StringArgumentType.getString(context, "namespace"), page)));
    }

    /**
     * Sends one page of the per-namespace counts.
     *
     * @param source Command source. Never null.
     * @param page   Page to send, 1 or more.
     * @return The number of namespaces.
     */
    private static int runSummary(CommandSourceStack source, int page) {
        Map<String, FileCounts> countsByNamespace = new TreeMap<>();
        for (DropManager<?> manager : DropscoreCommand.MANAGERS) {
            DropLoadReport report = manager.report();
            for (ParsedDropFile file : report.files()) {
                countsByNamespace.computeIfAbsent(file.fileId().getNamespace(), key -> new FileCounts()).add(file, manager);
            }
            for (ResourceLocation fileId : report.skippedFileIds()) {
                countsByNamespace.computeIfAbsent(fileId.getNamespace(), key -> new FileCounts()).skippedCount++;
            }
        }
        if (countsByNamespace.isEmpty()) {
            PagedOutput.send(source, TITLE, List.of(CommandText.lang("files.empty").withStyle(ChatFormatting.GRAY)),
                    List.of(), 1, "/dropscore files");
            return 0;
        }

        List<Component> lines = new ArrayList<>();
        countsByNamespace.forEach((namespace, counts) -> lines.add(counts.describe(namespace)));
        Component title = CommandText.lang(lines.size() == 1 ? "files.header.one" : "files.header.many",
                CommandText.bold(String.valueOf(lines.size()), ChatFormatting.BLUE)).withStyle(ChatFormatting.BLUE);
        PagedOutput.send(source, TITLE, List.of(title), lines, page, "/dropscore files");
        return lines.size();
    }

    /**
     * Sends one page of the files of a namespace.
     *
     * @param source    Command source. Never null.
     * @param namespace Namespace to list. Never null.
     * @param page      Page to send, 1 or more.
     * @return The number of files.
     */
    private static int runNamespace(CommandSourceStack source, String namespace, int page) {
        List<Component> lines = new ArrayList<>();
        for (DropManager<?> manager : DropscoreCommand.MANAGERS) {
            DropLoadReport report = manager.report();
            for (ParsedDropFile file : report.files()) {
                if (file.fileId().getNamespace().equals(namespace)) lines.add(describeLoaded(file, report, manager));
            }
            for (ResourceLocation fileId : report.skippedFileIds()) {
                if (fileId.getNamespace().equals(namespace)) lines.add(describeSkipped(fileId, report, manager));
            }
        }
        if (lines.isEmpty()) {
            PagedOutput.send(source, TITLE, List.of(CommandText.lang("files.namespace_empty", namespace).withStyle(ChatFormatting.GRAY)),
                    List.of(), 1, "/dropscore files " + namespace);
            return 0;
        }

        Component title = CommandText.lang(lines.size() == 1 ? "files.namespace_header.one" : "files.namespace_header.many",
                        CommandText.bold(String.valueOf(lines.size()), ChatFormatting.BLUE),
                        CommandText.bold(namespace, ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.BLUE)
                .append(CommandText.text(":", ChatFormatting.YELLOW));
        PagedOutput.send(source, TITLE, List.of(title), lines, page, "/dropscore files " + namespace);
        return lines.size();
    }

    /**
     * Describes a loaded file.
     *
     * @param file    Loaded file. Never null.
     * @param report  Report of the file's module. Never null.
     * @param manager Module of the file. Never null.
     * @return Such as {@code "[mob] drops ns:zombie → minecraft:zombie (3 drops)"}, the file id in gray, or in
     *         yellow when the file has problems.
     */
    private static Component describeLoaded(ParsedDropFile file, DropLoadReport report, DropManager<?> manager) {
        boolean isDropFile = file instanceof ParsedDropFile.Drops;
        Component size = file instanceof ParsedDropFile.Drops drops
                ? CommandText.plural("count.drop", drops.file().entries().size())
                : CommandText.plural("count.removal_entry", ((ParsedDropFile.Removals) file).rule().entries().size());
        return CommandText.kindTag(manager)
                .append(CommandText.lang(isDropFile ? "files.kind.drops" : "files.kind.removals")
                        .withStyle(isDropFile ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(CommandText.text(" "))
                .append(CommandText.fileId(file.fileId(), manager, hasIssues(file.fileId(), report) ? ChatFormatting.YELLOW : ChatFormatting.GRAY))
                .append(CommandText.text(" → " + file.target() + " (", ChatFormatting.WHITE).append(size).append(")"));
    }

    /**
     * Tells whether the last load reported a problem in a file.
     *
     * @param fileId File id. Never null.
     * @param report Report of the file's module. Never null.
     * @return True when the file has at least one error or warning.
     */
    private static boolean hasIssues(ResourceLocation fileId, DropLoadReport report) {
        return report.issues().stream().anyMatch(issue -> issue.fileId().equals(fileId));
    }

    /**
     * Describes a skipped file with its number of problems, linking to {@code /dropscore validate}.
     *
     * @param fileId  Skipped file id. Never null.
     * @param report  Report of the file's module. Never null.
     * @param manager Module of the file. Never null.
     * @return Such as {@code "[mob] skipped ns:bad (2 problems)"}.
     */
    private static Component describeSkipped(ResourceLocation fileId, DropLoadReport report, DropManager<?> manager) {
        long issueCount = report.issues().stream().filter(issue -> issue.fileId().equals(fileId)).count();
        return CommandText.kindTag(manager)
                .append(CommandText.lang("files.kind.skipped").withStyle(ChatFormatting.DARK_RED))
                .append(CommandText.text(" "))
                .append(CommandText.fileId(fileId, manager, ChatFormatting.YELLOW))
                .append(CommandText.text(" "))
                .append(CommandText.runnable(CommandText.text("(").append(CommandText.plural("count.problem", issueCount)).append(")"),
                        CommandText.lang("files.show_problems", fileId.getNamespace()),
                        "/dropscore validate " + fileId.getNamespace(), ChatFormatting.DARK_RED));
    }

    /**
     * Counts the files of one namespace by kind and module.
     */
    private static final class FileCounts {
        private int mobDropCount;
        private int mobRemovalCount;
        private int blockDropCount;
        private int blockRemovalCount;
        private int skippedCount;

        /**
         * Counts a loaded file.
         *
         * @param file    Loaded file. Never null.
         * @param manager Module that loaded the file. Never null.
         */
        void add(ParsedDropFile file, DropManager<?> manager) {
            boolean isMob = manager == MobDropManager.INSTANCE;
            boolean isDropFile = file instanceof ParsedDropFile.Drops;
            if (isMob && isDropFile) mobDropCount++;
            else if (isMob) mobRemovalCount++;
            else if (isDropFile) blockDropCount++;
            else blockRemovalCount++;
        }

        /**
         * Describes the counts on one line, linking to the namespace's file list.
         *
         * @param namespace Namespace. Never null.
         * @return Such as {@code "ns: mob 3 drops + 1 removals, block 2 drops + 0 removals, 1 skipped"}.
         */
        Component describe(String namespace) {
            MutableComponent line = CommandText.text("")
                    .append(CommandText.runnable(namespace, CommandText.lang("files.list_namespace", namespace),
                            "/dropscore files " + namespace).withStyle(ChatFormatting.UNDERLINE))
                    .append(CommandText.text(": ", ChatFormatting.WHITE))
                    .append(CommandText.lang("files.counts", mobDropCount, mobRemovalCount, blockDropCount, blockRemovalCount)
                            .withStyle(ChatFormatting.WHITE));
            if (skippedCount > 0) {
                line.append(CommandText.text(", ", ChatFormatting.WHITE))
                        .append(CommandText.lang("files.skipped_count", skippedCount).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            return line;
        }
    }
}
