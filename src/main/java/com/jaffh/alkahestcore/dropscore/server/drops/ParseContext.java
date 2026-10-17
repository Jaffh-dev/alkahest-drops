package com.jaffh.alkahestcore.dropscore.server.drops;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Represents where the parser is inside a file, so every problem names its file and JSON path.
 * Also holds the value checks that report a warning when they clamp and fail with an error otherwise.
 *
 * @param fileId Id of the file being read. Never null.
 * @param path   JSON path of the current key, such as {@code drops[2].count}; empty at the file root. Never null.
 * @param log    Problem collector of the reload. Never null.
 */
public record ParseContext(ResourceLocation fileId, String path, DropIssueLog log) {

    /**
     * Creates the context of a file root.
     *
     * @param fileId Id of the file. Never null.
     * @param log    Problem collector of the reload. Never null.
     * @return A context with an empty path.
     */
    public static ParseContext root(ResourceLocation fileId, DropIssueLog log) {
        return new ParseContext(fileId, "", log);
    }

    /**
     * Returns the context of a child key.
     *
     * @param key JSON key. Never null.
     * @return A context whose path ends with the key.
     */
    public ParseContext at(String key) {
        return new ParseContext(fileId, path.isEmpty() ? key : path + "." + key, log);
    }

    /**
     * Returns the context of an element of a child array.
     *
     * @param key   JSON key of the array. Never null.
     * @param index Index of the element, 0 or more.
     * @return A context whose path ends with {@code key[index]}.
     */
    public ParseContext at(String key, int index) {
        return new ParseContext(fileId, at(key).path() + "[" + index + "]", log);
    }

    /**
     * Returns the context of a path below the current key written in the same notation, such as the
     * {@code .conditions[0]} vanilla loot validation reports.
     *
     * @param suffix Path to append, starting with {@code .} or {@code [}; empty for the current key. Never null.
     * @return A context whose path ends with the suffix.
     */
    public ParseContext atSuffix(String suffix) {
        return new ParseContext(fileId, path + suffix, log);
    }

    /**
     * Creates an error for the current key, to be thrown by the caller.
     *
     * @param message English description of the problem. Never null.
     * @return The exception to throw.
     */
    public DropDataException error(String message) {
        return new DropDataException(new DropIssue(fileId, path, message, DropIssue.Severity.ERROR));
    }

    /**
     * Creates a warning-level skip caused only by ids that name nothing registered, usually an optional mod that is
     * not installed.
     *
     * @param message English description of the problem. Never null.
     * @return The exception to throw.
     */
    public DropDataException unknownId(String message) {
        return new DropDataException(new DropIssue(fileId, path, message, DropIssue.Severity.WARNING));
    }

    /**
     * Reports a warning for the current key without skipping anything.
     *
     * @param message English description of the problem. Never null.
     */
    public void warn(String message) {
        log.report(new DropIssue(fileId, path, message, DropIssue.Severity.WARNING));
    }

    /**
     * Rejects a missing key.
     *
     * @param value Value read from the DTO, or null when the key is missing.
     * @param <T>   Type of the value.
     * @return The value, never null.
     * @throws DropDataException If the value is null.
     */
    public <T> T require(@Nullable T value) throws DropDataException {
        if (value == null) throw error("missing required key");
        return value;
    }

    /**
     * Rejects an int below a minimum.
     *
     * @param value Value to check.
     * @param min   Lowest allowed value.
     * @throws DropDataException If the value is below min.
     */
    public void requireAtLeast(int value, int min) throws DropDataException {
        if (value < min) throw error("must be at least " + min + ", got " + value);
    }

    /**
     * Rejects a decimal below a minimum, including NaN.
     *
     * @param value Value to check.
     * @param min   Lowest allowed value.
     * @throws DropDataException If the value is NaN or below min.
     */
    public void requireAtLeast(double value, double min) throws DropDataException {
        if (!(value >= min)) throw error("must be at least " + min + ", got " + value);
    }

    /**
     * Rejects a decimal that is not above 0, including NaN.
     *
     * @param value Value to check.
     * @throws DropDataException If the value is NaN, 0 or negative.
     */
    public void requirePositive(double value) throws DropDataException {
        if (!(value > 0)) throw error("must be above 0, got " + value);
    }

    /**
     * Lowers an int above a limit to the limit, with a warning.
     *
     * @param value Value to check.
     * @param max   Highest allowed value.
     * @return The value, or max when the value is above it.
     */
    public int clampAtMost(int value, int max) {
        if (value <= max) return value;
        warn("is " + value + ", above the limit " + max + "; clamped to " + max);
        return max;
    }

    /**
     * Lowers a decimal above a limit to the limit, with a warning.
     *
     * @param value Value to check.
     * @param max   Highest allowed value.
     * @return The value, or max when the value is above it.
     */
    public double clampAtMost(double value, double max) {
        if (value <= max) return value;
        warn("is " + value + ", above the limit " + max + "; clamped to " + max);
        return max;
    }

    /**
     * Raises an int below a minimum to the minimum, with a warning.
     *
     * @param value Value to check.
     * @param min   Lowest allowed value.
     * @return The value, or min when the value is below it.
     */
    public int clampAtLeast(int value, int min) {
        if (value >= min) return value;
        warn("is " + value + ", below the minimum " + min + "; clamped to " + min);
        return min;
    }

    /**
     * Raises a decimal below a minimum to the minimum, with a warning.
     *
     * @param value Value to check.
     * @param min   Lowest allowed value.
     * @return The value, or min when the value is below it.
     */
    public double clampAtLeast(double value, double min) {
        if (value >= min) return value;
        warn("is " + value + ", below the minimum " + min + "; clamped to " + min);
        return min;
    }
}
