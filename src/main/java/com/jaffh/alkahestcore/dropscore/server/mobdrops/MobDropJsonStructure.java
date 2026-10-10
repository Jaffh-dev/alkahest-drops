package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Gson DTO of one file in {@code data/<namespace>/mob_drops/}: either a drop file ({@code drops})
 * or a removal file ({@code removals} and/or {@code remove_experience}), never both.
 * The top-level {@code conditions} are the global conditions of either kind.
 */
public class MobDropJsonStructure {

    public String entity;

    public JsonArray conditions;

    public List<Drop> drops;

    /** Each entry is an item id string or an object read as {@link Removal}. */
    public List<JsonElement> removals;

    @SerializedName("remove_experience")
    public boolean shouldRemoveExperience;

    /**
     * Gson DTO of a {@code removals} entry written as an object.
     */
    public static class Removal {
        public String item;
        public JsonArray conditions;
    }

    /**
     * Gson DTO of one entry in {@code drops}.
     */
    public static class Drop {
        public String item;
        public JsonElement nbt;
        public double chance;

        @SerializedName("drop_behaviour")
        public DropBehaviour dropBehaviour;

        public Count count;

        @SerializedName("on_looting")
        public OnLooting onLooting;

        public JsonArray conditions;
    }

    /**
     * Gson DTO of {@code drop_behaviour}.
     */
    public static class DropBehaviour {
        public String type;
        public int rolls;
        @SerializedName("chance_per_roll")
        public double chancePerRoll;
        @SerializedName("increment_value")
        public int incrementValue;
    }

    /**
     * Gson DTO of {@code count}. A missing {@code max} means {@code max = min}.
     */
    public static class Count {
        public int min;
        public Integer max;
    }

    /**
     * Gson DTO of {@code on_looting}.
     */
    public static class OnLooting {
        @SerializedName("bonus_chance")
        public BonusChance bonusChance;
        @SerializedName("bonus_roll")
        public BonusRoll bonusRoll;
        @SerializedName("bonus_count")
        public BonusCount bonusCount;
    }

    /**
     * Gson DTO of {@code bonus_chance}.
     */
    public static class BonusChance {
        @SerializedName("bonus_chance_per_level")
        public double bonusChancePerLevel;
        @SerializedName("max_bonus_chance")
        public double maxBonusChance;
    }

    /**
     * Gson DTO of {@code bonus_roll}.
     */
    public static class BonusRoll {
        @SerializedName("bonus_rolls_per_level")
        public int bonusRollsPerLevel;
        @SerializedName("bonus_roll_chance_per_level")
        public double bonusRollChancePerLevel;
        @SerializedName("max_bonus_rolls")
        public int maxBonusRolls;
        @SerializedName("max_bonus_roll_chance")
        public double maxBonusRollChance;
    }

    /**
     * Gson DTO of {@code bonus_count}.
     */
    public static class BonusCount {
        @SerializedName("bonus_min_per_level")
        public int bonusMinPerLevel;
        @SerializedName("bonus_max_per_level")
        public int bonusMaxPerLevel;
        @SerializedName("max_bonus_min")
        public int maxBonusMin;
        @SerializedName("max_bonus_max")
        public int maxBonusMax;
    }
}
