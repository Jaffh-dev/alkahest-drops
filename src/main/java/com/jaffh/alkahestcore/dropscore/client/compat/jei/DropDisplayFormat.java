package com.jaffh.alkahestcore.dropscore.client.compat.jei;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Defines the wording that differs between the mob and block drop categories: the texts that name kills or breaks.
 */
enum DropDisplayFormat {
    /** Mob drops: kills. */
    MOB("mob"),
    /** Block drops: breaks. */
    BLOCK("block");

    private static final String KEY_PREFIX = "jei.alkahestcore.dropscore.";

    private final String moduleName;

    /**
     * Creates a format.
     *
     * @param moduleName Suffix of the module's translation keys. Never null.
     */
    DropDisplayFormat(String moduleName) {
        this.moduleName = moduleName;
    }

    /**
     * Creates a text shared by both modules.
     *
     * @param key  Key after {@code jei.alkahestcore.dropscore.}. Never null.
     * @param args Arguments of the text.
     * @return The translatable text.
     */
    static MutableComponent text(String key, Object... args) {
        return Component.translatable(KEY_PREFIX + key, args);
    }

    /**
     * Creates a text of this module, whose key ends with {@code .mob} or {@code .block}.
     *
     * @param key  Key after {@code jei.alkahestcore.dropscore.}, without the module suffix. Never null.
     * @param args Arguments of the text.
     * @return The translatable text.
     */
    MutableComponent moduleText(String key, Object... args) {
        return Component.translatable(KEY_PREFIX + key + "." + moduleName, args);
    }
}
