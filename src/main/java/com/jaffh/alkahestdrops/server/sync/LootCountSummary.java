package com.jaffh.alkahestdrops.server.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jaffh.alkahestdrops.common.drops.DropCount;
import com.jaffh.alkahestdrops.common.drops.EnchantBonus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Summarizes the loot functions applied to one entry: the functions that set or raise the count become a count range
 * and a count bonus; the others do not change what JEI shows.
 *
 * @param count   Count range of one pick. Never null.
 * @param bonuses Count bonus per level of each enchantment a function scales with, by enchantment id. Immutable,
 *                never null.
 */
record LootCountSummary(DropCount count, Map<ResourceLocation, EnchantBonus.CountBonus> bonuses) {
    private static final ResourceLocation SET_COUNT = new ResourceLocation("set_count");
    private static final ResourceLocation LOOTING_ENCHANT = new ResourceLocation("looting_enchant");
    private static final ResourceLocation APPLY_BONUS = new ResourceLocation("apply_bonus");
    private static final ResourceLocation LIMIT_COUNT = new ResourceLocation("limit_count");
    private static final ResourceLocation EXPLOSION_DECAY = new ResourceLocation("explosion_decay");
    private static final ResourceLocation LOOTING = new ResourceLocation("looting");
    private static final ResourceLocation UNIFORM_BONUS_COUNT = new ResourceLocation("uniform_bonus_count");

    /**
     * Summarizes serialized functions, applied in order to a count of 1.
     *
     * @param functions Serialized functions: the entry's, then the pool's, then the table's. Never null.
     * @return The summary.
     */
    static LootCountSummary of(List<JsonElement> functions) {
        int min = 1;
        int max = 1;
        Map<ResourceLocation, EnchantBonus.CountBonus> bonuses = new LinkedHashMap<>();
        for (JsonElement element : functions) {
            if (!element.isJsonObject()) continue;
            JsonObject function = element.getAsJsonObject();
            ResourceLocation type = LootJson.id(function, "function");
            if (type == null || type.equals(EXPLOSION_DECAY)) continue;
            if (type.equals(SET_COUNT)) {
                LootNumberRange range = LootNumberRange.of(function.get("count"), 1);
                boolean isAdded = LootJson.bool(function, "add", false);
                min = isAdded ? min + range.minInt() : range.minInt();
                max = isAdded ? max + range.maxInt() : range.maxInt();
            } else if (type.equals(LOOTING_ENCHANT)) {
                LootNumberRange range = LootNumberRange.of(function.get("count"), 0);
                int limit = (int) LootJson.number(function, "limit", 0);
                int cap = limit > 0 ? limit : Integer.MAX_VALUE;
                putBonus(bonuses, LOOTING, (int) Math.round(range.min()), (int) Math.round(range.max()), cap);
            } else if (type.equals(APPLY_BONUS) && isUniformBonus(function)) {
                JsonElement parameters = function.get("parameters");
                double multiplier = parameters != null && parameters.isJsonObject()
                        ? LootJson.number(parameters.getAsJsonObject(), "bonusMultiplier", 1)
                        : 1;
                putBonus(
                        bonuses,
                        LootJson.id(function, "enchantment"),
                        0,
                        (int) Math.round(multiplier),
                        Integer.MAX_VALUE);
            } else if (type.equals(LIMIT_COUNT)) {
                int[] limited = limit(function.get("limit"), min, max);
                min = limited[0];
                max = limited[1];
            }
        }
        min = Math.max(0, min);
        max = Math.max(min, max);
        return new LootCountSummary(new DropCount(min, max), Map.copyOf(bonuses));
    }

    /**
     * Stores the count bonus of one enchantment, replacing an earlier one of the same enchantment.
     *
     * @param bonuses     Bonuses found so far, by enchantment id. Never null.
     * @param enchantment Id of the enchantment, or null when the function names none.
     * @param minPerLevel Min added per level.
     * @param maxPerLevel Max added per level.
     * @param cap         Cap of both totals.
     */
    private static void putBonus(
            Map<ResourceLocation, EnchantBonus.CountBonus> bonuses,
            @Nullable ResourceLocation enchantment,
            int minPerLevel,
            int maxPerLevel,
            int cap) {
        if (enchantment == null) return;
        if (minPerLevel == 0 && maxPerLevel == 0) {
            bonuses.remove(enchantment);
            return;
        }
        bonuses.put(enchantment, new EnchantBonus.CountBonus(minPerLevel, maxPerLevel, cap, cap));
    }

    /**
     * Tells whether an {@code apply_bonus} function adds a uniform count per level of its enchantment, the only
     * formula that maps to a count bonus ({@code ore_drops} and {@code binomial_with_bonus_count} do not).
     *
     * @param function Serialized function. Never null.
     * @return True for {@code uniform_bonus_count}.
     */
    private static boolean isUniformBonus(JsonObject function) {
        return UNIFORM_BONUS_COUNT.equals(LootJson.id(function, "formula"));
    }

    /**
     * Applies a {@code limit_count} range to a count range.
     *
     * @param limit Serialized int range: a number, or an object with optional {@code min} and {@code max}.
     * @param min   Current min.
     * @param max   Current max.
     * @return The limited {@code {min, max}}.
     */
    private static int[] limit(JsonElement limit, int min, int max) {
        if (limit == null) return new int[] {min, max};
        if (limit.isJsonPrimitive()) {
            int exact = (int) limit.getAsDouble();
            return new int[] {exact, exact};
        }
        if (!limit.isJsonObject()) return new int[] {min, max};
        JsonObject range = limit.getAsJsonObject();
        int low = range.has("min") ? LootNumberRange.of(range.get("min"), min).minInt() : Integer.MIN_VALUE;
        int high = range.has("max") ? LootNumberRange.of(range.get("max"), max).maxInt() : Integer.MAX_VALUE;
        return new int[] {Math.max(low, Math.min(min, high)), Math.max(low, Math.min(max, high))};
    }
}
