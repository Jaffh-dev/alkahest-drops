package com.jaffh.alkahestdrops.common.drops;

import com.jaffh.alkahestdrops.api.DropTrigger;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Represents how the drop file format treats one trigger. Kept out of the public {@link DropTrigger} so format details
 * can change without touching the API.
 *
 * @param hasEnchantBonus Whether drops of the trigger read {@code enchant_bonus} (a held item is involved).
 * @param ticksKey        JSON key of the trigger's tick setting ({@code "interval"} or {@code "cooldown"}), or null for none.
 * @param defaultTicks    Tick setting when the key is missing.
 * @param minTicks        Lowest allowed tick setting.
 */
public record TriggerRules(boolean hasEnchantBonus, @Nullable String ticksKey, int defaultTicks, int minTicks) {
    private static final Map<DropTrigger, TriggerRules> RULES = new EnumMap<>(Map.of(
            DropTrigger.DEATH, new TriggerRules(true, null, 0, 0),
            DropTrigger.SHEAR, new TriggerRules(true, null, 0, 0),
            DropTrigger.HIT, new TriggerRules(true, "cooldown", 0, 0),
            DropTrigger.LIGHTNING, new TriggerRules(false, null, 0, 0),
            DropTrigger.PERIODIC, new TriggerRules(false, "interval", 6000, 1)
    ));

    /**
     * Returns the rules of a trigger.
     *
     * @param trigger Trigger. Never null.
     * @return Its rules. Never null.
     */
    public static TriggerRules of(DropTrigger trigger) {
        return RULES.get(trigger);
    }
}
