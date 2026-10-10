package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalEntry;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalRule;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Datapack reload listener for {@code data/<namespace>/mob_drops/*.json}. Reads drop and removal files and
 * builds one immutable {@link MobDropTable} per entity type, swapped in atomically on every reload.
 */
public class MobDropManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static volatile Map<EntityType<?>, MobDropTable> TABLES = Collections.emptyMap();

    /**
     * Creates the listener for the {@code mob_drops} datapack folder.
     */
    public MobDropManager() {
        super(GSON, "mob_drops");
    }

    /**
     * Returns the precomputed drop table of an entity type.
     *
     * @param type Entity type of the dying mob. Never null.
     * @return The table, or null when no file targets this type.
     */
    @Nullable
    public static MobDropTable getTable(EntityType<?> type) {
        return TABLES.get(type);
    }

    /**
     * Reads every mob drop file and swaps in the new tables.
     *
     * @param resources       Parsed JSON of every file, keyed by file id.
     * @param resourceManager Active resource manager.
     * @param profiler        Reload profiler.
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<EntityType<?>, List<MobDropFile>> dropsByType = new IdentityHashMap<>();
        Map<EntityType<?>, List<RemovalRule>> removalsByType = new IdentityHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                MobDropJsonStructure file = GSON.fromJson(entry.getValue(), MobDropJsonStructure.class);
                readFile(fileId, file, dropsByType, removalsByType);
            } catch (UnknownIdException e) {
                LOGGER.warn("[dropscore] {}: {}, file skipped", fileId, e.getMessage());
            } catch (InvalidDropDataException e) {
                LOGGER.error("[dropscore] {}: {}, file skipped", fileId, e.getMessage());
            } catch (Exception e) {
                LOGGER.error("[dropscore] {}: unexpected error, file skipped", fileId, e);
            }
        }

        Map<EntityType<?>, MobDropTable> tables = buildTables(dropsByType, removalsByType);
        TABLES = Collections.unmodifiableMap(tables);

        int dropCount = tables.values().stream()
                .flatMap(table -> table.dropFiles().stream())
                .mapToInt(file -> file.entries().size())
                .sum();
        LOGGER.info("[dropscore] Loaded {} mob drops for {} entity types.", dropCount, tables.size());
    }

    /**
     * Reads one file as either a drop file or a removal file and adds it to the build maps.
     *
     * @param fileId         Id of the file. Never null.
     * @param file           Parsed DTO, or null for an empty file.
     * @param dropsByType    Drop files being built, keyed by entity type.
     * @param removalsByType Removal rules being built, keyed by entity type.
     * @throws InvalidDropDataException If the file kind, target or global conditions are invalid.
     */
    private static void readFile(
            ResourceLocation fileId,
            @Nullable MobDropJsonStructure file,
            Map<EntityType<?>, List<MobDropFile>> dropsByType,
            Map<EntityType<?>, List<RemovalRule>> removalsByType
    ) throws InvalidDropDataException {
        if (file == null) {
            throw new InvalidDropDataException("file is empty");
        }

        boolean isDropFile = file.drops != null;
        boolean isRemovalFile = file.removals != null || file.shouldRemoveExperience;
        if (isDropFile && isRemovalFile) {
            throw new InvalidDropDataException("a file cannot have both 'drops' and 'removals'/'remove_experience'; split it into two files");
        }
        if (!isDropFile && !isRemovalFile) {
            LOGGER.warn("[dropscore] {}: no 'drops', 'removals' or 'remove_experience', file skipped", fileId);
            return;
        }

        EntityType<?> type = MobDropParser.resolveEntity(file.entity);
        if (isDropFile) {
            dropsByType.computeIfAbsent(type, key -> new ArrayList<>()).add(readDrops(fileId, file));
        } else {
            removalsByType.computeIfAbsent(type, key -> new ArrayList<>()).add(readRemoval(fileId, file));
        }
    }

    /**
     * Reads a drop file, skipping each invalid drop with an error.
     *
     * @param fileId Id of the file. Never null.
     * @param file   Parsed drop file. Never null.
     * @return The drop file with its valid drops.
     * @throws InvalidDropDataException If the global conditions are invalid.
     */
    private static MobDropFile readDrops(ResourceLocation fileId, MobDropJsonStructure file) throws InvalidDropDataException {
        List<DropEntry> entries = new ArrayList<>(file.drops.size());
        for (int i = 0; i < file.drops.size(); i++) {
            try {
                entries.add(MobDropParser.parseDrop(file.drops.get(i)));
            } catch (InvalidDropDataException e) {
                LOGGER.error("[dropscore] {}: drops[{}]: {}, drop skipped", fileId, i, e.getMessage());
            }
        }
        return new MobDropFile(fileId, MobDropParser.parseConditions(file.conditions), entries);
    }

    /**
     * Reads a removal file. Unknown items are skipped with a warning so optional mods do not break the file;
     * other invalid entries are skipped with an error.
     *
     * @param fileId Id of the file. Never null.
     * @param file   Parsed removal file. Never null.
     * @return The removal rule of the file.
     * @throws InvalidDropDataException If the global conditions are invalid.
     */
    private static RemovalRule readRemoval(ResourceLocation fileId, MobDropJsonStructure file) throws InvalidDropDataException {
        List<JsonElement> elements = file.removals != null ? file.removals : List.of();
        List<RemovalEntry> entries = new ArrayList<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            try {
                entries.add(MobDropParser.parseRemovalEntry(elements.get(i)));
            } catch (UnknownIdException e) {
                LOGGER.warn("[dropscore] {}: removals[{}]: {}, entry skipped", fileId, i, e.getMessage());
            } catch (InvalidDropDataException e) {
                LOGGER.error("[dropscore] {}: removals[{}]: {}, entry skipped", fileId, i, e.getMessage());
            }
        }
        return new RemovalRule(fileId, MobDropParser.parseConditions(file.conditions), file.shouldRemoveExperience, entries);
    }

    /**
     * Builds the table of every targeted entity type. Unconditional removal entries are applied to the drop
     * files here, once, against the original drop set, so the result does not depend on file load order.
     *
     * @param dropsByType    Drop files keyed by entity type. Never null.
     * @param removalsByType Removal rules keyed by entity type. Never null.
     * @return A new identity map of tables.
     */
    private static Map<EntityType<?>, MobDropTable> buildTables(
            Map<EntityType<?>, List<MobDropFile>> dropsByType,
            Map<EntityType<?>, List<RemovalRule>> removalsByType
    ) {
        Set<EntityType<?>> types = Collections.newSetFromMap(new IdentityHashMap<>());
        types.addAll(dropsByType.keySet());
        types.addAll(removalsByType.keySet());

        Map<EntityType<?>, MobDropTable> tables = new IdentityHashMap<>();
        for (EntityType<?> type : types) {
            List<RemovalRule> unconditional = new ArrayList<>();
            List<RemovalRule> conditional = new ArrayList<>();
            for (RemovalRule removal : removalsByType.getOrDefault(type, List.of())) {
                splitRemoval(removal, unconditional, conditional);
            }

            List<MobDropFile> dropFiles = dropsByType.getOrDefault(type, List.of()).stream()
                    .map(file -> applyRemovals(file, unconditional))
                    .filter(file -> !file.entries().isEmpty())
                    .toList();

            Set<Item> removedItems = new HashSet<>();
            unconditional.forEach(removal -> removal.entries().forEach(entry -> removedItems.add(entry.item())));

            tables.put(type, MobDropTable.of(dropFiles, removedItems, conditional));
        }
        return tables;
    }

    /**
     * Splits a removal rule into the part that always applies and the part that must be tested per kill.
     * A rule with global conditions is fully conditional; otherwise only its entries with local conditions are.
     *
     * @param removal       Rule to split. Never null.
     * @param unconditional Receives the always-applying part, when it has entries.
     * @param conditional   Receives the per-kill part, when it has entries.
     */
    private static void splitRemoval(RemovalRule removal, List<RemovalRule> unconditional, List<RemovalRule> conditional) {
        if (removal.hasGlobalConditions()) {
            if (!removal.entries().isEmpty()) conditional.add(removal);
            return;
        }

        List<RemovalEntry> always = removal.entries().stream().filter(entry -> !entry.hasConditions()).toList();
        List<RemovalEntry> tested = removal.entries().stream().filter(RemovalEntry::hasConditions).toList();
        if (!always.isEmpty()) unconditional.add(removal.withEntries(always));
        if (!tested.isEmpty()) conditional.add(removal.withEntries(tested));
    }

    /**
     * Removes from a drop file every drop that an unconditional removal of another namespace removes.
     *
     * @param file          Drop file. Never null.
     * @param unconditional Always-applying removal rules of the same entity type. Never null.
     * @return The drop file with the remaining drops.
     */
    private static MobDropFile applyRemovals(MobDropFile file, List<RemovalRule> unconditional) {
        List<DropEntry> kept = file.entries().stream()
                .filter(entry -> unconditional.stream()
                        .noneMatch(removal -> removal.shouldRemoveDropOf(file.fileId(), entry.item())))
                .toList();
        return file.withEntries(kept);
    }
}
