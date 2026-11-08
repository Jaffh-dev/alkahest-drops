package com.jaffh.alkahestdrops.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.List;
import java.util.Set;

/**
 * Validates drop and removal files against the JSON Schemas of the docs site and reports where they fail, with the
 * same path syntax as the mod's own issues ({@code drops[0].count.max}, {@code ""} for the file root).
 */
final class DropFileSchemas {
    private static final String SCHEMA_BASE_URL = "https://jaffh-dev.github.io/alkahest-wiki/schemas/";
    private static final String SCHEMA_VERSION_PATH = "alkahest-drops/v1/";

    /**
     * Keywords that only summarize errors of their branches; the branch errors carry the useful paths.
     */
    private static final Set<String> SUMMARY_KEYWORDS = Set.of("if", "then", "else", "anyOf", "allOf", "oneOf");

    /**
     * Keywords whose error names a property of the object instead of the object itself.
     */
    private static final Set<String> PROPERTY_KEYWORDS = Set.of("required", "additionalProperties", "dependencies");

    private final JsonSchema mobSchema;
    private final JsonSchema blockSchema;

    /**
     * Loads both schemas from the test resources, resolving their {@code $id} URLs to the local copies.
     */
    DropFileSchemas() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V7,
                builder -> builder.schemaMappers(mappers -> mappers.mapPrefix(SCHEMA_BASE_URL, "classpath:schemas/")));
        mobSchema = factory.getSchema(SchemaLocation.of(schemaUrl("mob_drops")));
        blockSchema = factory.getSchema(SchemaLocation.of(schemaUrl("block_drops")));
    }

    /**
     * Validates one file.
     *
     * @param folder The file's data folder, {@code mob_drops} or {@code block_drops}. Never null.
     * @param file   Parsed file content. Never null.
     * @return The paths of the schema errors, in no particular order; empty when the file passes. Never null.
     * @throws IllegalArgumentException If the folder is neither drop folder.
     */
    List<String> errorPaths(String folder, JsonNode file) {
        return schemaOf(folder).validate(file).stream()
                .filter(message -> !SUMMARY_KEYWORDS.contains(message.getType()))
                .map(DropFileSchemas::pathOf)
                .distinct()
                .toList();
    }

    /**
     * Returns the schema of a data folder.
     *
     * @param folder {@code mob_drops} or {@code block_drops}. Never null.
     * @return The folder's schema. Never null.
     * @throws IllegalArgumentException If the folder is neither drop folder.
     */
    private JsonSchema schemaOf(String folder) {
        return switch (folder) {
            case "mob_drops" -> mobSchema;
            case "block_drops" -> blockSchema;
            default -> throw new IllegalArgumentException("not a drop folder: " + folder);
        };
    }

    /**
     * Returns the {@code $id} URL of a schema.
     *
     * @param name Schema name without extension. Never null.
     * @return The URL. Never null.
     */
    private static String schemaUrl(String name) {
        return SCHEMA_BASE_URL + SCHEMA_VERSION_PATH + name + ".schema.json";
    }

    /**
     * Returns the path of an error in the mod's issue syntax.
     *
     * @param message Schema error. Never null.
     * @return The path, {@code ""} for the file root. Never null.
     */
    private static String pathOf(ValidationMessage message) {
        StringBuilder path = new StringBuilder();
        JsonNodePath location = message.getInstanceLocation();
        for (int index = 0; index < location.getNameCount(); index++) {
            appendElement(path, location.getElement(index));
        }
        if (PROPERTY_KEYWORDS.contains(message.getType()) && message.getProperty() != null) {
            appendElement(path, message.getProperty());
        }
        return path.toString();
    }

    /**
     * Appends one path element: {@code [n]} for an array index, {@code .key} (or {@code key} at the start) for a key.
     *
     * @param path    Path built so far. Never null.
     * @param element An {@link Integer} index or a {@link String} key. Never null.
     */
    private static void appendElement(StringBuilder path, Object element) {
        if (element instanceof Integer index) {
            path.append('[').append(index).append(']');
            return;
        }
        if (!path.isEmpty()) {
            path.append('.');
        }
        path.append(element);
    }
}
