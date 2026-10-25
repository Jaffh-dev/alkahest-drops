package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.server.lang.Localizer;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the problems of one reload and writes each one to the log as it is reported.
 */
public final class DropIssueLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final List<DropIssue> issues = new ArrayList<>();

    /**
     * Records a problem and logs it in English as {@code [dropscore] <file>: <problem>}.
     *
     * @param issue Problem to record. Never null.
     */
    public void report(DropIssue issue) {
        issues.add(issue);
        String problem = Localizer.english().text(issue.describe());
        if (issue.severity() == DropIssue.Severity.ERROR) {
            LOGGER.error("[dropscore] {}: {}", issue.fileId(), problem);
        } else {
            LOGGER.warn("[dropscore] {}: {}", issue.fileId(), problem);
        }
    }

    /**
     * Counts the recorded problems of one severity.
     *
     * @param severity Severity to count. Never null.
     * @return The number of problems with that severity.
     */
    public int count(DropIssue.Severity severity) {
        int total = 0;
        for (DropIssue issue : issues) {
            if (issue.severity() == severity) total++;
        }
        return total;
    }

    /**
     * Returns every recorded problem in report order.
     *
     * @return An immutable copy of the problems.
     */
    public List<DropIssue> issues() {
        return List.copyOf(issues);
    }
}
