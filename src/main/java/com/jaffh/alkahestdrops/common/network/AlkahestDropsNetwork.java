package com.jaffh.alkahestdrops.common.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Network channel of Alkahest Drops. It only carries the drop summary that recipe viewers show, so it exists only where
 * that summary can be used: on a dedicated server, which may have clients with a viewer, and on a client with JEI
 * (which also covers its singleplayer and LAN server). Either side accepts a peer without the channel, and the
 * server sends nothing to such a peer.
 */
public final class AlkahestDropsNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final String JEI_MOD_ID = "jei";

    @Nullable
    private static SimpleChannel channel;

    /**
     * Prevents instantiation of this utility class.
     */
    private AlkahestDropsNetwork() {
    }

    /**
     * Creates the channel and its messages when this side can use them. Call once, during mod construction.
     *
     * @param modId Namespace of the channel. Never null.
     */
    public static void register(String modId) {
        if (!needsChannel()) return;
        SimpleChannel created = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(modId, "sync"),
                () -> PROTOCOL_VERSION,
                NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
                NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
        );
        created.registerMessage(0, DropRulesSyncPacket.class, DropRulesSyncPacket::encode, DropRulesSyncPacket::decode,
                DropRulesSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel = created;
    }

    /**
     * Tells whether the channel exists on this side.
     *
     * @return False on a client without JEI.
     */
    public static boolean isEnabled() {
        return channel != null;
    }

    /**
     * Tells whether a player's client has the channel, so it can receive the drop summary.
     *
     * @param player Connected player. Never null.
     * @return False when this side or the player's client has no channel.
     */
    public static boolean canReceive(ServerPlayer player) {
        return channel != null && channel.isRemotePresent(player.connection.connection);
    }

    /**
     * Sends a message to one player. Check {@link #canReceive} first.
     *
     * @param player  Receiving player. Never null.
     * @param message Message to send. Never null.
     */
    public static void sendTo(ServerPlayer player, Object message) {
        if (channel == null) return;
        channel.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /**
     * Tells whether this side can use the channel.
     *
     * @return True on a dedicated server or when JEI is loaded.
     */
    private static boolean needsChannel() {
        return FMLEnvironment.dist.isDedicatedServer() || ModList.get().isLoaded(JEI_MOD_ID);
    }
}
