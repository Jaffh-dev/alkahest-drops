package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.jaffh.alkahestcore.dropscore.server.drops.IDropSimulator;
import com.jaffh.alkahestcore.dropscore.server.drops.InvalidSimulationException;
import com.jaffh.alkahestcore.dropscore.server.drops.RollContext;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulatedDrop;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationActor;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationFlag;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationSetup;
import com.jaffh.alkahestcore.dropscore.server.drops.TrackedDropRecorder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulates kills of one mob type with a mob that is never added to the level: the vanilla loot table (with loot
 * modifiers), then the mob's drop table, then the experience. The killer is a fake player, a mob of another type or
 * nobody. Equipment drops and drops other mods add in {@code LivingDropsEvent} are not simulated.
 */
public final class MobDropSimulator implements IDropSimulator {
    /** Fire ticks of a burning simulated mob; any positive value makes {@code is_on_fire} pass. */
    private static final int BURNING_TICKS = 100;

    private final ServerLevel level;
    private final LivingEntity mob;
    @Nullable
    private final LivingEntity killer;
    private final DamageSource source;
    private final LootTable lootTable;
    private final DropTable table;
    private final SimulationSetup setup;

    /**
     * Creates a simulator from its resolved parts.
     *
     * @param setup  Level, position, actor, flags and forced Looting of the simulation. Never null.
     * @param mob    Mob that is never added to the level. Never null.
     * @param killer Killer that is never added to the level, or null for no killer.
     */
    private MobDropSimulator(SimulationSetup setup, LivingEntity mob, @Nullable LivingEntity killer) {
        this.level = setup.level();
        this.mob = mob;
        this.killer = killer;
        this.source = damageSourceOf(level, killer);
        this.lootTable = level.getServer().getLootData().getLootTable(mob.getLootTable());
        this.table = MobDropManager.getTable(mob.getType());
        this.setup = setup;
    }

    /**
     * Creates a simulator for a mob type.
     *
     * @param type  Mob type. Never null.
     * @param setup Level, position, actor, flags and forced Looting of the simulation. Never null.
     * @return The simulator.
     * @throws InvalidSimulationException If the type or the killer type is not a living entity, the actor is an
     *                                  explosion, or {@code sneaking} is set without a killer.
     */
    public static MobDropSimulator create(EntityType<?> type, SimulationSetup setup) {
        LivingEntity mob = createLiving(type, setup, "no_drops");
        if (setup.has(SimulationFlag.BURNING)) mob.setRemainingFireTicks(BURNING_TICKS);
        return new MobDropSimulator(setup, mob, createKiller(setup));
    }

    /**
     * Creates the killer of the setup's actor.
     *
     * @param setup Setup of the simulation. Never null.
     * @return The killer, or null for nobody.
     * @throws InvalidSimulationException If the actor cannot kill a mob, or {@code sneaking} is set without a killer.
     */
    @Nullable
    private static LivingEntity createKiller(SimulationSetup setup) {
        SimulationActor actor = setup.actor();
        if (actor instanceof SimulationActor.AsPlayer player) return setup.createPlayer(player);
        if (actor instanceof SimulationActor.AsEntity entity) {
            LivingEntity killer = createLiving(entity.type(), setup, "not_a_killer");
            if (setup.has(SimulationFlag.SNEAKING)) killer.setPose(Pose.CROUCHING);
            return killer;
        }
        if (actor instanceof SimulationActor.Explosion) {
            throw new InvalidSimulationException(IDropSimulator.setupText("explosion_on_mob"));
        }
        if (setup.has(SimulationFlag.SNEAKING)) {
            throw new InvalidSimulationException(IDropSimulator.setupText("sneaking_without_killer"));
        }
        return null;
    }

    /**
     * Creates a living entity of a type at the setup's position, never added to the level.
     *
     * @param type    Entity type. Never null.
     * @param setup   Setup of the simulation. Never null.
     * @param problem Key of the error when the type is not living, such as {@code no_drops}. Never null.
     * @return The entity.
     * @throws InvalidSimulationException If the type is not a living entity.
     */
    private static LivingEntity createLiving(EntityType<?> type, SimulationSetup setup, String problem) {
        Entity entity = type.create(setup.level());
        if (!(entity instanceof LivingEntity living)) {
            throw new InvalidSimulationException(IDropSimulator.setupText(problem, ForgeRegistries.ENTITY_TYPES.getKey(type)));
        }
        living.moveTo(setup.origin().x, setup.origin().y, setup.origin().z, 0.0F, 0.0F);
        return living;
    }

    /**
     * Returns the damage source of every kill.
     *
     * @param level  Level of the simulation, which holds the damage types. Never null.
     * @param killer Killer, or null for nobody.
     * @return A player attack, a mob attack, or generic damage without a killer.
     */
    private static DamageSource damageSourceOf(ServerLevel level, @Nullable LivingEntity killer) {
        if (killer instanceof Player player) return level.damageSources().playerAttack(player);
        if (killer != null) return level.damageSources().mobAttack(killer);
        return level.damageSources().generic();
    }

    /**
     * Rolls one kill with the Looting level forced when the setup gives one.
     *
     * @return The items and experience of the kill.
     */
    @Override
    public SimulatedDrop simulateOnce() {
        return LootingOverride.INSTANCE.runWith(mob, setup.enchantLevel(), this::rollKill);
    }

    /**
     * Rolls one kill: loot table, removals and dropscore drops, then the experience.
     *
     * @return The items and experience of the kill.
     */
    private SimulatedDrop rollKill() {
        LootContext context = MobDropInjector.createLootContext(level, mob, source);
        RollContext rollContext = new RollContext(lootingLevel(), mob.getMaxHealth(), level.getRandom(), MobDropInjector.playerOf(killer));
        return TrackedDropRecorder.INSTANCE.recordRoll(() -> rollItems(context, rollContext), () -> rollExperience(context, rollContext));
    }

    /**
     * Rolls the items of one kill: the loot table, then removals and dropscore drops.
     *
     * @param context     Loot context of the kill. Never null.
     * @param rollContext Roll context of the kill. Never null.
     * @return The dropped stacks.
     */
    private List<ItemStack> rollItems(LootContext context, RollContext rollContext) {
        List<ItemStack> items = new ArrayList<>();
        lootTable.getRandomItems(context, items::add);
        return table.isEmpty() ? items : table.applyTo(items, context, rollContext);
    }

    /**
     * Returns the experience of one kill. As in vanilla, only kills by a player give experience.
     *
     * @param context     Loot context of the kill. Never null.
     * @param rollContext Roll context of the kill. Never null.
     * @return The experience, 0 when the killer is not a player.
     */
    private int rollExperience(LootContext context, RollContext rollContext) {
        if (!(killer instanceof Player)) return 0;
        int experience = mob.getExperienceReward();
        return table.affectsExperience() ? table.applyExperience(experience, context, rollContext) : experience;
    }

    /**
     * Returns the Looting level of a kill: the forced level, or the killer's weapon after Forge's event.
     *
     * @return The Looting level, 0 or more.
     */
    private int lootingLevel() {
        return ForgeHooks.getLootingLevel(mob, source.getEntity(), source);
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
     * Describes the killer, the forced conditions and the Looting level.
     *
     * @return Such as {@code "killer: Dev, sneaking, Looting 3"} or {@code "killer: Wolf (no experience), Looting 0"}.
     */
    @Override
    public Component describeSetup() {
        MutableComponent text;
        if (killer == null) {
            text = IDropSimulator.setupText("no_killer");
        } else {
            text = IDropSimulator.setupText("killer", killer.getName());
            if (!(killer instanceof Player)) text.append(" ").append(IDropSimulator.setupText("no_experience"));
            if (setup.has(SimulationFlag.SNEAKING)) text.append(", ").append(IDropSimulator.setupText("sneaking"));
        }
        if (setup.has(SimulationFlag.BURNING)) text.append(", ").append(IDropSimulator.setupText("burning"));
        int looting = LootingOverride.INSTANCE.runWith(mob, setup.enchantLevel(), this::lootingLevel);
        return text.append(", ").append(IDropSimulator.setupText("looting", looting));
    }
}
