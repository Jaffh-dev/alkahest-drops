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
            PagedOutput.send(source, "FILES", List.of(CommandText.text("No dropscore files are loaded.", ChatFormatting.GRAY)),
                    List.of(), 1, "/dropscore files");
            return 0;
        }

        List<Component> lines = new ArrayList<>();
        countsByNamespace.forEach((namespace, counts) -> lines.add(counts.describe(namespace)));
        Component title = CommandText.text("")
                .append(CommandText.text("Dropscore files in ", ChatFormatting.BLUE))
                .append(CommandText.bold(String.valueOf(lines.size()), ChatFormatting.BLUE))
                .append(CommandText.text(lines.size() == 1 ? " namespace:" : " namespaces:", ChatFormatting.BLUE));
        PagedOutput.send(source, "FILES", List.of(title), lines, page, "/dropscore files");
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
            PagedOutput.send(source, "FILES", List.of(CommandText.text("No dropscore files in namespace " + namespace + ".", ChatFormatting.GRAY)),
                    List.of(), 1, "/dropscore files " + namespace);
            return 0;
        }

        Component title = CommandText.text("")
                .append(CommandText.bold(String.valueOf(lines.size()), ChatFormatting.BLUE))
                .append(CommandText.text((lines.size() == 1 ? " file" : " files") + " in namespace ", ChatFormatting.BLUE))
                .append(CommandText.bold(namespace, ChatFormatting.YELLOW))
                .append(CommandText.text(":", ChatFormatting.YELLOW));
        PagedOutput.send(source, "FILES", List.of(title), lines, page, "/dropscore files " + namespace);
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
        String size = file instanceof ParsedDropFile.Drops drops
                ? CommandText.count(drops.file().entries().size(), "drop")
                : ((ParsedDropFile.Removals) file).rule().entries().size() + " removal entries";
        return CommandText.text("[" + manager.kind() + "] ", ChatFormatting.GRAY)
                .append(CommandText.text(isDropFile ? "drops " : "removals ", isDropFile ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(CommandText.fileId(file.fileId(), manager, hasIssues(file.fileId(), report) ? ChatFormatting.YELLOW : ChatFormatting.GRAY))
                .append(CommandText.text(" → " + file.target() + " (" + size + ")", ChatFormatting.WHITE));
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
        return CommandText.text("[" + manager.kind() + "] ", ChatFormatting.GRAY)
                .append(CommandText.text("skipped ", ChatFormatting.DARK_RED))
                .append(CommandText.fileId(fileId, manager, ChatFormatting.YELLOW))
                .append(CommandText.text(" "))
                .append(CommandText.runnable("(" + CommandText.count(issueCount, "problem") + ")",
                        "Show the problems of namespace " + fileId.getNamespace(),
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
                    .append(CommandText.runnable(namespace, "List the files of " + namespace, "/dropscore files " + namespace)
                            .withStyle(ChatFormatting.UNDERLINE))
                    .append(CommandText.text(": mob " + mobDropCount + " drop + " + mobRemovalCount + " removal, block "
                            + blockDropCount + " drop + " + blockRemovalCount + " removal files", ChatFormatting.WHITE));
            if (skippedCount > 0) {
                line.append(CommandText.text(", ", ChatFormatting.WHITE))
                        .append(CommandText.bold(skippedCount + " skipped", ChatFormatting.RED));
            }
            return line;
        }
    }
}
