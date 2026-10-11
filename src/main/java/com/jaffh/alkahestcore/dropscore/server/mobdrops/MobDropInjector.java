package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalRule;
import com.jaffh.alkahestcore.dropscore.server.drops.RollContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Applies the mob drop tables: on {@link LivingDropsEvent} it removes drops first, then rolls and adds dropscore
 * item drops; on {@link LivingExperienceDropEvent} it turns off the experience when a removal file asks for it and
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
        LivingEntity entity = event.getEntityLiving();
        if (!(entity.level instanceof ServerLevel)) return;
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
        LivingEntity entity = event.getEntityLiving();
        if (!(entity.level instanceof ServerLevel level)) return;
        deathSources.remove(entity);

        DropTable table = MobDropManager.getTable(entity.getType());
        if (table.isEmpty()) return;

        LootContext context = table.needsLootContext() ? createLootContext(level, entity, event.getSource()) : null;
        List<RemovalRule> activeRemovals = table.collectActiveRemovals(context);
        event.getDrops().removeIf(drop -> table.removesEventDrop(drop.getItem().getItem(), activeRemovals));

        RollContext rollContext = new RollContext(event.getLootingLevel(), entity.getMaxHealth(), level.getRandom());
        for (ItemStack stack : table.rollDrops(activeRemovals, context, rollContext)) {
            event.getDrops().add(new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), stack));
        }
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
        LivingEntity entity = event.getEntityLiving();
        if (!(entity.level instanceof ServerLevel level)) return;

        DropTable table = MobDropManager.getTable(entity.getType());
        if (!table.affectsExperience()) return;

        DamageSource source = deathSourceOf(entity);
        LootContext context = table.needsLootContext() ? createLootContext(level, entity, source) : null;
        int lootingLevel = ForgeHooks.getLootingLevel(entity, source.getEntity(), source);
        RollContext rollContext = new RollContext(lootingLevel, entity.getMaxHealth(), level.getRandom());
        event.setDroppedExperience(table.applyExperience(event.getDroppedExperience(), context, rollContext));
    }

    /**
     * Returns the damage source that killed an entity, since the experience event does not carry it.
     *
     * @param entity Dying entity. Never null.
     * @return The source kept on death, or {@link DamageSource#GENERIC} when the death event was not seen.
     */
    private DamageSource deathSourceOf(LivingEntity entity) {
        DamageSource source = deathSources.get(entity);
        return source != null ? source : DamageSource.GENERIC;
    }

    /**
     * Builds the loot context used to test conditions of this kill.
     *
     * @param level  Level of the dying entity. Never null.
     * @param entity Dying entity. Never null.
     * @param source Damage source that killed the entity. Never null.
     * @return A loot context with the entity, origin, damage source, killer and direct killer.
     */
    private static LootContext createLootContext(ServerLevel level, LivingEntity entity, DamageSource source) {
        LootContext.Builder builder = new LootContext.Builder(level)
                .withRandom(level.getRandom())
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, source.getDirectEntity());

        if (source.getEntity() instanceof Player player) {
            builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
        }

        return builder.create(LootContextParamSets.ENTITY);
    }
}
