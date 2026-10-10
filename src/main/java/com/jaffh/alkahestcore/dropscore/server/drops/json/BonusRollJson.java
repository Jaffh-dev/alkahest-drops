package com.jaffh.alkahestcore.dropscore.server.drops.json;

import com.google.gson.annotations.SerializedName;

/**
 * Gson DTO of {@code bonus_roll}. Every field is null when its key is missing.
 */
public class BonusRollJson {
    @SerializedName("bonus_rolls_per_level")
    public Integer bonusRollsPerLevel;

    @SerializedName("bonus_roll_chance_per_level")
    public Double bonusRollChancePerLevel;

    @SerializedName("bonus_increment_per_level")
    public Integer bonusIncrementPerLevel;

    @SerializedName("max_bonus_rolls")
    public Integer maxBonusRolls;

    @SerializedName("max_bonus_roll_chance")
    public Double maxBonusRollChance;

    @SerializedName("max_bonus_increment")
    public Integer maxBonusIncrement;
}
