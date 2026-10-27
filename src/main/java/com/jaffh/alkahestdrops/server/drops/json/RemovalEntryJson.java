package com.jaffh.alkahestdrops.server.drops.json;

import com.google.gson.JsonArray;

/**
 * Gson DTO of a {@code removals} entry written as an object. Every field is null when its key is missing.
 * An experience removal sets {@code experience} to true instead of {@code item}.
 */
public class RemovalEntryJson {
    public String item;
    public Boolean experience;
    public JsonArray conditions;
}
