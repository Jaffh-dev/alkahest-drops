package com.jaffh.alkahestcore.dropscore.server.drops;

import com.google.gson.JsonElement;
import com.jaffh.alkahestcore.dropscore.common.drops.DropBehavior;
import com.jaffh.alkahestcore.dropscore.common.drops.DropBehaviorType;
import com.jaffh.alkahestcore.dropscore.common.drops.DropCount;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonus;
import com.jaffh.alkahestcore.dropscore.common.drops.HealthFormula;
import com.jaffh.alkahestcore.dropscore.common.drops.HealthFunction;
import com.jaffh.alkahestcore.dropscore.server.drops.json.BonusRollJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.CountJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.DropBehaviorJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.DropEntryJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.EnchantBonusJson;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Validates one entry of {@code drops} and resolves it into a {@link DropEntry}. {@code item}, {@code chance},
 * {@code count} and {@code drop_behavior} are required; out-of-range values are clamped with a warning when
 * the intent is clear and rejected otherwise.
 */
public final class DropEntryParser {
    private final DropFileFormat format;
    private final DropLimits limits;
    private final EnchantBonusParser bonusParser;

    /**
     * Creates a parser for one file format and the given limits.
     *
     * @param format Format of the files being read. Never null.
     * @param limits Config limits of the reload. Never null.
     */
    public DropEntryParser(DropFileFormat format, DropLimits limits) {
        this.format = format;
        this.limits = limits;
        this.bonusParser = new EnchantBonusParser(limits);
    }

    /**
     * Resolves one drop.
     *
     * @param element JSON value of the drop. Never null.
     * @param ctx     Context of the drop, such as {@code drops[2]}. Never null.
     * @return The resolved drop.
     * @throws DropDataException If the drop is invalid (error) or its item is not registered (warning).
     */
    public DropEntry parse(JsonElement element, ParseContext ctx) throws DropDataException {
        DropEntryJson drop = DropValues.readObject(element, DropEntryJson.class, ctx);
        ParseContext itemCtx = ctx.at("item");
        EnchantBonusJson bonusJson = format.enchantBonusOf(drop);
        DropFileFormat other = format.other();
        if (other.enchantBonusOf(drop) != null) {
            ctx.at(other.bonusKey()).warn("is a key of " + other.filesName() + "; only this bonus is ignored, the drop "
                    + "still loads without it; " + format.filesName() + " use '" + format.bonusKey() + "'");
        }
        EnchantBonus bonus = bonusParser.parse(bonusJson, ctx.at(format.bonusKey()));
        DropBehavior behavior = parseBehavior(drop.dropBehavior, bonusJson, ctx.at("drop_behavior"));
        if (!behavior.type().needsRollSettings() && bonusJson != null && bonusJson.bonusRoll != null) {
            ctx.at(format.bonusKey()).at("bonus_roll").warn("has no effect with drop_behavior type '"
                    + behavior.type().name().toLowerCase(Locale.ROOT) + "'");
        }

        return new DropEntry(
                DropValues.resolveItem(itemCtx.require(drop.item), itemCtx),
                DropValues.parseNbt(drop.nbt, ctx.at("nbt")),
                parseChance(drop.chance, bonusJson, ctx.at("chance")),
                parseCount(drop.count, bonusJson, ctx.at("count")),
                behavior,
                bonus,
                DropValues.parseConditions(drop.conditions, ctx.at("conditions"))
        );
    }

    /**
     * Resolves {@code chance}: above 1 is clamped, below 0 is rejected, and 0 needs a chance bonus.
     *
     * @param chance    Raw chance, or null when missing.
     * @param bonusJson Enchantment bonus of the drop, or null when missing.
     * @param ctx       Context of {@code chance}. Never null.
     * @return The chance, between 0 and 1.
     * @throws DropDataException If the chance is missing, negative, or 0 without {@code bonus_chance}.
     */
    private double parseChance(@Nullable Double chance, @Nullable EnchantBonusJson bonusJson, ParseContext ctx) throws DropDataException {
        double value = ctx.require(chance);
        ctx.requireAtLeast(value, 0.0);
        if (value == 0 && (bonusJson == null || bonusJson.bonusChance == null)) {
            throw ctx.error("is 0 without " + format.bonusKey() + ".bonus_chance, so the drop could never happen");
        }
        return ctx.clampAtMost(value, 1.0);
    }

    /**
     * Resolves {@code count}: both keys required, {@code min} at most {@code max}, both clamped to the count limit.
     * A 0–0 range needs a count bonus that raises both ends.
     *
     * @param count     Gson DTO, or null when missing.
     * @param bonusJson Enchantment bonus of the drop, or null when missing.
     * @param ctx       Context of {@code count}. Never null.
     * @return The count range.
     * @throws DropDataException If a key is missing, {@code min} is negative or above {@code max},
     *                           or the range is 0–0 without a bonus raising both ends.
     */
    private DropCount parseCount(@Nullable CountJson count, @Nullable EnchantBonusJson bonusJson, ParseContext ctx) throws DropDataException {
        CountJson json = ctx.require(count);
        ParseContext minCtx = ctx.at("min");
        ParseContext maxCtx = ctx.at("max");
        int min = minCtx.require(json.min);
        int max = maxCtx.require(json.max);
        minCtx.requireAtLeast(min, 0);
        if (min > max) throw ctx.error("min (" + min + ") is greater than max (" + max + ")");
        if (max == 0 && !raisesBothCountEnds(bonusJson)) {
            throw ctx.error("min and max are 0 without " + format.bonusKey()
                    + ".bonus_count raising both, so the drop would always be empty");
        }
        return new DropCount(minCtx.clampAtMost(min, limits.maxCount()), maxCtx.clampAtMost(max, limits.maxCount()));
    }

    /**
     * Tells whether a count bonus raises both the min and the max.
     *
     * @param bonusJson Enchantment bonus of the drop, or null when missing.
     * @return True when both per-level count bonuses are set above 0.
     */
    private static boolean raisesBothCountEnds(@Nullable EnchantBonusJson bonusJson) {
        if (bonusJson == null || bonusJson.bonusCount == null) return false;
        Integer minPerLevel = bonusJson.bonusCount.bonusMinPerLevel;
        Integer maxPerLevel = bonusJson.bonusCount.bonusMaxPerLevel;
        return minPerLevel != null && minPerLevel > 0 && maxPerLevel != null && maxPerLevel > 0;
    }

    /**
     * Resolves {@code drop_behavior}. Roll settings are required for roll-based types and ignored, with a
     * warning, for the others; health settings are read only by {@code max_health}, which mob files alone accept.
     *
     * @param behavior Gson DTO, or null when missing.
     * @param bonusJson Enchantment bonus of the drop, or null when missing.
     * @param ctx       Context of {@code drop_behavior}. Never null.
     * @return The resolved behavior.
     * @throws DropDataException If the behavior, its type or a roll setting is missing or invalid.
     */
    private DropBehavior parseBehavior(@Nullable DropBehaviorJson behavior, @Nullable EnchantBonusJson bonusJson, ParseContext ctx) throws DropDataException {
        DropBehaviorJson json = ctx.require(behavior);
        ParseContext typeCtx = ctx.at("type");
        String typeId = typeCtx.require(json.type);
        DropBehaviorType type = DropBehaviorType.fromId(typeId)
                .orElseThrow(() -> typeCtx.error("unknown type '" + typeId + "'"));

        if (type == DropBehaviorType.MAX_HEALTH) {
            if (!format.canScaleWithHealth()) throw typeCtx.error("'" + typeId + "' is only valid in mob drop files");
            warnIgnoredRollSettings(json, typeId, ctx);
            return DropBehavior.ofMaxHealth(parseHealthFormula(json, ctx));
        }

        warnIgnoredHealthSettings(json, typeId, ctx);
        if (!type.needsRollSettings()) {
            warnIgnoredRollSettings(json, typeId, ctx);
            return DropBehavior.UNIFORM;
        }

        BonusRollJson rollBonus = bonusJson != null ? bonusJson.bonusRoll : null;
        return DropBehavior.ofRolls(
                type,
                parseRolls(json.rolls, rollBonus != null && rollBonus.bonusRollsPerLevel != null, ctx.at("rolls")),
                parseChancePerRoll(json.chancePerRoll, rollBonus != null && rollBonus.bonusRollChancePerLevel != null, ctx.at("chance_per_roll")),
                parseIncrement(json.incrementValue, rollBonus != null && rollBonus.bonusIncrementPerLevel != null, ctx.at("increment_value"))
        );
    }

    /**
     * Warns about each roll setting given to a type that does not read them.
     *
     * @param json   Gson DTO of {@code drop_behavior}. Never null.
     * @param typeId Raw type id, for the message. Never null.
     * @param ctx    Context of {@code drop_behavior}. Never null.
     */
    private static void warnIgnoredRollSettings(DropBehaviorJson json, String typeId, ParseContext ctx) {
        String message = "is ignored by type '" + typeId + "'";
        if (json.rolls != null) ctx.at("rolls").warn(message);
        if (json.chancePerRoll != null) ctx.at("chance_per_roll").warn(message);
        if (json.incrementValue != null) ctx.at("increment_value").warn(message);
    }

    /**
     * Warns about each health setting given to a type other than {@code max_health}.
     *
     * @param json   Gson DTO of {@code drop_behavior}. Never null.
     * @param typeId Raw type id, for the message. Never null.
     * @param ctx    Context of {@code drop_behavior}. Never null.
     */
    private static void warnIgnoredHealthSettings(DropBehaviorJson json, String typeId, ParseContext ctx) {
        String message = "is ignored by type '" + typeId + "'";
        if (json.function != null) ctx.at("function").warn(message);
        if (json.multiplier != null) ctx.at("multiplier").warn(message);
        if (json.divisor != null) ctx.at("divisor").warn(message);
    }

    /**
     * Resolves the settings of {@code max_health}: {@code function} defaults to linear, {@code multiplier}
     * and {@code divisor} default to 1 and must be above 0.
     *
     * @param json Gson DTO of {@code drop_behavior}. Never null.
     * @param ctx  Context of {@code drop_behavior}. Never null.
     * @return The formula settings.
     * @throws DropDataException If the function is unknown, or the multiplier or divisor is 0 or negative.
     */
    private static HealthFormula parseHealthFormula(DropBehaviorJson json, ParseContext ctx) throws DropDataException {
        HealthFormula defaults = HealthFormula.DEFAULT;
        HealthFunction function = defaults.function();
        if (json.function != null) {
            ParseContext functionCtx = ctx.at("function");
            function = HealthFunction.fromId(json.function)
                    .orElseThrow(() -> functionCtx.error("unknown function '" + json.function + "', expected 'linear' or 'log'"));
        }
        double multiplier = json.multiplier != null ? json.multiplier : defaults.multiplier();
        double divisor = json.divisor != null ? json.divisor : defaults.divisor();
        ctx.at("multiplier").requirePositive(multiplier);
        ctx.at("divisor").requirePositive(divisor);
        return new HealthFormula(function, multiplier, divisor);
    }

    /**
     * Resolves {@code rolls}: 0 is kept only when a rolls bonus exists ("rolls only with the enchantment"),
     * otherwise raised to 1; above the limit is clamped.
     *
     * @param rolls        Raw value, or null when missing.
     * @param hasRollBonus Whether {@code bonus_roll.bonus_rolls_per_level} is set.
     * @param ctx          Context of {@code rolls}. Never null.
     * @return The number of rolls.
     * @throws DropDataException If the value is missing or negative.
     */
    private int parseRolls(@Nullable Integer rolls, boolean hasRollBonus, ParseContext ctx) throws DropDataException {
        int value = ctx.require(rolls);
        ctx.requireAtLeast(value, 0);
        if (value == 0 && !hasRollBonus) {
            ctx.warn("is 0 without " + format.bonusKey() + ".bonus_roll.bonus_rolls_per_level; clamped to 1");
            return 1;
        }
        return ctx.clampAtMost(value, limits.maxRolls());
    }

    /**
     * Resolves {@code chance_per_roll}: 0 needs a roll chance bonus (raising it to 1 would turn a typo into
     * "always"); above 1 is clamped.
     *
     * @param chance             Raw value, or null when missing.
     * @param hasRollChanceBonus Whether {@code bonus_roll.bonus_roll_chance_per_level} is set.
     * @param ctx                Context of {@code chance_per_roll}. Never null.
     * @return The roll chance, between 0 and 1.
     * @throws DropDataException If the value is missing, negative, or 0 without a roll chance bonus.
     */
    private double parseChancePerRoll(@Nullable Double chance, boolean hasRollChanceBonus, ParseContext ctx) throws DropDataException {
        double value = ctx.require(chance);
        ctx.requireAtLeast(value, 0.0);
        if (value == 0 && !hasRollChanceBonus) {
            throw ctx.error("is 0 without " + format.bonusKey() + ".bonus_roll.bonus_roll_chance_per_level, so no roll could succeed");
        }
        return ctx.clampAtMost(value, 1.0);
    }

    /**
     * Resolves {@code increment_value}: 0 is kept only when an increment bonus exists, otherwise raised to 1;
     * above the limit is clamped.
     *
     * @param increment         Raw value, or null when missing.
     * @param hasIncrementBonus Whether {@code bonus_roll.bonus_increment_per_level} is set.
     * @param ctx               Context of {@code increment_value}. Never null.
     * @return The items added per successful roll.
     * @throws DropDataException If the value is missing or negative.
     */
    private int parseIncrement(@Nullable Integer increment, boolean hasIncrementBonus, ParseContext ctx) throws DropDataException {
        int value = ctx.require(increment);
        ctx.requireAtLeast(value, 0);
        if (value == 0 && !hasIncrementBonus) {
            ctx.warn("is 0 without " + format.bonusKey() + ".bonus_roll.bonus_increment_per_level; clamped to 1");
            return 1;
        }
        return ctx.clampAtMost(value, limits.maxIncrement());
    }
}
