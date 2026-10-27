package com.jaffh.alkahestdrops.common.drops;

/**
 * Represents the per-player settings of a drop: bad-luck protection, one-time drops and separate rolls for every
 * attacker. Pity and one-time drop progress is stored per player under {@code key}.
 *
 * @param key         Progress key of the drop, {@code "<mob|block>:<fileId>#<dropIndex>"}. Never null.
 * @param pity        Roll on which the drop is forced after that many misses in a row, 0 for no pity.
 * @param isFirstDrop Whether the drop happens only once per player, ignoring its chance ({@code first_kill} in
 *                    mob files, {@code first_break} in block files).
 * @param isPerPlayer Whether every player who damaged the mob gets an own roll, locked to them (mobs only).
 */
public record DropTracking(String key, int pity, boolean isFirstDrop, boolean isPerPlayer) {
    /**
     * Tells whether the drop has bad-luck protection.
     *
     * @return True when {@code pity} is set.
     */
    public boolean hasPity() {
        return pity > 0;
    }

    /**
     * Tells whether rolling the drop reads or changes the progress of a player.
     *
     * @return True for pity and one-time drops.
     */
    public boolean needsProgress() {
        return isFirstDrop || hasPity();
    }
}
