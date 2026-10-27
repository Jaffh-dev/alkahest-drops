package com.jaffh.alkahestdrops.server.progress;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;

/**
 * Exposes and saves the {@link DropProgress} of one player.
 */
final class DropProgressProvider implements ICapabilitySerializable<CompoundTag> {
    private final DropProgress progress = new DropProgress();
    private LazyOptional<DropProgress> optional = LazyOptional.of(() -> progress);

    /**
     * Returns the progress when the drop progress capability is asked for. Forge invalidates a player's capabilities
     * and revives them on every dimension change (the player entity is kept) and around the respawn copy, so an
     * invalidated optional is replaced with a new one; while the player is really removed, Forge itself answers empty
     * without asking this provider.
     *
     * @param capability Capability asked for. Never null.
     * @param side       Side asked from, unused.
     * @param <T>        Type of the capability.
     * @return The progress for {@link DropProgress#CAPABILITY}, an empty optional otherwise.
     */
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability != DropProgress.CAPABILITY) return LazyOptional.empty();
        if (!optional.isPresent()) optional = LazyOptional.of(() -> progress);
        return optional.cast();
    }

    /**
     * Invalidates the handed-out optional when Forge invalidates the player's capabilities, so holders drop it.
     */
    void invalidate() {
        optional.invalidate();
    }

    /**
     * Writes the progress to NBT, saved with the player.
     *
     * @return The progress tag.
     */
    @Override
    public CompoundTag serializeNBT() {
        return progress.save();
    }

    /**
     * Reads the progress saved with the player.
     *
     * @param tag Progress tag. Never null.
     */
    @Override
    public void deserializeNBT(CompoundTag tag) {
        progress.load(tag);
    }
}
