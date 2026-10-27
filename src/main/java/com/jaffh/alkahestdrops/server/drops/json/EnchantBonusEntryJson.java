package com.jaffh.alkahestdrops.server.drops.json;

import com.google.gson.annotations.SerializedName;

/**
 * Gson DTO of one entry in {@code enchant_bonus}: the enchantment id and its bonus. Every field is null when its key
 * is missing.
 */
public class EnchantBonusEntryJson {
    public String enchant;

    @SerializedName("on_enchant")
    public EnchantBonusJson onEnchant;
}
