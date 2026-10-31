package com.jaffh.alkahestdrops.server.mobdrops;

import java.util.OptionalInt;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LootingLevelEvent;

/**
 * Forces the Looting level of one simulated mob, so vanilla {@code looting_enchant} functions and {@code enchant_bonus}
 * entries read the level given to {@code /alkdrops simulate} instead of the killer's weapon. Server thread only.
 */
public final class LootingOverride {
    /** The override registered on the Forge bus. */
    public static final LootingOverride INSTANCE = new LootingOverride();

    @Nullable
    private LivingEntity target;

    private int lootingLevel;

    /**
     * Creates the single override.
     */
    private LootingOverride() {}

    /**
     * Replaces the Looting level of the simulated mob. Registered at LOWEST priority so it wins over other mods.
     *
     * @param event Looting level event of a dying or simulated mob.
     */
    public void onLootingLevel(LootingLevelEvent event) {
        if (target != null && event.getEntity() == target) {
            event.setLootingLevel(lootingLevel);
        }
    }

    /**
     * Runs an action with the Looting level of a mob forced.
     *
     * @param mob          Simulated mob. Never null.
     * @param lootingLevel Level to force; empty runs the action without override.
     * @param action       Action to run. Never null.
     * @param <R>          Result type.
     * @return The result of the action.
     */
    <R> R runWith(LivingEntity mob, OptionalInt lootingLevel, Supplier<R> action) {
        if (lootingLevel.isEmpty()) return action.get();
        target = mob;
        this.lootingLevel = lootingLevel.getAsInt();
        try {
            return action.get();
        } finally {
            target = null;
        }
    }
}
