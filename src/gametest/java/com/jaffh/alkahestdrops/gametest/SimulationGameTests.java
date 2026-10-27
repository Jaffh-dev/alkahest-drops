package com.jaffh.alkahestdrops.gametest;

import com.jaffh.alkahestdrops.AlkahestDrops;
import com.jaffh.alkahestdrops.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestdrops.server.blockdrops.BlockDropSimulator;
import com.jaffh.alkahestdrops.server.command.DropSimulation;
import com.jaffh.alkahestdrops.server.command.SimulationFilter;
import com.jaffh.alkahestdrops.server.drops.DropIssue;
import com.jaffh.alkahestdrops.server.drops.IDropSimulator;
import com.jaffh.alkahestdrops.server.drops.SimulationActor;
import com.jaffh.alkahestdrops.server.drops.SimulationFlag;
import com.jaffh.alkahestdrops.server.drops.SimulationSetup;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropManager;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropSimulator;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * GameTests for the {@code /alkdrops} commands: the mob and block simulators, the simulation filter, the
 * {@code drop} option and the lookup commands. Rules come from the {@code dstest_*} namespaces, with deterministic
 * rolls.
 */
@GameTestHolder(AlkahestDrops.ID)
@PrefixGameTestTemplate(false)
public class SimulationGameTests {
    private static final int RUNS = 20;
    private static final int EXPLOSION_RUNS = 200;

    /**
     * A husk drops exactly 3 rotten flesh per kill: its vanilla flesh is removed and its own namespace's flesh is
     * kept, while the diamond of another namespace's drop file is removed.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateMobAppliesRemovals(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        DropSimulation simulation = simulateMob(helper, EntityType.HUSK, player, Map.of(), SimulationFilter.ALL);
        assertTotal(simulation, Items.ROTTEN_FLESH, 3L * RUNS);
        assertTotal(simulation, Items.DIAMOND, 0);
        helper.succeed();
    }

    /**
     * A forced Looting level reaches the {@code enchant_bonus} without a killer: 1 + 3 amethyst shards per witch kill.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateMobForcesLooting(GameTestHelper helper) {
        DropSimulation simulation = simulateMob(helper, EntityType.WITCH, null, Map.of(Enchantments.MOB_LOOTING, 3), SimulationFilter.ALL);
        assertTotal(simulation, Items.AMETHYST_SHARD, 4L * RUNS);
        helper.succeed();
    }

    /**
     * A pity drop gets its own result line with its settings: pity 5 with chance 0 drops 4 dragon's breath in 20
     * kills by the simulator's player; drops of other rules have no settings.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulatePityHasOwnLine(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        DropSimulation simulation = simulateMob(helper, EntityType.RABBIT, player, Map.of(), SimulationFilter.ALL);
        DropSimulation.ItemResult breath = singleResult(simulation, Items.DRAGON_BREATH);
        DropTestKit.check(breath.total() == RUNS / 5, "expected " + RUNS / 5 + " dragon's breath, got " + breath.total());
        DropTestKit.check(breath.tracking() != null && breath.tracking().pity() == 5, "dragon's breath line has no pity 5");
        DropTestKit.check(singleResult(simulation, Items.NAUTILUS_SHELL).tracking() == null, "untracked drop has settings");
        helper.succeed();
    }

    /**
     * Experience of a pity drop gets its own line: pity 5 with chance 0 gives 10 experience on 4 of 20 kills, apart
     * from the vanilla experience of the llama.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulatePityExperienceHasOwnLine(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        DropSimulation simulation = simulateMob(helper, EntityType.LLAMA, player, Map.of(), SimulationFilter.ALL);
        List<DropSimulation.ExperienceResult> tracked = simulation.experienceResults().stream()
                .filter(result -> result.tracking() != null).toList();
        DropTestKit.check(tracked.size() == 1, "expected 1 tracked experience line, got " + tracked.size());
        DropTestKit.check(tracked.get(0).total() == 10L * RUNS / 5, "expected " + 10 * RUNS / 5 + " pity experience, got " + tracked.get(0).total());
        DropTestKit.check(tracked.get(0).runsWithDrop() == RUNS / 5, "expected pity experience on " + RUNS / 5 + " kills");
        long plain = simulation.experienceResults().stream().filter(result -> result.tracking() == null)
                .mapToLong(DropSimulation.ExperienceResult::total).sum();
        DropTestKit.check(plain + tracked.get(0).total() == simulation.experience(), "experience lines do not add up to the total");
        DropTestKit.check(plain > 0, "expected the vanilla experience on its own line");
        helper.succeed();
    }

    /**
     * The simulator's player counts as a real player: {@code killed_by_real_player} and {@code broken_by_real_player}
     * pass, while a simulation without a player fails them.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulatedPlayerIsReal(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        assertTotal(simulateMob(helper, EntityType.DONKEY, player, Map.of(), SimulationFilter.ALL), Items.DIAMOND_BLOCK, RUNS);
        assertTotal(simulateMob(helper, EntityType.DONKEY, null, Map.of(), SimulationFilter.ALL), Items.DIAMOND_BLOCK, 0);
        IDropSimulator blockSimulator = BlockDropSimulator.create(Blocks.HAY_BLOCK.defaultBlockState(), setup(helper, player, Map.of()));
        assertTotal(DropSimulation.run(blockSimulator, RUNS, SimulationFilter.ALL, false), Items.DIAMOND_BLOCK, RUNS);
        helper.succeed();
    }

    /**
     * A first-break drop gets its own result line and drops once per simulation.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateFirstBreakHasOwnLine(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        IDropSimulator simulator = BlockDropSimulator.create(Blocks.MELON.defaultBlockState(), setup(helper, player, Map.of()));
        DropSimulation simulation = DropSimulation.run(simulator, RUNS, SimulationFilter.ALL, false);
        DropSimulation.ItemResult totem = singleResult(simulation, Items.TOTEM_OF_UNDYING);
        DropTestKit.check(totem.total() == 1, "expected 1 totem, got " + totem.total());
        DropTestKit.check(totem.tracking() != null && totem.tracking().isFirstDrop(), "totem line is not a first drop");
        helper.succeed();
    }

    /**
     * A pig killed by a player gives exactly 6 experience: vanilla and other namespaces' experience removed, then
     * 0.6 × max health added.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateMobExperience(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        DropSimulation simulation = simulateMob(helper, EntityType.PIG, player, Map.of(), SimulationFilter.ALL);
        DropTestKit.check(simulation.experience() == 6L * RUNS, "expected " + 6 * RUNS + " experience, got " + simulation.experience());
        DropTestKit.check(simulation.runsWithExperience() == RUNS, "expected experience in every run");
        helper.succeed();
    }

    /**
     * Without a killer (console), a mob gives no experience, as in vanilla.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateMobWithoutKillerHasNoExperience(GameTestHelper helper) {
        DropSimulation simulation = simulateMob(helper, EntityType.PIG, null, Map.of(), SimulationFilter.ALL);
        DropTestKit.check(simulation.experience() == 0, "expected no experience, got " + simulation.experience());
        helper.succeed();
    }

    /**
     * A filter shows only the matching item, while the other drops are still rolled.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateFilterShowsOnlyMatches(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        SimulationFilter filter = SimulationFilter.ofItems("minecraft:rotten_flesh", stack -> stack.is(Items.ROTTEN_FLESH));
        DropSimulation simulation = simulateMob(helper, EntityType.HUSK, player, Map.of(), filter);
        DropTestKit.check(simulation.itemResults().size() == 1, "expected only rotten flesh, got " + simulation.itemResults());
        assertTotal(simulation, Items.ROTTEN_FLESH, 3L * RUNS);
        DropTestKit.check(simulation.experience() == 0, "expected the experience to be hidden");
        helper.succeed();
    }

    /**
     * A block broken with a pickaxe drops its loot table and the drop file drop in every run, and kept stacks merge.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateBlockWithPickaxe(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, new ItemStack(Items.IRON_PICKAXE));
        IDropSimulator simulator = BlockDropSimulator.create(Blocks.SANDSTONE.defaultBlockState(), setup(helper, player, Map.of()));
        DropSimulation simulation = DropSimulation.run(simulator, RUNS, SimulationFilter.ALL, true);
        assertTotal(simulation, Items.DIAMOND, RUNS);
        assertTotal(simulation, Items.SANDSTONE, RUNS);
        DropTestKit.check(simulation.keptStacks().size() == 2, "expected two merged stacks, got " + simulation.keptStacks());
        helper.succeed();
    }

    /**
     * A tool that cannot harvest the block drops nothing, as in survival.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateBlockWithoutHarvestTool(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        IDropSimulator simulator = BlockDropSimulator.create(Blocks.SANDSTONE.defaultBlockState(), setup(helper, player, Map.of()));
        DropTestKit.check(DropSimulation.run(simulator, RUNS, SimulationFilter.ALL, false).isEmpty(), "expected no drops by hand");
        helper.succeed();
    }

    /**
     * A forced Fortune level enchants the player's tool: 5 + 2 × 2 experience per bricks break with Fortune II.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateBlockForcesFortune(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, new ItemStack(Items.IRON_PICKAXE));
        IDropSimulator simulator = BlockDropSimulator.create(Blocks.BRICKS.defaultBlockState(), setup(helper, player, Map.of(Enchantments.BLOCK_FORTUNE, 2)));
        DropSimulation simulation = DropSimulation.run(simulator, RUNS, SimulationFilter.EXPERIENCE, false);
        DropTestKit.check(simulation.experience() == 9L * RUNS, "expected " + 9 * RUNS + " experience, got " + simulation.experience());
        DropTestKit.check(simulation.itemResults().isEmpty(), "expected the items to be hidden");
        helper.succeed();
    }

    /**
     * Forced enchantments are put on the tool, so vanilla conditions see them: a Silk Touch break of glass passes the
     * {@code match_tool} condition of its emerald and drops the glass itself.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateBlockForcesAnyEnchantment(GameTestHelper helper) {
        runCommand(helper, "alkdrops simulate block minecraft:glass 3 enchant [{id:\"minecraft:silk_touch\",lvl:1}] by none drop");
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.EMERALD, 3);
            DropTestKit.assertItemCount(helper, Items.GLASS, 3);
            helper.succeed();
        });
    }

    /**
     * Simulated drops spawned by {@code drop} run no on_drop actions: the silverfish's emerald does not glow.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulatedDropsRunNoDropActions(GameTestHelper helper) {
        runCommand(helper, "alkdrops simulate entity minecraft:silverfish 2 by none drop");
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.EMERALD, 2);
            OnDropGameTests.assertGlowing(helper, Items.EMERALD, false);
            helper.succeed();
        });
    }

    /**
     * The report lists the enchantments the runs read, and invalid enchantment lists, unknown ids, repeated ids,
     * levels above 255 and enchantments on a trigger that reads none are refused.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateEnchantClauseChecks(GameTestHelper helper) {
        List<String> report = runCommandCapturing(helper, "alkdrops simulate entity minecraft:witch 2 "
                + "enchant [{id:\"minecraft:looting\",lvl:3},{id:\"minecraft:sharpness\",lvl:5}] by none");
        DropTestKit.check(report.stream().anyMatch(line -> line.contains("enchantments: Looting 3, Sharpness 5")),
                "expected the enchantments in the report, got " + report);
        for (String enchant : List.of("{id:\"minecraft:looting\",lvl:3}", "[{id:\"dstest_missing:luck\",lvl:1}]",
                "[{id:\"minecraft:looting\",lvl:1},{id:\"minecraft:looting\",lvl:2}]", "[{id:\"minecraft:looting\",lvl:300}]")) {
            DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:witch 2 enchant " + enchant) == 0,
                    "expected " + enchant + " to be refused");
        }
        DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:turtle on periodic 20 "
                + "enchant [{id:\"minecraft:looting\",lvl:1}]") == 0, "expected enchantments on periodic to be refused");
        helper.succeed();
    }

    /**
     * Forcing Fortune with an empty hand is refused.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateFortuneNeedsTool(GameTestHelper helper) {
        Player player = DropTestKit.playerHolding(helper, ItemStack.EMPTY);
        try {
            BlockDropSimulator.create(Blocks.BRICKS.defaultBlockState(), setup(helper, player, Map.of(Enchantments.BLOCK_FORTUNE, 1)));
            helper.fail("expected Fortune with an empty hand to be refused");
        } catch (IllegalArgumentException e) {
            helper.succeed();
        }
    }

    /**
     * {@code drop} drops the shown items at the command's position: from the console the tool is a netherite pickaxe,
     * so every sandstone break drops a diamond and a sandstone.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateCommandDrops(GameTestHelper helper) {
        int result = runCommand(helper, "alkdrops simulate block minecraft:sandstone 3 drop");
        DropTestKit.check(result == 3, "expected 3 runs, got " + result);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.DIAMOND, 3);
            DropTestKit.assertItemCount(helper, Items.SANDSTONE, 3);
            helper.succeed();
        });
    }

    /**
     * {@code on shear} rolls the sheep's shear drops: 2 string per run (its death removal does not apply) and no
     * emerald (removed by the shear removal file of another namespace).
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateShearCommand(GameTestHelper helper) {
        int result = runCommand(helper, "alkdrops simulate entity minecraft:sheep on shear 3 by player drop");
        DropTestKit.check(result == 3, "expected 3 runs, got " + result);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.STRING, 6);
            DropTestKit.assertItemCount(helper, Items.EMERALD, 0);
            helper.succeed();
        });
    }

    /**
     * {@code on periodic} counts ticks: in 25 ticks the 10-tick drops roll at ticks 10 and 20, items and experience.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulatePeriodicCommand(GameTestHelper helper) {
        int result = runCommand(helper, "alkdrops simulate entity minecraft:turtle on periodic 25 drop");
        DropTestKit.check(result == 25, "expected 25 ticks, got " + result);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.SLIME_BALL, 2);
            DropTestKit.assertExperience(helper, 6);
            helper.succeed();
        });
    }

    /**
     * Each periodic drop gets its own line, rated by the ticks its full intervals cover: in 20 ticks the 10-tick
     * slime ball drops twice (120 per minute) and the 15-tick clay ball once, over 15 ticks (80 per minute, not 60).
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void periodicRatesFollowEachInterval(GameTestHelper helper) {
        List<String> output = runCommandCapturing(helper, "alkdrops simulate entity minecraft:turtle on periodic 20");
        DropTestKit.check(output.stream().anyMatch(line -> line.contains("Slimeball: 120 per minute (every 10 ticks)")), "slime ball line " + output);
        DropTestKit.check(output.stream().anyMatch(line -> line.contains("Clay Ball: 80 per minute (every 15 ticks)")), "clay ball line " + output);
        helper.succeed();
    }

    /**
     * A simulated hit deals 1 damage, so the vindicator's {@code damage_taken} drop reads 1 gold ingot per damage in
     * every hit, on a line of its own.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void damageTakenReadsPerDamage(GameTestHelper helper) {
        List<String> output = runCommandCapturing(helper, "alkdrops simulate entity minecraft:vindicator on hit 100 by player");
        DropTestKit.check(output.stream().anyMatch(line -> line.contains("1 damage per hit")), "setup line " + output);
        DropTestKit.check(output.stream().anyMatch(line -> line.contains("[100 total] Gold Ingot: 1 per damage in 100% of hits")),
                "gold ingot line " + output);
        helper.succeed();
    }

    /**
     * Setups that do not fit a trigger are refused: shear without a player, {@code by} on a trigger without player,
     * enchantments on lightning, an unknown trigger. Enchantments on hit and shear are accepted, and no {@code on} means death. A blank player's
     * bare-hand hit deals 1 damage, so the vindicator's {@code damage_taken} drop gives 1 gold ingot per hit.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateTriggerSetupChecks(GameTestHelper helper) {
        DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:sheep on shear 2") == 0, "shear without player");
        DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:turtle on periodic 2 by player") == 0, "periodic by player");
        DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:iron_golem on lightning 2 enchant [{id:\"minecraft:looting\",lvl:3}]") == 0,
                "enchantments on lightning");
        DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:sheep on shear 2 enchant [{id:\"minecraft:looting\",lvl:3}] by player") == 2,
                "enchantments on shear");
        DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:sheep on dance 2") == 0, "unknown trigger");
        DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:cave_spider on hit 2 enchant [{id:\"minecraft:looting\",lvl:3}] by player") == 2,
                "enchantments on hit");
        DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:zombie on death 2") == 2, "explicit death");
        DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:vindicator on hit 2 by player drop") == 2, "damage taken");
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.GOLD_INGOT, 2);
            helper.succeed();
        });
    }

    /**
     * {@code only <filter> drop} drops only the items the filter shows.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateCommandDropsOnlyFiltered(GameTestHelper helper) {
        runCommand(helper, "alkdrops simulate block minecraft:sandstone 2 only minecraft:diamond drop");
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.DIAMOND, 2);
            DropTestKit.assertItemCount(helper, Items.SANDSTONE, 0);
            helper.succeed();
        });
    }

    /**
     * A TNT explosion with drop decay on ({@code tntExplosionDropDecay}) has no tool and no player: the vanilla dirt
     * drop survives only 1 time in 4, while the unconditional drop file drop is always there.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateBlockByExplosion(GameTestHelper helper) {
        DropSimulation simulation = simulateDirtExplosions(helper, true);
        assertTotal(simulation, Items.NAUTILUS_SHELL, EXPLOSION_RUNS);
        long dirt = totalOf(simulation, Items.DIRT);
        DropTestKit.check(dirt > 0 && dirt < EXPLOSION_RUNS, "expected some dirt to be destroyed, got " + dirt);
        DropTestKit.check(simulation.experience() == 0, "expected no experience from an explosion");
        helper.succeed();
    }

    /**
     * A TNT explosion with drop decay off, the 1.20.1 default, keeps every vanilla dirt drop, and the drop file drop
     * is still always there.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateBlockByExplosionWithoutDecay(GameTestHelper helper) {
        DropSimulation simulation = simulateDirtExplosions(helper, false);
        assertTotal(simulation, Items.NAUTILUS_SHELL, EXPLOSION_RUNS);
        assertTotal(simulation, Items.DIRT, EXPLOSION_RUNS);
        DropTestKit.check(simulation.experience() == 0, "expected no experience from an explosion");
        helper.succeed();
    }

    /**
     * Simulates TNT explosions of dirt with the {@code tntExplosionDropDecay} game rule set for the run, then restores
     * the rule. The run is synchronous, so no other test sees the changed rule.
     *
     * @param helper    Helper of the test. Never null.
     * @param dropDecay Value of the game rule during the run.
     * @return The counts of the runs.
     */
    private static DropSimulation simulateDirtExplosions(GameTestHelper helper, boolean dropDecay) {
        GameRules.BooleanValue rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY);
        boolean previous = rule.get();
        rule.set(dropDecay, helper.getLevel().getServer());
        try {
            SimulationSetup setup = setup(helper, new SimulationActor.Explosion(), Set.of(), Map.of());
            return DropSimulation.run(BlockDropSimulator.create(Blocks.DIRT.defaultBlockState(), setup),
                    EXPLOSION_RUNS, SimulationFilter.ALL, false);
        } finally {
            rule.set(previous, helper.getLevel().getServer());
        }
    }

    /**
     * Sums the simulated count of one item over all its result lines.
     *
     * @param simulation Counts of the runs. Never null.
     * @param item       Item to sum. Never null.
     * @return The total count.
     */
    private static long totalOf(DropSimulation simulation, Item item) {
        return simulation.itemResults().stream().filter(result -> result.item() == item)
                .mapToLong(DropSimulation.ItemResult::total).sum();
    }

    /**
     * {@code sneaking} makes the fake breaker crouch: the global sneaking condition of a removal file turns off the
     * emerald ore experience, which is there without it.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateBlockSneaking(GameTestHelper helper) {
        SimulationActor actor = new SimulationActor.AsPlayer(DropTestKit.playerHolding(helper, new ItemStack(Items.IRON_PICKAXE)));
        DropSimulation standing = DropSimulation.run(BlockDropSimulator.create(Blocks.EMERALD_ORE.defaultBlockState(),
                setup(helper, actor, Set.of(), Map.of())), RUNS, SimulationFilter.EXPERIENCE, false);
        DropSimulation sneaking = DropSimulation.run(BlockDropSimulator.create(Blocks.EMERALD_ORE.defaultBlockState(),
                setup(helper, actor, Set.of(SimulationFlag.SNEAKING), Map.of())), RUNS, SimulationFilter.EXPERIENCE, false);
        DropTestKit.check(standing.experience() > 0, "expected experience when standing");
        DropTestKit.check(sneaking.experience() == 0, "expected no experience when sneaking, got " + sneaking.experience());
        helper.succeed();
    }

    /**
     * {@code burning} sets the mob on fire, so a {@code furnace_smelt} function gated by {@code is_on_fire} smelts the
     * beef of a goat.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateMobBurning(GameTestHelper helper) {
        SimulationSetup setup = setup(helper, new SimulationActor.Nobody(), Set.of(SimulationFlag.BURNING), Map.of());
        DropSimulation simulation = DropSimulation.run(MobDropSimulator.create(EntityType.GOAT, setup), RUNS, SimulationFilter.ALL, false);
        assertTotal(simulation, Items.COOKED_BEEF, 2L * RUNS);
        assertTotal(simulation, Items.BEEF, 0);
        helper.succeed();
    }

    /**
     * A mob killer is not a player: {@code killed_by_player} fails and no experience drops.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateMobKilledByEntity(GameTestHelper helper) {
        SimulationSetup setup = setup(helper, new SimulationActor.AsEntity(EntityType.WOLF), Set.of(), Map.of());
        DropSimulation simulation = DropSimulation.run(MobDropSimulator.create(EntityType.CREEPER, setup), RUNS, SimulationFilter.ALL, false);
        assertTotal(simulation, Items.EMERALD, 0);
        DropTestKit.check(simulation.experience() == 0, "expected no experience without a player killer");
        helper.succeed();
    }

    /**
     * A blank fake player from the console counts as a player killer: {@code killed_by_player} passes.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateMobKilledByBlankPlayer(GameTestHelper helper) {
        SimulationSetup setup = setup(helper, new SimulationActor.AsPlayer(null), Set.of(), Map.of());
        DropSimulation simulation = DropSimulation.run(MobDropSimulator.create(EntityType.CREEPER, setup), RUNS, SimulationFilter.ALL, false);
        assertTotal(simulation, Items.EMERALD, RUNS);
        helper.succeed();
    }

    /**
     * {@code sneaking} without a killer is refused.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateSneakingNeedsKiller(GameTestHelper helper) {
        try {
            MobDropSimulator.create(EntityType.SHEEP, setup(helper, new SimulationActor.Nobody(), Set.of(SimulationFlag.SNEAKING), Map.of()));
            helper.fail("expected sneaking without a killer to be refused");
        } catch (IllegalArgumentException e) {
            helper.succeed();
        }
    }

    /**
     * The full block option chain parses: {@code by explosion}, then {@code only} and {@code drop}.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateCommandByExplosion(GameTestHelper helper) {
        runCommand(helper, "alkdrops simulate block minecraft:dirt 4 by explosion only minecraft:nautilus_shell drop");
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.NAUTILUS_SHELL, 4);
            DropTestKit.assertItemCount(helper, Items.DIRT, 0);
            helper.succeed();
        });
    }

    /**
     * The killer and flag options parse for mobs: a sneaking blank player kills burning goats.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateCommandWithFlags(GameTestHelper helper) {
        runCommand(helper, "alkdrops simulate entity minecraft:goat 3 enchant [{id:\"minecraft:looting\",lvl:0}] by player sneaking burning drop");
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.COOKED_BEEF, 6);
            helper.succeed();
        });
    }

    /**
     * {@code drop no-text} drops the items like {@code drop}, without chat output.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateCommandDropsWithoutText(GameTestHelper helper) {
        int result = runCommand(helper, "alkdrops simulate block minecraft:sandstone 2 only minecraft:diamond drop no-text");
        DropTestKit.check(result == 2, "expected 2 runs, got " + result);
        helper.runAfterDelay(1, () -> {
            DropTestKit.assertItemCount(helper, Items.DIAMOND, 2);
            helper.succeed();
        });
    }

    /**
     * {@code validate errors <namespace>} reports only the errors of that namespace.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void validateFiltersBySeverity(GameTestHelper helper) {
        long expected = Stream.concat(MobDropManager.getIssues().stream(), BlockDropManager.getIssues().stream())
                .filter(issue -> issue.fileId().getNamespace().equals("dstest_c") && issue.severity() == DropIssue.Severity.ERROR)
                .count();
        int result = runCommand(helper, "alkdrops validate errors dstest_c");
        DropTestKit.check(expected > 0 && result == expected, "expected " + expected + " errors, got " + result);
        helper.succeed();
    }

    /**
     * {@code validate <namespace>} reports every problem of that namespace.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void validateCountsNamespaceIssues(GameTestHelper helper) {
        long expected = Stream.concat(MobDropManager.getIssues().stream(), BlockDropManager.getIssues().stream())
                .filter(issue -> issue.fileId().getNamespace().equals("dstest_c"))
                .count();
        int result = runCommand(helper, "alkdrops validate dstest_c");
        DropTestKit.check(expected > 0 && result == expected, "expected " + expected + " problems, got " + result);
        helper.succeed();
    }

    /**
     * {@code sources <item>} finds the drop files that add the item and the removal files that remove it.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void sourcesFindsAddsAndRemovals(GameTestHelper helper) {
        int result = runCommand(helper, "alkdrops sources minecraft:diamond");
        DropTestKit.check(result >= 3, "expected at least the husk drop, the husk removal and the sandstone drop, got " + result);
        DropTestKit.check(runCommand(helper, "alkdrops sources experience") > 0, "expected experience sources");
        helper.succeed();
    }

    /**
     * {@code files <namespace>} lists the loaded and skipped files of the namespace.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void filesListsNamespace(GameTestHelper helper) {
        long expected = Stream.of(MobDropManager.INSTANCE.report(), BlockDropManager.INSTANCE.report())
                .mapToLong(report -> report.skippedFileIds().stream().filter(id -> id.getNamespace().equals("dstest_c")).count()
                        + report.files().stream().filter(file -> file.fileId().getNamespace().equals("dstest_c")).count())
                .sum();
        int result = runCommand(helper, "alkdrops files dstest_c");
        DropTestKit.check(expected > 0 && result == expected, "expected " + expected + " files, got " + result);
        helper.succeed();
    }

    /**
     * {@code drops} lists every rule that applies to a mob through a tag, {@code minecraft:*} and {@code *}: the
     * skeleton gets the prismarine of {@code #dstest_a:tagged}, the nautilus shell and the barrier.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void dropsListsRulesOfMob(GameTestHelper helper) {
        int result = runCommand(helper, "alkdrops drops minecraft:skeleton");
        DropTestKit.check(result == 3, "expected 3 drops for the skeleton, got " + result);
        helper.succeed();
    }

    /**
     * {@code drops} also looks up blocks: the melon gets its first-break totem and the {@code minecraft:*} barrier.
     * An id naming a mob and a block shows both (TNT: the mob {@code minecraft:*} shell and {@code *} barrier, the
     * block {@code minecraft:*} barrier); an unknown id fails.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void dropsListsRulesOfBlock(GameTestHelper helper) {
        int melon = runCommand(helper, "alkdrops drops minecraft:melon");
        DropTestKit.check(melon == 2, "expected 2 drops for the melon, got " + melon);
        int tnt = runCommand(helper, "alkdrops drops minecraft:tnt");
        DropTestKit.check(tnt == 3, "expected 3 drops for TNT, got " + tnt);
        DropTestKit.check(runCommand(helper, "alkdrops drops minecraft:not_a_thing") == 0, "unknown id accepted");
        helper.succeed();
    }

    /**
     * {@code help} lists every topic, and each topic has a page.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void helpShowsEveryTopic(GameTestHelper helper) {
        DropTestKit.check(runCommand(helper, "alkdrops help") == 6, "expected 6 help topics");
        for (String topic : List.of("drops", "files", "reset", "simulate", "sources", "validate")) {
            DropTestKit.check(runCommand(helper, "alkdrops help " + topic) > 0, "expected a help page for " + topic);
        }
        DropTestKit.check(runCommand(helper, "alkdrops help simulate page 2") > 0, "expected a second simulate help page");
        helper.succeed();
    }

    /**
     * The run count is optional and defaults to 1; a lone number is the run count, and the enchantments may follow it
     * or stand alone.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void simulateRunCountDefaultsToOne(GameTestHelper helper) {
        DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:zombie") == 1, "expected 1 kill without a count");
        DropTestKit.check(runCommand(helper, "alkdrops simulate block minecraft:stone by none") == 1, "expected 1 break before an option");
        DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:zombie 3") == 3, "expected a lone number to be the count");
        DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:zombie 5 enchant [{id:\"minecraft:looting\",lvl:2}] by none") == 5,
                "expected count then enchantments");
        DropTestKit.check(runCommand(helper, "alkdrops simulate entity minecraft:zombie enchant [{id:\"minecraft:looting\",lvl:2}]") == 1,
                "expected enchantments without a count");
        helper.succeed();
    }

    /**
     * Simulates kills of a mob type at the arena center.
     *
     * @param helper  Helper of the test. Never null.
     * @param type    Mob type. Never null.
     * @param player  Killer, or null for no killer.
     * @param enchantments Forced enchantment levels, empty for none. Never null.
     * @param filter  What to count. Never null.
     * @return The counts of {@link #RUNS} kills.
     */
    private static DropSimulation simulateMob(GameTestHelper helper, EntityType<?> type, @Nullable Player player,
                                              Map<Enchantment, Integer> enchantments, SimulationFilter filter) {
        IDropSimulator simulator = MobDropSimulator.create(type, setup(helper, player, enchantments));
        return DropSimulation.run(simulator, RUNS, filter, false);
    }

    /**
     * Returns the only result line of an item, failing when there is none or more than one.
     *
     * @param simulation Counts of the runs. Never null.
     * @param item       Item to look for. Never null.
     * @return The result line.
     */
    private static DropSimulation.ItemResult singleResult(DropSimulation simulation, Item item) {
        List<DropSimulation.ItemResult> results = simulation.itemResults().stream().filter(result -> result.item() == item).toList();
        DropTestKit.check(results.size() == 1, "expected 1 line of " + item + ", got " + results.size());
        return results.get(0);
    }

    /**
     * Creates the setup of a simulation at the arena center.
     *
     * @param helper       Helper of the test. Never null.
     * @param player       Player, or null for the console.
     * @param enchantments Forced enchantment levels, empty for none. Never null.
     * @return The setup.
     */
    private static SimulationSetup setup(GameTestHelper helper, @Nullable Player player, Map<Enchantment, Integer> enchantments) {
        SimulationActor actor = player != null ? new SimulationActor.AsPlayer(player) : new SimulationActor.Nobody();
        return setup(helper, actor, Set.of(), enchantments);
    }

    /**
     * Creates the setup of a simulation at the arena center with an actor and forced conditions.
     *
     * @param helper       Helper of the test. Never null.
     * @param actor        Who kills or breaks. Never null.
     * @param flags        Forced conditions. Never null.
     * @param enchantments Forced enchantment levels, empty for none. Never null.
     * @return The setup.
     */
    private static SimulationSetup setup(GameTestHelper helper, SimulationActor actor, Set<SimulationFlag> flags,
                                         Map<Enchantment, Integer> enchantments) {
        return new SimulationSetup(helper.getLevel(), DropTestKit.arenaCenter(helper), actor, flags, enchantments);
    }

    /**
     * Runs a command as the console at the arena center and returns its chat output, translated to English.
     *
     * @param helper  Helper of the test. Never null.
     * @param command Command without the slash. Never null.
     * @return The chat lines, in order.
     */
    private static List<String> runCommandCapturing(GameTestHelper helper, String command) {
        List<String> output = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                output.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                .withSource(capture)
                .withLevel(helper.getLevel())
                .withPosition(DropTestKit.arenaCenter(helper));
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
        return output;
    }

    /**
     * Runs a command as the console, at the arena center, without chat output.
     *
     * @param helper  Helper of the test. Never null.
     * @param command Command without the slash. Never null.
     * @return The command result, 0 when it failed.
     */
    private static int runCommand(GameTestHelper helper, String command) {
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel())
                .withPosition(DropTestKit.arenaCenter(helper))
                .withSuppressedOutput();
        return helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
    }

    /**
     * Fails the test when the summed count of an item differs from the expected one.
     *
     * @param simulation Counts of the runs. Never null.
     * @param item       Item to check. Never null.
     * @param expected   Expected summed count.
     */
    private static void assertTotal(DropSimulation simulation, Item item, long expected) {
        long actual = totalOf(simulation, item);
        DropTestKit.check(actual == expected, "expected " + expected + " " + ForgeRegistries.ITEMS.getKey(item) + ", got " + actual);
    }
}
