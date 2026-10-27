package com.jaffh.alkahestdrops.common.drops;

/**
 * Represents when a drop happens: its trigger and the trigger's tick setting.
 *
 * @param trigger What makes the drop happen. Never null.
 * @param ticks   Interval of a periodic drop or cooldown of a hit drop, in ticks; 0 for the other triggers.
 */
public record TriggerSettings(DropTrigger trigger, int ticks) {
    /** A drop on death, the default. */
    public static final TriggerSettings DEATH = new TriggerSettings(DropTrigger.DEATH, 0);

    /**
     * Tells whether the drop happens on death.
     *
     * @return True for {@link DropTrigger#DEATH}.
     */
    public boolean isDeath() {
        return trigger == DropTrigger.DEATH;
    }
}
