package com.jaffh.alkahestcore.dropscore.server.drops.json;

import com.google.gson.annotations.SerializedName;

/**
 * Gson DTO of {@code on_looting} (mob files) or {@code on_fortune} (block files). Every field is null when its key is missing.
 */
public class EnchantBonusJson {
    @SerializedName("bonus_chance")
    public BonusChanceJson bonusChance;

    @SerializedName("bonus_roll")
    public BonusRollJson bonusRoll;

    @SerializedName("bonus_count")
    public BonusCountJson bonusCount;
}
