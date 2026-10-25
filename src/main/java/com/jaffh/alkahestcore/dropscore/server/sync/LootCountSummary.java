package com.jaffh.alkahestcore.dropscore.server.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jaffh.alkahestcore.dropscore.common.drops.DropCount;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonus;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Summarizes the loot functions applied to one entry: the functions that set or raise the count become a count range
 * and a count bonus; the others do not change what JEI shows.
 *
 * @param count Count range of one pick. Never null.
 * @param bonus Count bonus per level of the module's bonus enchantment. Never null.
 */
record LootCountSummary(DropCount count, EnchantBonus.CountBonus bonus) {
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
     * @param functions        Serialized functions: the entry's, then the pool's, then the table's. Never null.
     * @param bonusEnchantment Id of the module's bonus enchantment ({@code looting} or {@code fortune}). Never null.
     * @return The summary.
     */
    static LootCountSummary of(List<JsonElement> functions, ResourceLocation bonusEnchantment) {
        int min = 1;
        int max = 1;
        int bonusMin = 0;
        int bonusMax = 0;
        int bonusCap = Integer.MAX_VALUE;
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
            } else if (type.equals(LOOTING_ENCHANT) && bonusEnchantment.equals(LOOTING)) {
                LootNumberRange range = LootNumberRange.of(function.get("count"), 0);
                bonusMin = (int) Math.round(range.min());
                bonusMax = (int) Math.round(range.max());
                int limit = (int) LootJson.number(function, "limit", 0);
                if (limit > 0) bonusCap = limit;
            } else if (type.equals(APPLY_BONUS) && isUniformBonus(function, bonusEnchantment)) {
                JsonElement parameters = function.get("parameters");
                double multiplier = parameters != null && parameters.isJsonObject()
                        ? LootJson.number(parameters.getAsJsonObject(), "bonusMultiplier", 1) : 1;
                bonusMax = (int) Math.round(multiplier);
            } else if (type.equals(LIMIT_COUNT)) {
                int[] limited = limit(function.get("limit"), min, max);
                min = limited[0];
                max = limited[1];
            }
        }
        min = Math.max(0, min);
        max = Math.max(min, max);
        EnchantBonus.CountBonus bonus = bonusMin == 0 && bonusMax == 0
                ? EnchantBonus.CountBonus.NONE
                : new EnchantBonus.CountBonus(bonusMin, bonusMax, bonusCap, bonusCap);
        return new LootCountSummary(new DropCount(min, max), bonus);
    }

    /**
     * Tells whether an {@code apply_bonus} function adds a uniform count per level of the module's enchantment, the
     * only formula that maps to a count bonus ({@code ore_drops} and {@code binomial_with_bonus_count} do not).
     *
     * @param function         Serialized function. Never null.
     * @param bonusEnchantment Id of the module's bonus enchantment. Never null.
     * @return True for {@code uniform_bonus_count} with the module's enchantment.
     */
    private static boolean isUniformBonus(JsonObject function, ResourceLocation bonusEnchantment) {
        return bonusEnchantment.equals(LootJson.id(function, "enchantment"))
                && UNIFORM_BONUS_COUNT.equals(LootJson.id(function, "formula"));
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
        if (limit == null) return new int[]{min, max};
        if (limit.isJsonPrimitive()) {
            int exact = (int) limit.getAsDouble();
            return new int[]{exact, exact};
        }
        if (!limit.isJsonObject()) return new int[]{min, max};
        JsonObject range = limit.getAsJsonObject();
        int low = range.has("min") ? LootNumberRange.of(range.get("min"), min).minInt() : Integer.MIN_VALUE;
        int high = range.has("max") ? LootNumberRange.of(range.get("max"), max).maxInt() : Integer.MAX_VALUE;
        return new int[]{Math.max(low, Math.min(min, high)), Math.max(low, Math.min(max, high))};
    }
}
