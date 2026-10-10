package com.jaffh.alkahestcore.dropscore.server.drops;

import net.minecraft.resources.ResourceLocation;

/**
 * Represents one problem found while reading a dropscore file, kept for {@code /dropscore validate}.
 *
 * @param fileId   Id of the file. Never null.
 * @param field    JSON path of the bad key, such as {@code drops[2].count.max}; empty for the whole file. Never null.
 * @param message  English description of the problem and what was done about it. Never null.
 * @param severity How serious the problem is. Never null.
 */
public record DropIssue(ResourceLocation fileId, String field, String message, Severity severity) {

    /**
     * Describes the problem with its field, without the file id.
     *
     * @return {@code "<field>: <message>"}, or only the message when the field is empty.
     */
    public String describe() {
        return field.isEmpty() ? message : field + ": " + message;
    }

    /**
     * Defines how serious a problem is.
     */
    public enum Severity {
        /** The value was clamped or ignored, or an id names something not installed. */
        WARNING,
        /** The value is invalid, so a file, drop or entry was skipped. */
        ERROR
    }
}
