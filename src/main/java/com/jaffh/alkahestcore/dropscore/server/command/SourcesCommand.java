package com.jaffh.alkahestcore.dropscore.server.command;

import com.jaffh.alkahestcore.dropscore.common.drops.DropBehaviorType;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.server.drops.DropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.DropManager;
import com.jaffh.alkahestcore.dropscore.server.drops.ItemTarget;
import com.jaffh.alkahestcore.dropscore.server.drops.ParsedDropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalEntry;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalRule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
     * @return The node.
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("sources")
                .then(PagedOutput.withPages(Commands.literal("experience"),
                        (context, page) -> run(context.getSource(), Lookup.EXPERIENCE, page)))
                .then(PagedOutput.withPages(Commands.argument("item", ItemArgument.item()),
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
            lines.add(fileLine("+ ", ChatFormatting.GREEN, drops, manager)
                    .append(CommandText.text(": " + describeDrop(entry) + (isConditional ? ", conditional" : ""), ChatFormatting.WHITE)));
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
            lines.add(fileLine("- ", ChatFormatting.RED, removals, manager)
                    .append(CommandText.text(": removes " + describeRemoval(entry) + (isConditional ? ", conditional" : ""), ChatFormatting.WHITE)));
        }
    }

    /**
     * Starts a result line with its sign, module, file id and target.
     *
     * @param sign    {@code "+ "} for drops, {@code "- "} for removals. Never null.
     * @param color   Color of the sign. Never null.
     * @param file    File of the line. Never null.
     * @param manager Module of the file. Never null.
     * @return Such as {@code "+ [mob] ns:zombie → minecraft:zombie"}: the module in gray, the file id in yellow and
     *         the target in white.
     */
    private static MutableComponent fileLine(String sign, ChatFormatting color, ParsedDropFile file, DropManager<?> manager) {
        return CommandText.text(sign, color)
                .append(CommandText.text("[" + manager.kind() + "] ", ChatFormatting.GRAY))
                .append(CommandText.fileId(file.fileId(), manager, ChatFormatting.YELLOW))
                .append(CommandText.text(" → " + file.target(), ChatFormatting.WHITE));
    }

    /**
     * Describes the amount, chance and behavior of a drop.
     *
     * @param entry Drop. Never null.
     * @return Such as {@code "1–3 (5%)"}, {@code "3–7 XP (100%), max_health"} or {@code "1 (50%), functions"}.
     */
    private static String describeDrop(DropEntry entry) {
        int min = entry.count().min();
        int max = entry.count().max();
        StringBuilder text = new StringBuilder(min == max ? String.valueOf(min) : min + "–" + max);
        if (entry.isExperience()) text.append(" XP");
        text.append(" (").append(CommandText.percent(entry.chance())).append(")");
        DropBehaviorType type = entry.behavior().type();
        if (type != DropBehaviorType.UNIFORM) text.append(", ").append(type.name().toLowerCase(Locale.ROOT));
        if (entry.hasFunctions()) text.append(", functions");
        return text.toString();
    }

    /**
     * Describes how a removal entry names its target.
     *
     * @param entry Removal entry. Never null.
     * @return Such as {@code "experience"}, {@code "by id"}, {@code "by #minecraft:logs"} or {@code "by *"}.
     */
    private static String describeRemoval(RemovalEntry entry) {
        ItemTarget target = entry.target();
        if (target == null) return "experience";
        if (target instanceof ItemTarget.Tagged tagged) return "by #" + tagged.tag().location();
        if (target instanceof ItemTarget.Any) return "by *";
        return "by id";
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
            return new Lookup(CommandText.itemName(item), String.valueOf(item.getRegistryName()),
                    entry -> entry.item() == item, entry -> entry.removes(item));
        }
    }
}
