package com.jaffh.alkahestdrops.server.drops.json;

import com.google.gson.annotations.SerializedName;

/**
 * Gson DTO of a drop's {@code on_drop}. Every field is null when its key is missing; a missing action is off.
 */
public class DropActionsJson {
    @SerializedName("glow")
    public Boolean shouldGlow;

    @SerializedName("run_function")
    public String runFunction;
}
