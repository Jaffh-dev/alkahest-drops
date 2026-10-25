package com.jaffh.alkahestcore.dropscore.server.drops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonus;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonusEntry;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonuses;
import com.jaffh.alkahestcore.dropscore.server.drops.json.BonusChanceJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.BonusCountJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.BonusRollJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.EnchantBonusEntryJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.EnchantBonusJson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates the {@code enchant_bonus} list of a drop and resolves it into {@link EnchantBonuses}: each entry names an
 * enchantment ({@code enchant}) and its bonus ({@code on_enchant}). Inside a bonus, every {@code max_*} key is optional
 * but needs its per-level key; a missing one means no cap other than the config limit.
 */
public final class EnchantBonusParser {
    private static final ChanceBonusSpec DROP_CHANCE =
            new ChanceBonusSpec("bonus_chance_per_level", "max_bonus_chance");
    private static final ChanceBonusSpec ROLL_CHANCE =
            new ChanceBonusSpec("bonus_roll_chance_per_level", "max_bonus_roll_chance");

    private final IntBonusSpec rolls;
    private final IntBonusSpec increment;
    private final IntBonusSpec countMin;
    private final IntBonusSpec countMax;

    /**
     * Creates a parser for the given limits.
     *
     * @param limits Config limits of the reload. Never null.
     */
    public EnchantBonusParser(DropLimits limits) {
        this.rolls = new IntBonusSpec("bonus_rolls_per_level", "max_bonus_rolls", 1, limits.maxRolls());
        this.increment = new IntBonusSpec("bonus_increment_per_level", "max_bonus_increment", 1, limits.maxIncrement());
        this.countMin = new IntBonusSpec("bonus_min_per_level", "max_bonus_min", 0, limits.maxCount());
        this.countMax = new IntBonusSpec("bonus_max_per_level", "max_bonus_max", 0, limits.maxCount());
    }

    /**
     * Resolves the {@code enchant_bonus} list of a drop. An entry whose enchantment is not registered (an optional mod
     * that is not installed) is skipped with a warning; the drop still loads.
     *
     * @param element    JSON value of {@code enchant_bonus}, or null when the key is missing.
     * @param readsRolls Whether the drop's behavior reads roll settings; a {@code bonus_roll} is ignored otherwise.
     * @param typeId     Raw behavior type of the drop, for the {@code bonus_roll} warning. Never null.
     * @param ctx        Context of {@code enchant_bonus}. Never null.
     * @return {@link EnchantBonuses#NONE} when missing, otherwise one entry per registered enchantment, in file order.
     * @throws DropDataException If the value is not a non-empty array, an entry is invalid or an enchantment repeats.
     */
    public EnchantBonuses parseList(@Nullable JsonElement element, boolean readsRolls, String typeId, ParseContext ctx)
            throws DropDataException {
        if (element == null) return EnchantBonuses.NONE;
        if (!element.isJsonArray()) throw ctx.error("not_array");
        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) throw ctx.error("empty_enchant_bonus");
        List<EnchantBonusEntry> entries = new ArrayList<>(array.size());
        Set<ResourceLocation> seen = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            ParseContext entryCtx = ctx.atSuffix("[" + i + "]");
            EnchantBonusEntryJson json = DropValues.readObject(array.get(i), EnchantBonusEntryJson.class, entryCtx);
            ParseContext enchantCtx = entryCtx.at("enchant");
            ResourceLocation id = DropValues.parseId(enchantCtx.require(json.enchant), enchantCtx);
            if (!seen.add(id)) throw enchantCtx.error("duplicate_enchantment", id);
            ParseContext bonusCtx = entryCtx.at("on_enchant");
            EnchantBonus bonus = parse(bonusCtx.require(json.onEnchant), bonusCtx);
            if (!readsRolls && json.onEnchant.bonusRoll != null) bonusCtx.at("bonus_roll").warn("bonus_roll_unused", typeId);
            Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
            if (enchantment == null || !ForgeRegistries.ENCHANTMENTS.containsKey(id)) {
                ctx.log().report(enchantCtx.unknownId("unknown_enchantment", id).toIssue("entry"));
                continue;
            }
            entries.add(new EnchantBonusEntry(enchantment, bonus));
        }
        return entries.isEmpty() ? EnchantBonuses.NONE : new EnchantBonuses(entries);
    }

    /**
     * Resolves one {@code on_enchant} bonus.
     *
     * @param json Gson DTO, or null when the key is missing.
     * @param ctx  Context of {@code on_enchant}. Never null.
     * @return {@link EnchantBonus#NONE} when missing; each missing part becomes its {@code NONE} constant.
     * @throws DropDataException If the bonus is empty or a value is invalid.
     */
    public EnchantBonus parse(@Nullable EnchantBonusJson json, ParseContext ctx) throws DropDataException {
        if (json == null) return EnchantBonus.NONE;
        if (json.bonusChance == null && json.bonusRoll == null && json.bonusCount == null) {
            throw ctx.error("needs_one_of", "bonus_chance, bonus_roll, bonus_count");
        }
        return new EnchantBonus(
                parseChanceBonus(json.bonusChance, ctx.at("bonus_chance")),
                parseRollBonus(json.bonusRoll, ctx.at("bonus_roll")),
                parseCountBonus(json.bonusCount, ctx.at("bonus_count"))
        );
    }

    /**
     * Resolves {@code bonus_chance}.
     *
     * @param json Gson DTO, or null when missing.
     * @param ctx  Context of {@code bonus_chance}. Never null.
     * @return {@link EnchantBonus.ChanceBonus#NONE} when missing, otherwise the resolved bonus.
     * @throws DropDataException If {@code bonus_chance_per_level} is missing or not above 0.
     */
    private EnchantBonus.ChanceBonus parseChanceBonus(@Nullable BonusChanceJson json, ParseContext ctx) throws DropDataException {
        if (json == null) return EnchantBonus.ChanceBonus.NONE;
        ctx.at(DROP_CHANCE.perLevelKey()).require(json.bonusChancePerLevel);
        ChanceBonusValue value = parseChancePair(json.bonusChancePerLevel, json.maxBonusChance, DROP_CHANCE, ctx);
        return new EnchantBonus.ChanceBonus(value.perLevel(), value.max());
    }

    /**
     * Resolves {@code bonus_roll}.
     *
     * @param json Gson DTO, or null when missing.
     * @param ctx  Context of {@code bonus_roll}. Never null.
     * @return {@link EnchantBonus.RollBonus#NONE} when missing, otherwise the resolved bonus.
     * @throws DropDataException If no per-level key is set or a value is invalid.
     */
    private EnchantBonus.RollBonus parseRollBonus(@Nullable BonusRollJson json, ParseContext ctx) throws DropDataException {
        if (json == null) return EnchantBonus.RollBonus.NONE;
        if (json.bonusRollsPerLevel == null && json.bonusRollChancePerLevel == null && json.bonusIncrementPerLevel == null) {
            throw ctx.error("needs_one_of", "bonus_rolls_per_level, bonus_roll_chance_per_level, bonus_increment_per_level");
        }
        IntBonusValue rollsValue = parseIntPair(json.bonusRollsPerLevel, json.maxBonusRolls, rolls, ctx);
        ChanceBonusValue chanceValue = parseChancePair(json.bonusRollChancePerLevel, json.maxBonusRollChance, ROLL_CHANCE, ctx);
        IntBonusValue incrementValue = parseIntPair(json.bonusIncrementPerLevel, json.maxBonusIncrement, increment, ctx);
        return new EnchantBonus.RollBonus(
                rollsValue.perLevel(), chanceValue.perLevel(),
                rollsValue.max(), chanceValue.max(),
                incrementValue.perLevel(), incrementValue.max()
        );
    }

    /**
     * Resolves {@code bonus_count}.
     *
     * @param json Gson DTO, or null when missing.
     * @param ctx  Context of {@code bonus_count}. Never null.
     * @return {@link EnchantBonus.CountBonus#NONE} when missing, otherwise the resolved bonus.
     * @throws DropDataException If no per-level key is set or a value is invalid.
     */
    private EnchantBonus.CountBonus parseCountBonus(@Nullable BonusCountJson json, ParseContext ctx) throws DropDataException {
        if (json == null) return EnchantBonus.CountBonus.NONE;
        if (json.bonusMinPerLevel == null && json.bonusMaxPerLevel == null) {
            throw ctx.error("needs_one_of", "bonus_min_per_level, bonus_max_per_level");
        }
        IntBonusValue minValue = parseIntPair(json.bonusMinPerLevel, json.maxBonusMin, countMin, ctx);
        IntBonusValue maxValue = parseIntPair(json.bonusMaxPerLevel, json.maxBonusMax, countMax, ctx);
        return new EnchantBonus.CountBonus(minValue.perLevel(), maxValue.perLevel(), minValue.max(), maxValue.max());
    }

    /**
     * Resolves a whole-number per-level bonus and its cap. Per-level values above the limit and caps outside
     * [per-level, limit] are clamped with a warning.
     *
     * @param perLevel Per-level value, or null when missing.
     * @param max      Cap, or null when missing (no cap other than the limit).
     * @param spec     Keys and range of the pair. Never null.
     * @param ctx      Context of the bonus block. Never null.
     * @return {@link IntBonusValue#NONE} when both are missing, otherwise the resolved pair.
     * @throws DropDataException If the cap is set without its per-level key, or the per-level value is too low.
     */
    private static IntBonusValue parseIntPair(@Nullable Integer perLevel, @Nullable Integer max, IntBonusSpec spec, ParseContext ctx) throws DropDataException {
        ParseContext perLevelCtx = ctx.at(spec.perLevelKey());
        ParseContext maxCtx = ctx.at(spec.maxKey());
        if (perLevel == null) {
            if (max != null) throw maxCtx.error("needs_key", spec.perLevelKey());
            return IntBonusValue.NONE;
        }
        perLevelCtx.requireAtLeast(perLevel, spec.lowestPerLevel());
        int resolvedPerLevel = perLevelCtx.clampAtMost(perLevel, spec.limit());
        if (max == null) return new IntBonusValue(resolvedPerLevel, spec.limit());

        int resolvedMax = maxCtx.clampAtLeast(max, resolvedPerLevel);
        return new IntBonusValue(resolvedPerLevel, maxCtx.clampAtMost(resolvedMax, spec.limit()));
    }

    /**
     * Resolves a per-level chance bonus and its cap. Per-level values above 1 and caps outside
     * [per-level, 1] are clamped with a warning.
     *
     * @param perLevel Per-level chance, or null when missing.
     * @param max      Cap, or null when missing (no cap below 1).
     * @param spec     Keys of the pair. Never null.
     * @param ctx      Context of the bonus block. Never null.
     * @return {@link ChanceBonusValue#NONE} when both are missing, otherwise the resolved pair.
     * @throws DropDataException If the cap is set without its per-level key, or the per-level chance is not above 0.
     */
    private static ChanceBonusValue parseChancePair(@Nullable Double perLevel, @Nullable Double max, ChanceBonusSpec spec, ParseContext ctx) throws DropDataException {
        ParseContext perLevelCtx = ctx.at(spec.perLevelKey());
        ParseContext maxCtx = ctx.at(spec.maxKey());
        if (perLevel == null) {
            if (max != null) throw maxCtx.error("needs_key", spec.perLevelKey());
            return ChanceBonusValue.NONE;
        }
        perLevelCtx.requirePositive(perLevel);
        double resolvedPerLevel = perLevelCtx.clampAtMost(perLevel, 1.0);
        if (max == null) return new ChanceBonusValue(resolvedPerLevel, 1.0);

        if (Double.isNaN(max)) throw maxCtx.error("not_a_number");
        double resolvedMax = maxCtx.clampAtLeast(max, resolvedPerLevel);
        return new ChanceBonusValue(resolvedPerLevel, maxCtx.clampAtMost(resolvedMax, 1.0));
    }

    /**
     * Describes a whole-number bonus pair.
     *
     * @param perLevelKey    JSON key of the per-level value.
     * @param maxKey         JSON key of the cap.
     * @param lowestPerLevel Lowest allowed per-level value.
     * @param limit          Config limit of the per-level value and the cap.
     */
    private record IntBonusSpec(String perLevelKey, String maxKey, int lowestPerLevel, int limit) {
    }

    /**
     * Describes a chance bonus pair, whose range is always (0, 1].
     *
     * @param perLevelKey JSON key of the per-level chance.
     * @param maxKey      JSON key of the cap.
     */
    private record ChanceBonusSpec(String perLevelKey, String maxKey) {
    }

    /**
     * Represents a resolved whole-number bonus pair.
     *
     * @param perLevel Value added per level.
     * @param max      Cap of the total added value.
     */
    private record IntBonusValue(int perLevel, int max) {
        /** No bonus. */
        static final IntBonusValue NONE = new IntBonusValue(0, 0);
    }

    /**
     * Represents a resolved chance bonus pair.
     *
     * @param perLevel Chance added per level.
     * @param max      Cap of the total added chance.
     */
    private record ChanceBonusValue(double perLevel, double max) {
        /** No bonus. */
        static final ChanceBonusValue NONE = new ChanceBonusValue(0.0, 0.0);
    }
}
