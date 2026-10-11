package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents everything dropscore does for one target (entity type or block), built the first time the target
 * drops after a reload, once tags are bound. Unconditional removals are already applied to the drop files here,
 * against the original drop set, so the result does not depend on file load order.
 *
 * @param dropFiles          Drop files left after unconditional removals of other namespaces. Immutable, never null.
 * @param removedItems       Items unconditional single-item entries remove from the event drops. Immutable, never null.
 * @param removedTargets     Tag and {@code *} targets unconditional entries remove from the event drops.
 *                           Immutable, never null.
 * @param perKillRemovals    Removal files with global or local conditions, tested per drop. Immutable, never null.
 * @param experienceRemovals Removal files with {@code remove_experience}, gated by their global conditions only.
 *                           Immutable, never null.
 * @param needsLootContext   Whether any condition must be tested, so a drop must build a loot context.
 */
public record DropTable(
        List<DropFile> dropFiles,
        Set<Item> removedItems,
        List<ItemTarget> removedTargets,
        List<RemovalRule> perKillRemovals,
        List<RemovalRule> experienceRemovals,
        boolean needsLootContext
) {
    /** A table that does nothing. */
    public static final DropTable EMPTY = new DropTable(List.of(), Set.of(), List.of(), List.of(), List.of(), false);

    /**
     * Builds the table of one target from every rule that matches it.
     *
     * @param dropFiles Drop files matching the target. Never null.
     * @param removals  Removal files matching the target. Never null.
     * @return The new table.
     */
    public static DropTable build(List<DropFile> dropFiles, List<RemovalRule> removals) {
        List<RemovalRule> unconditional = new ArrayList<>();
        List<RemovalRule> perKill = new ArrayList<>();
        List<RemovalRule> experience = new ArrayList<>();
        for (RemovalRule removal : removals) {
            if (removal.shouldRemoveExperience()) experience.add(removal);
            splitRemoval(removal, unconditional, perKill);
        }

        List<DropFile> keptFiles = dropFiles.stream()
                .map(file -> applyRemovals(file, unconditional))
                .filter(file -> !file.entries().isEmpty())
                .toList();

        Set<Item> removedItems = new HashSet<>();
        List<ItemTarget> removedTargets = new ArrayList<>();
        for (RemovalRule removal : unconditional) {
            for (RemovalEntry entry : removal.entries()) {
                if (entry.target() instanceof ItemTarget.Exact exact) {
                    removedItems.add(exact.item());
                } else {
                    removedTargets.add(entry.target());
                }
            }
        }

        boolean needsLootContext = keptFiles.stream().anyMatch(DropFile::hasAnyConditions)
                || perKill.stream().anyMatch(RemovalRule::hasAnyConditions)
                || experience.stream().anyMatch(RemovalRule::hasGlobalConditions);
        return new DropTable(
                keptFiles,
                Set.copyOf(removedItems),
                List.copyOf(removedTargets),
                List.copyOf(perKill),
                List.copyOf(experience),
                needsLootContext
        );
    }

    /**
     * Splits the item entries of a removal rule into the part applied once when the table is built and the part
     * tested per drop. Entries are applied at build time only when neither the file nor the entry has conditions.
     *
     * @param removal       Rule to split. Never null.
     * @param unconditional Receives the build-time part, when it has entries.
     * @param perKill       Receives the per-drop part, when it has entries.
     */
    private static void splitRemoval(RemovalRule removal, List<RemovalRule> unconditional, List<RemovalRule> perKill) {
        if (removal.entries().isEmpty()) return;
        if (removal.hasGlobalConditions()) {
            perKill.add(removal);
            return;
        }

        List<RemovalEntry> atBuild = removal.entries().stream().filter(entry -> !entry.hasConditions()).toList();
        List<RemovalEntry> tested = removal.entries().stream().filter(RemovalEntry::hasConditions).toList();
        if (!atBuild.isEmpty()) unconditional.add(removal.withEntries(atBuild));
        if (!tested.isEmpty()) perKill.add(removal.withEntries(tested));
    }

    /**
     * Removes from a drop file every drop that an unconditional removal of another namespace removes.
     *
     * @param file          Drop file. Never null.
     * @param unconditional Build-time removal rules of the same target. Never null.
     * @return The drop file with the remaining drops.
     */
    private static DropFile applyRemovals(DropFile file, List<RemovalRule> unconditional) {
        List<DropEntry> kept = file.entries().stream()
                .filter(entry -> unconditional.stream()
                        .noneMatch(removal -> removal.shouldRemoveDropOf(file.fileId(), entry.item())))
                .toList();
        return file.withEntries(kept);
    }

    /**
     * Tells whether this table does nothing, so a drop can skip it without building a loot context.
     *
     * @return True when the table has no drop, removal or experience rule.
     */
    public boolean isEmpty() {
        return dropFiles.isEmpty() && removedItems.isEmpty() && removedTargets.isEmpty()
                && perKillRemovals.isEmpty() && experienceRemovals.isEmpty();
    }

    /**
     * Returns the part of each per-drop removal file that applies: files whose global conditions fail are
     * left out, and only the entries whose local conditions pass are kept.
     *
     * @param context Loot context, non-null whenever {@link #needsLootContext()} is true.
     * @return The applying removals, each holding only its passing entries; an empty immutable list when none apply.
     */
    public List<RemovalRule> collectActiveRemovals(@Nullable LootContext context) {
        if (perKillRemovals.isEmpty()) return List.of();

        List<RemovalRule> active = new ArrayList<>(perKillRemovals.size());
        for (RemovalRule removal : perKillRemovals) {
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
     * Tells whether a drop that was already there (vanilla loot table or another mod) must be removed.
     *
     * @param item           Dropped item. Never null.
     * @param activeRemovals Removals returned by {@link #collectActiveRemovals}. Never null.
     * @return True when an unconditional or an active removal names the item.
     */
    public boolean removesEventDrop(Item item, List<RemovalRule> activeRemovals) {
        if (removedItems.contains(item)) return true;
        for (ItemTarget target : removedTargets) {
            if (target.matches(item)) return true;
        }
        for (RemovalRule removal : activeRemovals) {
            if (removal.removes(item)) return true;
        }
        return false;
    }

    /**
     * Rolls every drop whose global and local conditions pass and that no active removal of another namespace
     * removes. Global conditions are tested once per file.
     *
     * @param activeRemovals Removals returned by {@link #collectActiveRemovals}. Never null.
     * @param lootContext    Loot context, non-null whenever {@link #needsLootContext()} is true.
     * @param rollContext    Enchantment level, max health and random source of the drop. Never null.
     * @return The rolled stacks, none of them empty.
     */
    public List<ItemStack> rollDrops(List<RemovalRule> activeRemovals, @Nullable LootContext lootContext, RollContext rollContext) {
        if (dropFiles.isEmpty()) return List.of();

        List<ItemStack> stacks = new ArrayList<>();
        for (DropFile file : dropFiles) {
            if (file.hasGlobalConditions() && !LootConditions.testAll(file.globalConditions(), lootContext)) continue;

            for (DropEntry entry : file.entries()) {
                if (isDropRemoved(file.fileId(), entry.item(), activeRemovals)) continue;
                if (entry.hasConditions() && !LootConditions.testAll(entry.conditions(), lootContext)) continue;

                ItemStack stack = DropRoller.roll(entry, rollContext);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
        }
        return stacks;
    }

    /**
     * Tells whether an active removal of another namespace removes a dropscore drop.
     *
     * @param dropFileId     Id of the drop file that adds the drop. Never null.
     * @param item           Dropped item. Never null.
     * @param activeRemovals Active removals. Never null.
     * @return True when the drop must not be rolled.
     */
    private static boolean isDropRemoved(ResourceLocation dropFileId, Item item, List<RemovalRule> activeRemovals) {
        for (RemovalRule removal : activeRemovals) {
            if (removal.shouldRemoveDropOf(dropFileId, item)) return true;
        }
        return false;
    }

    /**
     * Tells whether a removal file turns off the experience of this drop.
     *
     * @param context Loot context, non-null whenever {@link #needsLootContext()} is true.
     * @return True when a file with {@code remove_experience} has passing global conditions.
     */
    public boolean shouldRemoveExperience(@Nullable LootContext context) {
        for (RemovalRule removal : experienceRemovals) {
            if (LootConditions.testAll(removal.globalConditions(), context)) return true;
        }
        return false;
    }
}
