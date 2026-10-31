package com.jaffh.alkahestdrops.server.drops;

import com.jaffh.alkahestdrops.server.lang.Localizer;

/**
 * Signals that a file, drop or entry holds invalid data and must be skipped. Created through {@link ParseContext}
 * and turned into a {@link DropIssue} by the code that decides what gets skipped.
 */
public class DropDataException extends Exception {
    private final DropIssue issue;

    /**
     * Creates the exception for a problem.
     *
     * @param issue Problem without the skipped scope in its message. Never null.
     */
    DropDataException(DropIssue issue) {
        super(Localizer.english().text(issue.message()));
        this.issue = issue;
    }

    /**
     * Returns the problem with what was skipped added to its message.
     *
     * @param skipped What was skipped: {@code file}, {@code drop} or {@code entry}. Never null.
     * @return A new issue whose message ends with the consequence, such as {@code ", drop skipped"}.
     */
    public DropIssue toIssue(String skipped) {
        return new DropIssue(
                issue.fileId(),
                issue.field(),
                issue.message().copy().append(", ").append(DropIssue.text("skipped." + skipped)),
                issue.severity());
    }
}
