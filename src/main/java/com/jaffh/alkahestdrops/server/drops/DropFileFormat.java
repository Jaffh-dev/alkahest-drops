package com.jaffh.alkahestdrops.server.drops;

import com.jaffh.alkahestdrops.common.drops.DropBehaviorType;
import com.jaffh.alkahestdrops.server.drops.json.DropEntryJson;
import com.jaffh.alkahestdrops.server.drops.json.DropFileJson;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/**
 * Defines the keys that differ between mob drop files and block drop files, so one parser reads both.
 */
public enum DropFileFormat {
    /**
     * Files in {@code data/<namespace>/mob_drops/}: target key {@code entity}, one-time drop key
     * {@code first_kill}.
     */
    MOB("entity", "first_kill", EnumSet.of(DropBehaviorType.MAX_HEALTH, DropBehaviorType.DAMAGE_TAKEN)) {
        @Nullable
        @Override
        public String targetOf(DropFileJson file) {
            return file.entity;
        }

        @Nullable
        @Override
        public Boolean firstDropOf(DropEntryJson drop) {
            return drop.isFirstKill;
        }

        @Override
        public LootContextParamSet lootParams() {
            return LootContextParamSets.ENTITY;
        }
    },
    /**
     * Files in {@code data/<namespace>/block_drops/}: target key {@code block}, one-time drop key
     * {@code first_break}.
     */
    BLOCK("block", "first_break", EnumSet.of(DropBehaviorType.HARDNESS)) {
        @Nullable
        @Override
        public String targetOf(DropFileJson file) {
            return file.block;
        }

        @Nullable
        @Override
        public Boolean firstDropOf(DropEntryJson drop) {
            return drop.isFirstBreak;
        }

        @Override
        public LootContextParamSet lootParams() {
            return LootContextParamSets.BLOCK;
        }
    };

    private final String targetKey;
    private final String firstDropKey;
    private final Set<DropBehaviorType> scaleTypes;

    /**
     * Creates a file format.
     *
     * @param targetKey          JSON key of the file target.
     * @param firstDropKey       JSON key of a drop that happens once per player.
     * @param scaleTypes         The scaled behaviors drops of this format may use.
     */
    DropFileFormat(String targetKey, String firstDropKey, Set<DropBehaviorType> scaleTypes) {
        this.targetKey = targetKey;
        this.firstDropKey = firstDropKey;
        this.scaleTypes = Set.copyOf(scaleTypes);
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
     * Returns whether a drop happens only once per player.
     *
     * @param drop Parsed drop. Never null.
     * @return The value of the one-time drop key, or null when missing.
     */
    @Nullable
    public abstract Boolean firstDropOf(DropEntryJson drop);

    /**
     * Returns the loot parameters the loot context of this format can hold, the same as the vanilla loot tables
     * of its targets.
     *
     * @return {@code entity} parameters for mob files, {@code block} parameters for block files.
     */
    public abstract LootContextParamSet lootParams();

    /**
     * Returns the other format, whose keys are a likely mistake in files of this one.
     *
     * @return {@link #BLOCK} for {@link #MOB} and the other way around.
     */
    public DropFileFormat other() {
        return this == MOB ? BLOCK : MOB;
    }

    /**
     * Returns the name of the files of this format in problem messages.
     *
     * @return Translatable text, such as "mob drop files". Never null.
     */
    public Component filesName() {
        return DropIssue.text("files." + name().toLowerCase(Locale.ROOT));
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
     * Returns the JSON key of a drop that happens once per player.
     *
     * @return {@code first_kill} or {@code first_break}.
     */
    public String firstDropKey() {
        return firstDropKey;
    }

    /**
     * Returns the start of the progress keys of this format's drops, so the progress of mob and block files with
     * the same id never mixes.
     *
     * @return {@code mob:} or {@code block:}.
     */
    public String progressKeyPrefix() {
        return name().toLowerCase(Locale.ROOT) + ":";
    }

    /**
     * Tells whether a progress key belongs to a drop of this format.
     *
     * @param key Progress key. Never null.
     * @return True when the key starts with {@link #progressKeyPrefix()}.
     */
    public boolean ownsProgressKey(String key) {
        return key.startsWith(progressKeyPrefix());
    }

    /**
     * Builds the key under which the pity and one-time drop progress of a drop is stored for each player. A drop with
     * an {@code id} keeps its key when drops are added, removed or reordered; without one, the key follows its
     * position. The two forms use different separators, so they never collide.
     *
     * @param fileId Id of the drop file. Never null.
     * @param id     The drop's {@code id}, or null when it has none.
     * @param index  Position of the drop in {@code drops}, 0 or more.
     * @return Such as {@code mob:test_mobs:sheep@rare_star} with an id, or {@code mob:test_mobs:sheep#0} without.
     */
    public String progressKey(ResourceLocation fileId, @Nullable String id, int index) {
        return progressKeyPrefix() + fileId + (id != null ? "@" + id : "#" + index);
    }

    /**
     * Reads the drop file id out of a progress key of this format.
     *
     * @param key Progress key built by {@link #progressKey}. Never null.
     * @return The file id, or null when the key does not belong to this format or is malformed.
     */
    @Nullable
    public ResourceLocation fileIdOfProgressKey(String key) {
        if (!ownsProgressKey(key)) return null;
        String rest = key.substring(progressKeyPrefix().length());
        int idStart = rest.indexOf('@');
        int indexStart = rest.indexOf('#');
        int end = idStart < 0 ? indexStart : indexStart < 0 ? idStart : Math.min(idStart, indexStart);
        return end > 0 ? ResourceLocation.tryParse(rest.substring(0, end)) : null;
    }

    /**
     * Tells whether drops of this format may use a scaled behavior; the others are an error.
     *
     * @param type Scaled behavior type. Never null.
     * @return True for {@code max_health} and {@code damage_taken} in mob files, {@code hardness} in block files.
     */
    public boolean allowsScaleType(DropBehaviorType type) {
        return scaleTypes.contains(type);
    }

    /**
     * Lists the scaled behaviors of this format for messages.
     *
     * @return Such as {@code 'max_health', 'damage_taken'}.
     */
    public String scaleTypeNames() {
        return scaleTypes.stream()
                .sorted()
                .map(type -> "'" + type.name().toLowerCase(Locale.ROOT) + "'")
                .collect(Collectors.joining(", "));
    }

    /**
     * Tells whether drops of this format can roll once per attacking player ({@code per_player}). A block has
     * only its breaker, so block files ignore the key.
     *
     * @return True for mob files.
     */
    public boolean canRollPerPlayer() {
        return this == MOB;
    }

    /**
     * Tells whether drops and removal files of this format may set {@code on}, the action that makes them drop. A
     * block only drops when it breaks, so block files reject the key.
     *
     * @return True for mob files.
     */
    public boolean canUseTriggers() {
        return this == MOB;
    }
}
