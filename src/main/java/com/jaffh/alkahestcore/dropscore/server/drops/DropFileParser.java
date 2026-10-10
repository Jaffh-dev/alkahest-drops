package com.jaffh.alkahestcore.dropscore.server.drops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.server.drops.json.DropFileJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.RemovalEntryJson;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads one dropscore file (mob or block, per {@link DropFileFormat}) into a {@link ParsedDropFile}, reporting
 * every problem to the reload's {@link DropIssueLog}. A bad file-level key skips the file; a bad drop or removal
 * entry skips only that entry. Target resolution is left to the module that owns the file.
 */
public final class DropFileParser {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final DropFileFormat format;
    private final DropIssueLog log;
    private final DropEntryParser entryParser;

    /**
     * Creates a parser for one reload.
     *
     * @param format Format of the files being read. Never null.
     * @param limits Config limits of the reload. Never null.
     * @param log    Problem collector of the reload. Never null.
     */
    public DropFileParser(DropFileFormat format, DropLimits limits, DropIssueLog log) {
        this.format = format;
        this.log = log;
        this.entryParser = new DropEntryParser(format, limits);
    }

    /**
     * Reads one file.
     *
     * @param fileId Id of the file. Never null.
     * @param json   Root JSON value of the file. Never null.
     * @return The parsed file, or empty when the file was skipped (the reason is already reported).
     */
    public Optional<ParsedDropFile> parse(ResourceLocation fileId, JsonElement json) {
        ParseContext ctx = ParseContext.root(fileId, log);
        try {
            return Optional.ofNullable(parseFile(json, ctx));
        } catch (DropDataException e) {
            log.report(e.toIssue("file skipped"));
            return Optional.empty();
        } catch (RuntimeException e) {
            // A reload runs while the world opens, so an unforeseen exception here would crash the server.
            LOGGER.error("[dropscore] {}: unexpected error, file skipped", fileId, e);
            log.report(new DropIssue(fileId, "", "unexpected error (" + e + "), file skipped", DropIssue.Severity.ERROR));
            return Optional.empty();
        }
    }

    /**
     * Reads one file after checking its version and kind.
     *
     * @param json Root JSON value of the file. Never null.
     * @param ctx  Context of the file root. Never null.
     * @return The parsed file, or null when it was skipped with a warning.
     * @throws DropDataException If a file-level key is invalid.
     */
    @Nullable
    private ParsedDropFile parseFile(JsonElement json, ParseContext ctx) throws DropDataException {
        if (!json.isJsonObject()) throw ctx.error("file must be a JSON object");
        JsonObject upgraded = DropFileUpgrader.upgrade(json.getAsJsonObject(), ctx);
        DropFileJson file = DropValues.readObject(upgraded, DropFileJson.class, ctx);

        boolean isDropFile = file.drops != null;
        boolean isRemovalFile = file.removals != null || Boolean.TRUE.equals(file.shouldRemoveExperience);
        if (isDropFile && isRemovalFile) {
            throw ctx.error("a file cannot have both 'drops' and 'removals'/'remove_experience'; split it into two files");
        }
        if (!isDropFile && !isRemovalFile) {
            ctx.warn("no 'drops', 'removals' or 'remove_experience: true', file skipped");
            return null;
        }

        String target = ctx.at(format.targetKey()).require(format.targetOf(file));
        List<LootItemCondition> globalConditions = DropValues.parseConditions(file.conditions, ctx.at("conditions"));
        return isDropFile
                ? parseDropFile(file, target, globalConditions, ctx)
                : parseRemovalFile(file, target, globalConditions, ctx);
    }

    /**
     * Reads the drops of a drop file, skipping each invalid drop.
     *
     * @param file             Parsed file with {@code drops}. Never null.
     * @param target           Raw target. Never null.
     * @param globalConditions Parsed global conditions. Never null.
     * @param ctx              Context of the file root. Never null.
     * @return The drop file, or null when no drop is valid (reported as a warning).
     */
    @Nullable
    private ParsedDropFile parseDropFile(DropFileJson file, String target, List<LootItemCondition> globalConditions, ParseContext ctx) {
        List<DropEntry> entries = new ArrayList<>(file.drops.size());
        for (int i = 0; i < file.drops.size(); i++) {
            try {
                entries.add(entryParser.parse(file.drops.get(i), ctx.at("drops", i)));
            } catch (DropDataException e) {
                log.report(e.toIssue("drop skipped"));
            }
        }
        if (entries.isEmpty()) {
            ctx.warn("no valid drops, file skipped");
            return null;
        }
        return new ParsedDropFile.Drops(target, new DropFile(ctx.fileId(), globalConditions, entries));
    }

    /**
     * Reads the entries of a removal file, skipping each invalid entry.
     *
     * @param file             Parsed file with {@code removals} and/or {@code remove_experience: true}. Never null.
     * @param target           Raw target. Never null.
     * @param globalConditions Parsed global conditions. Never null.
     * @param ctx              Context of the file root. Never null.
     * @return The removal file, or null when it removes nothing (reported as a warning).
     */
    @Nullable
    private ParsedDropFile parseRemovalFile(DropFileJson file, String target, List<LootItemCondition> globalConditions, ParseContext ctx) {
        JsonArray removals = file.removals != null ? file.removals : new JsonArray();
        List<RemovalEntry> entries = new ArrayList<>(removals.size());
        for (int i = 0; i < removals.size(); i++) {
            try {
                entries.add(parseRemovalEntry(removals.get(i), ctx.at("removals", i)));
            } catch (DropDataException e) {
                log.report(e.toIssue("entry skipped"));
            }
        }

        boolean shouldRemoveExperience = Boolean.TRUE.equals(file.shouldRemoveExperience);
        if (entries.isEmpty() && !shouldRemoveExperience) {
            ctx.warn("no valid removals, file skipped");
            return null;
        }
        return new ParsedDropFile.Removals(target,
                new RemovalRule(ctx.fileId(), globalConditions, shouldRemoveExperience, entries));
    }

    /**
     * Reads one {@code removals} entry: an item id, {@code #item_tag} or {@code *} string, or an object
     * with {@code item} (same values) and local {@code conditions}, both required.
     *
     * @param element JSON value of the entry. Never null.
     * @param ctx     Context of the entry. Never null.
     * @return The removal entry.
     * @throws DropDataException If the entry is invalid (error) or its item is not registered (warning).
     */
    private static RemovalEntry parseRemovalEntry(JsonElement element, ParseContext ctx) throws DropDataException {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new RemovalEntry(DropValues.parseItemTarget(element.getAsString(), ctx), List.of());
        }
        if (!element.isJsonObject()) {
            throw ctx.error("must be an item id, '#tag', '*' or an object with 'item' and 'conditions'");
        }

        RemovalEntryJson entry = DropValues.readObject(element, RemovalEntryJson.class, ctx);
        ParseContext itemCtx = ctx.at("item");
        ParseContext conditionsCtx = ctx.at("conditions");
        ItemTarget target = DropValues.parseItemTarget(itemCtx.require(entry.item), itemCtx);
        return new RemovalEntry(target, DropValues.parseConditions(conditionsCtx.require(entry.conditions), conditionsCtx));
    }
}
