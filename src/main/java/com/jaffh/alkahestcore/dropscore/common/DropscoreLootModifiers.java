package com.jaffh.alkahestcore.dropscore.common;

import com.jaffh.alkahestcore.dropscore.server.blockdrops.BlockDropModifier;
import net.minecraftforge.common.loot.GlobalLootModifierSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Registers the Global Loot Modifier serializers of dropscore.
 */
public final class DropscoreLootModifiers {
    /** Path of the block drops modifier; its loot modifier file is {@code data/<modId>/loot_modifiers/block_drops.json}. */
    public static final String BLOCK_DROPS = "block_drops";

    /**
     * Prevents instantiation of this utility class.
     */
    private DropscoreLootModifiers() {
    }

    /**
     * Registers the serializers under the mod's namespace on the mod bus.
     *
     * @param modEventBus Event bus of the mod. Never null.
     * @param modId       Namespace of the serializers. Never null.
     */
    public static void register(IEventBus modEventBus, String modId) {
        DeferredRegister<GlobalLootModifierSerializer<?>> serializers =
                DeferredRegister.create(ForgeRegistries.Keys.LOOT_MODIFIER_SERIALIZERS, modId);
        serializers.register(BLOCK_DROPS, BlockDropModifier.Serializer::new);
        serializers.register(modEventBus);
    }
}
