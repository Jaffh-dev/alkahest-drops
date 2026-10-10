package com.jaffh.alkahestcore.dropscore.server.drops.json;

import com.google.gson.JsonArray;

/**
 * Gson DTO of a {@code removals} entry written as an object. Every field is null when its key is missing.
 */
public class RemovalEntryJson {
    public String item;
    public JsonArray conditions;
}
