package com.jaffh.alkahestcore.dropscore.common.drops;

import net.minecraft.resources.ResourceLocation;

/**
 * Represents where a synced drop comes from: the target's vanilla loot table, its vanilla experience, or a dropscore
 * drop file. Clients show the namespace of the id as "Added by".
 *
 * @param kind Kind of source. Never null.
 * @param id   Loot table id, drop file id, or the target's id for vanilla experience. Never null.
 */
public record SyncedDropSource(Kind kind, ResourceLocation id) {

    /**
     * Creates the source of a loot table drop.
     *
     * @param tableId Loot table id. Never null.
     * @return The source.
     */
    public static SyncedDropSource lootTable(ResourceLocation tableId) {
        return new SyncedDropSource(Kind.LOOT_TABLE, tableId);
    }

    /**
     * Creates the source of the vanilla experience of a target.
     *
     * @param targetId Id of the mob or block. Never null.
     * @return The source.
     */
    public static SyncedDropSource experience(ResourceLocation targetId) {
        return new SyncedDropSource(Kind.EXPERIENCE, targetId);
    }

    /**
     * Creates the source of a dropscore drop.
     *
     * @param fileId Id of the drop file. Never null.
     * @return The source.
     */
    public static SyncedDropSource dropscore(ResourceLocation fileId) {
        return new SyncedDropSource(Kind.DROPSCORE, fileId);
    }

    /**
     * Defines the kinds of drop sources.
     */
    public enum Kind {
        /** A drop of the target's vanilla loot table (as changed by datapacks). */
        LOOT_TABLE,
        /** The vanilla experience of the target. */
        EXPERIENCE,
        /** A drop of a dropscore drop file. */
        DROPSCORE
    }
}
