package com.jaffh.alkahestdrops.server.mobdrops;

import com.jaffh.alkahestdrops.common.drops.DropEntry;
import com.jaffh.alkahestdrops.common.drops.DropNumbers;
import com.jaffh.alkahestdrops.common.drops.DropTracking;
import com.jaffh.alkahestdrops.common.drops.DropTrigger;
import com.jaffh.alkahestdrops.server.drops.DropFile;
import com.jaffh.alkahestdrops.server.drops.DropTable;
import com.jaffh.alkahestdrops.server.drops.EnchantLevels;
import com.jaffh.alkahestdrops.server.drops.IDropSimulator;
import com.jaffh.alkahestdrops.server.drops.InvalidSimulationException;
import com.jaffh.alkahestdrops.server.drops.RollContext;
import com.jaffh.alkahestdrops.server.drops.SimulatedDrop;
import com.jaffh.alkahestdrops.server.drops.SimulatedStack;
import com.jaffh.alkahestdrops.server.drops.SimulationActor;
import com.jaffh.alkahestdrops.server.drops.SimulationFlag;
import com.jaffh.alkahestdrops.server.drops.SimulationSetup;
import com.jaffh.alkahestdrops.server.drops.TrackedDropRecorder;
import com.jaffh.alkahestdrops.server.drops.TriggeredDrops;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Simulates the drops of a trigger other than death on one mob type: each run is one shear, hit or lightning strike,
 * or one tick of a periodic simulation, in which each drop rolls when its own interval has passed (first at its
 * interval, as in game) and is counted on its own, so the report can rate it by its interval. Only the drop file drops of that trigger roll (the game's own drops of those actions, such
 * as wool, are not described by loot tables); cooldowns are ignored. Shear and hit need the player actor; lightning and
 * periodic drops have no player, so they refuse {@code by}, {@code enchant} and {@code sneaking}.
 */
public final class MobTriggerSimulator implements IDropSimulator {
    /** Ticks of one minute, the unit of periodic rates. */
    private static final int TICKS_PER_MINUTE = 1200;
    /** Damage of one simulated hit. */
    private static final float HIT_DAMAGE = 1.0F;

    private final DropTrigger trigger;
    private final ServerLevel level;
    private final LivingEntity mob;
    @Nullable
    private final Player player;
    private final DamageSource source;
    private final DropTable table;
    private final SimulationSetup setup;
    /** Enchantment levels of every run: the player's held item never changes during a simulation. */
    private final EnchantLevels enchantLevels;
    /**
     * Health a simulated hit takes from the mob, read by {@code damage_taken}: always 1, so the number of hits is
     * also the damage dealt; 0 for the other triggers.
     */
    private final float hitDamage;
    /** Hit drops with the {@code damage_taken} behavior, rolled and counted on their own lines; by identity. */
    private final Set<DropEntry> perDamageDrops = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Interval of every periodic drop, by progress key. */
    private final Map<String, Integer> intervals = new HashMap<>();
    private long elapsedTicks;

    /**
     * Creates a simulator.
     *
     * @param trigger Simulated trigger, not death. Never null.
     * @param setup   Setup of the simulation. Never null.
     * @param mob     Simulated mob, never added to the level. Never null.
     * @param player  Acting player for shear and hit, else null.
     */
    private MobTriggerSimulator(DropTrigger trigger, SimulationSetup setup, LivingEntity mob, @Nullable Player player) {
        this.trigger = trigger;
        this.level = setup.level();
        this.mob = mob;
        this.player = player;
        this.source = damageSourceOf(trigger, level, player);
        this.table = MobDropManager.getTable(mob.getType(), trigger);
        this.setup = setup;
        this.enchantLevels = trigger.hasEnchantBonus() && player != null ? setup.levelsOf(player.getMainHandItem()) : EnchantLevels.NONE;
        this.hitDamage = trigger == DropTrigger.HIT ? Math.min(HIT_DAMAGE, mob.getHealth()) : 0.0F;
        for (DropFile file : table.dropFiles()) {
            for (DropEntry entry : file.entries()) {
                if (trigger == DropTrigger.PERIODIC) intervals.put(entry.tracking().key(), entry.trigger().ticks());
                if (entry.behavior().type().readsDamageTaken()) perDamageDrops.add(entry);
            }
        }
    }

    /**
     * Creates the simulator of a trigger on a mob type.
     *
     * @param type    Mob type. Never null.
     * @param trigger Trigger to simulate, not death. Never null.
     * @param setup   Setup of the simulation. Never null.
     * @return The simulator.
     * @throws InvalidSimulationException If the type is not living or the setup does not fit the trigger.
     */
    public static MobTriggerSimulator create(EntityType<?> type, DropTrigger trigger, SimulationSetup setup) {
        if (!setup.enchantments().isEmpty() && !trigger.hasEnchantBonus()) {
            throw new InvalidSimulationException(IDropSimulator.setupText("enchant_with_trigger", trigger.id()));
        }
        Player player = playerOf(trigger, setup);
        return new MobTriggerSimulator(trigger, setup, MobDropSimulator.createMob(type, setup), player);
    }

    /**
     * Returns the acting player after checking that the actor fits the trigger.
     *
     * @param trigger Simulated trigger. Never null.
     * @param setup   Setup of the simulation. Never null.
     * @return The player for shear and hit, null for lightning and periodic.
     * @throws InvalidSimulationException If shear or hit has no player actor, or lightning or periodic has an actor
     *                                    or {@code sneaking}.
     */
    @Nullable
    private static Player playerOf(DropTrigger trigger, SimulationSetup setup) {
        SimulationActor actor = setup.actor();
        if (trigger.hasPlayer()) {
            if (actor instanceof SimulationActor.AsPlayer asPlayer) return setup.createPlayer(asPlayer);
            throw new InvalidSimulationException(IDropSimulator.setupText("trigger_needs_player", trigger.id()));
        }
        if (!(actor instanceof SimulationActor.Nobody)) {
            throw new InvalidSimulationException(IDropSimulator.setupText("trigger_without_player", trigger.id()));
        }
        if (setup.has(SimulationFlag.SNEAKING)) {
            throw new InvalidSimulationException(IDropSimulator.setupText("sneaking_with_trigger", trigger.id()));
        }
        return null;
    }

    /**
     * Returns the damage source the loot conditions read, the same as in game.
     *
     * @param trigger Simulated trigger. Never null.
     * @param level   Level of the simulation. Never null.
     * @param player  Acting player, or null.
     * @return The player's attack for shear and hit, lightning, or generic damage for periodic drops.
     */
    private static DamageSource damageSourceOf(DropTrigger trigger, ServerLevel level, @Nullable Player player) {
        if (player != null) return level.damageSources().playerAttack(player);
        if (trigger == DropTrigger.LIGHTNING) return level.damageSources().lightningBolt();
        return level.damageSources().generic();
    }

    /**
     * Rolls one run: every drop of the trigger's table, or for periodic drops one tick, with only the drops whose
     * interval has passed.
     *
     * @return The items and experience of the run; {@link SimulatedDrop#NOTHING} for a tick without due drop.
     */
    @Override
    public SimulatedDrop simulateOnce() {
        if (trigger == DropTrigger.PERIODIC) {
            Set<DropEntry> due = dueAt(++elapsedTicks);
            return due.isEmpty() ? SimulatedDrop.NOTHING : rollEach(due);
        }
        if (!perDamageDrops.isEmpty()) return rollWithPerDamageDrops();
        LootContext context = lootContext();
        RollContext rollContext = rollContext();
        TriggeredDrops[] rolled = new TriggeredDrops[1];
        return TrackedDropRecorder.INSTANCE.recordRoll(() -> {
            rolled[0] = table.rollTriggered(context, rollContext, entry -> true);
            return rolled[0].items();
        }, () -> rolled[0].experience());
    }

    /**
     * Rolls one hit of a table with {@code damage_taken} drops: the other drops together, then each
     * {@code damage_taken} drop on its own with what it gave labeled with the drop, so the report gives it its own
     * "per damage" line. Pity and one-time drops are still recorded as usual.
     *
     * @return The items and experience of the hit.
     */
    private SimulatedDrop rollWithPerDamageDrops() {
        LootContext context = lootContext();
        RollContext rollContext = rollContext();
        Map<ItemStack, DropTracking> labels = new IdentityHashMap<>();
        Map<DropTracking, Integer> perDamageExperience = new HashMap<>();
        int[] experience = new int[1];
        SimulatedDrop recorded = TrackedDropRecorder.INSTANCE.recordRoll(() -> {
            TriggeredDrops others = table.rollTriggered(context, rollContext, entry -> !perDamageDrops.contains(entry));
            List<ItemStack> items = new ArrayList<>(others.items());
            experience[0] = others.experience();
            for (DropEntry entry : perDamageDrops) {
                TriggeredDrops rolled = table.rollTriggered(context, rollContext, candidate -> candidate == entry);
                for (ItemStack stack : rolled.items()) {
                    items.add(stack);
                    labels.put(stack, entry.tracking());
                }
                experience[0] += rolled.experience();
                if (rolled.experience() > 0 && !entry.tracking().needsProgress()) {
                    perDamageExperience.merge(entry.tracking(), rolled.experience(), Integer::sum);
                }
            }
            return items;
        }, () -> experience[0]);

        List<SimulatedStack> items = new ArrayList<>(recorded.items().size());
        for (SimulatedStack stack : recorded.items()) {
            DropTracking label = labels.get(stack.stack());
            items.add(label != null ? new SimulatedStack(stack.stack(), label) : stack);
        }
        Map<DropTracking, Integer> trackedExperience = new HashMap<>(recorded.trackedExperience());
        perDamageExperience.forEach((tracking, amount) -> trackedExperience.merge(tracking, amount, Integer::sum));
        return new SimulatedDrop(items, recorded.experience(), trackedExperience);
    }

    /**
     * Rolls each due periodic drop on its own and labels what it gave with the drop, so every periodic drop gets its
     * own report line. Periodic drops have no pity or one-time drops, so nothing else needs recording.
     *
     * @param due Drops due at this tick. Never null.
     * @return The labeled items and experience of the tick.
     */
    private SimulatedDrop rollEach(Set<DropEntry> due) {
        LootContext context = lootContext();
        RollContext rollContext = rollContext();
        List<SimulatedStack> items = new ArrayList<>();
        Map<DropTracking, Integer> trackedExperience = new HashMap<>();
        int experience = 0;
        for (DropEntry entry : due) {
            TriggeredDrops rolled = table.rollTriggered(context, rollContext, candidate -> candidate == entry);
            for (ItemStack stack : rolled.items()) items.add(new SimulatedStack(stack, entry.tracking()));
            if (rolled.experience() > 0) {
                experience += rolled.experience();
                trackedExperience.merge(entry.tracking(), rolled.experience(), Integer::sum);
            }
        }
        return new SimulatedDrop(items, experience, trackedExperience);
    }

    /**
     * Creates the loot context of one run.
     *
     * @return The context, or null when no drop of the table needs one.
     */
    @Nullable
    private LootContext lootContext() {
        return table.needsLootContext() ? MobDropInjector.createLootContext(level, mob, source) : null;
    }

    /**
     * Creates the roll context of one run.
     *
     * @return The context with the run's enchantment levels and player.
     */
    private RollContext rollContext() {
        return new RollContext(enchantLevels, mob.getMaxHealth(), hitDamage, level.getRandom(), player);
    }

    /**
     * Tells whether a result line counts a {@code damage_taken} drop, rated per damage.
     *
     * @param tracking Drop of a result line. Never null.
     * @return True for the {@code damage_taken} drops of a hit simulation.
     */
    @Override
    public boolean isPerDamage(DropTracking tracking) {
        for (DropEntry entry : perDamageDrops) {
            if (entry.tracking().key().equals(tracking.key())) return true;
        }
        return false;
    }

    /**
     * Returns the interval of a periodic drop.
     *
     * @param tracking Drop of a result line. Never null.
     * @return Its interval in ticks, or empty when it is not a periodic drop of this simulation.
     */
    @Override
    public OptionalInt intervalOf(DropTracking tracking) {
        Integer interval = intervals.get(tracking.key());
        return interval != null ? OptionalInt.of(interval) : OptionalInt.empty();
    }

    /**
     * Collects the periodic drops due at a tick: those whose interval divides it.
     *
     * @param tick Tick of the simulation, from 1.
     * @return The due drops, compared by identity; an empty immutable set when none is due, without allocating.
     */
    private Set<DropEntry> dueAt(long tick) {
        Set<DropEntry> due = null;
        for (DropFile file : table.dropFiles()) {
            for (DropEntry entry : file.entries()) {
                if (tick % entry.trigger().ticks() != 0) continue;
                if (due == null) due = Collections.newSetFromMap(new IdentityHashMap<>());
                due.add(entry);
            }
        }
        return due == null ? Set.of() : due;
    }

    /**
     * Returns the runs one rate of the report covers: a minute of ticks for periodic drops, else one run.
     *
     * @return 1200 for periodic, 1 otherwise.
     */
    @Override
    public int runsPerRate() {
        return trigger == DropTrigger.PERIODIC ? TICKS_PER_MINUTE : 1;
    }

    /**
     * Names one run after the trigger.
     *
     * @return {@code shear}, {@code hit}, {@code strike} or {@code tick}.
     */
    @Override
    public String runNoun() {
        return switch (trigger) {
            case LIGHTNING -> "strike";
            case PERIODIC -> "tick";
            default -> trigger.id();
        };
    }

    /**
     * Returns the name of the simulated mob type.
     *
     * @return The translated type name.
     */
    @Override
    public Component targetName() {
        return mob.getType().getDescription();
    }

    /**
     * Describes the trigger, the player and the forced conditions.
     *
     * @return Such as "on hit, player: Dev, sneaking, 1 damage per hit, enchantments: Looting 3" or "periodic, no player, mob on
     *         fire".
     */
    @Override
    public Component describeSetup() {
        MutableComponent text = IDropSimulator.setupText("trigger." + trigger.id());
        if (player != null) {
            text.append(", ").append(IDropSimulator.setupText("player", player.getName()));
            if (player.isCrouching()) text.append(", ").append(IDropSimulator.setupText("sneaking"));
        } else {
            text.append(", ").append(IDropSimulator.setupText("nobody"));
        }
        if (setup.has(SimulationFlag.BURNING)) text.append(", ").append(IDropSimulator.setupText("burning"));
        if (trigger == DropTrigger.HIT) {
            text.append(", ").append(IDropSimulator.setupText("damage", DropNumbers.number(hitDamage)));
        }
        if (trigger.hasEnchantBonus() && player != null) {
            text.append(", ").append(IDropSimulator.enchantmentsText(setup.effectiveEnchantments(player.getMainHandItem())));
        }
        return text;
    }
}
