package com.jaffh.alkahestcore.dropscore.server.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents the range of a serialized loot number provider ({@code constant}, {@code uniform}, {@code binomial}).
 * Providers that depend on the game state (such as {@code score}) give the fallback.
 *
 * @param min       Lowest value.
 * @param max       Highest value.
 * @param mean      Average value.
 * @param isUniform Whether every whole value between min and max is equally likely.
 */
record LootNumberRange(double min, double max, double mean, boolean isUniform) {
    private static final ResourceLocation CONSTANT = new ResourceLocation("constant");
    private static final ResourceLocation UNIFORM = new ResourceLocation("uniform");
    private static final ResourceLocation BINOMIAL = new ResourceLocation("binomial");
    /** Most roll counts averaged one by one; larger ranges use the mean. */
    private static final int MAX_EXACT_ROLLS = 64;

    /**
     * Reads a number provider.
     *
     * @param element  Serialized provider, or null when the key is missing.
     * @param fallback Value of a missing or unknown provider.
     * @return The range.
     */
    static LootNumberRange of(JsonElement element, double fallback) {
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) return constant(primitive.getAsDouble());
        if (element == null || !element.isJsonObject()) return constant(fallback);
        JsonObject object = element.getAsJsonObject();
        ResourceLocation type = LootJson.id(object, "type");
        if (type == null || type.equals(UNIFORM)) {
            double min = of(object.get("min"), fallback).min();
            double max = of(object.get("max"), fallback).max();
            return new LootNumberRange(min, max, (min + max) / 2.0, true);
        }
        if (type.equals(CONSTANT)) return constant(LootJson.number(object, "value", fallback));
        if (type.equals(BINOMIAL)) {
            double trials = of(object.get("n"), 0).mean();
            double probability = of(object.get("p"), 0).mean();
            return new LootNumberRange(0, trials, trials * probability, false);
        }
        return constant(fallback);
    }

    /**
     * Creates a range of one value.
     *
     * @param value The value.
     * @return The range.
     */
    static LootNumberRange constant(double value) {
        return new LootNumberRange(value, value, value, true);
    }

    /**
     * Returns the lowest value as a whole number.
     *
     * @return The min, rounded down.
     */
    int minInt() {
        return (int) Math.floor(min);
    }

    /**
     * Returns the highest value as a whole number.
     *
     * @return The max, rounded down (vanilla rounds providers down when it needs an int).
     */
    int maxInt() {
        return (int) Math.floor(max);
    }

    /**
     * Computes the chance that something with a per-roll chance happens at least once, when this range is the
     * number of rolls.
     *
     * @param perRoll Chance (0–1) per roll.
     * @return The chance of at least one success, 0–1.
     */
    double chanceOfAtLeastOne(double perRoll) {
        if (perRoll <= 0) return 0;
        int low = Math.max(0, minInt());
        int high = Math.max(0, maxInt());
        if (!isUniform || high - low > MAX_EXACT_ROLLS) return 1.0 - Math.pow(1.0 - perRoll, Math.max(0, mean));
        double sum = 0;
        for (int rolls = low; rolls <= high; rolls++) sum += 1.0 - Math.pow(1.0 - perRoll, rolls);
        return sum / (high - low + 1);
    }
}
