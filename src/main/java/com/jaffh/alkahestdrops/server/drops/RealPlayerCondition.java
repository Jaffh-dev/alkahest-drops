package com.jaffh.alkahestdrops.server.drops;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.jaffh.alkahestdrops.common.AlkahestDropsLootConditions;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.Serializer;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/**
 * Loot condition that passes only when a real player, not a machine's fake player, killed the mob or broke the block
 * ({@link RealPlayers#isReal}). Without keys: {@code { "condition": "alkahestdrops:killed_by_real_player" }}. Works in
 * Alkahest Drops files and in any loot table.
 */
public final class RealPlayerCondition implements LootItemCondition {
    /** {@code killed_by_real_player}: the player who last hurt the mob, like vanilla {@code killed_by_player}. */
    public static final RealPlayerCondition KILLED = new RealPlayerCondition(
            LootContextParams.LAST_DAMAGE_PLAYER, () -> AlkahestDropsLootConditions.KILLED_BY_REAL_PLAYER.get());
    /** {@code broken_by_real_player}: the player breaking the block; explosions and machines without one fail. */
    public static final RealPlayerCondition BROKEN = new RealPlayerCondition(
            LootContextParams.THIS_ENTITY, () -> AlkahestDropsLootConditions.BROKEN_BY_REAL_PLAYER.get());

    private final LootContextParam<? extends Entity> playerParam;
    private final Supplier<LootItemConditionType> type;

    /**
     * Creates one of the two conditions.
     *
     * @param playerParam Loot parameter holding the player to check. Never null.
     * @param type        Registered type of the condition, read lazily. Never null.
     */
    private RealPlayerCondition(LootContextParam<? extends Entity> playerParam, Supplier<LootItemConditionType> type) {
        this.playerParam = playerParam;
        this.type = type;
    }

    /**
     * Returns the registered type of this condition.
     *
     * @return The condition type.
     */
    @Override
    public LootItemConditionType getType() {
        return type.get();
    }

    /**
     * Returns the loot parameter this condition reads, for vanilla's loot validation.
     *
     * @return The player parameter.
     */
    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(playerParam);
    }

    /**
     * Tells whether a real player killed the mob or broke the block.
     *
     * @param context Loot context of the drop. Never null.
     * @return True when the player parameter holds a real player; false when it is missing or a fake player.
     */
    @Override
    public boolean test(LootContext context) {
        return RealPlayers.isReal(context.getParamOrNull(playerParam));
    }

    /**
     * Creates the JSON serializer of this condition, which has no keys and always reads back this instance.
     *
     * @return The serializer.
     */
    public Serializer<RealPlayerCondition> serializer() {
        return new Serializer<>() {
            /**
             * Writes nothing, since the condition has no keys.
             *
             * @param json      Object being written. Never null.
             * @param condition Condition. Never null.
             * @param context   Serialization context. Never null.
             */
            @Override
            public void serialize(JsonObject json, RealPlayerCondition condition, JsonSerializationContext context) {}

            /**
             * Reads the condition.
             *
             * @param json    Object of the condition. Never null.
             * @param context Deserialization context. Never null.
             * @return This condition.
             */
            @Override
            public RealPlayerCondition deserialize(JsonObject json, JsonDeserializationContext context) {
                return RealPlayerCondition.this;
            }
        };
    }
}
