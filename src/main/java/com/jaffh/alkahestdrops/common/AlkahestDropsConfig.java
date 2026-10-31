package com.jaffh.alkahestdrops.common;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Server config of Alkahest Drops, stored per world in {@code serverconfig/alkahestdrops-server.toml}.
 * Holds the limits that drop files are clamped to; a change applies on the next {@code /reload}.
 */
public final class AlkahestDropsConfig {
    /** Name of the config file inside the world's {@code serverconfig} folder. */
    public static final String FILE_NAME = "alkahestdrops-server.toml";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_LIMIT = 1024;
    /** Keeps base value + enchantment bonus + one increment far below {@link Integer#MAX_VALUE}. */
    private static final int UPPER_BOUND = 1_000_000;

    /** Compiled spec, registered as a SERVER config. */
    public static final ForgeConfigSpec SPEC;
    /** Highest {@code rolls}, roll bonus and total roll bonus a drop may use. */
    public static final ForgeConfigSpec.IntValue MAX_ROLLS;
    /** Highest count, count bonus and total count bonus a drop may use. */
    public static final ForgeConfigSpec.IntValue MAX_COUNT;
    /** Highest {@code increment_value}, increment bonus and total increment bonus a drop may use. */
    public static final ForgeConfigSpec.IntValue MAX_INCREMENT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment(
                        " Limits of Alkahest Drops files. Values above a limit are clamped with a warning in the log.",
                        " Changes apply on the next /reload.")
                .push("limits");
        MAX_ROLLS = builder.comment(
                        " Highest rolls of a roll-based drop_behavior, and of its enchantment roll bonus.",
                        " Rolls run on every kill or block break, so high values cost server time.")
                .defineInRange("max_rolls", DEFAULT_LIMIT, 1, UPPER_BOUND);
        MAX_COUNT = builder.comment(" Highest count.min/count.max of a drop, and of its enchantment count bonus.")
                .defineInRange("max_count", DEFAULT_LIMIT, 1, UPPER_BOUND);
        MAX_INCREMENT = builder.comment(
                        " Highest increment_value of a roll-based drop_behavior, and of its enchantment increment bonus.")
                .defineInRange("max_increment", DEFAULT_LIMIT, 1, UPPER_BOUND);
        builder.pop();
        SPEC = builder.build();
    }

    /**
     * Prevents instantiation of this holder class.
     */
    private AlkahestDropsConfig() {}

    /**
     * Registers the config with Forge as a SERVER config under {@link #FILE_NAME}.
     *
     * @param context Loading context of the mod. Never null.
     */
    public static void register(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, SPEC, FILE_NAME);
    }

    /**
     * Re-reads the config file from disk and corrects out-of-range values, so an edit applies on {@code /reload}.
     * Forge's file watcher misses saves made through a temp file + rename or through WSL, so it can't be relied on.
     * Does nothing when the config is not loaded; keeps the previous values when the file is not valid TOML.
     */
    public static void reloadFromDisk() {
        ModConfig config = ConfigTracker.INSTANCE.fileMap().get(FILE_NAME);
        if (config == null || !(config.getConfigData() instanceof CommentedFileConfig file)) return;
        try {
            file.load();
        } catch (ParsingException e) {
            LOGGER.error(
                    "[alkahestdrops] {} is not valid TOML, keeping the previous values: {}", FILE_NAME, e.getMessage());
            return;
        }
        if (!SPEC.isCorrect(file)) {
            LOGGER.warn("[alkahestdrops] {} has values out of range; they were reset to their defaults", FILE_NAME);
            SPEC.correct(file);
            file.save();
        }
        SPEC.afterReload();
    }
}
