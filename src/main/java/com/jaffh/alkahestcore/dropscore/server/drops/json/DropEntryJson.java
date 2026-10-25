package com.jaffh.alkahestcore.dropscore.server.drops.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * Gson DTO of one entry in {@code drops}. Every field is null when its key is missing.
 * {@code enchant_bonus} is a list of {@link EnchantBonusEntryJson}, read by hand so a bad entry is reported at its
 * own path. Mob files read {@code first_kill}, block files read {@code first_break}. An experience drop sets
 * {@code experience} to true instead of {@code item}. {@code on} picks the trigger (mob files only), with its tick
 * setting {@code interval} (periodic) or {@code cooldown} (hit). {@code on_drop} holds what happens when the item spawns.
 */
public class DropEntryJson {
    public String id;
    public String item;
    public Boolean experience;
    public JsonElement nbt;
    public Double chance;
    public CountJson count;

    @SerializedName("drop_behavior")
    public DropBehaviorJson dropBehavior;

    @SerializedName("enchant_bonus")
    public JsonElement enchantBonus;

    public JsonArray conditions;
    public JsonArray functions;
    public Integer pity;

    @SerializedName("first_kill")
    public Boolean isFirstKill;

    @SerializedName("first_break")
    public Boolean isFirstBreak;

    @SerializedName("per_player")
    public Boolean isPerPlayer;

    public String on;
    public Integer interval;
    public Integer cooldown;

    @SerializedName("on_drop")
    public DropActionsJson onDrop;
}
