package com.jaffh.alkahestdrops.common.network;

import com.jaffh.alkahestdrops.common.drops.DropBehavior;
import com.jaffh.alkahestdrops.common.drops.DropBehaviorType;
import com.jaffh.alkahestdrops.common.drops.DropCount;
import com.jaffh.alkahestdrops.common.drops.DropTracking;
import com.jaffh.alkahestdrops.api.DropTrigger;
import com.jaffh.alkahestdrops.common.drops.EnchantBonus;
import com.jaffh.alkahestdrops.common.drops.EnchantBonusEntry;
import com.jaffh.alkahestdrops.common.drops.EnchantBonuses;
import com.jaffh.alkahestdrops.common.drops.ScaleFormula;
import com.jaffh.alkahestdrops.common.drops.ScaleFunction;
import com.jaffh.alkahestdrops.common.drops.SyncedCondition;
import com.jaffh.alkahestdrops.common.drops.SyncedDrop;
import com.jaffh.alkahestdrops.common.drops.SyncedDropRules;
import com.jaffh.alkahestdrops.common.drops.SyncedDropSource;
import com.jaffh.alkahestdrops.common.drops.SyncedTarget;
import com.jaffh.alkahestdrops.common.drops.TriggerSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes and reads the synced drop records on a packet buffer. Every {@code read*} method reads exactly what the
 * matching {@code write*} method writes, in the same order; a behavior, bonus or tracking equal to its default is
 * written as a single flag.
 */
final class DropSyncCodec {

    /**
     * Prevents instantiation of this utility class.
     */
    private DropSyncCodec() {
    }

    /**
     * Writes every synced target.
     *
     * @param buf   Target buffer. Never null.
     * @param rules Rules to write. Never null.
     */
    static void writeRules(FriendlyByteBuf buf, SyncedDropRules rules) {
        buf.writeCollection(rules.mobs(), DropSyncCodec::writeTarget);
        buf.writeCollection(rules.blocks(), DropSyncCodec::writeTarget);
    }

    /**
     * Reads every synced target.
     *
     * @param buf Source buffer. Never null.
     * @return The rules.
     */
    static SyncedDropRules readRules(FriendlyByteBuf buf) {
        return new SyncedDropRules(buf.readList(DropSyncCodec::readTarget), buf.readList(DropSyncCodec::readTarget));
    }

    /**
     * Writes one target with its drops.
     *
     * @param buf    Target buffer. Never null.
     * @param target Target to write. Never null.
     */
    static void writeTarget(FriendlyByteBuf buf, SyncedTarget target) {
        buf.writeResourceLocation(target.id());
        buf.writeCollection(target.drops(), DropSyncCodec::writeDrop);
    }

    /**
     * Reads one target with its drops.
     *
     * @param buf Source buffer. Never null.
     * @return The target.
     */
    private static SyncedTarget readTarget(FriendlyByteBuf buf) {
        return new SyncedTarget(buf.readResourceLocation(), buf.readList(DropSyncCodec::readDrop));
    }

    /**
     * Writes one drop.
     *
     * @param buf  Target buffer. Never null.
     * @param drop Drop to write. Never null.
     */
    private static void writeDrop(FriendlyByteBuf buf, SyncedDrop drop) {
        buf.writeItem(drop.stack());
        buf.writeDouble(drop.chance());
        buf.writeVarInt(drop.count().min());
        buf.writeVarInt(drop.count().max());
        writeUnlessDefault(buf, drop.behavior(), DropBehavior.UNIFORM, DropSyncCodec::writeBehavior);
        writeUnlessDefault(buf, drop.enchantBonuses(), EnchantBonuses.NONE, DropSyncCodec::writeEnchantBonuses);
        writeUnlessDefault(buf, drop.tracking(), SyncedDrop.UNTRACKED, DropSyncCodec::writeTracking);
        buf.writeCollection(drop.conditions(), DropSyncCodec::writeCondition);
        buf.writeBoolean(drop.hasHiddenConditions());
        buf.writeEnum(drop.source().kind());
        buf.writeResourceLocation(drop.source().id());
        writeUnlessDefault(buf, drop.trigger(), TriggerSettings.DEATH, DropSyncCodec::writeTrigger);
    }

    /**
     * Writes the trigger of a drop.
     *
     * @param buf     Target buffer. Never null.
     * @param trigger Trigger settings to write. Never null.
     */
    private static void writeTrigger(FriendlyByteBuf buf, TriggerSettings trigger) {
        buf.writeEnum(trigger.trigger());
        buf.writeVarInt(trigger.ticks());
    }

    /**
     * Reads the trigger of a drop.
     *
     * @param buf Source buffer. Never null.
     * @return The trigger settings.
     */
    private static TriggerSettings readTrigger(FriendlyByteBuf buf) {
        return new TriggerSettings(buf.readEnum(DropTrigger.class), buf.readVarInt());
    }

    /**
     * Reads one drop.
     *
     * @param buf Source buffer. Never null.
     * @return The drop.
     */
    private static SyncedDrop readDrop(FriendlyByteBuf buf) {
        return new SyncedDrop(
                buf.readItem(),
                buf.readDouble(),
                new DropCount(buf.readVarInt(), buf.readVarInt()),
                buf.readBoolean() ? readBehavior(buf) : DropBehavior.UNIFORM,
                buf.readBoolean() ? readEnchantBonuses(buf) : EnchantBonuses.NONE,
                buf.readBoolean() ? readTracking(buf) : SyncedDrop.UNTRACKED,
                buf.readList(DropSyncCodec::readCondition),
                buf.readBoolean(),
                new SyncedDropSource(buf.readEnum(SyncedDropSource.Kind.class), buf.readResourceLocation()),
                buf.readBoolean() ? readTrigger(buf) : TriggerSettings.DEATH
        );
    }

    /**
     * Writes one condition.
     *
     * @param buf       Target buffer. Never null.
     * @param condition Condition to write. Never null.
     */
    private static void writeCondition(FriendlyByteBuf buf, SyncedCondition condition) {
        buf.writeEnum(condition.kind());
        buf.writeBoolean(condition.isNegated());
        buf.writeCollection(condition.ids(), FriendlyByteBuf::writeResourceLocation);
    }

    /**
     * Reads one condition.
     *
     * @param buf Source buffer. Never null.
     * @return The condition.
     */
    private static SyncedCondition readCondition(FriendlyByteBuf buf) {
        return new SyncedCondition(buf.readEnum(SyncedCondition.Kind.class), buf.readBoolean(),
                buf.readList(FriendlyByteBuf::readResourceLocation));
    }

    /**
     * Writes a value, or only a {@code false} flag when it equals its default: most drops are loot table drops with
     * the default behavior, no bonus and no tracking, so this keeps the summary small.
     *
     * @param buf          Target buffer. Never null.
     * @param value        Value to write. Never null.
     * @param defaultValue Default of the value. Never null.
     * @param writer       Writes the value. Never null.
     * @param <V>          Value type.
     */
    private static <V> void writeUnlessDefault(FriendlyByteBuf buf, V value, V defaultValue, FriendlyByteBuf.Writer<V> writer) {
        boolean isSet = !value.equals(defaultValue);
        buf.writeBoolean(isSet);
        if (isSet) writer.accept(buf, value);
    }

    /**
     * Writes a drop behavior with its scale formula.
     *
     * @param buf      Target buffer. Never null.
     * @param behavior Behavior to write. Never null.
     */
    private static void writeBehavior(FriendlyByteBuf buf, DropBehavior behavior) {
        buf.writeEnum(behavior.type());
        buf.writeVarInt(behavior.rolls());
        buf.writeDouble(behavior.chancePerRoll());
        buf.writeVarInt(behavior.incrementValue());
        ScaleFormula formula = behavior.scaleFormula();
        buf.writeEnum(formula.function());
        buf.writeDouble(formula.multiplier());
        buf.writeDouble(formula.divisor());
    }

    /**
     * Reads a drop behavior with its scale formula.
     *
     * @param buf Source buffer. Never null.
     * @return The behavior.
     */
    private static DropBehavior readBehavior(FriendlyByteBuf buf) {
        DropBehaviorType type = buf.readEnum(DropBehaviorType.class);
        int rolls = buf.readVarInt();
        double chancePerRoll = buf.readDouble();
        int incrementValue = buf.readVarInt();
        ScaleFormula formula = new ScaleFormula(buf.readEnum(ScaleFunction.class), buf.readDouble(), buf.readDouble());
        return new DropBehavior(type, rolls, chancePerRoll, incrementValue, formula);
    }

    /**
     * Writes the bonuses of every enchantment of a drop: the count, then each enchantment id and its bonus.
     *
     * @param buf     Target buffer. Never null.
     * @param bonuses Bonuses to write. Never null.
     */
    private static void writeEnchantBonuses(FriendlyByteBuf buf, EnchantBonuses bonuses) {
        buf.writeVarInt(bonuses.entries().size());
        for (EnchantBonusEntry entry : bonuses.entries()) {
            buf.writeResourceLocation(ForgeRegistries.ENCHANTMENTS.getKey(entry.enchantment()));
            writeEnchantBonus(buf, entry.bonus());
        }
    }

    /**
     * Reads the bonuses of every enchantment of a drop. An enchantment the client does not know is left out.
     *
     * @param buf Source buffer. Never null.
     * @return The bonuses.
     */
    private static EnchantBonuses readEnchantBonuses(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<EnchantBonusEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation id = buf.readResourceLocation();
            EnchantBonus bonus = readEnchantBonus(buf);
            Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
            if (enchantment != null) entries.add(new EnchantBonusEntry(enchantment, bonus));
        }
        return new EnchantBonuses(entries);
    }

    /**
     * Writes the bonus of one enchantment.
     *
     * @param buf   Target buffer. Never null.
     * @param bonus Bonus to write. Never null.
     */
    private static void writeEnchantBonus(FriendlyByteBuf buf, EnchantBonus bonus) {
        buf.writeDouble(bonus.chance().bonusPerLevel());
        buf.writeDouble(bonus.chance().maxBonus());
        EnchantBonus.RollBonus roll = bonus.roll();
        buf.writeVarInt(roll.bonusRollsPerLevel());
        buf.writeDouble(roll.bonusRollChancePerLevel());
        buf.writeVarInt(roll.maxBonusRolls());
        buf.writeDouble(roll.maxBonusRollChance());
        buf.writeVarInt(roll.bonusIncrementPerLevel());
        buf.writeVarInt(roll.maxBonusIncrement());
        EnchantBonus.CountBonus count = bonus.count();
        buf.writeVarInt(count.bonusMinPerLevel());
        buf.writeVarInt(count.bonusMaxPerLevel());
        buf.writeVarInt(count.maxBonusMin());
        buf.writeVarInt(count.maxBonusMax());
    }

    /**
     * Reads the bonus of one enchantment.
     *
     * @param buf Source buffer. Never null.
     * @return The bonus.
     */
    private static EnchantBonus readEnchantBonus(FriendlyByteBuf buf) {
        EnchantBonus.ChanceBonus chance = new EnchantBonus.ChanceBonus(buf.readDouble(), buf.readDouble());
        EnchantBonus.RollBonus roll = new EnchantBonus.RollBonus(
                buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readVarInt());
        EnchantBonus.CountBonus count = new EnchantBonus.CountBonus(
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        return new EnchantBonus(chance, roll, count);
    }

    /**
     * Writes the per-player settings of a drop.
     *
     * @param buf      Target buffer. Never null.
     * @param tracking Settings to write. Never null.
     */
    private static void writeTracking(FriendlyByteBuf buf, DropTracking tracking) {
        buf.writeUtf(tracking.key());
        buf.writeVarInt(tracking.pity());
        buf.writeBoolean(tracking.isFirstDrop());
        buf.writeBoolean(tracking.isPerPlayer());
    }

    /**
     * Reads the per-player settings of a drop.
     *
     * @param buf Source buffer. Never null.
     * @return The settings.
     */
    private static DropTracking readTracking(FriendlyByteBuf buf) {
        return new DropTracking(buf.readUtf(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }
}
