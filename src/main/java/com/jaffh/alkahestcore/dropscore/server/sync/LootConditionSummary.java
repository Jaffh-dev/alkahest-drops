package com.jaffh.alkahestcore.dropscore.server.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Summarizes the loot conditions of a pool or entry: random chances become a chance factor and an enchantment bonus,
 * every other condition is kept as JSON for {@link ConditionDescriber}.
 *
 * @param factor        Product of the random chances (0–1).
 * @param bonusPerLevel Chance added per level of the bonus enchantment, before the other factors.
 * @param bonusCap      Highest chance the bonus can add, before the other factors.
 * @param others        Conditions that are not random chances, in order. Immutable, never null.
 */
record LootConditionSummary(double factor, double bonusPerLevel, double bonusCap, List<JsonElement> others) {
    /** No conditions. */
    static final LootConditionSummary NONE = new LootConditionSummary(1.0, 0.0, 0.0, List.of());

    private static final ResourceLocation RANDOM_CHANCE = new ResourceLocation("random_chance");
    private static final ResourceLocation RANDOM_CHANCE_WITH_LOOTING = new ResourceLocation("random_chance_with_looting");
    private static final ResourceLocation TABLE_BONUS = new ResourceLocation("table_bonus");
    /** Passes on every player break; vanilla puts it on nearly every block table, so listing it is noise. */
    private static final ResourceLocation SURVIVES_EXPLOSION = new ResourceLocation("survives_explosion");
    private static final ResourceLocation LOOTING = new ResourceLocation("looting");

    /**
     * Summarizes serialized conditions.
     *
     * @param conditions       Serialized conditions. Never null.
     * @param bonusEnchantment Id of the module's bonus enchantment ({@code looting} or {@code fortune}). Never null.
     * @return The summary.
     */
    static LootConditionSummary of(List<JsonElement> conditions, ResourceLocation bonusEnchantment) {
        double factor = 1.0;
        double bonusPerLevel = 0.0;
        double bonusCap = 0.0;
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
                if (bonusEnchantment.equals(LOOTING)) {
                    bonusPerLevel += LootJson.number(condition, "looting_multiplier", 0.0);
                    bonusCap = 1.0;
                }
            } else if (type.equals(TABLE_BONUS)) {
                List<JsonElement> chances = LootJson.array(condition, "chances");
                double first = chances.isEmpty() ? 1.0 : chances.get(0).getAsDouble();
                factor *= first;
                if (chances.size() > 1 && bonusEnchantment.equals(LootJson.id(condition, "enchantment"))) {
                    double last = chances.get(chances.size() - 1).getAsDouble();
                    bonusPerLevel += (last - first) / (chances.size() - 1);
                    bonusCap += last - first;
                }
            } else {
                others.add(condition);
            }
        }
        return new LootConditionSummary(factor, bonusPerLevel, bonusCap, List.copyOf(others));
    }

    /**
     * Combines two summaries, such as a pool's and one of its entries'.
     *
     * @param other Summary to add. Never null.
     * @return The combined summary: factors multiplied, bonuses added, other conditions joined.
     */
    LootConditionSummary and(LootConditionSummary other) {
        List<JsonElement> merged = new ArrayList<>(others);
        merged.addAll(other.others);
        return new LootConditionSummary(factor * other.factor, bonusPerLevel + other.bonusPerLevel,
                Math.min(1.0, bonusCap + other.bonusCap), List.copyOf(merged));
    }
}
