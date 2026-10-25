package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.jaffh.alkahestcore.dropscore.api.DropOrigin;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.common.drops.DropTrigger;
import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.jaffh.alkahestcore.dropscore.server.drops.EnchantLevels;
import com.jaffh.alkahestcore.dropscore.server.drops.RollContext;
import com.jaffh.alkahestcore.dropscore.server.drops.TriggeredDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.IForgeShearable;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Drops the mob drops of the triggers other than death: shear, hit, lightning and periodic. Each handler reads only
 * its trigger's table, so mobs without such drops cost one table lookup. Drops spawn at the mob, with the Looting of
 * the acting player's main hand on hit (the only trigger besides death with a weapon); removal files of the same trigger remove drops of other namespaces.
 */
public class TriggerDropInjector {
    /** Items and orbs a lightning drop spawned, with their bolt, so the same bolt does not burn them; server thread only. */
    private final Map<Entity, LightningBolt> spawnedByBolt = new WeakHashMap<>();

    /**
     * Drops the shear drops when a player is about to shear a mob with shears. Forge shears every
     * {@link IForgeShearable} mob through {@link ShearsItem}, right after this event when nothing cancels it.
     * Dispensers shear without an event, so they never trigger the drops.
     *
     * @param event Entity interaction event of the Forge bus.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getTarget() instanceof LivingEntity mob) || !(mob instanceof IForgeShearable shearable)) return;
        DropTable table = MobDropManager.getTable(mob.getType(), DropTrigger.SHEAR);
        if (table.dropFiles().isEmpty()) return;

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ShearsItem)) return;
        if (!shearable.isShearable(stack, level, BlockPos.containing(mob.position()))) return;
        Player player = event.getEntity();
        drop(new TriggerCause(DropTrigger.SHEAR, mob, level.damageSources().playerAttack(player), player, stack, 0.0F), table, entry -> true);
    }

    /**
     * Drops the hit drops when a player hurts a mob, each drop waiting its own cooldown. Forge fires this event for
     * every hurt with damage above 0, after armor, Resistance and absorption hearts reduced it, so its amount is the
     * damage the mob took: {@code damage_taken} reads it, capped at the mob's health, and a hit fully blocked still
     * counts as a hit, with 0.
     *
     * @param event Damage event of the Forge bus, after other mods changed or canceled it.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDamage(LivingDamageEvent event) {
        LivingEntity mob = event.getEntity();
        if (!(mob.level() instanceof ServerLevel)) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        DropTable table = MobDropManager.getTable(mob.getType(), DropTrigger.HIT);
        if (table.dropFiles().isEmpty()) return;

        Set<DropEntry> due = DropTimers.collectDue(mob, table, mob.level().getGameTime());
        if (!due.isEmpty()) drop(new TriggerCause(DropTrigger.HIT, mob, event.getSource(), player, player.getMainHandItem(),
                damageTakenOf(mob, event.getAmount())), table, due::contains);
    }

    /**
     * Returns the health a hit takes from a mob: its damage, capped at the health the mob had, since a mob cannot lose
     * more than it has. The event fires before the health goes down, so the mob's health is still the one before the
     * hit.
     *
     * @param mob    Hit mob. Never null.
     * @param amount Damage after armor, Resistance and absorption.
     * @return The damage taken, between 0 and the mob's health.
     */
    private static float damageTakenOf(LivingEntity mob, float amount) {
        return Math.max(0.0F, Math.min(amount, mob.getHealth()));
    }

    /**
     * Drops the lightning drops once per bolt: a bolt fires this event on several ticks, so mobs it already hit
     * are skipped, and the items and orbs it made drop are spared by the same bolt.
     *
     * @param event Lightning event of the Forge bus.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onStruckByLightning(EntityStruckByLightningEvent event) {
        LightningBolt bolt = event.getLightning();
        if (spawnedByBolt.get(event.getEntity()) == bolt) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity mob) || !(mob.level() instanceof ServerLevel level)) return;
        DropTable table = MobDropManager.getTable(mob.getType(), DropTrigger.LIGHTNING);
        if (table.dropFiles().isEmpty()) return;
        if (bolt.getHitEntities().anyMatch(hit -> hit == mob)) return;

        for (Entity spawned : drop(new TriggerCause(DropTrigger.LIGHTNING, mob, level.damageSources().lightningBolt()), table, entry -> true)) {
            spawnedByBolt.put(spawned, bolt);
        }
    }

    /**
     * Drops the periodic drops whose interval has passed. Runs every tick for every living entity, so it leaves
     * after one table lookup for types without periodic drops and allocates nothing until a drop is due.
     *
     * @param event Living tick event of the Forge bus.
     */
    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity mob = event.getEntity();
        if (!(mob.level() instanceof ServerLevel level)) return;
        DropTable table = MobDropManager.getTable(mob.getType(), DropTrigger.PERIODIC);
        if (table.dropFiles().isEmpty() || !mob.isAlive()) return;

        Set<DropEntry> due = DropTimers.collectDue(mob, table, level.getGameTime());
        if (!due.isEmpty()) drop(new TriggerCause(DropTrigger.PERIODIC, mob, level.damageSources().generic()), table, due::contains);
    }

    /**
     * Rolls the due drops of a trigger and spawns them at the mob: stacks as item entities, experience as orbs.
     *
     * @param cause Mob, damage source and acting player of the trigger. Never null.
     * @param table Table of the trigger. Never null.
     * @param isDue Tells whether a drop may roll now. Never null.
     * @return The spawned item entities and experience orbs.
     */
    private static List<Entity> drop(TriggerCause cause, DropTable table, Predicate<DropEntry> isDue) {
        LivingEntity mob = cause.mob();
        ServerLevel level = (ServerLevel) mob.level();
        LootContext lootContext = table.needsLootContext() ? MobDropInjector.createLootContext(level, mob, cause.source()) : null;
        Player player = cause.player();
        EnchantLevels levels = cause.trigger().hasEnchantBonus() ? EnchantLevels.ofItem(cause.heldItem()) : EnchantLevels.NONE;
        RollContext rollContext = new RollContext(levels, mob.getMaxHealth(), cause.damageTaken(), level.getRandom(), player)
                .withOrigin(new DropOrigin.Mob(mob));

        TriggeredDrops drops = table.rollTriggered(lootContext, rollContext, isDue);
        if (drops.isEmpty()) return List.of();
        List<Entity> spawned = new ArrayList<>();
        for (ItemStack stack : drops.items()) {
            ItemEntity item = mob.spawnAtLocation(stack);
            if (item != null) spawned.add(item);
        }
        for (int left = drops.experience(); left > 0; ) {
            int value = ExperienceOrb.getExperienceValue(left);
            left -= value;
            ExperienceOrb orb = new ExperienceOrb(level, mob.getX(), mob.getY(), mob.getZ(), value);
            level.addFreshEntity(orb);
            spawned.add(orb);
        }
        return spawned;
    }

    /**
     * Represents who and what caused a trigger.
     *
     * @param trigger     Trigger that fired. Never null.
     * @param mob         Mob that drops. Never null.
     * @param source      Damage source the loot conditions read: the player's attack for shear and hit, lightning, or
     *                    generic damage for periodic drops. Never null.
     * @param player      Player who caused the trigger, or null for lightning and periodic drops.
     * @param heldItem    Item whose enchantments the drops read: the weapon of a hit, the shears; empty without player.
     *                    Never null.
     * @param damageTaken Health the mob lost from a hit; 0 for the other triggers.
     */
    private record TriggerCause(DropTrigger trigger, LivingEntity mob, DamageSource source, @Nullable Player player,
                                ItemStack heldItem, float damageTaken) {
        /**
         * Creates the cause of a trigger without player.
         *
         * @param trigger Trigger that fired. Never null.
         * @param mob     Mob that drops. Never null.
         * @param source  Damage source the loot conditions read. Never null.
         */
        TriggerCause(DropTrigger trigger, LivingEntity mob, DamageSource source) {
            this(trigger, mob, source, null, ItemStack.EMPTY, 0.0F);
        }
    }
}
