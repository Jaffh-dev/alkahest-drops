package com.jaffh.alkahestdrops.common;

import com.jaffh.alkahestdrops.server.blockdrops.BlockDropModifier;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Registers the Global Loot Modifier codecs of Alkahest Drops.
 */
public final class AlkahestDropsLootModifiers {
    /** Path of the block drops modifier; its loot modifier file is {@code data/<modId>/loot_modifiers/block_drops.json}. */
    public static final String BLOCK_DROPS = "block_drops";

    /**
     * Prevents instantiation of this utility class.
     */
    private AlkahestDropsLootModifiers() {
    }

    /**
     * Registers the codecs under the mod's namespace on the mod bus.
     *
     * @param modEventBus Event bus of the mod. Never null.
     * @param modId       Namespace of the codecs. Never null.
     */
    public static void register(IEventBus modEventBus, String modId) {
        DeferredRegister<Codec<? extends IGlobalLootModifier>> serializers =
                DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, modId);
        serializers.register(BLOCK_DROPS, BlockDropModifier.CODEC);
        serializers.register(modEventBus);
    }
}
