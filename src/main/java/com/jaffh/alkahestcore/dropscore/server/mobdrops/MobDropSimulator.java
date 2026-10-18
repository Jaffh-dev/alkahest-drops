package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.jaffh.alkahestcore.dropscore.server.drops.IDropSimulator;
import com.jaffh.alkahestcore.dropscore.server.drops.RollContext;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulatedDrop;
import com.jaffh.alkahestcore.dropscore.server.drops.TrackedDropRecorder;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationActor;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationFlag;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationSetup;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Simulates kills of one mob type with a mob that is never added to the level: the vanilla loot table (with loot
 * modifiers), then the mob's drop table, then the experience. The killer is a fake player, a mob of another type or
 * nobody. Equipment drops and drops other mods add in {@code LivingDropsEvent} are not simulated.
 */
public final class MobDropSimulator implements IDropSimulator {
    /** {@code LivingEntity.getExperienceReward(Player)}, protected in vanilla. */
    private static final Method GET_EXPERIENCE_REWARD =
            ObfuscationReflectionHelper.findMethod(LivingEntity.class, "m_6552_", Player.class);
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
        this.source = damageSourceOf(killer);
        this.lootTable = level.getServer().getLootTables().get(mob.getLootTable());
        this.table = MobDropManager.getTable(mob.getType());
        this.setup = setup;
    }

    /**
     * Creates a simulator for a mob type.
     *
     * @param type  Mob type. Never null.
     * @param setup Level, position, actor, flags and forced Looting of the simulation. Never null.
     * @return The simulator.
     * @throws IllegalArgumentException If the type or the killer type is not a living entity, the actor is an
     *                                  explosion, or {@code sneaking} is set without a killer.
     */
    public static MobDropSimulator create(EntityType<?> type, SimulationSetup setup) {
        LivingEntity mob = createLiving(type, setup, "has no drops");
        if (setup.has(SimulationFlag.BURNING)) mob.setRemainingFireTicks(BURNING_TICKS);
        return new MobDropSimulator(setup, mob, createKiller(setup));
    }

    /**
     * Creates the killer of the setup's actor.
     *
     * @param setup Setup of the simulation. Never null.
     * @return The killer, or null for nobody.
     * @throws IllegalArgumentException If the actor cannot kill a mob, or {@code sneaking} is set without a killer.
     */
    @Nullable
    private static LivingEntity createKiller(SimulationSetup setup) {
        SimulationActor actor = setup.actor();
        if (actor instanceof SimulationActor.AsPlayer player) return setup.createPlayer(player);
        if (actor instanceof SimulationActor.AsEntity entity) {
            LivingEntity killer = createLiving(entity.type(), setup, "cannot be a killer");
            if (setup.has(SimulationFlag.SNEAKING)) killer.setPose(Pose.CROUCHING);
            return killer;
        }
        if (actor instanceof SimulationActor.Explosion) {
            throw new IllegalArgumentException("'by explosion' only applies to blocks");
        }
        if (setup.has(SimulationFlag.SNEAKING)) {
            throw new IllegalArgumentException("'sneaking' needs a killer: use 'by player' or 'by <entity>'");
        }
        return null;
    }

    /**
     * Creates a living entity of a type at the setup's position, never added to the level.
     *
     * @param type    Entity type. Never null.
     * @param setup   Setup of the simulation. Never null.
     * @param problem End of the error message when the type is not living, such as {@code "has no drops"}. Never null.
     * @return The entity.
     * @throws IllegalArgumentException If the type is not a living entity.
     */
    private static LivingEntity createLiving(EntityType<?> type, SimulationSetup setup, String problem) {
        Entity entity = type.create(setup.level());
        if (!(entity instanceof LivingEntity living)) {
            throw new IllegalArgumentException(type.getRegistryName() + " is not a living entity, it " + problem);
        }
        living.moveTo(setup.origin().x, setup.origin().y, setup.origin().z, 0.0F, 0.0F);
        return living;
    }

    /**
     * Returns the damage source of every kill.
     *
     * @param killer Killer, or null for nobody.
     * @return A player attack, a mob attack, or generic damage without a killer.
     */
    private static DamageSource damageSourceOf(@Nullable LivingEntity killer) {
        if (killer instanceof Player player) return DamageSource.playerAttack(player);
        if (killer != null) return DamageSource.mobAttack(killer);
        return DamageSource.GENERIC;
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
        List<ItemStack> items = lootTable.getRandomItems(context);
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
        if (!(killer instanceof Player player)) return 0;
        int experience = vanillaExperience(player);
        return table.affectsExperience() ? table.applyExperience(experience, context, rollContext) : experience;
    }

    /**
     * Returns the experience vanilla gives for the mob.
     *
     * @param player Killing player. Never null.
     * @return The vanilla experience reward.
     * @throws IllegalStateException If the reflective call fails.
     */
    private int vanillaExperience(Player player) {
        try {
            return (int) GET_EXPERIENCE_REWARD.invoke(mob, player);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("cannot read the experience reward of " + mob.getType().getRegistryName(), e);
        }
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
    public String describeSetup() {
        StringBuilder text = new StringBuilder();
        if (killer == null) {
            text.append("no killer (no experience)");
        } else {
            text.append("killer: ").append(killer.getName().getString());
            if (!(killer instanceof Player)) text.append(" (no experience)");
            if (setup.has(SimulationFlag.SNEAKING)) text.append(", sneaking");
        }
        if (setup.has(SimulationFlag.BURNING)) text.append(", mob on fire");
        int looting = LootingOverride.INSTANCE.runWith(mob, setup.enchantLevel(), this::lootingLevel);
        return text.append(", Looting ").append(looting).toString();
    }
}
