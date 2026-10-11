package com.jaffh.alkahestcore.dropscore.server.drops;

import com.jaffh.alkahestcore.dropscore.common.drops.DropBehaviorType;
import com.jaffh.alkahestcore.dropscore.server.drops.json.DropEntryJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.DropFileJson;
import com.jaffh.alkahestcore.dropscore.server.drops.json.EnchantBonusJson;

import javax.annotation.Nullable;

/**
 * Defines the keys that differ between mob drop files and block drop files, so one parser reads both.
 */
public enum DropFileFormat {
    /** Files in {@code data/<namespace>/mob_drops/}: target key {@code entity}, bonus key {@code on_looting}. */
    MOB("mob drop files", "entity", "on_looting", DropBehaviorType.MAX_HEALTH) {
        @Nullable
        @Override
        public String targetOf(DropFileJson file) {
            return file.entity;
        }

        @Nullable
        @Override
        public EnchantBonusJson enchantBonusOf(DropEntryJson drop) {
            return drop.onLooting;
        }
    },
    /** Files in {@code data/<namespace>/block_drops/}: target key {@code block}, bonus key {@code on_fortune}. */
    BLOCK("block drop files", "block", "on_fortune", DropBehaviorType.HARDNESS) {
        @Nullable
        @Override
        public String targetOf(DropFileJson file) {
            return file.block;
        }

        @Nullable
        @Override
        public EnchantBonusJson enchantBonusOf(DropEntryJson drop) {
            return drop.onFortune;
        }
    };

    private final String filesName;
    private final String targetKey;
    private final String bonusKey;
    private final DropBehaviorType scaleType;

    /**
     * Creates a file format.
     *
     * @param filesName          Name of the files of this format in messages, such as {@code mob drop files}.
     * @param targetKey          JSON key of the file target.
     * @param bonusKey           JSON key of a drop's enchantment bonus.
     * @param scaleType          The only scaled behavior drops of this format may use.
     */
    DropFileFormat(String filesName, String targetKey, String bonusKey, DropBehaviorType scaleType) {
        this.filesName = filesName;
        this.targetKey = targetKey;
        this.bonusKey = bonusKey;
        this.scaleType = scaleType;
    }

    /**
     * Returns the raw target of a file.
     *
     * @param file Parsed file. Never null.
     * @return The value of the target key, or null when missing.
     */
    @Nullable
    public abstract String targetOf(DropFileJson file);

    /**
     * Returns the enchantment bonus of a drop.
     *
     * @param drop Parsed drop. Never null.
     * @return The value of the bonus key, or null when missing.
     */
    @Nullable
    public abstract EnchantBonusJson enchantBonusOf(DropEntryJson drop);

    /**
     * Returns the other format, whose keys are a likely mistake in files of this one.
     *
     * @return {@link #BLOCK} for {@link #MOB} and the other way around.
     */
    public DropFileFormat other() {
        return this == MOB ? BLOCK : MOB;
    }

    /**
     * Returns the name of the files of this format in messages.
     *
     * @return {@code mob drop files} or {@code block drop files}.
     */
    public String filesName() {
        return filesName;
    }

    /**
     * Returns the JSON key of the file target.
     *
     * @return {@code entity} or {@code block}.
     */
    public String targetKey() {
        return targetKey;
    }

    /**
     * Returns the JSON key of a drop's enchantment bonus.
     *
     * @return {@code on_looting} or {@code on_fortune}.
     */
    public String bonusKey() {
        return bonusKey;
    }

    /**
     * Returns the scaled behavior drops of this format may use; the other scaled behavior is an error.
     *
     * @return {@code max_health} for mob files, {@code hardness} for block files.
     */
    public DropBehaviorType scaleType() {
        return scaleType;
    }
}
