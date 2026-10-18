package com.jaffh.alkahestcore.dropscore.server.command;

import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.server.drops.DropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.DropManager;
import com.jaffh.alkahestcore.dropscore.server.drops.ParsedDropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalEntry;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalRule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * {@code /dropscore sources <item|experience> [page <n>]}: reverse lookup of every drop file that adds an item (or
 * experience) and every removal file that removes it, with the target of each file.
 */
final class SourcesCommand {

    /**
     * Prevents instantiation of this utility class.
     */
    private SourcesCommand() {
    }

    /**
     * Builds the {@code sources} node.
     *
     * @param buildContext Registry access of the command arguments. Never null.
     * @return The node.
     */
    static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext buildContext) {
        return Commands.literal("sources")
                .then(PagedOutput.withPages(Commands.literal("experience"),
                        (context, page) -> run(context.getSource(), Lookup.EXPERIENCE, page)))
                .then(PagedOutput.withPages(Commands.argument("item", ItemArgument.item(buildContext)),
                        (context, page) -> run(context.getSource(), Lookup.ofItem(ItemArgument.getItem(context, "item").getItem()), page)));
    }

    /**
     * Sends one page of the files that add or remove the looked-up drop.
     *
     * @param source Command source. Never null.
     * @param lookup What to look up. Never null.
     * @param page   Page to send, 1 or more.
     * @return The number of matching drops and removal entries.
     */
    private static int run(CommandSourceStack source, Lookup lookup, int page) {
        List<Component> addLines = new ArrayList<>();
        List<Component> removeLines = new ArrayList<>();
        for (DropManager<?> manager : DropscoreCommand.MANAGERS) {
            for (ParsedDropFile file : manager.report().files()) {
                if (file instanceof ParsedDropFile.Drops drops) {
                    collectAdds(drops, manager, lookup, addLines);
                } else if (file instanceof ParsedDropFile.Removals removals) {
                    collectRemovals(removals, manager, lookup, removeLines);
                }
            }
        }

        if (addLines.isEmpty() && removeLines.isEmpty()) {
            Component message = CommandText.text("No dropscore file adds or removes ", ChatFormatting.GRAY)
                    .append(lookup.name()).append(CommandText.text(".", ChatFormatting.GRAY));
            PagedOutput.send(source, "SOURCES", List.of(message), List.of(), 1, "/dropscore sources " + lookup.argument());
            return 0;
        }

        List<Component> lines = new ArrayList<>(addLines);
        lines.addAll(removeLines);
        Component title = CommandText.text("Sources of ", ChatFormatting.BLUE)
                .append(lookup.name())
                .append(CommandText.text(": ", ChatFormatting.YELLOW))
                .append(CommandText.text(String.valueOf(addLines.size()), ChatFormatting.GREEN))
                .append(CommandText.text(addLines.size() == 1 ? " drop, " : " drops, ", ChatFormatting.GRAY))
                .append(CommandText.text(String.valueOf(removeLines.size()), ChatFormatting.RED))
                .append(CommandText.text(removeLines.size() == 1 ? " removal" : " removals", ChatFormatting.GRAY));
        PagedOutput.send(source, "SOURCES", List.of(title), lines, page, "/dropscore sources " + lookup.argument());
        return lines.size();
    }

    /**
     * Adds a line for every drop of a drop file that gives the looked-up drop.
     *
     * @param drops   Drop file. Never null.
     * @param manager Module of the file. Never null.
     * @param lookup  What to look up. Never null.
     * @param lines   Receives the lines. Never null.
     */
    private static void collectAdds(ParsedDropFile.Drops drops, DropManager<?> manager, Lookup lookup, List<Component> lines) {
        DropFile file = drops.file();
        for (DropEntry entry : file.entries()) {
            if (!lookup.matchesDrop().test(entry)) continue;
            boolean isConditional = file.hasGlobalConditions() || entry.hasConditions();
            lines.add(RuleText.fileLine("+ ", ChatFormatting.GREEN, drops, manager)
                    .append(CommandText.text(": " + RuleText.describeDrop(entry, manager.format(), isConditional), ChatFormatting.WHITE)));
        }
    }

    /**
     * Adds a line for every entry of a removal file that removes the looked-up drop.
     *
     * @param removals Removal file. Never null.
     * @param manager  Module of the file. Never null.
     * @param lookup   What to look up. Never null.
     * @param lines    Receives the lines. Never null.
     */
    private static void collectRemovals(ParsedDropFile.Removals removals, DropManager<?> manager, Lookup lookup, List<Component> lines) {
        RemovalRule rule = removals.rule();
        for (RemovalEntry entry : rule.entries()) {
            if (!lookup.matchesRemoval().test(entry)) continue;
            boolean isConditional = rule.hasGlobalConditions() || entry.hasConditions();
            lines.add(RuleText.fileLine("- ", ChatFormatting.RED, removals, manager)
                    .append(CommandText.text(": removes " + RuleText.describeRemovalMatch(entry) + (isConditional ? ", conditional" : ""), ChatFormatting.WHITE)));
        }
    }

    /**
     * Represents what {@code sources} looks up: an item or experience.
     *
     * @param name           Shown name. Never null.
     * @param argument       Command argument that repeats the lookup. Never null.
     * @param matchesDrop    Tells whether a drop gives it. Never null.
     * @param matchesRemoval Tells whether a removal entry removes it. Never null.
     */
    private record Lookup(Component name, String argument, Predicate<DropEntry> matchesDrop, Predicate<RemovalEntry> matchesRemoval) {
        /** Experience drops and experience removal entries. */
        static final Lookup EXPERIENCE = new Lookup(
                CommandText.bold("experience", ChatFormatting.YELLOW), "experience",
                DropEntry::isExperience, RemovalEntry::isExperience);

        /**
         * Creates the lookup of an item: drops of the item, and removal entries whose id, tag or {@code *} names it.
         *
         * @param item Item to look up. Never null.
         * @return The lookup.
         */
        static Lookup ofItem(Item item) {
            return new Lookup(CommandText.itemName(item), String.valueOf(ForgeRegistries.ITEMS.getKey(item)),
                    entry -> entry.item() == item, entry -> entry.removes(item));
        }
    }
}
