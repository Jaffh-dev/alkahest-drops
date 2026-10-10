package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.jaffh.alkahestcore.dropscore.common.drops.DropBehaviour;
import com.jaffh.alkahestcore.dropscore.common.drops.DropBehaviourType;
import com.jaffh.alkahestcore.dropscore.common.drops.DropCount;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonus;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalEntry;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Converts the Gson DTOs of mob drop files into resolved records, applying defaults and rejecting invalid data.
 */
public final class MobDropParser {
    private static final Gson GSON = new Gson();
    private static final Gson LOOT_GSON = Deserializers.createConditionSerializer().create();
    /** Highest rolls a drop may set; roll loops run once per roll on every kill, so this bounds the per-kill cost. */
    private static final int MAX_ROLLS = 1024;
    /** Highest count or count bonus a drop may set; keeps the count math far from int overflow. */
    private static final int MAX_COUNT = 1024;

    /**
     * Prevents instantiation of this utility class.
     */
    private MobDropParser() {
    }

    /**
     * Resolves the target entity type of a file.
     *
     * @param id Entity id from the {@code entity} key, or null when missing.
     * @return The registered entity type.
     * @throws InvalidDropDataException If the id is missing, malformed or not registered.
     */
    public static EntityType<?> resolveEntity(@Nullable String id) throws InvalidDropDataException {
        ResourceLocation location = parseId(id, "entity");
        if (!ForgeRegistries.ENTITIES.containsKey(location)) {
            throw new UnknownIdException("entity '" + id + "' is not registered");
        }
        return ForgeRegistries.ENTITIES.getValue(location);
    }

    /**
     * Resolves a registered item.
     *
     * @param id    Item id, or null when missing.
     * @param field JSON key the id came from, used in the error message.
     * @return The registered item.
     * @throws InvalidDropDataException If the id is missing or malformed, or an {@link UnknownIdException}
     *                                  if it is not registered.
     */
    public static Item resolveItem(@Nullable String id, String field) throws InvalidDropDataException {
        ResourceLocation location = parseId(id, field);
        if (!ForgeRegistries.ITEMS.containsKey(location)) {
            throw new UnknownIdException(field + " '" + id + "' is not a registered item");
        }
        return ForgeRegistries.ITEMS.getValue(location);
    }

    /**
     * Converts one entry of {@code removals}, written as an item id string or as an object with
     * {@code item} and local {@code conditions}.
     *
     * @param element JSON value of the entry. Never null.
     * @return The resolved removal entry.
     * @throws InvalidDropDataException If the entry has the wrong shape, no {@code item} or invalid conditions,
     *                                  or an {@link UnknownIdException} if the item is not registered.
     */
    public static RemovalEntry parseRemovalEntry(JsonElement element) throws InvalidDropDataException {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new RemovalEntry(resolveItem(element.getAsString(), "item"), List.of());
        }
        if (!element.isJsonObject()) {
            throw new InvalidDropDataException("entry must be an item id or an object with 'item'");
        }
        MobDropJsonStructure.Removal removal;
        try {
            removal = GSON.fromJson(element, MobDropJsonStructure.Removal.class);
        } catch (JsonParseException e) {
            throw new InvalidDropDataException("invalid entry: " + e.getMessage());
        }
        return new RemovalEntry(resolveItem(removal.item, "item"), parseConditions(removal.conditions));
    }

    /**
     * Converts one entry of {@code drops} into a resolved drop with every default applied.
     *
     * @param drop Gson DTO of the drop. Never null.
     * @return The resolved drop.
     * @throws InvalidDropDataException If the item, NBT, chance, count, behavior, Looting bonus or conditions are invalid.
     */
    public static DropEntry parseDrop(MobDropJsonStructure.Drop drop) throws InvalidDropDataException {
        requireInRange(drop.chance, 0.0, 1.0, "chance");
        return new DropEntry(
                resolveItem(drop.item, "item"),
                parseNbt(drop.nbt),
                drop.chance,
                parseCount(drop.count),
                parseBehaviour(drop.dropBehaviour),
                parseLootingBonus(drop.onLooting),
                parseConditions(drop.conditions)
        );
    }

    /**
     * Parses a JSON array of vanilla loot conditions.
     *
     * @param conditions JSON array, or null when missing.
     * @return The parsed conditions, empty when the array is missing.
     * @throws InvalidDropDataException If any condition is invalid.
     */
    public static List<LootItemCondition> parseConditions(@Nullable JsonArray conditions) throws InvalidDropDataException {
        if (conditions == null || conditions.isEmpty()) return List.of();
        try {
            return List.of(LOOT_GSON.fromJson(conditions, LootItemCondition[].class));
        } catch (JsonParseException e) {
            throw new InvalidDropDataException("invalid conditions: " + e.getMessage());
        }
    }

    /**
     * Parses a resource location from a JSON string.
     *
     * @param id    Raw id, or null when missing.
     * @param field JSON key the id came from, used in the error message.
     * @return The parsed location.
     * @throws InvalidDropDataException If the id is missing or malformed.
     */
    private static ResourceLocation parseId(@Nullable String id, String field) throws InvalidDropDataException {
        if (id == null) {
            throw new InvalidDropDataException("missing required key '" + field + "'");
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new InvalidDropDataException(field + " '" + id + "' is not a valid id");
        }
        return location;
    }

    /**
     * Parses the {@code nbt} of a drop, given as an SNBT string or a JSON object.
     *
     * @param nbt JSON value, or null when missing.
     * @return The parsed tag, or null when missing.
     * @throws InvalidDropDataException If the SNBT is invalid.
     */
    @Nullable
    private static CompoundTag parseNbt(@Nullable JsonElement nbt) throws InvalidDropDataException {
        if (nbt == null || nbt.isJsonNull()) return null;
        String snbt = nbt.isJsonPrimitive() ? nbt.getAsString() : nbt.toString();
        try {
            return TagParser.parseTag(snbt);
        } catch (CommandSyntaxException e) {
            throw new InvalidDropDataException("invalid nbt: " + e.getMessage());
        }
    }

    /**
     * Resolves the count range of a drop.
     *
     * @param count Gson DTO, or null when missing.
     * @return {@link DropCount#ONE} when missing, otherwise the range with {@code max} defaulting to {@code min}.
     * @throws InvalidDropDataException If min or max is out of range, or max is below min.
     */
    private static DropCount parseCount(@Nullable MobDropJsonStructure.Count count) throws InvalidDropDataException {
        if (count == null) return DropCount.ONE;
        int max = count.max != null ? count.max : count.min;
        requireInRange(count.min, 0, MAX_COUNT, "count.min");
        requireInRange(max, count.min, MAX_COUNT, "count.max");
        return new DropCount(count.min, max);
    }

    /**
     * Resolves the roll behavior of a drop. The roll settings are only checked for roll-based types,
     * since {@code uniform} ignores them.
     *
     * @param behaviour Gson DTO, or null when missing.
     * @return {@link DropBehaviour#UNIFORM} when missing, otherwise the resolved behavior.
     * @throws InvalidDropDataException If the behavior type is unknown or a roll setting is out of range.
     */
    private static DropBehaviour parseBehaviour(@Nullable MobDropJsonStructure.DropBehaviour behaviour) throws InvalidDropDataException {
        if (behaviour == null) return DropBehaviour.UNIFORM;
        DropBehaviourType type = DropBehaviourType.UNIFORM;
        if (behaviour.type != null) {
            type = DropBehaviourType.fromId(behaviour.type)
                    .orElseThrow(() -> new InvalidDropDataException("unknown drop_behaviour type '" + behaviour.type + "'"));
        }
        if (type != DropBehaviourType.UNIFORM) {
            requireInRange(behaviour.rolls, 0, MAX_ROLLS, "drop_behaviour.rolls");
            requireInRange(behaviour.chancePerRoll, 0.0, 1.0, "drop_behaviour.chance_per_roll");
            requireInRange(behaviour.incrementValue, 1, MAX_COUNT, "drop_behaviour.increment_value");
        }
        return new DropBehaviour(type, behaviour.rolls, behaviour.chancePerRoll, behaviour.incrementValue);
    }

    /**
     * Resolves the Looting bonuses of a drop.
     *
     * @param looting Gson DTO, or null when missing.
     * @return {@link EnchantBonus#NONE} when missing; each missing part becomes its {@code NONE} constant.
     * @throws InvalidDropDataException If a bonus value is out of range.
     */
    private static EnchantBonus parseLootingBonus(@Nullable MobDropJsonStructure.OnLooting looting) throws InvalidDropDataException {
        if (looting == null) return EnchantBonus.NONE;
        return new EnchantBonus(
                parseChanceBonus(looting.bonusChance),
                parseRollBonus(looting.bonusRoll),
                parseCountBonus(looting.bonusCount)
        );
    }

    /**
     * Resolves the chance bonus of {@code on_looting}.
     *
     * @param chance Gson DTO, or null when missing.
     * @return {@link EnchantBonus.ChanceBonus#NONE} when missing, otherwise the resolved bonus.
     * @throws InvalidDropDataException If a value is outside 0–1.
     */
    private static EnchantBonus.ChanceBonus parseChanceBonus(@Nullable MobDropJsonStructure.BonusChance chance) throws InvalidDropDataException {
        if (chance == null) return EnchantBonus.ChanceBonus.NONE;
        requireInRange(chance.bonusChancePerLevel, 0.0, 1.0, "bonus_chance.bonus_chance_per_level");
        requireInRange(chance.maxBonusChance, 0.0, 1.0, "bonus_chance.max_bonus_chance");
        return new EnchantBonus.ChanceBonus(chance.bonusChancePerLevel, chance.maxBonusChance);
    }

    /**
     * Resolves the roll bonus of {@code on_looting}.
     *
     * @param roll Gson DTO, or null when missing.
     * @return {@link EnchantBonus.RollBonus#NONE} when missing, otherwise the resolved bonus.
     * @throws InvalidDropDataException If a value is out of range.
     */
    private static EnchantBonus.RollBonus parseRollBonus(@Nullable MobDropJsonStructure.BonusRoll roll) throws InvalidDropDataException {
        if (roll == null) return EnchantBonus.RollBonus.NONE;
        requireInRange(roll.bonusRollsPerLevel, 0, MAX_ROLLS, "bonus_roll.bonus_rolls_per_level");
        requireInRange(roll.bonusRollChancePerLevel, 0.0, 1.0, "bonus_roll.bonus_roll_chance_per_level");
        requireInRange(roll.maxBonusRolls, 0, MAX_ROLLS, "bonus_roll.max_bonus_rolls");
        requireInRange(roll.maxBonusRollChance, 0.0, 1.0, "bonus_roll.max_bonus_roll_chance");
        return new EnchantBonus.RollBonus(roll.bonusRollsPerLevel, roll.bonusRollChancePerLevel,
                roll.maxBonusRolls, roll.maxBonusRollChance);
    }

    /**
     * Resolves the count bonus of {@code on_looting}.
     *
     * @param count Gson DTO, or null when missing.
     * @return {@link EnchantBonus.CountBonus#NONE} when missing, otherwise the resolved bonus.
     * @throws InvalidDropDataException If a value is out of range.
     */
    private static EnchantBonus.CountBonus parseCountBonus(@Nullable MobDropJsonStructure.BonusCount count) throws InvalidDropDataException {
        if (count == null) return EnchantBonus.CountBonus.NONE;
        requireInRange(count.bonusMinPerLevel, 0, MAX_COUNT, "bonus_count.bonus_min_per_level");
        requireInRange(count.bonusMaxPerLevel, 0, MAX_COUNT, "bonus_count.bonus_max_per_level");
        requireInRange(count.maxBonusMin, 0, MAX_COUNT, "bonus_count.max_bonus_min");
        requireInRange(count.maxBonusMax, 0, MAX_COUNT, "bonus_count.max_bonus_max");
        return new EnchantBonus.CountBonus(count.bonusMinPerLevel, count.bonusMaxPerLevel,
                count.maxBonusMin, count.maxBonusMax);
    }

    /**
     * Rejects an int value outside an inclusive range.
     *
     * @param value Value to check.
     * @param min   Lowest allowed value.
     * @param max   Highest allowed value.
     * @param field JSON field name, used in the error message.
     * @throws InvalidDropDataException If the value is below min or above max.
     */
    private static void requireInRange(int value, int min, int max, String field) throws InvalidDropDataException {
        if (value < min || value > max) {
            throw new InvalidDropDataException(field + " must be between " + min + " and " + max + ", got " + value);
        }
    }

    /**
     * Rejects a decimal value outside an inclusive range, including NaN.
     *
     * @param value Value to check.
     * @param min   Lowest allowed value.
     * @param max   Highest allowed value.
     * @param field JSON field name, used in the error message.
     * @throws InvalidDropDataException If the value is NaN, below min or above max.
     */
    private static void requireInRange(double value, double min, double max, String field) throws InvalidDropDataException {
        if (!(value >= min && value <= max)) {
            throw new InvalidDropDataException(field + " must be between " + min + " and " + max + ", got " + value);
        }
    }
}
