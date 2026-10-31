package com.jaffh.alkahestdrops.server.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jaffh.alkahestdrops.common.drops.EnchantBonus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Summarizes the loot conditions of a pool or entry: random chances become a chance factor and an enchantment bonus,
 * every other condition is kept as JSON for {@link ConditionDescriber}.
 *
 * @param factor  Product of the random chances (0–1).
 * @param bonuses Chance bonus of each enchantment, by enchantment id: the chance added per level and the highest
 *                chance it can add, both before the other factors. Immutable, never null.
 * @param others  Conditions that are not random chances, in order. Immutable, never null.
 */
record LootConditionSummary(
        double factor, Map<ResourceLocation, EnchantBonus.ChanceBonus> bonuses, List<JsonElement> others) {
    /** No conditions. */
    static final LootConditionSummary NONE = new LootConditionSummary(1.0, Map.of(), List.of());

    private static final ResourceLocation RANDOM_CHANCE = new ResourceLocation("random_chance");
    private static final ResourceLocation RANDOM_CHANCE_WITH_LOOTING =
            new ResourceLocation("random_chance_with_looting");
    private static final ResourceLocation TABLE_BONUS = new ResourceLocation("table_bonus");
    /** Passes on every player break; vanilla puts it on nearly every block table, so listing it is noise. */
    private static final ResourceLocation SURVIVES_EXPLOSION = new ResourceLocation("survives_explosion");

    private static final ResourceLocation LOOTING = new ResourceLocation("looting");

    /**
     * Summarizes serialized conditions.
     *
     * @param conditions Serialized conditions. Never null.
     * @return The summary.
     */
    static LootConditionSummary of(List<JsonElement> conditions) {
        double factor = 1.0;
        Map<ResourceLocation, EnchantBonus.ChanceBonus> bonuses = new LinkedHashMap<>();
        List<JsonElement> others = new ArrayList<>();
        for (JsonElement element : conditions) {
            if (!element.isJsonObject()) continue;
            JsonObject condition = element.getAsJsonObject();
            ResourceLocation type = LootJson.id(condition, "condition");
            if (type == null || type.equals(SURVIVES_EXPLOSION)) continue;
            if (type.equals(RANDOM_CHANCE)) {
                factor *= LootJson.number(condition, "chance", 1.0);
            } else if (type.equals(RANDOM_CHANCE_WITH_LOOTING)) {
                factor *= LootJson.number(condition, "chance", 1.0);
                addBonus(bonuses, LOOTING, LootJson.number(condition, "looting_multiplier", 0.0), 1.0);
            } else if (type.equals(TABLE_BONUS)) {
                List<JsonElement> chances = LootJson.array(condition, "chances");
                double first = chances.isEmpty() ? 1.0 : chances.get(0).getAsDouble();
                factor *= first;
                ResourceLocation enchantment = LootJson.id(condition, "enchantment");
                if (chances.size() > 1 && enchantment != null) {
                    double last = chances.get(chances.size() - 1).getAsDouble();
                    addBonus(bonuses, enchantment, (last - first) / (chances.size() - 1), last - first);
                }
            } else {
                others.add(condition);
            }
        }
        return new LootConditionSummary(factor, Map.copyOf(bonuses), List.copyOf(others));
    }

    /**
     * Adds a chance bonus of one enchantment to the bonuses found so far: per-level chances and caps add up, the cap
     * staying at most 1.
     *
     * @param bonuses     Bonuses by enchantment id. Never null.
     * @param enchantment Id of the enchantment. Never null.
     * @param perLevel    Chance added per level.
     * @param cap         Highest chance it can add.
     */
    private static void addBonus(
            Map<ResourceLocation, EnchantBonus.ChanceBonus> bonuses,
            ResourceLocation enchantment,
            double perLevel,
            double cap) {
        if (perLevel <= 0) return;
        bonuses.merge(
                enchantment,
                new EnchantBonus.ChanceBonus(perLevel, cap),
                (first, second) -> new EnchantBonus.ChanceBonus(
                        first.bonusPerLevel() + second.bonusPerLevel(),
                        Math.min(1.0, first.maxBonus() + second.maxBonus())));
    }

    /**
     * Combines two summaries, such as a pool's and one of its entries'.
     *
     * @param other Summary to add. Never null.
     * @return The combined summary: factors multiplied, bonuses of the same enchantment added, other conditions joined.
     */
    LootConditionSummary and(LootConditionSummary other) {
        List<JsonElement> merged = new ArrayList<>(others);
        merged.addAll(other.others);
        Map<ResourceLocation, EnchantBonus.ChanceBonus> mergedBonuses = new LinkedHashMap<>(bonuses);
        other.bonuses.forEach(
                (enchantment, bonus) -> addBonus(mergedBonuses, enchantment, bonus.bonusPerLevel(), bonus.maxBonus()));
        return new LootConditionSummary(factor * other.factor, Map.copyOf(mergedBonuses), List.copyOf(merged));
    }
}
