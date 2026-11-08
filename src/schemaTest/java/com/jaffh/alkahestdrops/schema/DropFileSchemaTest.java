package com.jaffh.alkahestdrops.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Keeps the JSON Schema of the docs site in step with the mod's parser: the files the GameTests load as valid must
 * pass it, and the invalid files must fail it at the paths where the GameTests expect the mod to report an issue.
 */
class DropFileSchemaTest {
    private static final List<String> DROP_FOLDERS = List.of("mob_drops", "block_drops");
    private static final List<String> VALID_NAMESPACES = List.of("dstest_a", "dstest_b");
    private static final String INVALID_NAMESPACE = "dstest_c";
    private static final String SCHEMA_RESOURCE_DIR = "/schemas/alkahest-drops/v1/";

    /**
     * Issues a schema cannot catch, keyed like {@link ExpectedIssue#key()}, with the reason. The test fails when one
     * of them is caught after all, so the list never keeps a stale entry.
     */
    private static final Map<String, String> NOT_EXPRESSIBLE = Map.ofEntries(
            Map.entry("unknown_block: block", "needs the block registry"),
            Map.entry("unknown_namespace: entity", "needs the list of loaded mods"),
            Map.entry("unknown_items_only: removals[0]", "needs the item registry"),
            Map.entry("unknown_items_only: ", "needs the item registry"),
            Map.entry(
                    "enchant_bonus_issues: drops[4].enchant_bonus[1].enchant", "same value twice in a list of objects"),
            Map.entry("enchant_bonus_issues: drops[5].enchant_bonus[0].enchant", "needs the enchantment registry"),
            Map.entry("invalid_function: drops[0].functions", "needs the loot function registry (mods add types)"),
            Map.entry("on_drop_issues: drops[1].on_drop.run_function", "needs the loaded datapack functions"),
            Map.entry("tracking_issues: drops[6].id", "same value twice in a list of objects"));

    private static final ObjectMapper JSON = new ObjectMapper();

    private static Path dataDir;
    private static DropFileSchemas schemas;

    /**
     * Reads the folders the build passes in and loads the schemas.
     */
    @BeforeAll
    static void setUp() {
        dataDir = Path.of(System.getProperty("schemaTest.dataDir"));
        schemas = new DropFileSchemas();
    }

    /**
     * Every drop and removal file the GameTests load as valid passes the schema.
     *
     * @throws IOException If a file cannot be read.
     */
    @Test
    void validFilesPassTheSchema() throws IOException {
        List<String> failures = new ArrayList<>();
        int fileCount = 0;
        for (String namespace : VALID_NAMESPACES) {
            for (String folder : DROP_FOLDERS) {
                for (Path file : jsonFiles(dataDir.resolve(namespace).resolve(folder))) {
                    fileCount++;
                    List<String> errors = schemas.errorPaths(folder, JSON.readTree(file.toFile()));
                    if (!errors.isEmpty()) {
                        failures.add(dataDir.relativize(file) + " fails at " + errors);
                    }
                }
            }
        }
        assertTrue(fileCount > 0, "no valid test files found under " + dataDir);
        assertTrue(failures.isEmpty(), "valid files rejected by the schema:\n" + String.join("\n", failures));
    }

    /**
     * Every issue a GameTest expects in a {@code dstest_c} file is a schema error at the same path, except the
     * issues listed in {@link #NOT_EXPRESSIBLE}.
     *
     * @throws IOException If a file cannot be read.
     */
    @Test
    void invalidFilesFailWhereTheGameTestsExpect() throws IOException {
        List<ExpectedIssue> expected = ExpectedIssue.readAll(Path.of(System.getProperty("schemaTest.gameTestSources")));
        assertFalse(expected.isEmpty(), "no assertIssue calls found in the GameTest sources");
        List<String> missed = new ArrayList<>();
        List<String> staleExemptions = new ArrayList<>();
        for (ExpectedIssue issue : expected) {
            boolean isCaught = isCaught(issue, invalidFileErrors(issue.file()));
            boolean isExempt = NOT_EXPRESSIBLE.containsKey(issue.key());
            if (!isCaught && !isExempt) {
                missed.add(issue.key() + " (schema errors: " + invalidFileErrors(issue.file()) + ")");
            } else if (isCaught && isExempt) {
                staleExemptions.add(issue.key());
            }
        }
        Set<String> expectedKeys = expected.stream().map(ExpectedIssue::key).collect(Collectors.toSet());
        NOT_EXPRESSIBLE.keySet().stream()
                .filter(key -> !expectedKeys.contains(key))
                .forEach(key -> staleExemptions.add(key + " (no GameTest expects it)"));
        assertTrue(missed.isEmpty(), "issues the schema does not catch:\n" + String.join("\n", missed));
        assertTrue(
                staleExemptions.isEmpty(),
                "remove from NOT_EXPRESSIBLE, the schema catches them now:\n" + String.join("\n", staleExemptions));
    }

    /**
     * Every {@code dstest_c} file is checked by at least one {@code assertIssue} call, so an invalid file never sits
     * there untested.
     *
     * @throws IOException If a folder cannot be listed.
     */
    @Test
    void everyInvalidFileHasAnExpectedIssue() throws IOException {
        Set<String> expectedFiles =
                ExpectedIssue.readAll(Path.of(System.getProperty("schemaTest.gameTestSources"))).stream()
                        .map(ExpectedIssue::file)
                        .collect(Collectors.toSet());
        Set<String> untested = new TreeSet<>();
        for (String folder : DROP_FOLDERS) {
            for (Path file : jsonFiles(dataDir.resolve(INVALID_NAMESPACE).resolve(folder))) {
                String name = file.getFileName().toString().replace(".json", "");
                if (!expectedFiles.contains(name)) {
                    untested.add(folder + "/" + name);
                }
            }
        }
        assertTrue(untested.isEmpty(), "dstest_c files without an assertIssue call: " + untested);
    }

    /**
     * The schema copy of this repository matches the docs site's, when the wiki folder is present.
     *
     * @throws IOException If a schema cannot be read.
     */
    @Test
    void schemaCopyMatchesTheWiki() throws IOException {
        Path wikiDir = Path.of(System.getProperty("schemaTest.wikiSchemaDir"), "alkahest-drops", "v1");
        assumeTrue(Files.isDirectory(wikiDir), "wiki folder not found, copy not compared: " + wikiDir);
        List<Path> wikiFiles = jsonFiles(wikiDir);
        assertFalse(wikiFiles.isEmpty(), "no schemas in " + wikiDir);
        for (Path wikiFile : wikiFiles) {
            String name = wikiFile.getFileName().toString();
            try (InputStream copy = DropFileSchemaTest.class.getResourceAsStream(SCHEMA_RESOURCE_DIR + name)) {
                assertTrue(copy != null, name + " is in the wiki but not in src/schemaTest/resources");
                assertEquals(
                        normalize(Files.readString(wikiFile)),
                        normalize(new String(copy.readAllBytes(), StandardCharsets.UTF_8)),
                        name + " differs from the wiki; copy the wiki's schemas into src/schemaTest/resources");
            }
        }
    }

    /**
     * Tells whether the schema errors of a file cover an expected issue. The mod reports some problems at the object
     * the schema reports at one of its keys ({@code functions[0]} against {@code functions[0].function}), so an error
     * inside the expected path counts; an issue at the file root (the whole file skipped) counts when the file fails
     * the schema anywhere.
     *
     * @param issue  Expected issue. Never null.
     * @param errors Schema error paths of the issue's file. Never null.
     * @return Whether one of the errors covers the issue.
     */
    private static boolean isCaught(ExpectedIssue issue, List<String> errors) {
        String path = issue.path();
        if (path.isEmpty()) {
            return !errors.isEmpty();
        }
        return errors.stream()
                .anyMatch(error -> error.equals(path) || error.startsWith(path + ".") || error.startsWith(path + "["));
    }

    /**
     * Returns the schema errors of a {@code dstest_c} file, looking in both drop folders.
     *
     * @param name File name without folder or extension. Never null.
     * @return The error paths. Never null.
     * @throws IOException           If the file cannot be read.
     * @throws IllegalStateException If no drop folder has the file.
     */
    private static List<String> invalidFileErrors(String name) throws IOException {
        for (String folder : DROP_FOLDERS) {
            Path file = dataDir.resolve(INVALID_NAMESPACE).resolve(folder).resolve(name + ".json");
            if (Files.exists(file)) {
                return schemas.errorPaths(folder, JSON.readTree(file.toFile()));
            }
        }
        throw new IllegalStateException("no dstest_c file named " + name);
    }

    /**
     * Lists the JSON files under a folder, recursively and sorted.
     *
     * @param folder Folder to list. Never null.
     * @return The files; empty when the folder does not exist. Never null.
     * @throws IOException If the folder cannot be listed.
     */
    private static List<Path> jsonFiles(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Makes line endings comparable, since git may check the wiki out with CRLF on Windows.
     *
     * @param text File content. Never null.
     * @return The content with LF line endings. Never null.
     */
    private static String normalize(String text) {
        return text.replace("\r\n", "\n");
    }
}
