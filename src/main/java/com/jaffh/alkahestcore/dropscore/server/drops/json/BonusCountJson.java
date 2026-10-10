package com.jaffh.alkahestcore.dropscore.server.drops.json;

import com.google.gson.annotations.SerializedName;

/**
 * Gson DTO of {@code bonus_count}. Every field is null when its key is missing.
 */
public class BonusCountJson {
    @SerializedName("bonus_min_per_level")
    public Integer bonusMinPerLevel;

    @SerializedName("bonus_max_per_level")
    public Integer bonusMaxPerLevel;

    @SerializedName("max_bonus_min")
    public Integer maxBonusMin;

    @SerializedName("max_bonus_max")
    public Integer maxBonusMax;
}
