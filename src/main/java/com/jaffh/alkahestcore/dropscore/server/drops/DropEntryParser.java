package com.jaffh.alkahestcore.dropscore.server.drops;

import com.google.gson.JsonElement;
import com.jaffh.alkahestcore.dropscore.common.drops.DropBehavior;
import com.jaffh.alkahestcore.dropscore.common.drops.DropBehaviorType;
import com.jaffh.alkahestcore.dropscore.common.drops.DropActions;
import com.jaffh.alkahestcore.dropscore.common.drops.DropCount;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.common.drops.DropTracking;
import com.jaffh.alkahestcore.dropscore.common.drops.DropTrigger;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonus;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonuses;
import com.jaffh.alkahestcore.dropscore.common.drops.ScaleFormula;
import com.jaffh.alkahestcore.dropscore.common.drops.ScaleFunction;
import com.jaffh.alkahestcore.dropscore.common.drops.TriggerSettings;
import com.jaffh.alkahestcore.dropscore.server.drops.json.CountJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.DropActionsJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.DropBehaviorJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.DropEntryJson;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Validates one entry of {@code drops} and resolves it into a {@link DropEntry}. {@code item} (or
 * {@code "experience": true}), {@code chance}, {@code count} and {@code drop_behavior} are required; out-of-range
 * values are clamped with a warning when the intent is clear and rejected otherwise. {@code functions} are optional
 * vanilla loot functions; {@code enchant_bonus} lists the bonus of each enchantment; {@code pity}, {@code first_kill} / {@code first_break} and {@code per_player} are
 * optional per-player settings; {@code on} (mob files only) picks the trigger, with {@code interval} for periodic drops
 * and {@code cooldown} for hit drops; {@code on_drop} holds optional actions run when the item spawns.
 */
public final class DropEntryParser {
    /** Characters a drop {@code id} may use, the same as a resource path. */
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_./-]+");
    /** JSON key of the enchantment bonuses of a drop. */
    private static final String BONUS_KEY = "enchant_bonus";

    private final DropFileFormat format;
    private final DropLimits limits;
    private final EnchantBonusParser bonusParser;
    private final LootFunctionChecker functionChecker;
    private final Predicate<ResourceLocation> isKnownFunction;

    /**
     * Creates a parser for one file format and the given limits.
     *
     * @param format          Format of the files being read. Never null.
     * @param limits          Config limits of the reload. Never null.
     * @param isKnownFunction Tells whether a datapack function of the same reload exists. Never null.
     */
    public DropEntryParser(DropFileFormat format, DropLimits limits, Predicate<ResourceLocation> isKnownFunction) {
        this.format = format;
        this.isKnownFunction = isKnownFunction;
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
            throw ctx.at("experience").error("experience_with_item");
        }
        if (isExperience && drop.nbt != null) ctx.at("nbt").warn("ignored_by_experience");
        if (isExperience && drop.functions != null) ctx.at("functions").warn("ignored_by_experience");
        if (isExperience && drop.onDrop != null) ctx.at("on_drop").warn("ignored_by_experience");
        ParseContext itemCtx = ctx.at("item");
        TriggerSettings trigger = parseTrigger(drop, ctx);
        JsonElement bonusJson = drop.enchantBonus;
        if (bonusJson != null && !trigger.trigger().hasEnchantBonus()) {
            ctx.at(BONUS_KEY).warn("bonus_unused_by_trigger", trigger.trigger().id());
            bonusJson = null;
        }
        DropBehaviorType declaredType = declaredTypeOf(drop.dropBehavior);
        boolean readsRolls = declaredType == null || declaredType.needsRollSettings();
        String typeId = declaredType != null ? declaredType.name().toLowerCase(Locale.ROOT) : "";
        EnchantBonuses bonuses = bonusParser.parseList(bonusJson, readsRolls, typeId, ctx.at(BONUS_KEY));
        DropBehavior behavior = parseBehavior(drop.dropBehavior, bonuses, trigger.trigger(), ctx.at("drop_behavior"));

        Item item = isExperience ? null : DropValues.resolveItem(itemCtx.require(drop.item), itemCtx);
        CompoundTag nbt = isExperience ? null : DropValues.parseNbt(drop.nbt, ctx.at("nbt"));
        double chance = parseChance(drop.chance, bonuses, ctx.at("chance"));
        DropCount count = parseCount(drop.count, bonuses, ctx.at("count"));
        DropTracking tracking = parseTracking(drop, isExperience, canMiss(chance, count, behavior), index, ctx);
        return new DropEntry(
                item,
                nbt,
                chance,
                count,
                behavior,
                bonuses,
                DropValues.parseConditions(drop.conditions, ctx.at("conditions")),
                isExperience ? List.of() : parseFunctions(drop, bonuses, ctx),
                restrictToTrigger(tracking, trigger.trigger(), ctx),
                trigger,
                isExperience ? DropActions.NONE : parseOnDrop(drop.onDrop, ctx.at("on_drop"))
        );
    }

    /**
     * Resolves {@code on_drop}: {@code glow} and {@code run_function} are optional and off when missing. A function
     * no datapack defines is ignored with a warning, and an object turning no action on is ignored with a warning.
     *
     * @param json Gson DTO, or null when missing.
     * @param ctx  Context of {@code on_drop}. Never null.
     * @return The actions, {@link DropActions#NONE} when missing or empty.
     * @throws DropDataException If the function id is malformed.
     */
    private DropActions parseOnDrop(@Nullable DropActionsJson json, ParseContext ctx) throws DropDataException {
        if (json == null) return DropActions.NONE;
        DropActions actions = new DropActions(Boolean.TRUE.equals(json.shouldGlow),
                parseFunctionId(json.runFunction, ctx.at("run_function")));
        if (!actions.hasActions()) {
            ctx.warn("no_drop_actions");
            return DropActions.NONE;
        }
        return actions;
    }

    /**
     * Resolves the datapack function of {@code on_drop}.
     *
     * @param id  Raw function id, or null when missing.
     * @param ctx Context of {@code run_function}. Never null.
     * @return The function id, or null when missing or not defined by any datapack (reported as a warning).
     * @throws DropDataException If the id is malformed.
     */
    @Nullable
    private ResourceLocation parseFunctionId(@Nullable String id, ParseContext ctx) throws DropDataException {
        if (id == null) return null;
        ResourceLocation location = DropValues.parseId(id, ctx);
        if (!isKnownFunction.test(location)) {
            ctx.warn("unknown_function_id", id);
            return null;
        }
        return location;
    }

    /**
     * Resolves {@code on} and its tick setting. {@code on} defaults to death and is an error in block files;
     * {@code interval} (periodic, default 6000, at least 1) and {@code cooldown} (hit, default 0, at least 0) are
     * ignored, with a warning, on any other trigger.
     *
     * @param drop Gson DTO of the drop. Never null.
     * @param ctx  Context of the drop. Never null.
     * @return The trigger settings.
     * @throws DropDataException If {@code on} is set in a block file or unknown, or the tick setting is too low.
     */
    private TriggerSettings parseTrigger(DropEntryJson drop, ParseContext ctx) throws DropDataException {
        DropTrigger trigger = DropTrigger.DEATH;
        if (drop.on != null) {
            ParseContext onCtx = ctx.at("on");
            if (!format.canUseTriggers()) throw onCtx.error("trigger_in_block_file");
            trigger = DropTrigger.fromId(drop.on).orElseThrow(() -> onCtx.error("unknown_trigger", drop.on));
        }
        int ticks = trigger.defaultTicks();
        ticks = parseTicks(drop.interval, "interval", trigger, ticks, ctx);
        ticks = parseTicks(drop.cooldown, "cooldown", trigger, ticks, ctx);
        return trigger == DropTrigger.DEATH ? TriggerSettings.DEATH : new TriggerSettings(trigger, ticks);
    }

    /**
     * Reads one tick setting when it belongs to the trigger, or warns that it is ignored.
     *
     * @param value   Raw value, or null when missing.
     * @param key     JSON key of the value, {@code interval} or {@code cooldown}. Never null.
     * @param trigger Trigger of the drop. Never null.
     * @param ticks   Tick setting read so far.
     * @param ctx     Context of the drop. Never null.
     * @return The value when it belongs to the trigger, else {@code ticks}.
     * @throws DropDataException If the value is below the trigger's minimum.
     */
    private static int parseTicks(@Nullable Integer value, String key, DropTrigger trigger, int ticks, ParseContext ctx)
            throws DropDataException {
        if (value == null) return ticks;
        ParseContext keyCtx = ctx.at(key);
        if (!key.equals(trigger.ticksKey())) {
            keyCtx.warn("ignored_by_trigger", trigger.id());
            return ticks;
        }
        keyCtx.requireAtLeast(value, trigger.minTicks());
        return value;
    }

    /**
     * Drops the per-player settings a trigger cannot use. Only death rolls once per attacker, so {@code per_player}
     * is ignored, with a warning, on the other triggers. Lightning and periodic drops have no player: pity is ignored
     * with a warning, and a one-time drop is an error, since it could never drop.
     *
     * @param tracking Per-player settings read from the drop. Never null.
     * @param trigger  Trigger of the drop. Never null.
     * @param ctx      Context of the drop. Never null.
     * @return The settings the trigger keeps.
     * @throws DropDataException If a one-time drop has a trigger without player.
     */
    private DropTracking restrictToTrigger(DropTracking tracking, DropTrigger trigger, ParseContext ctx) throws DropDataException {
        if (trigger == DropTrigger.DEATH) return tracking;
        boolean isPerPlayer = tracking.isPerPlayer();
        if (isPerPlayer) {
            ctx.at("per_player").warn("per_player_trigger", trigger.id());
            isPerPlayer = false;
        }
        int pity = tracking.pity();
        if (!trigger.hasPlayer()) {
            if (tracking.isFirstDrop()) throw ctx.at(format.firstDropKey()).error("first_drop_without_player", trigger.id());
            if (pity > 0) ctx.at("pity").warn("pity_without_player", trigger.id());
            pity = 0;
        }
        return new DropTracking(tracking.key(), pity, tracking.isFirstDrop(), isPerPlayer);
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
            ctx.at(other.firstDropKey()).warn("other_first_drop_key", other.filesName(), format.filesName(), format.firstDropKey());
        }
        boolean isPerPlayer = Boolean.TRUE.equals(drop.isPerPlayer);
        int pity = 0;
        if (drop.pity != null) {
            ParseContext pityCtx = ctx.at("pity");
            pityCtx.requireAtLeast(drop.pity, 1);
            if (isFirstDrop) {
                pityCtx.warn("pity_with_first_drop", format.firstDropKey());
            } else {
                pity = drop.pity;
                if (!canMiss) {
                    pityCtx.warn("pity_never_misses");
                }
            }
        }
        if (isPerPlayer && !format.canRollPerPlayer()) {
            ctx.at("per_player").warn("per_player_unused", format.filesName());
            isPerPlayer = false;
        } else if (isPerPlayer && isExperience) {
            ctx.at("per_player").warn("per_player_experience");
            isPerPlayer = false;
        }
        if (id != null && pity == 0 && !isFirstDrop) {
            ctx.at("id").warn("id_unused", format.firstDropKey());
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
            throw ctx.error("bad_drop_id", id);
        }
        return id;
    }

    /**
     * Resolves {@code functions} and warns about functions that would not work as expected.
     *
     * @param drop            Gson DTO of the drop. Never null.
     * @param bonuses         Enchantment bonuses of the drop. Never null.
     * @param ctx             Context of the drop. Never null.
     * @return The functions in file order, empty when the key is missing.
     * @throws DropDataException If any function is invalid.
     */
    private List<LootItemFunction> parseFunctions(DropEntryJson drop, EnchantBonuses bonuses, ParseContext ctx) throws DropDataException {
        List<LootItemFunction> functions = DropValues.parseFunctions(drop.functions, ctx.at("functions"));
        if (!functions.isEmpty()) functionChecker.check(functions, drop.functions, bonuses, ctx);
        return functions;
    }

    /**
     * Resolves {@code chance}: above 1 is clamped, below 0 is rejected, and 0 needs a chance bonus.
     *
     * @param chance    Raw chance, or null when missing.
     * @param bonuses Enchantment bonuses of the drop. Never null.
     * @param ctx     Context of {@code chance}. Never null.
     * @return The chance, between 0 and 1.
     * @throws DropDataException If the chance is missing, negative, or 0 without a chance bonus.
     */
    private static double parseChance(@Nullable Double chance, EnchantBonuses bonuses, ParseContext ctx) throws DropDataException {
        double value = ctx.require(chance);
        ctx.requireAtLeast(value, 0.0);
        if (value == 0 && !bonuses.hasChanceBonus()) {
            throw ctx.error("zero_chance");
        }
        return ctx.clampAtMost(value, 1.0);
    }

    /**
     * Resolves {@code count}: both keys required, {@code min} at most {@code max}, both clamped to the count limit.
     * A 0–0 range needs a count bonus that raises both ends.
     *
     * @param count     Gson DTO, or null when missing.
     * @param bonuses Enchantment bonuses of the drop. Never null.
     * @param ctx     Context of {@code count}. Never null.
     * @return The count range.
     * @throws DropDataException If a key is missing, {@code min} is negative or above {@code max},
     *                           or the range is 0–0 without bonuses raising both ends.
     */
    private DropCount parseCount(@Nullable CountJson count, EnchantBonuses bonuses, ParseContext ctx) throws DropDataException {
        CountJson json = ctx.require(count);
        ParseContext minCtx = ctx.at("min");
        ParseContext maxCtx = ctx.at("max");
        int min = minCtx.require(json.min);
        int max = maxCtx.require(json.max);
        minCtx.requireAtLeast(min, 0);
        if (min > max) throw ctx.error("min_above_max", min, max);
        if (max == 0 && !raisesBothCountEnds(bonuses)) {
            throw ctx.error("zero_count");
        }
        return new DropCount(minCtx.clampAtMost(min, limits.maxCount()), maxCtx.clampAtMost(max, limits.maxCount()));
    }

    /**
     * Tells whether the count bonuses raise both the min and the max, possibly through different enchantments.
     *
     * @param bonuses Enchantment bonuses of the drop. Never null.
     * @return True when some entry raises the min and some entry raises the max.
     */
    private static boolean raisesBothCountEnds(EnchantBonuses bonuses) {
        boolean raisesMin = bonuses.entries().stream().anyMatch(entry -> entry.bonus().count().bonusMinPerLevel() > 0);
        boolean raisesMax = bonuses.entries().stream().anyMatch(entry -> entry.bonus().count().bonusMaxPerLevel() > 0);
        return raisesMin && raisesMax;
    }

    /**
     * Reads the behavior type a drop declares, before the behavior itself is validated.
     *
     * @param behavior Gson DTO of {@code drop_behavior}, or null when missing.
     * @return The type, or null when it is missing or unknown (reported when the behavior is parsed).
     */
    @Nullable
    private static DropBehaviorType declaredTypeOf(@Nullable DropBehaviorJson behavior) {
        if (behavior == null || behavior.type == null) return null;
        return DropBehaviorType.fromId(behavior.type).orElse(null);
    }

    /**
     * Resolves {@code drop_behavior}. Roll settings are required for roll-based types and ignored, with a
     * warning, for the others; scale settings are read only by the scaled types, {@code max_health} and
     * {@code damage_taken} (hit drops only) in mob files, {@code hardness} in block files.
     *
     * @param behavior Gson DTO, or null when missing.
     * @param bonuses   Enchantment bonuses of the drop. Never null.
     * @param trigger   Trigger of the drop. Never null.
     * @param ctx       Context of {@code drop_behavior}. Never null.
     * @return The resolved behavior.
     * @throws DropDataException If the behavior, its type or a roll setting is missing or invalid, or the type does
     *                           not fit the file format or trigger.
     */
    private DropBehavior parseBehavior(@Nullable DropBehaviorJson behavior, EnchantBonuses bonuses,
                                       DropTrigger trigger, ParseContext ctx) throws DropDataException {
        DropBehaviorJson json = ctx.require(behavior);
        ParseContext typeCtx = ctx.at("type");
        String typeId = typeCtx.require(json.type);
        DropBehaviorType type = DropBehaviorType.fromId(typeId)
                .orElseThrow(() -> typeCtx.error("unknown_type", typeId));

        if (type.isScaled()) {
            if (!format.allowsScaleType(type)) {
                throw typeCtx.error("wrong_scale_type", typeId, format.other().filesName(), format.filesName(),
                        format.scaleTypeNames());
            }
            if (type.readsDamageTaken() && trigger != DropTrigger.HIT) {
                throw typeCtx.error("damage_taken_needs_hit", trigger.id());
            }
            warnIgnoredRollSettings(json, typeId, ctx);
            return DropBehavior.ofScale(type, parseScaleFormula(json, ctx));
        }

        warnIgnoredScaleSettings(json, typeId, ctx);
        if (!type.needsRollSettings()) {
            warnIgnoredRollSettings(json, typeId, ctx);
            return DropBehavior.UNIFORM;
        }

        return DropBehavior.ofRolls(
                type,
                parseRolls(json.rolls, hasRollBonus(bonuses, roll -> roll.bonusRollsPerLevel() > 0), ctx.at("rolls")),
                parseChancePerRoll(json.chancePerRoll, hasRollBonus(bonuses, roll -> roll.bonusRollChancePerLevel() > 0), ctx.at("chance_per_roll")),
                parseIncrement(json.incrementValue, hasRollBonus(bonuses, roll -> roll.bonusIncrementPerLevel() > 0), ctx.at("increment_value"))
        );
    }

    /**
     * Tells whether some enchantment gives a kind of roll bonus.
     *
     * @param bonuses Enchantment bonuses of the drop. Never null.
     * @param kind    Tells whether one roll bonus has the wanted per-level value. Never null.
     * @return True when an entry matches.
     */
    private static boolean hasRollBonus(EnchantBonuses bonuses, Predicate<EnchantBonus.RollBonus> kind) {
        return bonuses.entries().stream().anyMatch(entry -> kind.test(entry.bonus().roll()));
    }

    /**
     * Warns about each roll setting given to a type that does not read them.
     *
     * @param json   Gson DTO of {@code drop_behavior}. Never null.
     * @param typeId Raw type id, for the message. Never null.
     * @param ctx    Context of {@code drop_behavior}. Never null.
     */
    private static void warnIgnoredRollSettings(DropBehaviorJson json, String typeId, ParseContext ctx) {
        if (json.rolls != null) ctx.at("rolls").warn("ignored_by_type", typeId);
        if (json.chancePerRoll != null) ctx.at("chance_per_roll").warn("ignored_by_type", typeId);
        if (json.incrementValue != null) ctx.at("increment_value").warn("ignored_by_type", typeId);
    }

    /**
     * Warns about each scale setting given to a type that is not scaled.
     *
     * @param json   Gson DTO of {@code drop_behavior}. Never null.
     * @param typeId Raw type id, for the message. Never null.
     * @param ctx    Context of {@code drop_behavior}. Never null.
     */
    private static void warnIgnoredScaleSettings(DropBehaviorJson json, String typeId, ParseContext ctx) {
        if (json.function != null) ctx.at("function").warn("ignored_by_type", typeId);
        if (json.multiplier != null) ctx.at("multiplier").warn("ignored_by_type", typeId);
        if (json.divisor != null) ctx.at("divisor").warn("ignored_by_type", typeId);
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
                    .orElseThrow(() -> functionCtx.error("unknown_function", json.function));
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
            ctx.warn("zero_rolls");
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
            throw ctx.error("zero_roll_chance");
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
            ctx.warn("zero_increment");
            return 1;
        }
        return ctx.clampAtMost(value, limits.maxIncrement());
    }
}
