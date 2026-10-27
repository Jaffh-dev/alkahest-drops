package com.jaffh.alkahestdrops.server.command;

import com.jaffh.alkahestdrops.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestdrops.server.drops.DropFileFormat;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropManager;
import com.jaffh.alkahestdrops.server.progress.DropProgress;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * {@code /alkdrops reset <player> [first | pity] [entities | blocks | <entity | block>]}: resets the drop progress of
 * players, so one-time drops ({@code first_kill}, {@code first_break}) drop again and pity counters start over.
 * The player is required, as in vanilla admin commands; {@code first} / {@code pity} reset only one kind of
 * progress (both when missing); without a target it resets every drop, {@code entities} / {@code blocks} every mob /
 * block drop, and an id the drops that mob or block can give, including those of tag, {@code mod:*} and {@code *}
 * files, which are then reset for every target they match.
 */
final class ResetCommand {
    private static final String PLAYERS = "player";
    private static final String TARGET = "target";

    /** Suggests every mob and block that has a pity or one-time drop. */
    private static final SuggestionProvider<CommandSourceStack> TRACKED_TARGETS =
            TargetLookup.suggestingTables(table -> !table.progressKeys().isEmpty());

    /**
     * Prevents instantiation of this utility class.
     */
    private ResetCommand() {
    }

    /**
     * Builds the {@code reset} node: the required player, the optional {@code first} / {@code pity} choice, then the
     * optional target.
     *
     * @return The node.
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> players = withTargets(
                Commands.argument(PLAYERS, EntityArgument.players()), ResetMode.ALL);
        for (ResetMode mode : List.of(ResetMode.FIRST, ResetMode.PITY)) {
            players.then(withTargets(Commands.literal(mode.keyword()), mode));
        }
        return Commands.literal("reset").then(players);
    }

    /**
     * Makes a node reset everything, and attaches the optional target: {@code entities}, {@code blocks} or an id.
     *
     * @param node Node to extend. Never null.
     * @param mode Which kind of progress to reset. Never null.
     * @param <T>  Builder type of the node.
     * @return The node.
     */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withTargets(T node, ResetMode mode) {
        return node
                .executes(context -> reset(context, mode, new Selection(CommandText.lang("reset.selection.all"), key -> true)))
                .then(Commands.literal("entities")
                        .executes(context -> reset(context, mode, Selection.ofFormat(CommandText.lang("reset.selection.mobs"), DropFileFormat.MOB))))
                .then(Commands.literal("blocks")
                        .executes(context -> reset(context, mode, Selection.ofFormat(CommandText.lang("reset.selection.blocks"), DropFileFormat.BLOCK))))
                .then(Commands.argument(TARGET, ResourceLocationArgument.id())
                        .suggests(TRACKED_TARGETS)
                        .executes(context -> reset(context, mode,
                                Selection.ofTarget(ResourceLocationArgument.getId(context, TARGET)))));
    }

    /**
     * Resets the selected progress of every target player and reports the summed counts.
     *
     * @param context   Command context, with the player argument. Never null.
     * @param mode      Which kind of progress to reset. Never null.
     * @param selection Which progress keys to reset. Never null.
     * @return The number of one-time drops and pity counters reset, summed over the players.
     * @throws CommandSyntaxException If the player argument matches nobody.
     */
    private static int reset(CommandContext<CommandSourceStack> context, ResetMode mode, Selection selection) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, PLAYERS);
        Predicate<String> keys = selection.keys();
        int firstKills = 0;
        int firstBreaks = 0;
        int pityCounters = 0;
        for (ServerPlayer player : targets) {
            DropProgress progress = DropProgress.of(player);
            if (progress == null) continue;
            if (mode.resetsFirstDrops()) {
                firstKills += progress.resetFirstDrops(keys.and(DropFileFormat.MOB::ownsProgressKey));
                firstBreaks += progress.resetFirstDrops(keys.and(DropFileFormat.BLOCK::ownsProgressKey));
            }
            if (mode.resetsPity()) pityCounters += progress.resetMisses(keys);
        }
        Component who = targets.size() == 1
                ? CommandText.text(targets.iterator().next().getGameProfile().getName())
                : CommandText.lang("reset.players", targets.size());
        MutableComponent message = message(selection.label(), who, firstKills, firstBreaks, pityCounters);
        context.getSource().sendSuccess(() -> message, true);
        return firstKills + firstBreaks + pityCounters;
    }

    /**
     * Builds the report of a reset, naming only the counts above 0.
     *
     * @param label        What was reset, such as {@code minecraft:zombie}. Never null.
     * @param who          Name of the reset player, or such as "3 players". Never null.
     * @param firstKills   One-time mob drops reset.
     * @param firstBreaks  One-time block drops reset.
     * @param pityCounters Pity counters reset.
     * @return Such as "Reset 1 first kill and 2 pity counters for minecraft:zombie of Steve.", or
     *         "Nothing to reset ..." when every count is 0.
     */
    private static MutableComponent message(Component label, Component who, int firstKills, int firstBreaks, int pityCounters) {
        List<MutableComponent> parts = new ArrayList<>();
        if (firstKills > 0) parts.add(CommandText.plural("count.first_kill", firstKills));
        if (firstBreaks > 0) parts.add(CommandText.plural("count.first_break", firstBreaks));
        if (pityCounters > 0) parts.add(CommandText.plural("count.pity_counter", pityCounters));

        Component shownLabel = label.copy().withStyle(ChatFormatting.YELLOW);
        Component shownWho = who.copy().withStyle(ChatFormatting.YELLOW);
        if (parts.isEmpty()) {
            return CommandText.lang("reset.nothing", shownLabel, shownWho).withStyle(ChatFormatting.GRAY);
        }
        MutableComponent list = CommandText.text("");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0 && i == parts.size() - 1) list.append(" ").append(CommandText.lang("reset.and")).append(" ");
            else if (i > 0) list.append(", ");
            list.append(parts.get(i).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        }
        return CommandText.lang("reset.done", list, shownLabel, shownWho).withStyle(ChatFormatting.GRAY);
    }

    /**
     * Represents which progress keys a reset clears.
     *
     * @param label What is reset, for the report. Never null.
     * @param keys  Which progress keys to reset. Never null.
     */
    private record Selection(Component label, Predicate<String> keys) {
        /**
         * Selects every drop of one module.
         *
         * @param label  What is reset, for the report. Never null.
         * @param format Format of the module's files. Never null.
         * @return The selection.
         */
        static Selection ofFormat(Component label, DropFileFormat format) {
            return new Selection(label, format::ownsProgressKey);
        }

        /**
         * Selects the drops a mob or block can give. An id naming both a mob and a block (such as
         * {@code minecraft:tnt}) selects the drops of both.
         *
         * @param id Id of the mob or block. Never null.
         * @return The selection.
         * @throws CommandSyntaxException If no mob or block has this id.
         */
        static Selection ofTarget(ResourceLocation id) throws CommandSyntaxException {
            TargetLookup lookup = TargetLookup.of(id);
            Set<String> keys = new HashSet<>();
            if (lookup.entity() != null) keys.addAll(MobDropManager.INSTANCE.triggerTablesOf(lookup.entity()).progressKeys());
            if (lookup.block() != null) keys.addAll(BlockDropManager.INSTANCE.triggerTablesOf(lookup.block()).progressKeys());
            return new Selection(CommandText.text(id.toString()), keys::contains);
        }
    }

    /**
     * Defines which kind of progress a reset clears.
     */
    private enum ResetMode {
        /** One-time drops and pity counters. */
        ALL(true, true),
        /** Only one-time drops ({@code first_kill}, {@code first_break}). */
        FIRST(true, false),
        /** Only pity counters. */
        PITY(false, true);

        private final boolean isFirstDropsReset;
        private final boolean isPityReset;

        /**
         * Creates a mode.
         *
         * @param isFirstDropsReset Whether one-time drops are reset.
         * @param isPityReset       Whether pity counters are reset.
         */
        ResetMode(boolean isFirstDropsReset, boolean isPityReset) {
            this.isFirstDropsReset = isFirstDropsReset;
            this.isPityReset = isPityReset;
        }

        /**
         * Tells whether one-time drops are reset.
         *
         * @return True for {@link #ALL} and {@link #FIRST}.
         */
        boolean resetsFirstDrops() {
            return isFirstDropsReset;
        }

        /**
         * Tells whether pity counters are reset.
         *
         * @return True for {@link #ALL} and {@link #PITY}.
         */
        boolean resetsPity() {
            return isPityReset;
        }

        /**
         * Returns the literal of the mode in the command.
         *
         * @return {@code first} or {@code pity} ({@code all} is never typed).
         */
        String keyword() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
