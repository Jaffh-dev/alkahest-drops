package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.jaffh.alkahestcore.dropscore.common.DropscoreConfig;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.server.drops.DropDataException;
import com.jaffh.alkahestcore.dropscore.server.drops.DropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.DropFileFormat;
import com.jaffh.alkahestcore.dropscore.server.drops.DropFileParser;
import com.jaffh.alkahestcore.dropscore.server.drops.DropIssue;
import com.jaffh.alkahestcore.dropscore.server.drops.DropIssueLog;
import com.jaffh.alkahestcore.dropscore.server.drops.DropLimits;
import com.jaffh.alkahestcore.dropscore.server.drops.DropValues;
import com.jaffh.alkahestcore.dropscore.server.drops.ItemTarget;
import com.jaffh.alkahestcore.dropscore.server.drops.ParseContext;
import com.jaffh.alkahestcore.dropscore.server.drops.ParsedDropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalEntry;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalRule;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
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
 * Keeps the problems of the last load for {@code /dropscore validate}.
 */
public class MobDropManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static volatile Map<EntityType<?>, MobDropTable> TABLES = Collections.emptyMap();
    private static volatile List<DropIssue> ISSUES = List.of();
    /**
     * Files waiting for the server config. When a world opens, Forge loads the world's datapacks before its
     * server config, so the first load waits for {@link #onConfigLoading} to read the configured limits.
     */
    @Nullable
    private static volatile Map<ResourceLocation, JsonElement> PENDING_FILES;

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
     * Returns the problems found by the last load.
     *
     * @return An immutable list, empty when every file was valid.
     */
    public static List<DropIssue> getIssues() {
        return ISSUES;
    }

    /**
     * Loads the files waiting for the dropscore server config, once Forge loads it.
     *
     * @param event Config loading event of the mod bus.
     */
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != DropscoreConfig.SPEC) return;
        Map<ResourceLocation, JsonElement> pending = PENDING_FILES;
        if (pending == null) return;
        PENDING_FILES = null;
        load(pending, DropLimits.fromConfig());
    }

    /**
     * Reads every mob drop file and swaps in the new tables, or keeps the files until the server config loads.
     *
     * @param resources       Parsed JSON of every file, keyed by file id.
     * @param resourceManager Active resource manager.
     * @param profiler        Reload profiler.
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (!DropscoreConfig.SPEC.isLoaded()) {
            PENDING_FILES = Map.copyOf(resources);
            TABLES = Collections.emptyMap();
            ISSUES = List.of();
            LOGGER.info("[dropscore] Waiting for the server config to load mob drops.");
            return;
        }
        PENDING_FILES = null;
        DropscoreConfig.reloadFromDisk();
        load(resources, DropLimits.fromConfig());
    }

    /**
     * Parses every file, builds the tables and swaps them in with the problems found.
     *
     * @param resources Parsed JSON of every file, keyed by file id. Never null.
     * @param limits    Config limits to clamp values to. Never null.
     */
    private static void load(Map<ResourceLocation, JsonElement> resources, DropLimits limits) {
        DropIssueLog log = new DropIssueLog();
        DropFileParser parser = new DropFileParser(DropFileFormat.MOB, limits, log);
        Map<EntityType<?>, List<DropFile>> dropsByType = new IdentityHashMap<>();
        Map<EntityType<?>, List<RemovalRule>> removalsByType = new IdentityHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            parser.parse(entry.getKey(), entry.getValue())
                    .ifPresent(parsed -> addFile(parsed, dropsByType, removalsByType, log));
        }

        Map<EntityType<?>, MobDropTable> tables = buildTables(dropsByType, removalsByType);
        TABLES = Collections.unmodifiableMap(tables);
        ISSUES = log.issues();

        int dropCount = tables.values().stream()
                .flatMap(table -> table.dropFiles().stream())
                .mapToInt(file -> file.entries().size())
                .sum();
        LOGGER.info("[dropscore] Loaded {} mob drops for {} entity types ({} errors, {} warnings).",
                dropCount, tables.size(), log.count(DropIssue.Severity.ERROR), log.count(DropIssue.Severity.WARNING));
    }

    /**
     * Resolves the target of a parsed file and adds it to the build maps, reporting a bad target.
     *
     * @param parsed         Parsed file. Never null.
     * @param dropsByType    Drop files being built, keyed by entity type.
     * @param removalsByType Removal rules being built, keyed by entity type.
     * @param log            Problem collector of the reload.
     */
    private static void addFile(
            ParsedDropFile parsed,
            Map<EntityType<?>, List<DropFile>> dropsByType,
            Map<EntityType<?>, List<RemovalRule>> removalsByType,
            DropIssueLog log
    ) {
        try {
            EntityType<?> type = resolveEntity(parsed, new ParseContext(parsed.fileId(), "entity", log));
            if (parsed instanceof ParsedDropFile.Drops drops) {
                dropsByType.computeIfAbsent(type, key -> new ArrayList<>()).add(drops.file());
            } else if (parsed instanceof ParsedDropFile.Removals removals) {
                removalsByType.computeIfAbsent(type, key -> new ArrayList<>()).add(removals.rule());
            }
        } catch (DropDataException e) {
            log.report(e.toIssue("file skipped"));
        }
    }

    /**
     * Resolves the target entity type of a file.
     *
     * @param parsed Parsed file. Never null.
     * @param ctx    Context of the {@code entity} key. Never null.
     * @return The registered entity type.
     * @throws DropDataException If the id is malformed or a removal file targets players (error),
     *                           or the entity is not registered (warning).
     */
    private static EntityType<?> resolveEntity(ParsedDropFile parsed, ParseContext ctx) throws DropDataException {
        ResourceLocation id = DropValues.parseId(parsed.target(), ctx);
        if (!ForgeRegistries.ENTITIES.containsKey(id)) throw ctx.unknownId("'" + id + "' is not a registered entity");
        EntityType<?> type = ForgeRegistries.ENTITIES.getValue(id);
        if (type == EntityType.PLAYER && parsed instanceof ParsedDropFile.Removals) {
            throw ctx.error("removal files cannot target players, a player's drops are their inventory");
        }
        return type;
    }

    /**
     * Builds the table of every targeted entity type. Unconditional single-item removal entries are applied to
     * the drop files here, once, against the original drop set, so the result does not depend on file load order.
     *
     * @param dropsByType    Drop files keyed by entity type. Never null.
     * @param removalsByType Removal rules keyed by entity type. Never null.
     * @return A new identity map of tables.
     */
    private static Map<EntityType<?>, MobDropTable> buildTables(
            Map<EntityType<?>, List<DropFile>> dropsByType,
            Map<EntityType<?>, List<RemovalRule>> removalsByType
    ) {
        Set<EntityType<?>> types = Collections.newSetFromMap(new IdentityHashMap<>());
        types.addAll(dropsByType.keySet());
        types.addAll(removalsByType.keySet());

        Map<EntityType<?>, MobDropTable> tables = new IdentityHashMap<>();
        for (EntityType<?> type : types) {
            List<RemovalRule> unconditional = new ArrayList<>();
            List<RemovalRule> perKill = new ArrayList<>();
            for (RemovalRule removal : removalsByType.getOrDefault(type, List.of())) {
                splitRemoval(removal, unconditional, perKill);
            }

            List<DropFile> dropFiles = dropsByType.getOrDefault(type, List.of()).stream()
                    .map(file -> applyRemovals(file, unconditional))
                    .filter(file -> !file.entries().isEmpty())
                    .toList();

            Set<Item> removedItems = new HashSet<>();
            for (RemovalRule removal : unconditional) {
                for (RemovalEntry entry : removal.entries()) {
                    removedItems.add(((ItemTarget.Exact) entry.target()).item());
                }
            }

            tables.put(type, MobDropTable.of(dropFiles, removedItems, perKill));
        }
        return tables;
    }

    /**
     * Splits a removal rule into the part applied once at reload and the part tested per kill. Only single-item
     * entries without global or local conditions are applied at reload; tag and {@code *} entries are tested per
     * kill because tags are bound after reload listeners run.
     *
     * @param removal       Rule to split. Never null.
     * @param unconditional Receives the reload-time part, when it has entries.
     * @param perKill       Receives the per-kill part, when it has entries.
     */
    private static void splitRemoval(RemovalRule removal, List<RemovalRule> unconditional, List<RemovalRule> perKill) {
        if (removal.hasGlobalConditions()) {
            if (!removal.entries().isEmpty()) perKill.add(removal);
            return;
        }

        List<RemovalEntry> atReload = removal.entries().stream().filter(MobDropManager::isAppliedAtReload).toList();
        List<RemovalEntry> tested = removal.entries().stream().filter(entry -> !isAppliedAtReload(entry)).toList();
        if (!atReload.isEmpty()) unconditional.add(removal.withEntries(atReload));
        if (!tested.isEmpty()) perKill.add(removal.withEntries(tested));
    }

    /**
     * Tells whether a removal entry of a rule without global conditions can be applied once at reload.
     *
     * @param entry Removal entry. Never null.
     * @return True for a single item without local conditions.
     */
    private static boolean isAppliedAtReload(RemovalEntry entry) {
        return !entry.hasConditions() && entry.target() instanceof ItemTarget.Exact;
    }

    /**
     * Removes from a drop file every drop that an unconditional removal of another namespace removes.
     *
     * @param file          Drop file. Never null.
     * @param unconditional Reload-time removal rules of the same entity type. Never null.
     * @return The drop file with the remaining drops.
     */
    private static DropFile applyRemovals(DropFile file, List<RemovalRule> unconditional) {
        List<DropEntry> kept = file.entries().stream()
                .filter(entry -> unconditional.stream()
                        .noneMatch(removal -> removal.shouldRemoveDropOf(file.fileId(), entry.item())))
                .toList();
        return file.withEntries(kept);
    }
}
