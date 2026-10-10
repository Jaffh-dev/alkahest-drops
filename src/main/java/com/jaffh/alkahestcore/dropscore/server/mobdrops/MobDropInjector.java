package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.jaffh.alkahestcore.dropscore.common.drops.DropBehaviour;
import com.jaffh.alkahestcore.dropscore.common.drops.DropCount;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonus;
import com.jaffh.alkahestcore.dropscore.server.drops.LootConditions;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalEntry;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalRule;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Applies the mob drop tables on {@link LivingDropsEvent}: removes drops first, then rolls and adds dropscore drops.
 */
public class MobDropInjector {

    /**
     * Handles a mob death. Runs at LOW priority so drops added by other mods at NORMAL priority can be removed.
     *
     * @param event Drops event of the dying entity.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (!(entity.level instanceof ServerLevel level)) return;

        MobDropTable table = MobDropManager.getTable(entity.getType());
        if (table == null) return;

        LootContext context = table.needsLootContext() ? createLootContext(level, event) : null;
        List<RemovalRule> activeRemovals = collectActiveRemovals(table.conditionalRemovals(), context);

        removeEventDrops(event.getDrops(), table.removedItems(), activeRemovals);
        addDrops(event, table.dropFiles(), activeRemovals, context);
    }

    /**
     * Builds the loot context used to test conditions of this kill.
     *
     * @param level Level of the dying entity.
     * @param event Drops event of the dying entity.
     * @return A loot context with the entity, origin, damage source, killer and direct killer.
     */
    private static LootContext createLootContext(ServerLevel level, LivingDropsEvent event) {
        LootContext.Builder builder = new LootContext.Builder(level)
                .withRandom(level.getRandom())
                .withParameter(LootContextParams.THIS_ENTITY, event.getEntityLiving())
                .withParameter(LootContextParams.ORIGIN, event.getEntityLiving().position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, event.getSource().getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, event.getSource().getDirectEntity());

        if (event.getSource().getEntity() instanceof Player player) {
            builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
        }

        return builder.create(LootContextParamSets.ENTITY);
    }

    /**
     * Returns the part of each conditional removal file that applies to this kill: files whose global
     * conditions fail are dropped, and only the entries whose local conditions pass are kept.
     *
     * @param conditionalRemovals Removal files tested per kill. Never null.
     * @param context             Loot context, non-null whenever the list is not empty.
     * @return The applying removals, each holding only its passing entries; an empty immutable list when none apply.
     */
    private static List<RemovalRule> collectActiveRemovals(List<RemovalRule> conditionalRemovals, @Nullable LootContext context) {
        if (conditionalRemovals.isEmpty()) return List.of();

        List<RemovalRule> active = new ArrayList<>(conditionalRemovals.size());
        for (RemovalRule removal : conditionalRemovals) {
            if (!LootConditions.testAll(removal.globalConditions(), context)) continue;

            List<RemovalEntry> passing = new ArrayList<>(removal.entries().size());
            for (RemovalEntry entry : removal.entries()) {
                if (LootConditions.testAll(entry.conditions(), context)) {
                    passing.add(entry);
                }
            }
            if (!passing.isEmpty()) {
                active.add(removal.withEntries(passing));
            }
        }
        return active;
    }

    /**
     * Removes matching drops already in the event (vanilla loot table and other mods).
     *
     * @param drops          Mutable drops of the event.
     * @param removedItems   Items removed unconditionally. Never null.
     * @param activeRemovals Conditional removals that passed. Never null.
     */
    private static void removeEventDrops(Collection<ItemEntity> drops, Set<Item> removedItems, List<RemovalRule> activeRemovals) {
        if (removedItems.isEmpty() && activeRemovals.isEmpty()) return;

        Iterator<ItemEntity> iterator = drops.iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next().getItem().getItem();
            if (removedItems.contains(item) || isRemovedByAny(activeRemovals, item)) {
                iterator.remove();
            }
        }
    }

    /**
     * Tells whether any active removal lists an item.
     *
     * @param activeRemovals Conditional removals that passed. Never null.
     * @param item           Item to check.
     * @return True when one of the removals lists the item.
     */
    private static boolean isRemovedByAny(List<RemovalRule> activeRemovals, Item item) {
        for (RemovalRule activeRemoval : activeRemovals) {
            if (activeRemoval.removes(item)) return true;
        }
        return false;
    }

    /**
     * Rolls every drop whose global and local conditions pass and that no active removal of another
     * namespace removes, and adds the resulting stacks to the event. Global conditions are tested once per file.
     *
     * @param event          Drops event of the dying entity.
     * @param dropFiles      Drop files of the entity type. Never null.
     * @param activeRemovals Conditional removals that passed. Never null.
     * @param context        Loot context, non-null whenever a file or drop has conditions.
     */
    private static void addDrops(LivingDropsEvent event, List<MobDropFile> dropFiles, List<RemovalRule> activeRemovals, @Nullable LootContext context) {
        LivingEntity entity = event.getEntityLiving();
        Random random = entity.level.getRandom();
        int lootingLevel = event.getLootingLevel();

        for (MobDropFile file : dropFiles) {
            if (file.hasGlobalConditions() && !LootConditions.testAll(file.globalConditions(), context)) continue;

            for (DropEntry entry : file.entries()) {
                if (isDropRemoved(file.fileId(), entry.item(), activeRemovals)) continue;
                if (entry.hasConditions() && !LootConditions.testAll(entry.conditions(), context)) continue;

                ItemStack stack = roll(entry, lootingLevel, random);
                if (!stack.isEmpty()) {
                    event.getDrops().add(new ItemEntity(entity.level, entity.getX(), entity.getY(), entity.getZ(), stack));
                }
            }
        }
    }

    /**
     * Tells whether an active removal of another namespace removes a dropscore drop.
     *
     * @param dropFileId     Id of the drop file that adds the drop.
     * @param item           Dropped item.
     * @param activeRemovals Conditional removals that passed. Never null.
     * @return True when the drop must not be rolled.
     */
    private static boolean isDropRemoved(ResourceLocation dropFileId, Item item, List<RemovalRule> activeRemovals) {
        for (RemovalRule activeRemoval : activeRemovals) {
            if (activeRemoval.shouldRemoveDropOf(dropFileId, item)) return true;
        }
        return false;
    }

    /**
     * Rolls one drop: chance first, then the count with the Looting bonuses applied.
     *
     * @param entry        Drop to roll.
     * @param lootingLevel Looting level of the kill, 0 for none.
     * @param random       Random source of the level.
     * @return The dropped stack, or {@link ItemStack#EMPTY} when the drop fails.
     */
    private static ItemStack roll(DropEntry entry, int lootingLevel, Random random) {
        EnchantBonus bonus = entry.enchantBonus();
        double chance = bonus.chance().applyTo(entry.chance(), lootingLevel);
        if (random.nextDouble() >= chance) return ItemStack.EMPTY;

        DropCount range = entry.count().withBonus(bonus.count(), lootingLevel);
        DropBehaviour behaviour = entry.behaviour().withBonus(bonus.roll(), lootingLevel);
        return entry.createStack(behaviour.type().rollCount(range, behaviour, random));
    }
}
