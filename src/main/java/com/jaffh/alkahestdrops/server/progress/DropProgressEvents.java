package com.jaffh.alkahestdrops.server.progress;

import com.jaffh.alkahestdrops.AlkahestDrops;
import com.jaffh.alkahestdrops.server.drops.RealPlayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Registers the {@link DropProgress} capability, attaches it to real players on the server and keeps it when a player
 * respawns or returns from the End.
 */
public final class DropProgressEvents {
    private static final ResourceLocation CAPABILITY_ID = new ResourceLocation(AlkahestDrops.ID, "drop_progress");

    /**
     * Prevents instantiation of this utility class.
     */
    private DropProgressEvents() {
    }

    /**
     * Registers the capability type (mod bus).
     *
     * @param event Capability registration event.
     */
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(DropProgress.class);
    }

    /**
     * Attaches empty progress to every real player on the server; the saved progress is read after this. Fake
     * players of machine mods get none, so farms never build up pity or claim one-time drops.
     *
     * @param event Attach event of an entity.
     */
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        Entity entity = event.getObject();
        if (!RealPlayers.isReal(entity) || entity.level().isClientSide) return;

        DropProgressProvider provider = new DropProgressProvider();
        event.addCapability(CAPABILITY_ID, provider);
        event.addListener(provider::invalidate);
    }

    /**
     * Copies the progress to the new player entity, after a death and when returning from the End.
     *
     * @param event Clone event of the player.
     */
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        DropProgress oldProgress = DropProgress.of(original);
        DropProgress newProgress = DropProgress.of(event.getEntity());
        if (oldProgress != null && newProgress != null) newProgress.copyFrom(oldProgress);
        original.invalidateCaps();
    }
}
