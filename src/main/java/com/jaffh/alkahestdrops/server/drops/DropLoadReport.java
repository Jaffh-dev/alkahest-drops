package com.jaffh.alkahestdrops.server.drops;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents what one load of a drop folder read, kept for the {@code /alkdrops} commands.
 *
 * @param files          Files added to the rule set, in load order. Immutable, never null.
 * @param skippedFileIds Ids of the files skipped by an error or a warning. Immutable, never null.
 * @param issues         Every problem found, in report order. Immutable, never null.
 */
public record DropLoadReport(List<ParsedDropFile> files, Set<ResourceLocation> skippedFileIds, List<DropIssue> issues) {
    /** The report of a folder that has not loaded yet. */
    public static final DropLoadReport EMPTY = new DropLoadReport(List.of(), Set.of(), List.of());

    /**
     * Creates a report and stores immutable copies of its collections.
     */
    public DropLoadReport {
        files = List.copyOf(files);
        skippedFileIds = Set.copyOf(skippedFileIds);
        issues = List.copyOf(issues);
    }
}
