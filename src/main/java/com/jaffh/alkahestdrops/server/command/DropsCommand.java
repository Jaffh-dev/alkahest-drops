package com.jaffh.alkahestdrops.server.command;

import com.jaffh.alkahestdrops.common.drops.DropEntry;
import com.jaffh.alkahestdrops.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestdrops.server.drops.DropFile;
import com.jaffh.alkahestdrops.server.drops.DropManager;
import com.jaffh.alkahestdrops.server.drops.ParsedDropFile;
import com.jaffh.alkahestdrops.server.drops.RemovalEntry;
import com.jaffh.alkahestdrops.server.drops.RemovalRule;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * {@code /alkdrops drops <entity | block> [page <n>]}: every drop and removal entry Alkahest Drops applies to one mob
 * or block, from files that name it by id, {@code mod:*}, {@code #tag} or {@code *}. Like {@code sources}, but looked
 * up by target instead of by item. An id naming both a mob and a block (such as {@code minecraft:tnt}) shows both.
 */
final class DropsCommand {
    private static final String TARGET = "target";
    private static final Component TITLE = CommandText.lang("drops.title");

    /** Suggests every mob and block that some Alkahest Drops file applies to. */
    private static final SuggestionProvider<CommandSourceStack> TARGETS_WITH_RULES =
            TargetLookup.suggestingTables(table -> !table.isEmpty());

    /**
     * Prevents instantiation of this utility class.
     */
    private DropsCommand() {}

    /**
     * Builds the {@code drops} node.
     *
     * @return The node.
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("drops")
                .then(PagedOutput.withPages(
                        Commands.argument(TARGET, ResourceLocationArgument.id()).suggests(TARGETS_WITH_RULES),
                        (context, page) -> run(
                                context.getSource(),
                                TargetLookup.of(ResourceLocationArgument.getId(context, TARGET)),
                                page)));
    }

    /**
     * Sends one page of the drops and removals that apply to a mob, a block, or both.
     *
     * @param source Command source. Never null.
     * @param lookup Mob and block of the typed id. Never null.
     * @param page   Page to send, 1 or more.
     * @return The number of drops and removal entries.
     */
    private static int run(CommandSourceStack source, TargetLookup lookup, int page) {
        List<Component> addLines = new ArrayList<>();
        List<Component> removeLines = new ArrayList<>();
        if (lookup.entity() != null) collect(MobDropManager.INSTANCE, lookup.entity(), addLines, removeLines);
        if (lookup.block() != null) collect(BlockDropManager.INSTANCE, lookup.block(), addLines, removeLines);

        String command = "/alkdrops drops " + lookup.id();
        MutableComponent name = nameOf(lookup);
        if (addLines.isEmpty() && removeLines.isEmpty()) {
            Component message = CommandText.lang("drops.none", name).withStyle(ChatFormatting.GRAY);
            PagedOutput.send(source, TITLE, List.of(message), List.of(), 1, command);
            return 0;
        }

        List<Component> lines = new ArrayList<>(addLines);
        lines.addAll(removeLines);
        Component title = CommandText.lang("drops.header", name)
                .withStyle(ChatFormatting.BLUE)
                .append(CommandText.text(": ", ChatFormatting.YELLOW))
                .append(RuleText.counts(addLines.size(), removeLines.size()));
        PagedOutput.send(source, TITLE, List.of(title), lines, page, command);
        return lines.size();
    }

    /**
     * Returns the shown name of the looked-up target, in bold yellow.
     *
     * @param lookup Mob and block of the typed id. Never null.
     * @return The mob name or the block name; the id when it names both.
     */
    private static MutableComponent nameOf(TargetLookup lookup) {
        Component name;
        if (lookup.entity() != null && lookup.block() != null) {
            name = CommandText.text(lookup.id().toString());
        } else if (lookup.entity() != null) {
            name = lookup.entity().getDescription();
        } else {
            name = lookup.block().getName();
        }
        return name.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
    }

    /**
     * Adds the lines of every loaded file of a module that applies to a target.
     *
     * @param manager     Module. Never null.
     * @param value       Mob type or block. Never null.
     * @param addLines    Receives the drop lines. Never null.
     * @param removeLines Receives the removal lines. Never null.
     * @param <T>         Registry entry type of the module.
     */
    private static <T> void collect(
            DropManager<T> manager, T value, List<Component> addLines, List<Component> removeLines) {
        for (ParsedDropFile file : manager.report().files()) {
            if (!manager.doesFileApplyTo(file.fileId(), value)) continue;
            if (file instanceof ParsedDropFile.Drops drops) {
                collectAdds(drops, manager, addLines);
            } else if (file instanceof ParsedDropFile.Removals removals) {
                collectRemovals(removals, manager, removeLines);
            }
        }
    }

    /**
     * Adds a line for every drop of a drop file: what it drops, then its amount, chance and settings.
     *
     * @param drops   Drop file that applies to the target. Never null.
     * @param manager Module of the file. Never null.
     * @param lines   Receives the lines. Never null.
     */
    private static void collectAdds(ParsedDropFile.Drops drops, DropManager<?> manager, List<Component> lines) {
        DropFile file = drops.file();
        for (DropEntry entry : file.entries()) {
            boolean isConditional = file.hasGlobalConditions() || entry.hasConditions();
            Component what = entry.isExperience() ? CommandText.experienceName() : CommandText.itemName(entry.item());
            lines.add(RuleText.fileLine("+ ", ChatFormatting.GREEN, drops, manager)
                    .append(CommandText.text(": ", ChatFormatting.WHITE))
                    .append(what)
                    .append(CommandText.text(" ", ChatFormatting.WHITE))
                    .append(RuleText.describeDrop(entry, manager.format(), isConditional)
                            .withStyle(ChatFormatting.WHITE)));
        }
    }

    /**
     * Adds a line for every entry of a removal file: what it removes.
     *
     * @param removals Removal file that applies to the target. Never null.
     * @param manager  Module of the file. Never null.
     * @param lines    Receives the lines. Never null.
     */
    private static void collectRemovals(
            ParsedDropFile.Removals removals, DropManager<?> manager, List<Component> lines) {
        RemovalRule rule = removals.rule();
        for (RemovalEntry entry : rule.entries()) {
            boolean isConditional = rule.hasGlobalConditions() || entry.hasConditions();
            lines.add(RuleText.fileLine("- ", ChatFormatting.RED, removals, manager)
                    .append(CommandText.text(": ", ChatFormatting.WHITE))
                    .append(CommandText.lang("rule.removes", RuleText.removedThing(entry))
                            .withStyle(ChatFormatting.WHITE))
                    .append(RuleText.removalTrigger(rule.trigger()))
                    .append(
                            isConditional
                                    ? CommandText.text(", ", ChatFormatting.WHITE)
                                            .append(RuleText.conditional())
                                    : CommandText.text("")));
        }
    }
}
