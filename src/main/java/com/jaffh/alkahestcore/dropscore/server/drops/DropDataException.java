package com.jaffh.alkahestcore.dropscore.server.drops;

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
        super(issue.message());
        this.issue = issue;
    }

    /**
     * Returns the problem with what was skipped added to its message.
     *
     * @param consequence What was skipped, such as {@code "drop skipped"}. Never null.
     * @return A new issue whose message ends with the consequence.
     */
    public DropIssue toIssue(String consequence) {
        return new DropIssue(issue.fileId(), issue.field(), issue.message() + ", " + consequence, issue.severity());
    }
}
