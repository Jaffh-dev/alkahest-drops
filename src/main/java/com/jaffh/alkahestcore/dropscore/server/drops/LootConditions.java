package com.jaffh.alkahestcore.dropscore.server.drops;

import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;

/**
 * Tests lists of vanilla loot conditions for drop and removal rules.
 */
public final class LootConditions {

    /**
     * Prevents instantiation of this utility class.
     */
    private LootConditions() {
    }

    /**
     * Tests every condition against a loot context.
     *
     * @param conditions Conditions to test. Never null; an empty list passes.
     * @param context    Loot context of the kill. Never null when the list is not empty.
     * @return True when all conditions pass.
     */
    public static boolean testAll(List<LootItemCondition> conditions, LootContext context) {
        for (LootItemCondition condition : conditions) {
            if (!condition.test(context)) return false;
        }
        return true;
    }
}
