package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.jaffh.alkahestcore.dropscore.api.DropOrigin;
import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalRule;
import com.jaffh.alkahestcore.dropscore.server.drops.EnchantLevels;
import com.jaffh.alkahestcore.dropscore.server.drops.RollContext;
import com.jaffh.alkahestcore.dropscore.server.drops.RolledDrop;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Applies the mob drop tables: on {@link LivingDropsEvent} it removes drops first, then rolls and adds dropscore
 * item drops (per-player drops once per attacking player, locked to them); on {@link LivingExperienceDropEvent} it turns off the experience when a removal file asks for it and
 * adds dropscore experience drops.
 */
public class MobDropInjector {
    /**
     * Damage source of each mob that is dying right now, kept for {@link LivingExperienceDropEvent}, which has none.
     * Weak keys, so a death canceled after this handler never leaks the entity.
     */
    private final Map<LivingEntity, DamageSource> deathSources = new WeakHashMap<>();

    /**
     * Remembers the damage source of a dying mob whose table changes its experience. Vanilla sets
     * {@code lastDamageSource} only after {@code die()} returns, so it cannot be read during the death.
     * Runs at LOWEST priority so deaths canceled by other mods are skipped.
     *
     * @param event Death event of the entity.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel)) return;
        if (!MobDropManager.getTable(entity.getType()).affectsExperience()) return;
        deathSources.put(entity, event.getSource());
    }

    /**
     * Handles a mob death. Runs at LOW priority so drops added by other mods at NORMAL priority can be removed.
     *
     * @param event Drops event of the dying entity.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        deathSources.remove(entity);

        DropTable table = MobDropManager.getTable(entity.getType());
        if (table.isEmpty()) return;

        LootContext context = table.needsLootContext() ? createLootContext(level, entity, event.getSource()) : null;
        List<RemovalRule> activeRemovals = table.collectActiveRemovals(context);
        event.getDrops().removeIf(drop -> table.removesEventDrop(drop.getItem().getItem(), activeRemovals));

        Entity killer = event.getSource().getEntity();
        DropOrigin origin = new DropOrigin.Mob(entity);
        RollContext rollContext = new RollContext(killLevelsOf(killer, event.getLootingLevel()), entity.getMaxHealth(),
                level.getRandom(), playerOf(killer)).withOrigin(origin);
        List<RollContext> attackerRolls = table.hasPerPlayerDrops()
                ? attackerRollsOf(entity, level, killer, event.getLootingLevel())
                : List.of();
        for (RolledDrop drop : table.rollDrops(activeRemovals, context, rollContext, attackerRolls)) {
            ItemEntity itemEntity = new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), drop.stack());
            itemEntity.setTarget(drop.owner());
            event.getDrops().add(itemEntity);
        }
    }

    /**
     * Builds the roll context of every online player who damaged the mob, for its per-player drops. The killer
     * keeps the enchantment levels of the kill; the other players use the enchantments of their main hand.
     *
     * @param entity        Dying mob. Never null.
     * @param level         Level of the mob. Never null.
     * @param killer        Entity that killed the mob, or null.
     * @param killerLooting Looting level of the kill.
     * @return One roll context per attacker, each with its player set; empty when no attacker is online.
     */
    private static List<RollContext> attackerRollsOf(LivingEntity entity, ServerLevel level, @Nullable Entity killer, int killerLooting) {
        List<RollContext> rolls = new ArrayList<>();
        for (ServerPlayer player : HitTracker.onlineAttackersOf(entity, level.getServer(), killer)) {
            EnchantLevels levels = player == killer ? killLevelsOf(killer, killerLooting) : EnchantLevels.ofItem(player.getMainHandItem());
            rolls.add(new RollContext(levels, entity.getMaxHealth(), level.getRandom(), player).withOrigin(new DropOrigin.Mob(entity)));
        }
        return rolls;
    }

    /**
     * Returns the enchantment levels of a kill: the killer's main hand, with the Looting level of the kill, which
     * mods may change through {@code LootingLevelEvent}.
     *
     * @param killer       Entity that killed the mob, or null.
     * @param lootingLevel Looting level of the kill, 0 or more.
     * @return The levels; only Looting without a living killer.
     */
    static EnchantLevels killLevelsOf(@Nullable Entity killer, int lootingLevel) {
        EnchantLevels levels = killer instanceof LivingEntity living ? EnchantLevels.ofItem(living.getMainHandItem()) : EnchantLevels.NONE;
        return levels.withOverride(Enchantments.MOB_LOOTING, lootingLevel);
    }

    /**
     * Returns the player whose progress a kill uses.
     *
     * @param killer Entity that killed the mob, or null.
     * @return The killer when it is a player, null otherwise.
     */
    @Nullable
    static Player playerOf(@Nullable Entity killer) {
        return killer instanceof Player player ? player : null;
    }

    /**
     * Drops no experience when an experience removal entry has passing global and local conditions, then
     * adds the experience drops. Vanilla fires this event inside {@code dropAllDeathLoot}, before
     * {@link LivingDropsEvent}, and only for kills that give experience (by a player), so the decision is made here,
     * with the source kept by {@link #onLivingDeath}. Runs at LOW priority so changes of other mods at NORMAL
     * priority are overridden.
     *
     * @param event Experience drop event of the dying entity.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;

        DropTable table = MobDropManager.getTable(entity.getType());
        if (!table.affectsExperience()) return;

        DamageSource source = deathSourceOf(entity);
        LootContext context = table.needsLootContext() ? createLootContext(level, entity, source) : null;
        int lootingLevel = ForgeHooks.getLootingLevel(entity, source.getEntity(), source);
        RollContext rollContext = new RollContext(killLevelsOf(source.getEntity(), lootingLevel), entity.getMaxHealth(),
                level.getRandom(), playerOf(source.getEntity()));
        event.setDroppedExperience(table.applyExperience(event.getDroppedExperience(), context, rollContext));
    }

    /**
     * Returns the damage source that killed an entity, since the experience event does not carry it.
     *
     * @param entity Dying entity. Never null.
     * @return The source kept on death, or the generic damage source when the death event was not seen.
     */
    private DamageSource deathSourceOf(LivingEntity entity) {
        DamageSource source = deathSources.get(entity);
        return source != null ? source : entity.damageSources().generic();
    }

    /**
     * Builds the loot context used to test conditions of this kill.
     *
     * @param level  Level of the dying entity. Never null.
     * @param entity Dying entity. Never null.
     * @param source Damage source that killed the entity. Never null.
     * @return A loot context with the entity, origin, damage source, killer and direct killer.
     */
    static LootContext createLootContext(ServerLevel level, LivingEntity entity, DamageSource source) {
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, source.getDirectEntity());

        if (source.getEntity() instanceof Player player) {
            builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
        }

        return new LootContext.Builder(builder.create(LootContextParamSets.ENTITY)).create(null);
    }
}
