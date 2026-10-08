package com.jaffh.alkahestcore.fluidscore;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * System configuration center for the AlkahestCore framework.
 * <p>Manages the backend framework toggles and initializes configuration properties utilizing
 * Forge's specification system wrapped in an early-access IO file loader pipeline.</p>
 */
public class AlkahestConfig {

    /**
     * The internal specification builder used to construct the config schema tree structure.
     */
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    /**
     * The compiled configuration specification blueprint utilized by the engine to validate TOML values.
     */
    public static final ForgeConfigSpec SPEC;

    /**
     * Framework toggle determining if data-driven fluid registration is enabled.
     * <p>If set to {@code true}, the system scans, parses, and loads external JSON definitions
     * from the root {@code customfluids} directory during the mod initialization event loop.</p>
     */
    public static final ForgeConfigSpec.BooleanValue ENABLE_DATA_DRIVEN_FLUIDS;

    static {
        BUILDER.push("General Settings");
        ENABLE_DATA_DRIVEN_FLUIDS = BUILDER
                .comment(" If enabled, reads JSON files from the 'customfluids' directory in the game root folder to dynamically register fluids.")
                .define("enable_data_driven_fluids", false);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    /**
     * Synchronously boots and parses the configuration file early in the mod loading lifecycle.
     */
    public static void loadEarly() {
        CommentedFileConfig configData = CommentedFileConfig.builder(FMLPaths.CONFIGDIR.get().resolve("alkahestcore_fluids-common.toml"))
                .sync()
                .autosave()
                .writingMode(WritingMode.REPLACE)
                .build();
        configData.load();
        SPEC.acceptConfig(configData);
    }

}
