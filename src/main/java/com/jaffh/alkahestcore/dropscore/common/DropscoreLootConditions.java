package com.jaffh.alkahestcore.dropscore.common;

import com.jaffh.alkahestcore.AlkahestCore;
import com.jaffh.alkahestcore.dropscore.server.drops.RealPlayerCondition;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers the loot condition types of dropscore, usable in dropscore files and in any loot table.
 */
public final class DropscoreLootConditions {
    private static final DeferredRegister<LootItemConditionType> CONDITION_TYPES =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, AlkahestCore.ID);

    /** {@code <modId>:killed_by_real_player}: a real player, not a machine's fake player, killed the mob. */
    public static final RegistryObject<LootItemConditionType> KILLED_BY_REAL_PLAYER = CONDITION_TYPES.register(
            "killed_by_real_player", () -> new LootItemConditionType(RealPlayerCondition.KILLED.serializer()));

    /** {@code <modId>:broken_by_real_player}: a real player, not a machine or an explosion, broke the block. */
    public static final RegistryObject<LootItemConditionType> BROKEN_BY_REAL_PLAYER = CONDITION_TYPES.register(
            "broken_by_real_player", () -> new LootItemConditionType(RealPlayerCondition.BROKEN.serializer()));

    /**
     * Prevents instantiation of this utility class.
     */
    private DropscoreLootConditions() {
    }

    /**
     * Registers the condition types on the mod bus.
     *
     * @param modEventBus Event bus of the mod. Never null.
     */
    public static void register(IEventBus modEventBus) {
        CONDITION_TYPES.register(modEventBus);
    }
}
