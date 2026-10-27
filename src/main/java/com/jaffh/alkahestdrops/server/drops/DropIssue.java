package com.jaffh.alkahestdrops.server.drops;

import com.jaffh.alkahestdrops.AlkahestDrops;
import com.jaffh.alkahestdrops.server.lang.Localizer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents one problem found while reading an Alkahest Drops file, kept for {@code /alkdrops validate}.
 *
 * @param fileId   Id of the file. Never null.
 * @param field    JSON path of the bad key, such as {@code drops[2].count.max}; empty for the whole file. Never null.
 * @param message  Translatable description of the problem and what was done about it. Never null.
 * @param severity How serious the problem is. Never null.
 */
public record DropIssue(ResourceLocation fileId, String field, Component message, Severity severity) {
    private static final String KEY_PREFIX = "issue." + AlkahestDrops.ID + ".";

    /**
     * Creates the translatable text of a problem. Arguments that are not texts are kept as strings, so they show
     * as typed in the file.
     *
     * @param key  Key without the {@code issue.alkahestdrops.} prefix, such as {@code missing_key}. Never null.
     * @param args Values of the placeholders. Never null.
     * @return The text.
     */
    public static MutableComponent text(String key, Object... args) {
        Object[] values = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            values[i] = args[i] instanceof Component ? args[i] : String.valueOf(args[i]);
        }
        return Localizer.translatable(KEY_PREFIX + key, values);
    }

    /**
     * Describes the problem with its field, without the file id.
     *
     * @return {@code "<field>: <message>"}, or only the message when the field is empty.
     */
    public Component describe() {
        return field.isEmpty() ? message : Component.literal(field + ": ").append(message);
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
