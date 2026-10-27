package com.jaffh.alkahestdrops.server.drops.json;

import com.google.gson.annotations.SerializedName;

/**
 * Gson DTO of {@code drop_behavior}. Every field is null when its key is missing.
 */
public class DropBehaviorJson {
    public String type;
    public Integer rolls;

    @SerializedName("chance_per_roll")
    public Double chancePerRoll;

    @SerializedName("increment_value")
    public Integer incrementValue;

    public String function;
    public Double multiplier;
    public Double divisor;
}
