package com.jaffh.alkahestcore.fluidscore.common.jsonfluids;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jaffh.alkahestcore.fluidscore.AlkahestConfig;
import com.jaffh.alkahestcore.fluidscore.common.FluidManager;
import com.jaffh.alkahestcore.fluidscore.common.fluids.BaseFluid;
import com.jaffh.alkahestcore.fluidscore.common.fluids.CustomFluid;
import com.jaffh.alkahestcore.fluidscore.common.interactions.FluidInteractionManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;

/**
 * Disk-based configuration loader responsible for dynamic runtime fluid generation.
 * <p>Unlike the {@link FluidInteractionManager} which hooks into the standard data pack resource reload pipeline,
 * this utility operates directly at the local filesystem layer. It reads raw JSON files from a dedicated
 * folder in the game's root directory, parsing them into registry entries before the game finishes initialization.</p>
 */
public class CustomFluidJsonLoader {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * The designated root path target directory named "customfluids" located in the main game folder.
     */
    private static final File CUSTOM_FLUIDS_DIR = new File(FMLPaths.GAMEDIR.get().toFile(), "customfluids");

    /**
     * Initializes the filesystem bootstrapper, scanning and registering dynamic custom fluids.
     *
     * <p><b>Execution Pipeline:</b></p>
     * <ol>
     *   <li>Ensures the target folder exists, executing asset scaffolding generation if missing.</li>
     *   <li>Verifies if data-driven features are globally authorized via {@link AlkahestConfig}.</li>
     *   <li>Filters and array-maps all valid {@code .json} data structures in the directory.</li>
     *   <li>Parses properties, converts HEX text layouts into scalar color integers, maps behavioral configurations,
     *       and hooks entries straight into the provided {@link FluidManager}.</li>
     * </ol>
     *
     * @param manager The engine's orchestration manager instance used to process registration streams.
     */
    public static void init(FluidManager manager){

        if (!CUSTOM_FLUIDS_DIR.exists()) {
            CUSTOM_FLUIDS_DIR.mkdirs();
            generateExampleFile();
        }

        // Short-circuit execution loop if data-driven fluids are deactivated in the master configuration file
        if(!AlkahestConfig.ENABLE_DATA_DRIVEN_FLUIDS.get()) return;

        File[] files = CUSTOM_FLUIDS_DIR.listFiles(((dir, name) -> name.endsWith(".json")));
        if(files == null) return;

        for(File file : files){
            try(FileReader reader = new FileReader(file)){
                JsonFluidsStructure data = GSON.fromJson(reader, JsonFluidsStructure.class);

                BaseFluid.Attributes attributes = new BaseFluid.Attributes(
                        data.attributes.temperature,
                        data.attributes.viscosity,
                        data.attributes.density,
                        data.attributes.luminosity,
                        data.attributes.fluidName
                );

                // Converts CSS-style hexadecimal strings (e.g., "#39E600") into a raw bitwise integer configuration
                if(data.attributes.fogColorHex != null){
                    int hex = Integer.parseInt(data.attributes.fogColorHex.replace("#", ""), 16);
                    float alpha = data.attributes.fogAlpha != null ? data.attributes.fogAlpha : 1.0f;
                    attributes = attributes.withFogColor(hex, alpha);
                }

                JsonFluidsStructure.JsonConfigs jConfig = data.configs != null ? data.configs : new JsonFluidsStructure.JsonConfigs();
                DamageSource damageSource = JsonFluidsStructure.parseDamageSource(jConfig.damageSource);

                CustomFluid.Configs configs = new CustomFluid.Configs(
                        jConfig.isHot,
                        jConfig.tickDelay,
                        jConfig.slopeFindDistance,
                        jConfig.dropOff,
                        jConfig.canConvertToSource,
                        jConfig.fireSpreadIntensity,
                        jConfig.entityDamage,
                        damageSource,
                        jConfig.bucketStackSize,
                        jConfig.secondsOnFire,
                        jConfig.deletesItems
                );

                // Registers the fully constructed fluid directly into the miscellaneous creative inventory tab
                manager.registerCustomFluid(attributes, configs, CreativeModeTab.TAB_MISC);

            } catch (Exception e) {
                LOGGER.error("[AlkahestCore] Critical failure encountered while parsing fluid data file: " + file.getName(), e);
            }
        }
    }

    /**
     * An internal safe-failure scaffolding utility that dumps a structured reference template file
     * onto the disk drive.
     * Helps end-users or modpack creators understand the exact JSON element layout parameters
     * needed by the system without consulting source code repositories.
     */
    private static void generateExampleFile() {
        File example = new File(CUSTOM_FLUIDS_DIR, "example.json");
        try (FileWriter writer = new FileWriter(example)) {
            String jsonSample = """
            {
              "attributes": {
                "fluidName": "acid",
                "temperature": 300,
                "viscosity": 1000,
                "density": 1200,
                "luminosity": 5,
                "fogColorHex": "#39E600",
                "fogAlpha": 0.6
              },
              "configs": {
                "isHot": false,
                "tickDelay": 5,
                "entityDamage": 2.5,
                "damageSource": "magic"
              }
            }
            """;
            writer.write(jsonSample);
        } catch (IOException e) {
            LOGGER.error("[AlkahestCore] Failed to construct framework example asset file profile template.", e);
        }
    }
}
