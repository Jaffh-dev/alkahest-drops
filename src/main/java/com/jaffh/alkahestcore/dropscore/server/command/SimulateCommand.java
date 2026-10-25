package com.jaffh.alkahestcore.dropscore.server.command;

import com.jaffh.alkahestcore.dropscore.common.drops.DropTracking;
import com.jaffh.alkahestcore.dropscore.common.drops.DropTrigger;
import com.jaffh.alkahestcore.dropscore.server.blockdrops.BlockDropSimulator;
import com.jaffh.alkahestcore.dropscore.server.drops.IDropSimulator;
import com.jaffh.alkahestcore.dropscore.server.drops.InvalidSimulationException;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationActor;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationFlag;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationSetup;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.MobDropSimulator;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.MobTriggerSimulator;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.NbtTagArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * {@code /dropscore simulate entity <type> [on <trigger>] [kills] [enchant <enchantments>] [by player|none|<entity>]
 * [sneaking] [burning] [only <filter>] [drop [no-text]]} and {@code simulate block <block> [breaks]
 * [enchant <enchantments>] [by player|none|explosion] [sneaking] [only <filter>] [drop [no-text]]}: rolls the vanilla
 * loot table and the dropscore rules many times without killing or breaking anything and reports, per item, the total,
 * the average per run and the share of runs that dropped it, plus the experience. Options are optional but keep this
 * order; the run count defaults to 1. {@code enchant} takes enchantments in the item NBT format, such as
 * {@code [{id:"minecraft:looting",lvl:3}]}, and replaces those levels of the held item. {@code only} shows just the items an item id or
 * {@code #tag} matches, or just the experience; {@code drop} also drops what is shown at the command's position, and
 * {@code drop no-text} only drops, without any chat output (for datapack functions).
 * The last report of each command source is kept for {@code /dropscore simulate page <n>}, so paging never re-rolls.
 */
final class SimulateCommand {
    private static final int MAX_RUNS = 1_000_000;
    private static final int MAX_ENCHANT_LEVEL = 255;
    private static final Component TITLE = CommandText.lang("simulate.title");
    private static final String PAGE_COMMAND = "/dropscore simulate";
    private static final int DEFAULT_RUNS = 1;
    private static final String KILLER = "killer";
    private static final String FILTER = "filter";
    private static final String PLAYER = "player";
    private static final String NOBODY = "none";
    private static final String EXPLOSION = "explosion";
    private static final String EXPERIENCE = "experience";
    private static final String DROP_OPTION = "drop";
    private static final String NO_TEXT = "no-text";
    private static final String TRIGGER = "trigger";
    private static final String ENCHANTMENTS = "enchantments";
    private static final String ENCHANTMENT_ID_KEY = "id";
    private static final String ENCHANTMENT_LEVEL_KEY = "lvl";

    /** Suggests every trigger id. */
    private static final SuggestionProvider<CommandSourceStack> TRIGGERS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(DropTrigger.values()).map(DropTrigger::id), builder);
    private static final DynamicCommandExceptionType UNKNOWN_TRIGGER = new DynamicCommandExceptionType(
            id -> CommandText.lang("simulate.unknown_trigger", id));
    private static final SimpleCommandExceptionType BAD_ENCHANTMENTS = new SimpleCommandExceptionType(
            CommandText.lang("simulate.enchant.format"));
    private static final DynamicCommandExceptionType UNKNOWN_ENCHANTMENT = new DynamicCommandExceptionType(
            id -> CommandText.lang("simulate.enchant.unknown", id));
    private static final DynamicCommandExceptionType DUPLICATE_ENCHANTMENT = new DynamicCommandExceptionType(
            id -> CommandText.lang("simulate.enchant.duplicate", id));
    private static final DynamicCommandExceptionType BAD_ENCHANTMENT_LEVEL = new DynamicCommandExceptionType(
            id -> CommandText.lang("simulate.enchant.level", id, MAX_ENCHANT_LEVEL));

    /** Last report of each command source, by source name; server thread only. */
    private static final Map<String, Report> LAST_REPORTS = new HashMap<>();

    /**
     * Prevents instantiation of this utility class.
     */
    private SimulateCommand() {
    }

    /**
     * Builds the {@code simulate} node.
     *
     * @param buildContext Registry access of the command arguments. Never null.
     * @return The node.
     */
    static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext buildContext) {
        return PagedOutput.withPages(Commands.literal("simulate"), (context, page) -> showPage(context.getSource(), page))
                .then(Commands.literal("entity")
                        .then(continueFrom(entityArgument("entity", buildContext), Kind.ENTITY, 0, buildContext)))
                .then(Commands.literal("block")
                        .then(continueFrom(Commands.argument("block", BlockStateArgument.block(buildContext)), Kind.BLOCK, 0, buildContext)));
    }

    /**
     * Creates an argument node of a summonable entity type, suggesting the summonable types.
     *
     * @param name         Name of the argument. Never null.
     * @param buildContext Registry access of the command arguments. Never null.
     * @return The node.
     */
    private static ArgumentBuilder<CommandSourceStack, ?> entityArgument(String name, CommandBuildContext buildContext) {
        return Commands.argument(name, ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                .suggests(SuggestionProviders.SUMMONABLE_ENTITIES);
    }

    /**
     * Makes a node run the simulation and lets every later stage follow it, so each stage is optional while the
     * order stays fixed.
     *
     * @param node         Node ending a stage, or the target node. Never null.
     * @param kind         Mob or block simulation. Never null.
     * @param firstStage   Index of the first stage that may follow, in {@link Kind#stages}.
     * @param buildContext Registry access of the command arguments. Never null.
     * @return The node.
     */
    private static ArgumentBuilder<CommandSourceStack, ?> continueFrom(ArgumentBuilder<CommandSourceStack, ?> node, Kind kind,
                                                                       int firstStage, CommandBuildContext buildContext) {
        node.executes(context -> simulate(context, kind));
        for (int i = firstStage; i < kind.stages.size(); i++) {
            Stage stage = kind.stages.get(i);
            int nextStage = i + 1;
            node.then(stage.create(kind, buildContext, leaf -> continueFrom(leaf, kind, nextStage, buildContext)));
        }
        return node;
    }

    /**
     * Runs the simulation, drops the results when asked, then sends the first page of its report and keeps the
     * report for the other pages; with {@code no-text}, nothing is sent or kept.
     *
     * @param context Command context. Never null.
     * @param kind    Mob or block simulation. Never null.
     * @return The number of runs, 0 when the setup was refused.
     * @throws CommandSyntaxException If an argument is invalid.
     */
    private static int simulate(CommandContext<CommandSourceStack> context, Kind kind) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Set<String> usedNodes = usedNodeNames(context);
        DropTrigger trigger = readTrigger(context, usedNodes);
        SimulationSetup setup = new SimulationSetup(source.getLevel(), source.getPosition(),
                readActor(context, usedNodes, trigger), readFlags(usedNodes), readEnchantments(context, usedNodes));

        IDropSimulator simulator;
        try {
            simulator = kind.factory.create(context, setup, trigger);
        } catch (InvalidSimulationException e) {
            source.sendFailure(e.text());
            return 0;
        }

        SimulationFilter filter = readFilter(context, usedNodes);
        boolean shouldDrop = usedNodes.contains(DROP_OPTION);
        int runs = usedNodes.contains(kind.runArgument) ? IntegerArgumentType.getInteger(context, kind.runArgument) : DEFAULT_RUNS;
        DropSimulation simulation = DropSimulation.run(simulator, runs, filter, shouldDrop);

        Component dropLine = shouldDrop ? dropResults(setup, simulation) : null;
        if (usedNodes.contains(NO_TEXT)) return runs;

        Report report = buildReport(simulator, simulation, filter);
        if (dropLine != null) report.intro().add(dropLine);
        LAST_REPORTS.put(source.getTextName(), report);
        PagedOutput.send(source, TITLE, report.intro(), report.lines(), 1, PAGE_COMMAND);
        return runs;
    }

    /**
     * Sends a page of the last report of the command source.
     *
     * @param source Command source. Never null.
     * @param page   Page to send, 1 or more.
     * @return 1 when a report was shown, 0 when there is none.
     */
    private static int showPage(CommandSourceStack source, int page) {
        Report report = LAST_REPORTS.get(source.getTextName());
        if (report == null) {
            source.sendFailure(CommandText.lang("simulate.no_report"));
            return 0;
        }
        PagedOutput.send(source, TITLE, report.intro(), report.lines(), page, PAGE_COMMAND);
        return 1;
    }

    /**
     * Collects the names of the nodes the command went through, to tell which optional stages were used.
     *
     * @param context Command context. Never null.
     * @return The node names, such as {@code runs}, {@code by}, {@code none} or {@code drop}.
     */
    private static Set<String> usedNodeNames(CommandContext<CommandSourceStack> context) {
        Set<String> names = new HashSet<>();
        for (ParsedCommandNode<CommandSourceStack> node : context.getNodes()) {
            names.add(node.getNode().getName());
        }
        return names;
    }

    /**
     * Reads the trigger of a mob simulation.
     *
     * @param context   Command context. Never null.
     * @param usedNodes Names of the used nodes. Never null.
     * @return The trigger after {@code on}, death when missing.
     * @throws CommandSyntaxException If the trigger id is unknown.
     */
    private static DropTrigger readTrigger(CommandContext<CommandSourceStack> context, Set<String> usedNodes) throws CommandSyntaxException {
        if (!usedNodes.contains(TRIGGER)) return DropTrigger.DEATH;
        String id = StringArgumentType.getString(context, TRIGGER);
        return DropTrigger.fromId(id).orElseThrow(() -> UNKNOWN_TRIGGER.create(id));
    }

    /**
     * Reads who kills or breaks. Without {@code by}, a player source plays itself and the console is nobody; on a
     * trigger without player (lightning, periodic), nobody acts.
     *
     * @param context   Command context. Never null.
     * @param usedNodes Names of the used nodes. Never null.
     * @param trigger   Simulated trigger. Never null.
     * @return The actor.
     * @throws CommandSyntaxException If the killer type is not summonable.
     */
    private static SimulationActor readActor(CommandContext<CommandSourceStack> context, Set<String> usedNodes,
                                             DropTrigger trigger) throws CommandSyntaxException {
        Player sourcePlayer = context.getSource().getEntity() instanceof Player player ? player : null;
        if (usedNodes.contains(KILLER)) {
            return new SimulationActor.AsEntity(ResourceArgument.getSummonableEntityType(context, KILLER).value());
        }
        if (usedNodes.contains(EXPLOSION)) return new SimulationActor.Explosion();
        if (usedNodes.contains(NOBODY)) return new SimulationActor.Nobody();
        if (usedNodes.contains(PLAYER) || (sourcePlayer != null && trigger.hasPlayer())) {
            return new SimulationActor.AsPlayer(sourcePlayer);
        }
        return new SimulationActor.Nobody();
    }

    /**
     * Reads the forced conditions.
     *
     * @param usedNodes Names of the used nodes. Never null.
     * @return The flags whose keyword was used.
     */
    private static Set<SimulationFlag> readFlags(Set<String> usedNodes) {
        Set<SimulationFlag> flags = EnumSet.noneOf(SimulationFlag.class);
        for (SimulationFlag flag : SimulationFlag.values()) {
            if (usedNodes.contains(flag.keyword())) flags.add(flag);
        }
        return flags;
    }

    /**
     * Reads the forced enchantments: a list of {@code {id:"<enchantment>",lvl:<level>}} compounds, the format items
     * store their enchantments in.
     *
     * @param context   Command context. Never null.
     * @param usedNodes Names of the used nodes. Never null.
     * @return The levels in command order, empty to read every level from the actor's main hand.
     * @throws CommandSyntaxException If the tag is not such a list, an id is unknown or repeated, or a level is out of
     *                                range.
     */
    private static Map<Enchantment, Integer> readEnchantments(CommandContext<CommandSourceStack> context, Set<String> usedNodes)
            throws CommandSyntaxException {
        if (!usedNodes.contains(ENCHANTMENTS)) return Map.of();
        if (!(NbtTagArgument.getNbtTag(context, ENCHANTMENTS) instanceof ListTag list)) throw BAD_ENCHANTMENTS.create();
        Map<Enchantment, Integer> levels = new LinkedHashMap<>();
        for (Tag element : list) {
            if (!(element instanceof CompoundTag compound) || !compound.contains(ENCHANTMENT_ID_KEY, Tag.TAG_STRING)
                    || !compound.contains(ENCHANTMENT_LEVEL_KEY, Tag.TAG_ANY_NUMERIC)) {
                throw BAD_ENCHANTMENTS.create();
            }
            String id = compound.getString(ENCHANTMENT_ID_KEY);
            ResourceLocation location = ResourceLocation.tryParse(id);
            Enchantment enchantment = location != null && ForgeRegistries.ENCHANTMENTS.containsKey(location)
                    ? ForgeRegistries.ENCHANTMENTS.getValue(location) : null;
            if (enchantment == null) throw UNKNOWN_ENCHANTMENT.create(id);
            int level = compound.getInt(ENCHANTMENT_LEVEL_KEY);
            if (level < 0 || level > MAX_ENCHANT_LEVEL) throw BAD_ENCHANTMENT_LEVEL.create(id);
            if (levels.putIfAbsent(enchantment, level) != null) throw DUPLICATE_ENCHANTMENT.create(id);
        }
        return levels;
    }

    /**
     * Reads what to show: everything, only the experience, or only the items of a filter labeled as typed.
     *
     * @param context   Command context. Never null.
     * @param usedNodes Names of the used nodes. Never null.
     * @return The filter.
     * @throws CommandSyntaxException If the filter names an unknown tag.
     */
    private static SimulationFilter readFilter(CommandContext<CommandSourceStack> context, Set<String> usedNodes) throws CommandSyntaxException {
        if (usedNodes.contains(EXPERIENCE)) return SimulationFilter.EXPERIENCE;
        if (!usedNodes.contains(FILTER)) return SimulationFilter.ALL;

        String label = FILTER;
        for (ParsedCommandNode<CommandSourceStack> node : context.getNodes()) {
            if (node.getNode().getName().equals(FILTER)) label = node.getRange().get(context.getInput());
        }
        return SimulationFilter.ofItems(label, ItemPredicateArgument.getItemPredicate(context, FILTER));
    }

    /**
     * Builds the report: the header lines, then one line per shown item and per experience line; pity and one-time drops get lines of their own.
     *
     * @param simulator  Simulator that ran. Never null.
     * @param simulation Counts of the runs. Never null.
     * @param filter     Filter of the command. Never null.
     * @param kind       Mob or block simulation. Never null.
     * @return The report, with a mutable intro.
     */
    private static Report buildReport(IDropSimulator simulator, DropSimulation simulation, SimulationFilter filter) {
        String runNoun = simulator.runNoun();
        int runs = simulation.runCount();
        RunInfo runInfo = new RunInfo(runs, simulator);
        List<Component> intro = new ArrayList<>();
        String headerKey = "simulate.header." + runNoun + (runs == 1 ? ".one" : ".many");
        intro.add(CommandText.lang(headerKey, CommandText.bold(String.valueOf(runs), ChatFormatting.BLUE),
                        simulator.targetName().copy().withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                .withStyle(ChatFormatting.BLUE)
                .append(CommandText.text(" (", ChatFormatting.GRAY).append(simulator.describeSetup()).append(")")));
        if (filter.isFiltered()) intro.add(CommandText.lang("simulate.only", filter.label()).withStyle(ChatFormatting.GRAY));

        List<Component> lines = new ArrayList<>();
        if (simulation.isEmpty()) {
            Component runText = CommandText.plural("count." + runNoun, runs);
            lines.add((filter.isFiltered()
                    ? CommandText.lang("simulate.empty.filtered", filter.label(), runText)
                    : CommandText.lang("simulate.empty.all", runText)).withStyle(ChatFormatting.GRAY));
        }
        for (DropSimulation.ItemResult result : simulation.itemResults()) {
            lines.add(resultLine(CommandText.itemName(result.item()), ChatFormatting.YELLOW,
                    new LineCounts(result.total(), result.runsWithDrop(), result.tracking()), runInfo));
        }
        for (DropSimulation.ExperienceResult result : simulation.experienceResults()) {
            lines.add(resultLine(CommandText.experienceName(), ChatFormatting.GREEN,
                    new LineCounts(result.total(), result.runsWithDrop(), result.tracking()), runInfo));
        }
        return new Report(intro, lines);
    }

    /**
     * Describes one result: the total in aqua, the name (bold), then the average per run and the share of runs that
     * dropped something, with the values in gray and the words in white; a pity, one-time or periodic drop ends with
     * a green note. A periodic drop's rate only counts the ticks its full intervals cover, so drops with different
     * intervals compare right in short simulations.
     *
     * @param name      Item name or {@code Experience}, already colored. Never null.
     * @param nameColor Color of the name, used for its colon. Never null.
     * @param counts    Total, runs with a drop and drop of the line. Never null.
     * @param runInfo   Runs and simulator of the report. Never null.
     * @return Such as "[1520 total] Rotten Flesh: 1.52 per kill in 81.3% of kills" or
     *         "[2 total] Iron Nugget: 8 per minute (every 150 ticks)".
     */
    private static Component resultLine(Component name, ChatFormatting nameColor, LineCounts counts, RunInfo runInfo) {
        int runs = runInfo.runs();
        OptionalInt interval = counts.tracking() != null ? runInfo.simulator().intervalOf(counts.tracking()) : OptionalInt.empty();
        long rateRuns = interval.isPresent() ? runs - runs % interval.getAsInt() : runs;
        double rate = (double) counts.total() * runInfo.simulator().runsPerRate() / Math.max(1, rateRuns);
        MutableComponent line = CommandText.text("")
                .append(CommandText.text("[", ChatFormatting.AQUA).append(CommandText.lang("simulate.total", counts.total())).append("] "))
                .append(name)
                .append(CommandText.text(": ", nameColor))
                .append(CommandText.lang("simulate.rate." + rateNounOf(counts.tracking(), runInfo.simulator()),
                                CommandText.text(CommandText.number(rate), ChatFormatting.GRAY),
                                CommandText.text(CommandText.percent((double) counts.runsWithAny() / runs), ChatFormatting.GRAY))
                        .withStyle(ChatFormatting.WHITE));
        if (interval.isPresent()) {
            return line.append(note(CommandText.lang("simulate.every", interval.getAsInt())));
        }
        DropTracking tracking = counts.tracking();
        if (tracking != null && (tracking.hasPity() || tracking.isFirstDrop())) {
            line.append(trackingNote(tracking, runInfo.simulator().runNoun()));
        }
        return line;
    }

    /**
     * Picks the word of a line's rate key: "damage" for a {@code damage_taken} drop, else the run's word.
     *
     * @param tracking  Drop of the line, or null.
     * @param simulator Simulator that ran. Never null.
     * @return Such as {@code kill}, {@code hit} or {@code damage}.
     */
    private static String rateNounOf(@Nullable DropTracking tracking, IDropSimulator simulator) {
        return tracking != null && simulator.isPerDamage(tracking) ? "damage" : simulator.runNoun();
    }

    /**
     * Describes the pity or one-time drop a result line counts, in green.
     *
     * @param tracking Settings of the drop. Never null.
     * @param runNoun  Word for one run, such as {@code kill}. Never null.
     * @return Such as " (guaranteed within 50 kills)" or " (first break)".
     */
    private static Component trackingNote(DropTracking tracking, String runNoun) {
        Component note = tracking.isFirstDrop()
                ? CommandText.lang("simulate.first." + runNoun)
                : CommandText.lang("simulate.guaranteed", CommandText.plural("count." + runNoun, tracking.pity()));
        return note(note);
    }

    /**
     * Wraps a note of a result line in green parentheses.
     *
     * @param note Note text. Never null.
     * @return Such as " (first kill)".
     */
    private static Component note(Component note) {
        return CommandText.text(" (", ChatFormatting.GREEN).append(note).append(")");
    }

    /**
     * Drops the shown items, split into full stacks, and the shown experience at the command's position.
     *
     * @param setup      Level and position of the simulation. Never null.
     * @param simulation Counts of the runs, with kept stacks. Never null.
     * @return The line telling what was dropped.
     */
    private static Component dropResults(SimulationSetup setup, DropSimulation simulation) {
        ServerLevel level = setup.level();
        Vec3 origin = setup.origin();
        long itemCount = 0;
        int entityCount = 0;
        for (ItemStack kept : simulation.keptStacks()) {
            itemCount += kept.getCount();
            for (int left = kept.getCount(); left > 0; left -= kept.getMaxStackSize()) {
                ItemStack part = kept.copy();
                part.setCount(Math.min(left, kept.getMaxStackSize()));
                level.addFreshEntity(new ItemEntity(level, origin.x, origin.y, origin.z, part));
                entityCount++;
            }
        }
        int experience = (int) Math.min(simulation.experience(), Integer.MAX_VALUE);
        if (experience > 0) ExperienceOrb.award(level, origin, experience);

        return CommandText.lang("simulate.dropped", CommandText.plural("count.item", itemCount),
                CommandText.plural("count.stack", entityCount), experience,
                CommandText.number(origin.x), CommandText.number(origin.y), CommandText.number(origin.z))
                .withStyle(ChatFormatting.GREEN);
    }

    /**
     * Represents a rendered simulation report, kept for paging.
     *
     * @param intro Lines shown on every page. Mutable while the report is built, never null.
     * @param lines Result lines, split into pages. Never null.
     */
    private record Report(List<Component> intro, List<Component> lines) {
    }

    /**
     * Groups what the result lines need to know about the runs.
     *
     * @param runs      Number of runs, 1 or more.
     * @param simulator Simulator that ran: names the runs, the rate unit and the interval of periodic drops. Never null.
     */
    private record RunInfo(int runs, IDropSimulator simulator) {
    }

    /**
     * Groups the counts of one result line.
     *
     * @param total       Summed amount over every run.
     * @param runsWithAny Number of runs with at least one.
     * @param tracking    Drop the line counts on its own (pity, one-time or periodic drops), or null.
     */
    private record LineCounts(long total, int runsWithAny, @Nullable DropTracking tracking) {
    }

    /**
     * Creates the simulator of a command.
     */
    @FunctionalInterface
    private interface SimulatorFactory {
        /**
         * Creates the simulator of the command's target.
         *
         * @param context Command context. Never null.
         * @param setup   Level, position, actor, flags and forced enchantments. Never null.
         * @param trigger Simulated trigger; always death for blocks. Never null.
         * @return The simulator.
         * @throws CommandSyntaxException   If the target argument is invalid.
         * @throws InvalidSimulationException If the setup cannot be simulated, with a message for the player.
         */
        IDropSimulator create(CommandContext<CommandSourceStack> context, SimulationSetup setup, DropTrigger trigger)
                throws CommandSyntaxException;
    }

    /**
     * Defines the optional stages after the target, in command order. Each stage creates its nodes and hands
     * every node that ends it to a continuation, which attaches the later stages.
     */
    private enum Stage {
        /** {@code on <trigger>}, mobs only; death when missing. */
        ON {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, CommandBuildContext buildContext,
                                                          UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                return Commands.literal("on")
                        .then(next.apply(Commands.argument(TRIGGER, StringArgumentType.word()).suggests(TRIGGERS)));
            }
        },
        /** {@code <kills>} or {@code <breaks>}; 1 when missing. */
        RUNS {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, CommandBuildContext buildContext,
                                                          UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                return next.apply(Commands.argument(kind.runArgument, IntegerArgumentType.integer(1, MAX_RUNS)));
            }
        },
        /** {@code enchant <enchantments>}, in the item NBT format. */
        ENCHANT {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, CommandBuildContext buildContext,
                                                          UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                return Commands.literal("enchant").then(next.apply(Commands.argument(ENCHANTMENTS, NbtTagArgument.nbtTag())));
            }
        },
        /** {@code by <actor>}. */
        BY {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, CommandBuildContext buildContext,
                                                          UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                LiteralArgumentBuilder<CommandSourceStack> by = Commands.literal("by");
                for (ArgumentBuilder<CommandSourceStack, ?> actor : kind.actorNodes.apply(buildContext)) by.then(next.apply(actor));
                return by;
            }
        },
        /** {@code sneaking}. */
        SNEAKING {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, CommandBuildContext buildContext,
                                                          UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                return next.apply(Commands.literal(SimulationFlag.SNEAKING.keyword()));
            }
        },
        /** {@code burning}, mobs only. */
        BURNING {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, CommandBuildContext buildContext,
                                                          UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                return next.apply(Commands.literal(SimulationFlag.BURNING.keyword()));
            }
        },
        /** {@code only experience} or {@code only <item filter>}. */
        ONLY {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, CommandBuildContext buildContext,
                                                          UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                return Commands.literal("only")
                        .then(next.apply(Commands.literal(EXPERIENCE)))
                        .then(next.apply(Commands.argument(FILTER, ItemPredicateArgument.itemPredicate(buildContext))));
            }
        },
        /** {@code drop}, optionally followed by {@code no-text}. */
        DROP {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, CommandBuildContext buildContext,
                                                          UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                return next.apply(Commands.literal(DROP_OPTION)).then(next.apply(Commands.literal(NO_TEXT)));
            }
        };

        /**
         * Creates the nodes of this stage.
         *
         * @param kind         Mob or block simulation. Never null.
         * @param buildContext Registry access of the command arguments. Never null.
         * @param next         Attaches the later stages to a node that ends this stage, and returns it. Never null.
         * @return The first node of this stage.
         */
        abstract ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, CommandBuildContext buildContext,
                                                          UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next);
    }

    /**
     * Defines the two kinds of simulation.
     */
    private enum Kind {
        /** Mob kills or another trigger, with optional enchantments, a killer and the mob on fire. */
        ENTITY("kill",
                List.of(Stage.ON, Stage.RUNS, Stage.ENCHANT, Stage.BY, Stage.SNEAKING, Stage.BURNING, Stage.ONLY, Stage.DROP),
                buildContext -> List.of(Commands.literal(PLAYER), Commands.literal(NOBODY), entityArgument(KILLER, buildContext)),
                (context, setup, trigger) -> {
                    EntityType<?> type = ResourceArgument.getSummonableEntityType(context, "entity").value();
                    return trigger == DropTrigger.DEATH
                            ? MobDropSimulator.create(type, setup)
                            : MobTriggerSimulator.create(type, trigger, setup);
                }),
        /** Block breaks, with optional enchantments and a breaker. */
        BLOCK("break",
                List.of(Stage.RUNS, Stage.ENCHANT, Stage.BY, Stage.SNEAKING, Stage.ONLY, Stage.DROP),
                buildContext -> List.of(Commands.literal(PLAYER), Commands.literal(NOBODY), Commands.literal(EXPLOSION)),
                (context, setup, trigger) -> BlockDropSimulator.create(BlockStateArgument.getBlock(context, "block").getState(), setup));

        private final String runNoun;
        private final String runArgument;
        private final List<Stage> stages;
        private final Function<CommandBuildContext, List<ArgumentBuilder<CommandSourceStack, ?>>> actorNodes;
        private final SimulatorFactory factory;

        /**
         * Creates a kind.
         *
         * @param runNoun       Word for one run, such as {@code kill}; its plural names the run count argument.
         *                      Never null.
         * @param stages        Optional stages, in command order. Never null.
         * @param actorNodes    Creates fresh nodes for the values of {@code by}. Never null.
         * @param factory       Creates the simulator. Never null.
         */
        Kind(String runNoun, List<Stage> stages,
             Function<CommandBuildContext, List<ArgumentBuilder<CommandSourceStack, ?>>> actorNodes, SimulatorFactory factory) {
            this.runNoun = runNoun;
            this.runArgument = runNoun + "s";
            this.stages = stages;
            this.actorNodes = actorNodes;
            this.factory = factory;
        }
    }
}
