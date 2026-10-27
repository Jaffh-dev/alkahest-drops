package com.jaffh.alkahestdrops.server.drops.json;

import com.google.gson.annotations.SerializedName;

/**
 * Gson DTO of {@code bonus_chance}. Every field is null when its key is missing.
 */
public class BonusChanceJson {
    @SerializedName("bonus_chance_per_level")
    public Double bonusChancePerLevel;

    @SerializedName("max_bonus_chance")
    public Double maxBonusChance;
}
