package com.jaffh.alkahestdrops.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;

/**
 * Represents one drop that an Alkahest Drops drop file adds to a mob or block. Instances are created by Alkahest Drops
 * only; read them through {@link AlkahestDropsApi}.
 *
 * @param fileId          Id of the drop file, such as {@code mypack:zombie}. Never null.
 * @param dropKey         Key of the drop, the same one {@link com.jaffh.alkahestdrops.api.event.OnDropEvent#getDropKey()}
 *                        returns. Never null.
 * @param item            Dropped item, or null for an experience drop.
 * @param chance          Base chance (0–1) before enchantment bonuses.
 * @param minCount        Lowest base count (or experience) before enchantment bonuses. Roll-based and scaled drops
 *                        ({@code drop_behavior}) may give other amounts.
 * @param maxCount        Highest base count (or experience) before enchantment bonuses.
 * @param trigger         What makes the drop happen. Never null.
 * @param isConditional   Whether loot conditions (of the file or the drop) decide if it drops.
 * @param hasEnchantBonus Whether some enchantment of the held item raises the chance, count or rolls.
 * @param pity            Roll on which the drop is forced after that many misses in a row by the same player, 0 for
 *                        no bad-luck protection; read a player's misses with {@link AlkahestDropsApi#getPityMisses}.
 * @param isFirstDrop     Whether the drop happens once per player, ignoring its chance ({@code first_kill} /
 *                        {@code first_break}); see {@link AlkahestDropsApi#hasReceivedFirstDrop}.
 * @since 1.0.0
 */
public record AddedDrop(ResourceLocation fileId, String dropKey, @Nullable Item item, double chance, int minCount,
                        int maxCount, DropTrigger trigger, boolean isConditional, boolean hasEnchantBonus, int pity,
                        boolean isFirstDrop) {
    /**
     * Tells whether this drop gives experience instead of an item.
     *
     * @return True when {@link #item()} is null.
     * @since 1.0.0
     */
    public boolean isExperience() {
        return item == null;
    }
}
