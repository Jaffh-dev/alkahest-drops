package com.jaffh.alkahestcore.dropscore.common.mobdrops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class MobDropJsonStructure {

    public String entity;

    @SerializedName("replace_drops")
    public List<String> replaceDrops;

    public String drop;
    public JsonElement nbt;
    public double chance;

    @SerializedName("drop_behaviour")
    public DropBehaviour dropBehaviour;

    public Count count;

    @SerializedName("on_looting")
    public OnLooting onLooting;

    public JsonArray conditions;

    public static class DropBehaviour{
        public String type;
        public int rolls;
        @SerializedName("chance_per_roll")
        public double chancePerRoll;
        @SerializedName("increment_value")
        public int incrementValue;
    }

    public static class Count{
        public int min;
        public int max;
    }

    public static class OnLooting{
        @SerializedName("bonus_chance")
        public BonusChance bonusChance;
        @SerializedName("bonus_roll")
        public BonusRoll bonusRoll;
        @SerializedName("bonus_count")
        public BonusCount bonusCount;
    }

    public static class BonusChance{
        @SerializedName("bonus_chance_per_level")
        public double bonusChancePerLevel;
        @SerializedName("max_bonus_chance")
        public double maxBonusChance;
    }

    public static class BonusRoll{
        @SerializedName("bonus_rolls_per_level")
        public int bonusRollsPerLevel;
        @SerializedName("bonus_roll_chance_level")
        public double bonusRollChancePerLevel;
        @SerializedName("max_bonus_rolls")
        public int maxBonusRolls;
        @SerializedName("max_bonus_roll_chance")
        public int maxBonusRollChance;
    }

    public static class BonusCount{
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
