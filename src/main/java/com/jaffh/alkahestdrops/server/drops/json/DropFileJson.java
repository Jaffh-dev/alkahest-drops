package com.jaffh.alkahestdrops.server.drops.json;

import com.google.gson.JsonArray;

/**
 * Gson DTO of one Alkahest Drops file, read after {@code DropFileUpgrader} has checked and upgraded its {@code version}.
 * A file is either a drop file ({@code drops}) or a removal file ({@code removals}).
 * {@code on} is the trigger of a removal file (mob files only).
 * Every field is null when its key is missing. {@code drops} and {@code removals} stay raw so one bad entry
 * skips only that entry.
 */
public class DropFileJson {
    public String entity;
    public String block;
    public JsonArray conditions;
    public JsonArray drops;
    public JsonArray removals;
    public String on;
}
