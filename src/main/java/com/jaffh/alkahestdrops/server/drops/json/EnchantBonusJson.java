package com.jaffh.alkahestdrops.server.drops.json;

import com.google.gson.annotations.SerializedName;

/**
 * Gson DTO of {@code on_enchant}, the bonus of one {@code enchant_bonus} entry. Every field is null when its key is
 * missing.
 */
public class EnchantBonusJson {
    @SerializedName("bonus_chance")
    public BonusChanceJson bonusChance;

    @SerializedName("bonus_roll")
    public BonusRollJson bonusRoll;

    @SerializedName("bonus_count")
    public BonusCountJson bonusCount;
}
