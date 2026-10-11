package com.jaffh.alkahestcore.dropscore.server.drops.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * Gson DTO of one entry in {@code drops}. Every field is null when its key is missing.
 * Mob files read {@code on_looting} and block files read {@code on_fortune}. An experience drop sets
 * {@code experience} to true instead of {@code item}.
 */
public class DropEntryJson {
    public String item;
    public Boolean experience;
    public JsonElement nbt;
    public Double chance;
    public CountJson count;

    @SerializedName("drop_behavior")
    public DropBehaviorJson dropBehavior;

    @SerializedName("on_looting")
    public EnchantBonusJson onLooting;

    @SerializedName("on_fortune")
    public EnchantBonusJson onFortune;

    public JsonArray conditions;
    public JsonArray functions;
}
