package com.jaffh.alkahestcore.dropscore.server.drops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.jaffh.alkahestcore.dropscore.common.DropscoreConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Datapack reload listener shared by the drop modules. Reads drop and removal files of one folder into an
 * immutable {@link DropRuleSet}, swapped in atomically on every reload, and keeps the problems of the last load
 * for {@code /dropscore validate}.
 *
 * @param <T> Registry entry type of the targets, such as {@code EntityType<?>} or {@code Block}.
 */
public abstract class DropManager<T extends IForgeRegistryEntry<T>> extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final DropFileFormat format;
    private final TargetMatcher<T> matcher;
    private final String kind;
    private volatile DropRuleSet<T> rules;
    private volatile List<DropIssue> issues = List.of();
    /**
     * Files waiting for the server config. When a world opens, Forge loads the world's datapacks before its
     * server config, so the first load waits for {@link #onConfigLoading} to read the configured limits.
     */
    @Nullable
    private volatile Map<ResourceLocation, JsonElement> pendingFiles;

    /**
     * Creates the listener of one datapack folder.
     *
     * @param folder  Folder under {@code data/<namespace>/}. Never null.
     * @param format  Format of the files. Never null.
     * @param matcher Target matcher of the registry. Never null.
     * @param kind    Word naming the targets in logs, such as {@code mob} or {@code block}. Never null.
     */
    protected DropManager(String folder, DropFileFormat format, TargetMatcher<T> matcher, String kind) {
        super(GSON, folder);
        this.format = format;
        this.matcher = matcher;
        this.kind = kind;
        this.rules = DropRuleSet.empty(matcher);
    }

    /**
     * Returns the drop table of a target, built on its first lookup after a reload.
     *
     * @param value Target that drops. Never null.
     * @return The table, {@link DropTable#EMPTY} when no file targets it.
     */
    public DropTable tableOf(T value) {
        return rules.tableOf(value);
    }

    /**
     * Returns the problems found by the last load.
     *
     * @return An immutable list, empty when every file was valid.
     */
    public List<DropIssue> issues() {
        return issues;
    }

    /**
     * Loads the files waiting for the dropscore server config, once Forge loads it.
     *
     * @param event Config loading event of the mod bus.
     */
    public void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != DropscoreConfig.SPEC) return;
        Map<ResourceLocation, JsonElement> pending = pendingFiles;
        if (pending == null) return;
        pendingFiles = null;
        load(pending, DropLimits.fromConfig());
    }

    /**
     * Forgets the cached tables when tags change, so {@code #tag} targets and tag removals read the new tags.
     *
     * @param event Tags update event of the Forge bus.
     */
    public void onTagsUpdated(TagsUpdatedEvent event) {
        rules.clearCache();
    }

    /**
     * Reads every file and swaps in the new rule set, or keeps the files until the server config loads.
     *
     * @param resources       Parsed JSON of every file, keyed by file id.
     * @param resourceManager Active resource manager.
     * @param profiler        Reload profiler.
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (!DropscoreConfig.SPEC.isLoaded()) {
            pendingFiles = Map.copyOf(resources);
            rules = DropRuleSet.empty(matcher);
            issues = List.of();
            LOGGER.info("[dropscore] Waiting for the server config to load {} drops.", kind);
            return;
        }
        pendingFiles = null;
        DropscoreConfig.reloadFromDisk();
        load(resources, DropLimits.fromConfig());
    }

    /**
     * Checks a resolved target against the rules of the module, such as "removal files cannot target players".
     *
     * @param parsed Parsed file. Never null.
     * @param target Resolved target. Never null.
     * @param ctx    Context of the target key. Never null.
     * @throws DropDataException If the module rejects the target.
     */
    protected void checkTarget(ParsedDropFile parsed, TargetMatcher.Target<T> target, ParseContext ctx) throws DropDataException {
    }

    /**
     * Parses every file, indexes them by target and swaps in the new rule set with the problems found.
     *
     * @param resources Parsed JSON of every file, keyed by file id. Never null.
     * @param limits    Config limits to clamp values to. Never null.
     */
    private void load(Map<ResourceLocation, JsonElement> resources, DropLimits limits) {
        DropIssueLog log = new DropIssueLog();
        DropFileParser parser = new DropFileParser(format, limits, log);
        DropRuleSet.Builder<T> builder = new DropRuleSet.Builder<>(matcher);
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

        rules = builder.build();
        issues = log.issues();
        LOGGER.info("[dropscore] Loaded {} {} drops from {} drop files and {} removal files ({} errors, {} warnings).",
                dropCount, kind, dropFileCount, removalFileCount,
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
    private boolean addFile(ParsedDropFile parsed, DropRuleSet.Builder<T> builder, DropIssueLog log) {
        try {
            ParseContext ctx = new ParseContext(parsed.fileId(), format.targetKey(), log);
            TargetMatcher.Target<T> target = matcher.parse(parsed.target(), ctx);
            checkTarget(parsed, target, ctx);
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
}
