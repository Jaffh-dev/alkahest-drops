package com.jaffh.alkahestcore.dropscore.server.command;

import com.jaffh.alkahestcore.dropscore.common.drops.DropTracking;
import com.jaffh.alkahestcore.dropscore.server.blockdrops.BlockDropSimulator;
import com.jaffh.alkahestcore.dropscore.server.drops.IDropSimulator;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationActor;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationFlag;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationSetup;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.MobDropSimulator;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntitySummonArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * {@code /dropscore simulate entity <type> [kills] [looting] [by player|none|<entity>] [sneaking] [burning]
 * [only <filter>] [drop [no-text]]} and {@code simulate block <block> [breaks] [fortune] [by player|none|explosion]
 * [sneaking] [only <filter>] [drop [no-text]]}: rolls the vanilla loot table and the dropscore rules many times without killing
 * or breaking anything and reports, per item, the total, the average per run and the share of runs that dropped it,
 * plus the experience. Options are optional but keep this order; the run count defaults to 1, and the enchantment
 * level only follows a run count, so a lone number is always the count. {@code only} shows just the items an item id or
 * {@code #tag} matches, or just the experience; {@code drop} also drops what is shown at the command's position, and
 * {@code drop no-text} only drops, without any chat output (for datapack functions).
 * The last report of each command source is kept for {@code /dropscore simulate page <n>}, so paging never re-rolls.
 */
final class SimulateCommand {
    private static final int MAX_RUNS = 1_000_000;
    private static final int MAX_ENCHANT_LEVEL = 255;
    private static final String TITLE = "SIMULATION";
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
     * @return The node.
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return PagedOutput.withPages(Commands.literal("simulate"), (context, page) -> showPage(context.getSource(), page))
                .then(Commands.literal("entity")
                        .then(continueFrom(Commands.argument("entity", EntitySummonArgument.id())
                                .suggests(SuggestionProviders.SUMMONABLE_ENTITIES), Kind.ENTITY, 0)))
                .then(Commands.literal("block")
                        .then(continueFrom(Commands.argument("block", BlockStateArgument.block()), Kind.BLOCK, 0)));
    }

    /**
     * Makes a node run the simulation and lets every later stage follow it, so each stage is optional while the
     * order stays fixed. A stage that needs the previous one only follows the node ending that stage.
     *
     * @param node       Node ending a stage, or the target node. Never null.
     * @param kind       Mob or block simulation. Never null.
     * @param firstStage Index of the first stage that may follow, in {@link Kind#stages}.
     * @return The node.
     */
    private static ArgumentBuilder<CommandSourceStack, ?> continueFrom(ArgumentBuilder<CommandSourceStack, ?> node, Kind kind, int firstStage) {
        node.executes(context -> simulate(context, kind));
        for (int i = firstStage; i < kind.stages.size(); i++) {
            Stage stage = kind.stages.get(i);
            if (stage.needsPreviousStage && i != firstStage) continue;
            int nextStage = i + 1;
            node.then(stage.create(kind, leaf -> continueFrom(leaf, kind, nextStage)));
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
        SimulationSetup setup = new SimulationSetup(source.getLevel(), source.getPosition(),
                readActor(context, usedNodes), readFlags(usedNodes), readLevel(context, kind, usedNodes));

        IDropSimulator simulator;
        try {
            simulator = kind.factory.create(context, setup);
        } catch (IllegalArgumentException e) {
            source.sendFailure(new TextComponent(e.getMessage()));
            return 0;
        }

        SimulationFilter filter = readFilter(context, usedNodes);
        boolean shouldDrop = usedNodes.contains(DROP_OPTION);
        int runs = usedNodes.contains(kind.runArgument) ? IntegerArgumentType.getInteger(context, kind.runArgument) : DEFAULT_RUNS;
        DropSimulation simulation = DropSimulation.run(simulator, runs, filter, shouldDrop);

        Component dropLine = shouldDrop ? dropResults(setup, simulation) : null;
        if (usedNodes.contains(NO_TEXT)) return runs;

        Report report = buildReport(simulator, simulation, filter, kind);
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
            source.sendFailure(new TextComponent("No simulation to show: run /dropscore simulate entity|block first"));
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
     * Reads who kills or breaks. Without {@code by}, a player source plays itself and the console is nobody.
     *
     * @param context   Command context. Never null.
     * @param usedNodes Names of the used nodes. Never null.
     * @return The actor.
     * @throws CommandSyntaxException If the killer type is not summonable.
     */
    private static SimulationActor readActor(CommandContext<CommandSourceStack> context, Set<String> usedNodes) throws CommandSyntaxException {
        Player sourcePlayer = context.getSource().getEntity() instanceof Player player ? player : null;
        if (usedNodes.contains(KILLER)) {
            return new SimulationActor.AsEntity(ForgeRegistries.ENTITIES.getValue(EntitySummonArgument.getSummonableEntity(context, KILLER)));
        }
        if (usedNodes.contains(EXPLOSION)) return new SimulationActor.Explosion();
        if (usedNodes.contains(NOBODY)) return new SimulationActor.Nobody();
        if (usedNodes.contains(PLAYER) || sourcePlayer != null) return new SimulationActor.AsPlayer(sourcePlayer);
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
     * Reads the forced enchantment level.
     *
     * @param context   Command context. Never null.
     * @param kind      Mob or block simulation. Never null.
     * @param usedNodes Names of the used nodes. Never null.
     * @return The level, or empty to read it from the actor's main hand.
     */
    private static OptionalInt readLevel(CommandContext<CommandSourceStack> context, Kind kind, Set<String> usedNodes) {
        if (!usedNodes.contains(kind.levelArgument)) return OptionalInt.empty();
        return OptionalInt.of(IntegerArgumentType.getInteger(context, kind.levelArgument));
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
    private static Report buildReport(IDropSimulator simulator, DropSimulation simulation, SimulationFilter filter, Kind kind) {
        int runs = simulation.runCount();
        List<Component> intro = new ArrayList<>();
        intro.add(CommandText.text("")
                .append(CommandText.text("Simulated ", ChatFormatting.BLUE))
                .append(CommandText.bold(String.valueOf(runs), ChatFormatting.BLUE))
                .append(CommandText.text(" " + kind.runNoun + (runs == 1 ? "" : "s") + " of ", ChatFormatting.BLUE))
                .append(simulator.targetName().copy().withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                .append(CommandText.text(" (" + simulator.describeSetup() + ")", ChatFormatting.GRAY)));
        if (filter.isFiltered()) intro.add(CommandText.text("Showing only " + filter.label(), ChatFormatting.GRAY));

        List<Component> lines = new ArrayList<>();
        if (simulation.isEmpty()) {
            String what = filter.isFiltered() ? "No " + filter.label() : "Nothing";
            lines.add(CommandText.text(what + " dropped in " + CommandText.count(runs, kind.runNoun) + ".", ChatFormatting.GRAY));
        }
        for (DropSimulation.ItemResult result : simulation.itemResults()) {
            Component line = resultLine(CommandText.itemName(result.item()), ChatFormatting.YELLOW,
                    result.total(), result.runsWithDrop(), runs, kind);
            lines.add(result.tracking() == null ? line : line.copy().append(trackingNote(result.tracking(), kind)));
        }
        for (DropSimulation.ExperienceResult result : simulation.experienceResults()) {
            Component line = resultLine(CommandText.bold("Experience", ChatFormatting.GREEN), ChatFormatting.GREEN,
                    result.total(), result.runsWithDrop(), runs, kind);
            lines.add(result.tracking() == null ? line : line.copy().append(trackingNote(result.tracking(), kind)));
        }
        return new Report(intro, lines);
    }

    /**
     * Describes one result: the total in aqua, the name (bold), then the average per run
     * and the share of runs that dropped something, with the values in gray and the words in white.
     *
     * @param name        Item name or {@code Experience}, already colored. Never null.
     * @param nameColor   Color of the name, used for its colon. Never null.
     * @param total       Summed amount over every run.
     * @param runsWithAny Number of runs with at least one.
     * @param runs        Number of runs, 1 or more.
     * @param kind        Mob or block simulation. Never null.
     * @return Such as {@code "[1520 total] Rotten Flesh: 1.52 per kill in 81.3% of kills"}.
     */
    private static Component resultLine(Component name, ChatFormatting nameColor, long total, int runsWithAny, int runs, Kind kind) {
        return CommandText.text("")
                .append(CommandText.text("[" + total + " total] ", ChatFormatting.AQUA))
                .append(name)
                .append(CommandText.text(": ", nameColor))
                .append(CommandText.text(CommandText.number((double) total / runs), ChatFormatting.GRAY))
                .append(CommandText.text(" per " + kind.runNoun + " in ", ChatFormatting.WHITE))
                .append(CommandText.text(CommandText.percent((double) runsWithAny / runs), ChatFormatting.GRAY))
                .append(CommandText.text(" of " + kind.runNoun + "s", ChatFormatting.WHITE));
    }

    /**
     * Describes the pity or one-time drop a result line counts, in green.
     *
     * @param tracking Settings of the drop. Never null.
     * @param kind     Mob or block simulation, which names the runs. Never null.
     * @return Such as {@code " (pity 50 kills)"} or {@code " (first break)"}.
     */
    private static Component trackingNote(DropTracking tracking, Kind kind) {
        String note = tracking.isFirstDrop() ? "first " + kind.runNoun : "pity " + CommandText.count(tracking.pity(), kind.runNoun);
        return CommandText.text(" (" + note + ")", ChatFormatting.GREEN);
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

        return CommandText.text("Dropped " + CommandText.count(itemCount, "item") + " in "
                + CommandText.count(entityCount, "stack") + " and " + experience + " experience at "
                + CommandText.number(origin.x) + ", " + CommandText.number(origin.y) + ", " + CommandText.number(origin.z) + ".",
                ChatFormatting.GREEN);
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
     * Creates the simulator of a command.
     */
    @FunctionalInterface
    private interface SimulatorFactory {
        /**
         * Creates the simulator of the command's target.
         *
         * @param context Command context. Never null.
         * @param setup   Level, position, actor, flags and forced enchantment level. Never null.
         * @return The simulator.
         * @throws CommandSyntaxException   If the target argument is invalid.
         * @throws IllegalArgumentException If the setup cannot be simulated, with a message for the player.
         */
        IDropSimulator create(CommandContext<CommandSourceStack> context, SimulationSetup setup) throws CommandSyntaxException;
    }

    /**
     * Defines the optional stages after the target, in command order. Each stage creates its nodes and hands
     * every node that ends it to a continuation, which attaches the later stages.
     */
    private enum Stage {
        /** {@code <kills>} or {@code <breaks>}; 1 when missing. */
        RUNS(false) {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                return next.apply(Commands.argument(kind.runArgument, IntegerArgumentType.integer(1, MAX_RUNS)));
            }
        },
        /** {@code <looting>} or {@code <fortune>}, only right after the run count so a lone number is always the count. */
        LEVEL(true) {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                return next.apply(Commands.argument(kind.levelArgument, IntegerArgumentType.integer(0, MAX_ENCHANT_LEVEL)));
            }
        },
        /** {@code by <actor>}. */
        BY(false) {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                LiteralArgumentBuilder<CommandSourceStack> by = Commands.literal("by");
                for (ArgumentBuilder<CommandSourceStack, ?> actor : kind.actorNodes.get()) by.then(next.apply(actor));
                return by;
            }
        },
        /** {@code sneaking}. */
        SNEAKING(false) {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                return next.apply(Commands.literal(SimulationFlag.SNEAKING.keyword()));
            }
        },
        /** {@code burning}, mobs only. */
        BURNING(false) {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                return next.apply(Commands.literal(SimulationFlag.BURNING.keyword()));
            }
        },
        /** {@code only experience} or {@code only <item filter>}. */
        ONLY(false) {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                return Commands.literal("only")
                        .then(next.apply(Commands.literal(EXPERIENCE)))
                        .then(next.apply(Commands.argument(FILTER, ItemPredicateArgument.itemPredicate())));
            }
        },
        /** {@code drop}, optionally followed by {@code no-text}. */
        DROP(false) {
            @Override
            ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next) {
                return next.apply(Commands.literal(DROP_OPTION)).then(next.apply(Commands.literal(NO_TEXT)));
            }
        };

        private final boolean needsPreviousStage;

        /**
         * Creates a stage.
         *
         * @param needsPreviousStage True when the stage may only follow the node ending the stage before it.
         */
        Stage(boolean needsPreviousStage) {
            this.needsPreviousStage = needsPreviousStage;
        }

        /**
         * Creates the nodes of this stage.
         *
         * @param kind Mob or block simulation. Never null.
         * @param next Attaches the later stages to a node that ends this stage, and returns it. Never null.
         * @return The first node of this stage.
         */
        abstract ArgumentBuilder<CommandSourceStack, ?> create(Kind kind, UnaryOperator<ArgumentBuilder<CommandSourceStack, ?>> next);
    }

    /**
     * Defines the two kinds of simulation.
     */
    private enum Kind {
        /** Mob kills, with an optional Looting level, a killer and the mob on fire. */
        ENTITY("kill", "looting",
                List.of(Stage.RUNS, Stage.LEVEL, Stage.BY, Stage.SNEAKING, Stage.BURNING, Stage.ONLY, Stage.DROP),
                () -> List.of(Commands.literal(PLAYER), Commands.literal(NOBODY),
                        Commands.argument(KILLER, EntitySummonArgument.id()).suggests(SuggestionProviders.SUMMONABLE_ENTITIES)),
                (context, setup) -> MobDropSimulator.create(
                        ForgeRegistries.ENTITIES.getValue(EntitySummonArgument.getSummonableEntity(context, "entity")), setup)),
        /** Block breaks, with an optional Fortune level and a breaker. */
        BLOCK("break", "fortune",
                List.of(Stage.RUNS, Stage.LEVEL, Stage.BY, Stage.SNEAKING, Stage.ONLY, Stage.DROP),
                () -> List.of(Commands.literal(PLAYER), Commands.literal(NOBODY), Commands.literal(EXPLOSION)),
                (context, setup) -> BlockDropSimulator.create(BlockStateArgument.getBlock(context, "block").getState(), setup));

        private final String runNoun;
        private final String runArgument;
        private final String levelArgument;
        private final List<Stage> stages;
        private final Supplier<List<ArgumentBuilder<CommandSourceStack, ?>>> actorNodes;
        private final SimulatorFactory factory;

        /**
         * Creates a kind.
         *
         * @param runNoun       Word for one run, such as {@code kill}; its plural names the run count argument.
         *                      Never null.
         * @param levelArgument Name of the enchantment level argument. Never null.
         * @param stages        Optional stages, in command order. Never null.
         * @param actorNodes    Creates fresh nodes for the values of {@code by}. Never null.
         * @param factory       Creates the simulator. Never null.
         */
        Kind(String runNoun, String levelArgument, List<Stage> stages,
             Supplier<List<ArgumentBuilder<CommandSourceStack, ?>>> actorNodes, SimulatorFactory factory) {
            this.runNoun = runNoun;
            this.runArgument = runNoun + "s";
            this.levelArgument = levelArgument;
            this.stages = stages;
            this.actorNodes = actorNodes;
            this.factory = factory;
        }
    }
}
