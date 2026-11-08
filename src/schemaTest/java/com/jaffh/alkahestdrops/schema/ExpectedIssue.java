package com.jaffh.alkahestdrops.schema;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * An issue a GameTest expects the mod to report for a {@code dstest_c} file, read from the
 * {@code DropTestKit.assertIssue(...)} calls of the GameTest sources, so the schema test never keeps its own copy of
 * the expectations.
 *
 * @param file Name of the {@code dstest_c} file, without folder or extension. Never null.
 * @param path JSON path of the issue, {@code ""} for the file root. Never null.
 */
record ExpectedIssue(String file, String path) {
    private static final String CALL = "DropTestKit.assertIssue(";
    private static final Pattern CALL_PATTERN = Pattern.compile(
            Pattern.quote(CALL) + "\\s*\\w+,\\s*\"([^\"]+)\",\\s*\"([^\"]*)\",\\s*DropIssue\\.Severity\\.\\w+\\s*\\)");

    /**
     * Reads every expected issue from the GameTest sources.
     *
     * @param sourceDir Root of the GameTest Java sources. Never null.
     * @return The expected issues, in source order. Never null.
     * @throws IllegalStateException If a call has a shape this reader does not understand (not literal arguments).
     * @throws UncheckedIOException  If a source file cannot be read.
     */
    static List<ExpectedIssue> readAll(Path sourceDir) {
        List<ExpectedIssue> issues = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceDir)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                issues.addAll(readFile(file));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return issues;
    }

    /**
     * Reads the expected issues of one source file.
     *
     * @param file Java source file. Never null.
     * @return The file's expected issues, in source order. Never null.
     * @throws IOException           If the file cannot be read.
     * @throws IllegalStateException If a call has a shape this reader does not understand.
     */
    private static List<ExpectedIssue> readFile(Path file) throws IOException {
        String source = Files.readString(file);
        List<ExpectedIssue> issues = new ArrayList<>();
        Matcher matcher = CALL_PATTERN.matcher(source);
        while (matcher.find()) {
            issues.add(new ExpectedIssue(matcher.group(1), matcher.group(2)));
        }
        int callCount = source.split(Pattern.quote(CALL), -1).length - 1;
        if (callCount != issues.size()) {
            throw new IllegalStateException(file.getFileName() + ": " + (callCount - issues.size())
                    + " assertIssue call(s) without literal file and path arguments; the schema test cannot read them");
        }
        return issues;
    }

    /**
     * Returns this issue as {@code file: path}, the key used by the test's lists.
     *
     * @return The key. Never null.
     */
    String key() {
        return file + ": " + path;
    }
}
