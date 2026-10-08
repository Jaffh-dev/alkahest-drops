package com.jaffh.alkahestcore.fluidscore.common.jsonfluids;

import net.minecraft.world.damagesource.DamageSource;

/**
 * Data Transfer Object (DTO) defining the structural schema for data-driven fluid JSON files.
 * <p>This class serves as the direct deserialization template for GSON or other JSON parsers.
 * It mirrors the raw configuration layout found in external files, segregating parameters into
 * distinct blocks for physical/cosmetic properties and gameplay-affecting behavioral logic.</p>
 */
public class JsonFluidsStructure {

    /**
     * The cosmetic and physical profile definitions parsed from the JSON dataset.
     */
    public JsonAttributes attributes;

    /**
     * The functional, technical, and environmental hazard variables parsed from the JSON dataset.
     */
    public JsonConfigs configs;

    /**
     * Nested schema blueprint holding physical and aesthetic fluid traits.
     */
    public static class JsonAttributes {
        public int temperature;
        public int viscosity;
        public int density;
        public int luminosity;
        public String fluidName;
        public String fogColorHex;
        public Float fogAlpha;
    }

    /**
     * Nested schema blueprint holding behavior variables, hazardous thresholds, and inventory properties.
     * Initialized with framework default values to guarantee safe fallbacks during partial JSON parsing.
     */
    public static class JsonConfigs {
        public Boolean isHot = false;
        public int tickDelay = 5;
        public int slopeFindDistance = 4;
        public int dropOff = 1;
        public Boolean canConvertToSource = false;
        public int fireSpreadIntensity = 0;
        public float entityDamage = 0.0f;
        public String damageSource = "lava";
        public int bucketStackSize = 1;
        public int secondsOnFire = 0;
        public Boolean deletesItems = false;
    }

    /**
     * Translates a raw string identifier extracted from the JSON file into a valid,
     * hardcoded vanilla Minecraft {@link DamageSource}.
     *
     * @param source The raw string key representing the intended damage classification type.
     * @return The corresponding matched {@link DamageSource}. Defaults to {@link DamageSource#LAVA}
     *         if the provided input is null, unrecognized, or misconfigured.
     */
    public static DamageSource parseDamageSource(String source) {
        if (source == null) return DamageSource.LAVA;
        return switch (source.toLowerCase()) {
            case "in_fire", "infire" -> DamageSource.IN_FIRE;
            case "on_fire", "onfire" -> DamageSource.ON_FIRE;
            case "wither" -> DamageSource.WITHER;
            case "magic" -> DamageSource.MAGIC;
            case "drown" -> DamageSource.DROWN;
            case "generic" -> DamageSource.GENERIC;
            default -> DamageSource.LAVA;
        };
    }
}
