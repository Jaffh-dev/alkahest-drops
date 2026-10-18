package com.jaffh.alkahestcore.dropscore.server.drops;

import com.google.gson.JsonElement;
import com.jaffh.alkahestcore.dropscore.common.drops.DropBehavior;
import com.jaffh.alkahestcore.dropscore.common.drops.DropBehaviorType;
import com.jaffh.alkahestcore.dropscore.common.drops.DropCount;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.common.drops.DropTracking;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonus;
import com.jaffh.alkahestcore.dropscore.common.drops.ScaleFormula;
import com.jaffh.alkahestcore.dropscore.common.drops.ScaleFunction;
import com.jaffh.alkahestcore.dropscore.server.drops.json.BonusRollJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.CountJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.DropBehaviorJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.DropEntryJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.EnchantBonusJson;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates one entry of {@code drops} and resolves it into a {@link DropEntry}. {@code item} (or
 * {@code "experience": true}), {@code chance}, {@code count} and {@code drop_behavior} are required; out-of-range
 * values are clamped with a warning when the intent is clear and rejected otherwise. {@code functions} are optional
 * vanilla loot functions; {@code pity}, {@code first_kill} / {@code first_break} and {@code per_player} are
 * optional per-player settings.
 */
public final class DropEntryParser {
    /** Characters a drop {@code id} may use, the same as a resource path. */
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_./-]+");

    private final DropFileFormat format;
    private final DropLimits limits;
    private final EnchantBonusParser bonusParser;
    private final LootFunctionChecker functionChecker;

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
        this.functionChecker = new LootFunctionChecker(format);
    }

    /**
     * Resolves one drop.
     *
     * @param element JSON value of the drop. Never null.
     * @param ctx     Context of the drop, such as {@code drops[2]}. Never null.
     * @param index   Position of the drop in {@code drops}, part of its progress key. 0 or more.
     * @return The resolved drop.
     * @throws DropDataException If the drop is invalid (error) or its item is not registered (warning).
     */
    public DropEntry parse(JsonElement element, ParseContext ctx, int index) throws DropDataException {
        DropEntryJson drop = DropValues.readObject(element, DropEntryJson.class, ctx);
        boolean isExperience = Boolean.TRUE.equals(drop.experience);
        if (isExperience && drop.item != null) {
            throw ctx.at("experience").error("cannot be used with 'item'; a drop gives either an item or experience");
        }
        if (isExperience && drop.nbt != null) ctx.at("nbt").warn("is ignored by experience drops");
        if (isExperience && drop.functions != null) ctx.at("functions").warn("is ignored by experience drops");
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

        Item item = isExperience ? null : DropValues.resolveItem(itemCtx.require(drop.item), itemCtx);
        CompoundTag nbt = isExperience ? null : DropValues.parseNbt(drop.nbt, ctx.at("nbt"));
        double chance = parseChance(drop.chance, bonusJson, ctx.at("chance"));
        DropCount count = parseCount(drop.count, bonusJson, ctx.at("count"));
        return new DropEntry(
                item,
                nbt,
                chance,
                count,
                behavior,
                bonus,
                DropValues.parseConditions(drop.conditions, ctx.at("conditions")),
                isExperience ? List.of() : parseFunctions(drop, bonusJson != null, ctx),
                parseTracking(drop, isExperience, canMiss(chance, count, behavior), index, ctx)
        );
    }

    /**
     * Tells whether a roll of a drop can give nothing, which pity counts as a miss. Only the plain case is known to
     * never miss: a chance of 1 and a uniform count of at least 1. Roll-based and scaled counts may still roll 0.
     *
     * @param chance   Base chance of the drop, between 0 and 1.
     * @param count    Count range of the drop. Never null.
     * @param behavior Count algorithm of the drop. Never null.
     * @return False only when every roll drops something.
     */
    private static boolean canMiss(double chance, DropCount count, DropBehavior behavior) {
        return chance < 1.0 || behavior.type() != DropBehaviorType.UNIFORM || count.min() < 1;
    }

    /**
     * Resolves {@code pity}, {@code first_kill} ({@code first_break} in block files) and {@code per_player}. Pity
     * must be at least 1 and is ignored, with a warning, on a one-time drop; it gets a warning on a drop that can never
     * miss, where it has no effect; the other format's one-time key is
     * ignored with a warning; {@code per_player} is ignored, with a warning, in block files and on experience drops,
     * which cannot be locked to a player.
     *
     * @param drop         Gson DTO of the drop. Never null.
     * @param isExperience Whether the drop gives experience.
     * @param canMiss      Whether a roll of the drop can give nothing, the misses pity counts.
     * @param index        Position of the drop in {@code drops}, the progress key when it has no {@code id}.
     * @param ctx          Context of the drop. Never null.
     * @return The per-player settings.
     * @throws DropDataException If {@code pity} is below 1 or {@code id} is invalid.
     */
    private DropTracking parseTracking(DropEntryJson drop, boolean isExperience, boolean canMiss, int index, ParseContext ctx)
            throws DropDataException {
        String id = parseId(drop.id, ctx.at("id"));
        boolean isFirstDrop = Boolean.TRUE.equals(format.firstDropOf(drop));
        DropFileFormat other = format.other();
        if (other.firstDropOf(drop) != null) {
            ctx.at(other.firstDropKey()).warn("is a key of " + other.filesName() + " and is ignored; "
                    + format.filesName() + " use '" + format.firstDropKey() + "'");
        }
        boolean isPerPlayer = Boolean.TRUE.equals(drop.isPerPlayer);
        int pity = 0;
        if (drop.pity != null) {
            ParseContext pityCtx = ctx.at("pity");
            pityCtx.requireAtLeast(drop.pity, 1);
            if (isFirstDrop) {
                pityCtx.warn("is ignored with '" + format.firstDropKey() + "', which always drops the first time");
            } else {
                pity = drop.pity;
                if (!canMiss) {
                    pityCtx.warn("has no effect: with chance 1 and a count of at least 1 the drop never misses, "
                            + "and pity only counts misses");
                }
            }
        }
        if (isPerPlayer && !format.canRollPerPlayer()) {
            ctx.at("per_player").warn("has no effect in " + format.filesName() + "; only the breaker gets the drop");
            isPerPlayer = false;
        } else if (isPerPlayer && isExperience) {
            ctx.at("per_player").warn("is ignored by experience drops, which cannot be locked to a player");
            isPerPlayer = false;
        }
        if (id != null && pity == 0 && !isFirstDrop) {
            ctx.at("id").warn("has no effect without 'pity' or '" + format.firstDropKey() + "'");
        }
        return new DropTracking(format.progressKey(ctx.fileId(), id, index), pity, isFirstDrop, isPerPlayer);
    }

    /**
     * Resolves {@code id}, which keeps a drop's pity and one-time progress when the file changes: lowercase letters,
     * digits, {@code _}, {@code -}, {@code .} and {@code /}.
     *
     * @param id  Raw id, or null when missing.
     * @param ctx Context of {@code id}. Never null.
     * @return The id, or null when missing.
     * @throws DropDataException If the id is empty or uses other characters.
     */
    @Nullable
    private static String parseId(@Nullable String id, ParseContext ctx) throws DropDataException {
        if (id == null) return null;
        if (!ID_PATTERN.matcher(id).matches()) {
            throw ctx.error("'" + id + "' must use only lowercase letters, digits, '_', '-', '.' and '/'");
        }
        return id;
    }

    /**
     * Resolves {@code functions} and warns about functions that would not work as expected.
     *
     * @param drop            Gson DTO of the drop. Never null.
     * @param hasEnchantBonus Whether the drop has the format's bonus key.
     * @param ctx             Context of the drop. Never null.
     * @return The functions in file order, empty when the key is missing.
     * @throws DropDataException If any function is invalid.
     */
    private List<LootItemFunction> parseFunctions(DropEntryJson drop, boolean hasEnchantBonus, ParseContext ctx) throws DropDataException {
        List<LootItemFunction> functions = DropValues.parseFunctions(drop.functions, ctx.at("functions"));
        if (!functions.isEmpty()) functionChecker.check(functions, drop.functions, hasEnchantBonus, ctx);
        return functions;
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
     * warning, for the others; scale settings are read only by the scaled types, {@code max_health} in mob files
     * and {@code hardness} in block files.
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

        if (type.isScaled()) {
            if (type != format.scaleType()) {
                throw typeCtx.error("'" + typeId + "' is only valid in " + format.other().filesName() + "; "
                        + format.filesName() + " use '" + format.scaleType().name().toLowerCase(Locale.ROOT) + "'");
            }
            warnIgnoredRollSettings(json, typeId, ctx);
            return DropBehavior.ofScale(type, parseScaleFormula(json, ctx));
        }

        warnIgnoredScaleSettings(json, typeId, ctx);
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
     * Warns about each scale setting given to a type that is not scaled.
     *
     * @param json   Gson DTO of {@code drop_behavior}. Never null.
     * @param typeId Raw type id, for the message. Never null.
     * @param ctx    Context of {@code drop_behavior}. Never null.
     */
    private static void warnIgnoredScaleSettings(DropBehaviorJson json, String typeId, ParseContext ctx) {
        String message = "is ignored by type '" + typeId + "'";
        if (json.function != null) ctx.at("function").warn(message);
        if (json.multiplier != null) ctx.at("multiplier").warn(message);
        if (json.divisor != null) ctx.at("divisor").warn(message);
    }

    /**
     * Resolves the settings of a scaled type: {@code function} defaults to linear, {@code multiplier}
     * and {@code divisor} default to 1 and must be above 0.
     *
     * @param json Gson DTO of {@code drop_behavior}. Never null.
     * @param ctx  Context of {@code drop_behavior}. Never null.
     * @return The formula settings.
     * @throws DropDataException If the function is unknown, or the multiplier or divisor is 0 or negative.
     */
    private static ScaleFormula parseScaleFormula(DropBehaviorJson json, ParseContext ctx) throws DropDataException {
        ScaleFormula defaults = ScaleFormula.DEFAULT;
        ScaleFunction function = defaults.function();
        if (json.function != null) {
            ParseContext functionCtx = ctx.at("function");
            function = ScaleFunction.fromId(json.function)
                    .orElseThrow(() -> functionCtx.error("unknown function '" + json.function + "', expected 'linear' or 'log'"));
        }
        double multiplier = json.multiplier != null ? json.multiplier : defaults.multiplier();
        double divisor = json.divisor != null ? json.divisor : defaults.divisor();
        ctx.at("multiplier").requirePositive(multiplier);
        ctx.at("divisor").requirePositive(divisor);
        return new ScaleFormula(function, multiplier, divisor);
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
