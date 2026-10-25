package com.jaffh.alkahestcore.dropscore.server.sync;

import com.jaffh.alkahestcore.dropscore.common.drops.DropBehavior;
import com.jaffh.alkahestcore.dropscore.common.drops.DropCount;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonus;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedCondition;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedDrop;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedDropSource;
import com.jaffh.alkahestcore.dropscore.server.drops.DropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalEntry;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalRule;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Builds the full drop list of one target, as a player sees it in game: the loot table drops that no removal file
 * always removes, the vanilla experience, and every dropscore drop of the target's table (files that name it by id,
 * tag, namespace or {@code *}, after the cross-namespace removals). A removal file that only removes a drop under
 * conditions is shown as conditions of that drop: the drop needs the removal's condition to fail.
 */
final class TargetDropsBuilder {
    private final LootTableAnalyzer analyzer;
    private final ConditionDescriber describer;

    /**
     * Creates a builder for one module.
     *
     * @param analyzer  Loot table analyzer of the module. Never null.
     * @param describer Condition describer of the module. Never null.
     */
    TargetDropsBuilder(LootTableAnalyzer analyzer, ConditionDescriber describer) {
        this.analyzer = analyzer;
        this.describer = describer;
    }

    /**
     * Builds the drops of one target.
     *
     * @param targetId   Id of the mob or block. Never null.
     * @param lootTable  Id of its loot table. Never null.
     * @param table      Its dropscore table. Never null.
     * @param experience Range of its vanilla experience, or null when it gives none.
     * @return The drops: loot table, then experience, then dropscore drops.
     */
    List<SyncedDrop> build(ResourceLocation targetId, ResourceLocation lootTable, DropTable table, @Nullable DropCount experience) {
        List<SyncedDrop> drops = new ArrayList<>();
        for (SyncedDrop drop : analyzer.analyze(lootTable)) {
            Item item = drop.stack().getItem();
            if (table.removesEventDrop(item, List.of())) continue;
            drops.add(withRemovals(drop, removalsOf(table.perKillRemovals(), rule -> true, entry -> entry.removes(item))));
        }
        if (experience != null && !removesExperience(table, null)) {
            SyncedDrop drop = new SyncedDrop(ItemStack.EMPTY, 1.0, experience, DropBehavior.UNIFORM, EnchantBonus.NONE,
                    SyncedDrop.UNTRACKED, List.of(), false, SyncedDropSource.experience(targetId));
            drops.add(withRemovals(drop, removalsOf(table.experienceRemovals(), rule -> true, RemovalEntry::isExperience)));
        }
        for (DropFile file : table.dropFiles()) {
            for (DropEntry entry : file.entries()) addDropscoreDrop(drops, file, entry, table);
        }
        return drops;
    }

    /**
     * Adds one dropscore drop with its file's and its own conditions, unless an unconditional experience removal of
     * another namespace removes it.
     *
     * @param drops Drops being built. Never null.
     * @param file  File of the drop. Never null.
     * @param entry The drop. Never null.
     * @param table Dropscore table of the target. Never null.
     */
    private void addDropscoreDrop(List<SyncedDrop> drops, DropFile file, DropEntry entry, DropTable table) {
        ResourceLocation fileId = file.fileId();
        ConditionDescriber.Description removals;
        if (entry.isExperience()) {
            if (removesExperience(table, fileId)) return;
            removals = removalsOf(table.experienceRemovals(), rule -> rule.shouldRemoveExperienceOf(fileId), RemovalEntry::isExperience);
        } else {
            Item item = entry.item();
            removals = removalsOf(table.perKillRemovals(), rule -> rule.shouldRemoveDropOf(fileId, item), removal -> removal.removes(item));
        }
        List<LootItemCondition> conditions = new ArrayList<>(file.globalConditions());
        conditions.addAll(entry.conditions());
        ConditionDescriber.Description own = describer.describe(conditions);
        SyncedDrop drop = new SyncedDrop(entry.createStack(1), entry.chance(), entry.count(), entry.behavior(),
                entry.enchantBonus(), entry.tracking(), own.conditions(), own.hasHidden(), SyncedDropSource.dropscore(fileId));
        drops.add(withRemovals(drop, removals));
    }

    /**
     * Describes the conditional removals that may remove a drop, as conditions the drop needs.
     *
     * @param rules        Conditional removal rules of the target. Never null.
     * @param appliesTo    Tells whether a rule may remove this drop (namespace rule for dropscore drops). Never null.
     * @param matchesEntry Tells whether a removal entry names this drop. Never null.
     * @return The negated conditions of every matching removal; hidden when a removal cannot be negated exactly
     *         (several conditions, several matching entries, or conditions that cannot be described).
     */
    private ConditionDescriber.Description removalsOf(List<RemovalRule> rules, Predicate<RemovalRule> appliesTo,
                                                      Predicate<RemovalEntry> matchesEntry) {
        List<SyncedCondition> conditions = new ArrayList<>();
        boolean hasHidden = false;
        for (RemovalRule rule : rules) {
            if (!appliesTo.test(rule)) continue;
            List<RemovalEntry> matching = rule.entries().stream().filter(matchesEntry).toList();
            if (matching.isEmpty()) continue;
            if (matching.size() > 1) {
                hasHidden = true;
                continue;
            }
            List<LootItemCondition> removalConditions = new ArrayList<>(rule.globalConditions());
            removalConditions.addAll(matching.get(0).conditions());
            ConditionDescriber.Description negated = describer.describe(removalConditions).asRemoval();
            conditions.addAll(negated.conditions());
            hasHidden |= negated.hasHidden();
        }
        return conditions.isEmpty() && !hasHidden ? ConditionDescriber.Description.NONE
                : new ConditionDescriber.Description(conditions, hasHidden);
    }

    /**
     * Adds the conditions of the removals that may remove a drop.
     *
     * @param drop     Drop. Never null.
     * @param removals Described removals. Never null.
     * @return The drop with the removals' conditions.
     */
    private static SyncedDrop withRemovals(SyncedDrop drop, ConditionDescriber.Description removals) {
        return drop.withConditions(removals.conditions(), removals.hasHidden());
    }

    /**
     * Tells whether an experience removal always applies: its file has no global conditions and one of its
     * experience entries has no local conditions.
     *
     * @param table      Dropscore table of the target. Never null.
     * @param dropFileId File of the experience drop, or null for the vanilla experience (removed by any namespace).
     * @return True when the experience never drops.
     */
    private static boolean removesExperience(DropTable table, @Nullable ResourceLocation dropFileId) {
        for (RemovalRule removal : table.experienceRemovals()) {
            if (removal.hasGlobalConditions()) continue;
            if (dropFileId != null && !removal.shouldRemoveExperienceOf(dropFileId)) continue;
            for (RemovalEntry entry : removal.entries()) {
                if (!entry.hasConditions()) return true;
            }
        }
        return false;
    }
}
