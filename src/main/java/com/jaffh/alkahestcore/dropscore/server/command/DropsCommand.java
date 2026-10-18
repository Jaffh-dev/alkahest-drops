package com.jaffh.alkahestcore.dropscore.server.command;

import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestcore.dropscore.server.drops.DropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.DropManager;
import com.jaffh.alkahestcore.dropscore.server.drops.ParsedDropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalEntry;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalRule;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.MobDropManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /dropscore drops <entity | block> [page <n>]}: every drop and removal entry dropscore applies to one mob or
 * block, from files that name it by id, {@code mod:*}, {@code #tag} or {@code *}. Like {@code sources}, but looked up
 * by target instead of by item. An id naming both a mob and a block (such as {@code minecraft:tnt}) shows both.
 */
final class DropsCommand {
    private static final String TARGET = "target";

    /** Suggests every mob and block that some dropscore file applies to. */
    private static final SuggestionProvider<CommandSourceStack> TARGETS_WITH_RULES =
            TargetLookup.suggestingTables(table -> !table.isEmpty());

    /**
     * Prevents instantiation of this utility class.
     */
    private DropsCommand() {
    }

    /**
     * Builds the {@code drops} node.
     *
     * @return The node.
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("drops")
                .then(PagedOutput.withPages(Commands.argument(TARGET, ResourceLocationArgument.id()).suggests(TARGETS_WITH_RULES),
                        (context, page) -> run(context.getSource(), TargetLookup.of(ResourceLocationArgument.getId(context, TARGET)), page)));
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

        String command = "/dropscore drops " + lookup.id();
        MutableComponent name = nameOf(lookup);
        if (addLines.isEmpty() && removeLines.isEmpty()) {
            Component message = CommandText.text("No dropscore file changes the drops of ", ChatFormatting.GRAY)
                    .append(name).append(CommandText.text(".", ChatFormatting.GRAY));
            PagedOutput.send(source, "DROPS", List.of(message), List.of(), 1, command);
            return 0;
        }

        List<Component> lines = new ArrayList<>(addLines);
        lines.addAll(removeLines);
        Component title = CommandText.text("Drops of ", ChatFormatting.BLUE)
                .append(name)
                .append(CommandText.text(": ", ChatFormatting.YELLOW))
                .append(CommandText.text(String.valueOf(addLines.size()), ChatFormatting.GREEN))
                .append(CommandText.text(addLines.size() == 1 ? " drop, " : " drops, ", ChatFormatting.GRAY))
                .append(CommandText.text(String.valueOf(removeLines.size()), ChatFormatting.RED))
                .append(CommandText.text(removeLines.size() == 1 ? " removal" : " removals", ChatFormatting.GRAY));
        PagedOutput.send(source, "DROPS", List.of(title), lines, page, command);
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
    private static <T extends IForgeRegistryEntry<T>> void collect(DropManager<T> manager, T value,
                                                                   List<Component> addLines, List<Component> removeLines) {
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
            Component what = entry.isExperience()
                    ? CommandText.bold("Experience", ChatFormatting.GREEN)
                    : CommandText.itemName(entry.item());
            lines.add(RuleText.fileLine("+ ", ChatFormatting.GREEN, drops, manager)
                    .append(CommandText.text(": ", ChatFormatting.WHITE))
                    .append(what)
                    .append(CommandText.text(" " + RuleText.describeDrop(entry, manager.format(), isConditional), ChatFormatting.WHITE)));
        }
    }

    /**
     * Adds a line for every entry of a removal file: what it removes.
     *
     * @param removals Removal file that applies to the target. Never null.
     * @param manager  Module of the file. Never null.
     * @param lines    Receives the lines. Never null.
     */
    private static void collectRemovals(ParsedDropFile.Removals removals, DropManager<?> manager, List<Component> lines) {
        RemovalRule rule = removals.rule();
        for (RemovalEntry entry : rule.entries()) {
            boolean isConditional = rule.hasGlobalConditions() || entry.hasConditions();
            lines.add(RuleText.fileLine("- ", ChatFormatting.RED, removals, manager)
                    .append(CommandText.text(": removes ", ChatFormatting.WHITE))
                    .append(RuleText.removedThing(entry))
                    .append(CommandText.text(isConditional ? ", conditional" : "", ChatFormatting.WHITE)));
        }
    }
}
