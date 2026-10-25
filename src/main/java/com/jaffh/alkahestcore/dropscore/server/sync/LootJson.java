package com.jaffh.alkahestcore.dropscore.server.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the JSON of a serialized loot table leniently: a missing or malformed value gives the fallback, since modded
 * entries may use any shape.
 */
final class LootJson {

    /**
     * Prevents instantiation of this utility class.
     */
    private LootJson() {
    }

    /**
     * Returns the elements of an array.
     *
     * @param object Object to read. Never null.
     * @param key    Key of the array. Never null.
     * @return The elements, empty when the key is missing or not an array.
     */
    static List<JsonElement> array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) return List.of();
        JsonArray array = element.getAsJsonArray();
        List<JsonElement> values = new ArrayList<>(array.size());
        array.forEach(values::add);
        return values;
    }

    /**
     * Returns the objects of an array, skipping other elements.
     *
     * @param object Object to read. Never null.
     * @param key    Key of the array. Never null.
     * @return The objects, empty when the key is missing or not an array.
     */
    static List<JsonObject> objects(JsonObject object, String key) {
        List<JsonObject> objects = new ArrayList<>();
        for (JsonElement element : array(object, key)) {
            if (element.isJsonObject()) objects.add(element.getAsJsonObject());
        }
        return objects;
    }

    /**
     * Returns a string value.
     *
     * @param object   Object to read. Never null.
     * @param key      Key of the value. Never null.
     * @param fallback Value when the key is missing or not a string.
     * @return The value.
     */
    static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : fallback;
    }

    /**
     * Returns a number value.
     *
     * @param object   Object to read. Never null.
     * @param key      Key of the value. Never null.
     * @param fallback Value when the key is missing or not a number.
     * @return The value.
     */
    static double number(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        return element instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsDouble() : fallback;
    }

    /**
     * Returns a boolean value.
     *
     * @param object   Object to read. Never null.
     * @param key      Key of the value. Never null.
     * @param fallback Value when the key is missing or not a boolean.
     * @return The value.
     */
    static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        return element instanceof JsonPrimitive primitive && primitive.isBoolean() ? primitive.getAsBoolean() : fallback;
    }

    /**
     * Returns an id value, with the {@code minecraft} namespace when it has none.
     *
     * @param object Object to read. Never null.
     * @param key    Key of the id. Never null.
     * @return The id, or null when the key is missing or not a valid id.
     */
    @Nullable
    static ResourceLocation id(JsonObject object, String key) {
        String value = string(object, key, "");
        return value.isEmpty() ? null : ResourceLocation.tryParse(value);
    }
}
