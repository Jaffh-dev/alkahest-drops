package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.jaffh.alkahestcore.dropscore.common.DropscoreConfig;
import com.jaffh.alkahestcore.dropscore.server.drops.DropDataException;
import com.jaffh.alkahestcore.dropscore.server.drops.DropFileFormat;
import com.jaffh.alkahestcore.dropscore.server.drops.DropFileParser;
import com.jaffh.alkahestcore.dropscore.server.drops.DropIssue;
import com.jaffh.alkahestcore.dropscore.server.drops.DropIssueLog;
import com.jaffh.alkahestcore.dropscore.server.drops.DropLimits;
import com.jaffh.alkahestcore.dropscore.server.drops.DropRuleSet;
import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.jaffh.alkahestcore.dropscore.server.drops.ParseContext;
import com.jaffh.alkahestcore.dropscore.server.drops.ParsedDropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.TargetMatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Datapack reload listener for {@code data/<namespace>/mob_drops/*.json}. Reads drop and removal files into an
 * immutable {@link DropRuleSet} indexed by entity type, swapped in atomically on every reload.
 * Keeps the problems of the last load for {@code /dropscore validate}.
 */
public class MobDropManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Patterns ({@code mod:*}, {@code #tag}, {@code *}) never match players: their drops are their inventory. */
    private static final TargetMatcher<EntityType<?>> MATCHER =
            new TargetMatcher<>(ForgeRegistries.ENTITIES, EntityType::is, type -> type != EntityType.PLAYER);

    private static volatile DropRuleSet<EntityType<?>> RULES = DropRuleSet.empty(MATCHER);
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
     * Returns the drop table of an entity type, built on its first lookup after a reload.
     *
     * @param type Entity type of the dying mob. Never null.
     * @return The table, {@link DropTable#EMPTY} when no file targets this type.
     */
    public static DropTable getTable(EntityType<?> type) {
        return RULES.tableOf(type);
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
     * Forgets the cached tables when tags change, so {@code #tag} targets and tag removals read the new tags.
     *
     * @param event Tags update event of the Forge bus.
     */
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        RULES.clearCache();
    }

    /**
     * Reads every mob drop file and swaps in the new rule set, or keeps the files until the server config loads.
     *
     * @param resources       Parsed JSON of every file, keyed by file id.
     * @param resourceManager Active resource manager.
     * @param profiler        Reload profiler.
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (!DropscoreConfig.SPEC.isLoaded()) {
            PENDING_FILES = Map.copyOf(resources);
            RULES = DropRuleSet.empty(MATCHER);
            ISSUES = List.of();
            LOGGER.info("[dropscore] Waiting for the server config to load mob drops.");
            return;
        }
        PENDING_FILES = null;
        DropscoreConfig.reloadFromDisk();
        load(resources, DropLimits.fromConfig());
    }

    /**
     * Parses every file, indexes them by target and swaps in the new rule set with the problems found.
     *
     * @param resources Parsed JSON of every file, keyed by file id. Never null.
     * @param limits    Config limits to clamp values to. Never null.
     */
    private static void load(Map<ResourceLocation, JsonElement> resources, DropLimits limits) {
        DropIssueLog log = new DropIssueLog();
        DropFileParser parser = new DropFileParser(DropFileFormat.MOB, limits, log);
        DropRuleSet.Builder<EntityType<?>> builder = new DropRuleSet.Builder<>(MATCHER);
        int dropFileCount = 0;
        int dropCount = 0;
        int removalFileCount = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ParsedDropFile parsed = parser.parse(entry.getKey(), entry.getValue()).orElse(null);
            if (parsed == null || !addFile(parsed, builder, log)) continue;
            if (parsed instanceof ParsedDropFile.Drops drops) {
                dropFileCount++;
                dropCount += drops.file().entries().size();
            } else {
                removalFileCount++;
            }
        }

        RULES = builder.build();
        ISSUES = log.issues();
        LOGGER.info("[dropscore] Loaded {} mob drops from {} drop files and {} removal files ({} errors, {} warnings).",
                dropCount, dropFileCount, removalFileCount,
                log.count(DropIssue.Severity.ERROR), log.count(DropIssue.Severity.WARNING));
    }

    /**
     * Resolves the target of a parsed file and adds the file to the rule set, reporting a bad target.
     *
     * @param parsed  Parsed file. Never null.
     * @param builder Rule set being built. Never null.
     * @param log     Problem collector of the reload. Never null.
     * @return True when the file was added.
     */
    private static boolean addFile(ParsedDropFile parsed, DropRuleSet.Builder<EntityType<?>> builder, DropIssueLog log) {
        try {
            TargetMatcher.Target<EntityType<?>> target = resolveTarget(parsed, new ParseContext(parsed.fileId(), "entity", log));
            if (parsed instanceof ParsedDropFile.Drops drops) {
                builder.addDrops(target, drops.file());
            } else if (parsed instanceof ParsedDropFile.Removals removals) {
                builder.addRemovals(target, removals.rule());
            }
            return true;
        } catch (DropDataException e) {
            log.report(e.toIssue("file skipped"));
            return false;
        }
    }

    /**
     * Resolves the target of a file: an entity id, {@code mod:*}, {@code #tag} or {@code *}.
     *
     * @param parsed Parsed file. Never null.
     * @param ctx    Context of the {@code entity} key. Never null.
     * @return The parsed target.
     * @throws DropDataException If the target is malformed or a removal file names players (error),
     *                           or the target names nothing registered (warning).
     */
    private static TargetMatcher.Target<EntityType<?>> resolveTarget(ParsedDropFile parsed, ParseContext ctx) throws DropDataException {
        TargetMatcher.Target<EntityType<?>> target = MATCHER.parse(parsed.target(), ctx);
        if (parsed instanceof ParsedDropFile.Removals
                && target instanceof TargetMatcher.Target.Values<EntityType<?>> values
                && values.values().contains(EntityType.PLAYER)) {
            throw ctx.error("removal files cannot target players, a player's drops are their inventory");
        }
        return target;
    }
}
