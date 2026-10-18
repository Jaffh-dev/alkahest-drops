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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
            return Optional.of(parseFile(json, ctx));
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
     * @return The parsed file.
     * @throws DropDataException If a file-level key is invalid or no entry of the file is valid.
     */
    private ParsedDropFile parseFile(JsonElement json, ParseContext ctx) throws DropDataException {
        if (!json.isJsonObject()) throw ctx.error("file must be a JSON object");
        JsonObject upgraded = DropFileUpgrader.upgrade(json.getAsJsonObject(), ctx);
        DropFileJson file = DropValues.readObject(upgraded, DropFileJson.class, ctx);

        boolean isDropFile = file.drops != null;
        boolean isRemovalFile = file.removals != null;
        if (isDropFile && isRemovalFile) {
            throw ctx.error("a file cannot have both 'drops' and 'removals'; split it into two files");
        }
        if (!isDropFile && !isRemovalFile) throw ctx.error("no 'drops' or 'removals'");

        DropFileFormat other = format.other();
        if (format.targetOf(file) == null && other.targetOf(file) != null) {
            throw ctx.at(other.targetKey()).error("is a key of " + other.filesName() + "; "
                    + format.filesName() + " use '" + format.targetKey() + "'");
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
     * @return The drop file.
     * @throws DropDataException If no drop is valid.
     */
    private ParsedDropFile parseDropFile(DropFileJson file, String target, List<LootItemCondition> globalConditions,
                                         ParseContext ctx) throws DropDataException {
        List<DropEntry> entries = new ArrayList<>(file.drops.size());
        Set<String> progressKeys = new HashSet<>();
        boolean isEverySkipAnUnknownId = !file.drops.isEmpty();
        for (int i = 0; i < file.drops.size(); i++) {
            try {
                DropEntry entry = entryParser.parse(file.drops.get(i), ctx.at("drops", i), i);
                if (!progressKeys.add(entry.tracking().key())) {
                    throw ctx.at("drops", i).at("id").error("is already used by another drop of this file");
                }
                entries.add(entry);
            } catch (DropDataException e) {
                DropIssue issue = e.toIssue("drop skipped");
                log.report(issue);
                if (issue.severity() == DropIssue.Severity.ERROR) isEverySkipAnUnknownId = false;
            }
        }
        if (entries.isEmpty()) throw noValidEntries("no valid drops", isEverySkipAnUnknownId, ctx);
        boolean hasSkippedDrops = entries.size() < file.drops.size();
        return new ParsedDropFile.Drops(target, new DropFile(ctx.fileId(), globalConditions, entries), hasSkippedDrops);
    }

    /**
     * Reads the entries of a removal file, skipping each invalid entry.
     *
     * @param file             Parsed file with {@code removals}. Never null.
     * @param target           Raw target. Never null.
     * @param globalConditions Parsed global conditions. Never null.
     * @param ctx              Context of the file root. Never null.
     * @return The removal file.
     * @throws DropDataException If no entry is valid.
     */
    private ParsedDropFile parseRemovalFile(DropFileJson file, String target, List<LootItemCondition> globalConditions,
                                            ParseContext ctx) throws DropDataException {
        JsonArray removals = file.removals;
        List<RemovalEntry> entries = new ArrayList<>(removals.size());
        boolean isEverySkipAnUnknownId = !removals.isEmpty();
        for (int i = 0; i < removals.size(); i++) {
            try {
                entries.add(parseRemovalEntry(removals.get(i), ctx.at("removals", i)));
            } catch (DropDataException e) {
                DropIssue issue = e.toIssue("entry skipped");
                log.report(issue);
                if (issue.severity() == DropIssue.Severity.ERROR) isEverySkipAnUnknownId = false;
            }
        }
        if (entries.isEmpty()) throw noValidEntries("no valid removals", isEverySkipAnUnknownId, ctx);
        return new ParsedDropFile.Removals(target, new RemovalRule(ctx.fileId(), globalConditions, entries));
    }

    /**
     * Creates the skip of a file left without entries. It is a warning only when every entry named an item that
     * is not installed (an optional mod), and an error otherwise, since then the file itself is wrong.
     *
     * @param message                English description of the problem. Never null.
     * @param isEverySkipAnUnknownId Whether every skipped entry was skipped only for an unknown id.
     * @param ctx                    Context of the file root. Never null.
     * @return The exception to throw.
     */
    private static DropDataException noValidEntries(String message, boolean isEverySkipAnUnknownId, ParseContext ctx) {
        return isEverySkipAnUnknownId ? ctx.unknownId(message) : ctx.error(message);
    }

    /**
     * Reads one {@code removals} entry: an item id, {@code #item_tag} or {@code *} string; an object with
     * {@code item} (same values) and local {@code conditions}, both required; or an object with
     * {@code "experience": true} and optional local {@code conditions}.
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
            throw ctx.error("must be an item id, '#tag', '*' or an object with 'item' and 'conditions' "
                    + "or with 'experience': true");
        }

        RemovalEntryJson entry = DropValues.readObject(element, RemovalEntryJson.class, ctx);
        ParseContext conditionsCtx = ctx.at("conditions");
        if (Boolean.TRUE.equals(entry.experience)) {
            if (entry.item != null) {
                throw ctx.at("experience").error("cannot be used with 'item'; an entry removes either items or experience");
            }
            return new RemovalEntry(null, DropValues.parseConditions(entry.conditions, conditionsCtx));
        }

        ParseContext itemCtx = ctx.at("item");
        ItemTarget target = DropValues.parseItemTarget(itemCtx.require(entry.item), itemCtx);
        return new RemovalEntry(target, DropValues.parseConditions(conditionsCtx.require(entry.conditions), conditionsCtx));
    }
}
