package com.jaffh.alkahestcore.dropscore.server.drops;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Checks the {@code version} of a dropscore file and upgrades older formats to the current one,
 * so the parser only ever reads the current format.
 */
public final class DropFileUpgrader {
    /** Format version this build reads. */
    public static final int CURRENT_VERSION = 1;

    /** Upgrade steps keyed by the version they upgrade from; each returns the file in the next version. */
    private static final Map<Integer, UnaryOperator<JsonObject>> UPGRADES = Map.of();

    /**
     * Prevents instantiation of this utility class.
     */
    private DropFileUpgrader() {
    }

    /**
     * Returns a file in the current format version.
     *
     * @param file Raw file root. Never null; never modified.
     * @param ctx  Context of the file root. Never null.
     * @return The file itself when it is already current, otherwise an upgraded copy.
     * @throws DropDataException If {@code version} is missing, not a whole number, below 1, newer than
     *                           {@link #CURRENT_VERSION}, or has no upgrade path.
     */
    public static JsonObject upgrade(JsonObject file, ParseContext ctx) throws DropDataException {
        ParseContext versionCtx = ctx.at("version");
        int version = readVersion(file.get("version"), versionCtx);
        if (version > CURRENT_VERSION) {
            throw versionCtx.error("version_too_new", version, CURRENT_VERSION);
        }

        JsonObject upgraded = file;
        for (int from = version; from < CURRENT_VERSION; from++) {
            UnaryOperator<JsonObject> step = UPGRADES.get(from);
            if (step == null) throw versionCtx.error("no_upgrade", from);
            upgraded = step.apply(upgraded.deepCopy());
        }
        return upgraded;
    }

    /**
     * Reads the version number.
     *
     * @param element Value of the {@code version} key, or null when missing.
     * @param ctx     Context of the {@code version} key. Never null.
     * @return The version, 1 or more.
     * @throws DropDataException If the value is missing, not a whole number or below 1.
     */
    private static int readVersion(JsonElement element, ParseContext ctx) throws DropDataException {
        ctx.require(element);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw ctx.error("not_whole_number", element);
        }
        double value = element.getAsDouble();
        if (value != Math.rint(value)) throw ctx.error("not_whole_number", element);
        if (value < 1) throw ctx.error("at_least", 1, element);
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
