package com.jaffh.alkahestcore.dropscore.server.drops.json;

import com.google.gson.JsonArray;

/**
 * Gson DTO of one dropscore file, read after {@code DropFileUpgrader} has checked and upgraded its {@code version}.
 * A file is either a drop file ({@code drops}) or a removal file ({@code removals}).
 * Every field is null when its key is missing. {@code drops} and {@code removals} stay raw so one bad entry
 * skips only that entry.
 */
public class DropFileJson {
    public String entity;
    public String block;
    public JsonArray conditions;
    public JsonArray drops;
    public JsonArray removals;
}
