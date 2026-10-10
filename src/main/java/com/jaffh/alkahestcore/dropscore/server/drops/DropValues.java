package com.jaffh.alkahestcore.dropscore.server.drops;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Reads the JSON values shared by every part of a dropscore file: objects, ids, items, NBT and loot conditions.
 */
public final class DropValues {
    private static final Gson GSON = new Gson();
    private static final Gson LOOT_GSON = Deserializers.createConditionSerializer().create();

    /**
     * Prevents instantiation of this utility class.
     */
    private DropValues() {
    }

    /**
     * Reads a JSON object into a Gson DTO.
     *
     * @param element JSON value. Never null.
     * @param type    DTO class.
     * @param ctx     Context of the value. Never null.
     * @param <T>     DTO type.
     * @return The DTO, with null fields for missing keys.
     * @throws DropDataException If the value is not an object or a key has the wrong type.
     */
    public static <T> T readObject(JsonElement element, Class<T> type, ParseContext ctx) throws DropDataException {
        if (!element.isJsonObject()) throw ctx.error("must be a JSON object");
        try {
            return GSON.fromJson(element, type);
        } catch (JsonParseException | NumberFormatException e) {
            // Reading from a JsonElement tree, Gson throws a bare NumberFormatException for "chance": "half".
            throw ctx.error("wrong value type (" + e.getMessage() + ")");
        }
    }

    /**
     * Parses a JSON array of vanilla loot conditions.
     *
     * @param conditions JSON array, or null when missing.
     * @param ctx        Context of the {@code conditions} key. Never null.
     * @return The parsed conditions, empty when the array is missing or empty.
     * @throws DropDataException If any condition is invalid.
     */
    public static List<LootItemCondition> parseConditions(@Nullable JsonArray conditions, ParseContext ctx) throws DropDataException {
        if (conditions == null || conditions.isEmpty()) return List.of();
        try {
            return List.of(LOOT_GSON.fromJson(conditions, LootItemCondition[].class));
        } catch (RuntimeException e) {
            // Vanilla condition serializers also throw non-Gson exceptions, such as ResourceLocationException for bad ids.
            throw ctx.error("invalid loot condition (" + e.getMessage() + ")");
        }
    }

    /**
     * Parses a resource location.
     *
     * @param id  Raw id. Never null.
     * @param ctx Context of the key the id came from. Never null.
     * @return The parsed location.
     * @throws DropDataException If the id is malformed.
     */
    public static ResourceLocation parseId(String id, ParseContext ctx) throws DropDataException {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) throw ctx.error("'" + id + "' is not a valid id");
        return location;
    }

    /**
     * Resolves a registered item.
     *
     * @param id  Item id. Never null.
     * @param ctx Context of the key the id came from. Never null.
     * @return The registered item.
     * @throws DropDataException If the id is malformed (error) or names no registered item (warning).
     */
    public static Item resolveItem(String id, ParseContext ctx) throws DropDataException {
        ResourceLocation location = parseId(id, ctx);
        if (!ForgeRegistries.ITEMS.containsKey(location)) throw ctx.unknownId("'" + id + "' is not a registered item");
        return ForgeRegistries.ITEMS.getValue(location);
    }

    /**
     * Parses the items a removal entry names: an item id, {@code #item_tag} or {@code *}.
     * Tags are not checked here because they are not bound yet while datapack files are read.
     *
     * @param raw Raw value. Never null.
     * @param ctx Context of the value. Never null.
     * @return The parsed target.
     * @throws DropDataException If the id or tag is malformed (error) or the item is not registered (warning).
     */
    public static ItemTarget parseItemTarget(String raw, ParseContext ctx) throws DropDataException {
        if (raw.equals("*")) return ItemTarget.ANY;
        if (raw.startsWith("#")) {
            return new ItemTarget.Tagged(TagKey.create(Registry.ITEM_REGISTRY, parseId(raw.substring(1), ctx)));
        }
        return new ItemTarget.Exact(resolveItem(raw, ctx));
    }

    /**
     * Parses an NBT value given as an SNBT string or a JSON object.
     *
     * @param nbt JSON value, or null when missing.
     * @param ctx Context of the {@code nbt} key. Never null.
     * @return The parsed tag, or null when missing.
     * @throws DropDataException If the SNBT is invalid.
     */
    @Nullable
    public static CompoundTag parseNbt(@Nullable JsonElement nbt, ParseContext ctx) throws DropDataException {
        if (nbt == null || nbt.isJsonNull()) return null;
        String snbt = nbt.isJsonPrimitive() ? nbt.getAsString() : nbt.toString();
        try {
            return TagParser.parseTag(snbt);
        } catch (CommandSyntaxException e) {
            throw ctx.error("invalid NBT (" + e.getMessage() + ")");
        }
    }
}
