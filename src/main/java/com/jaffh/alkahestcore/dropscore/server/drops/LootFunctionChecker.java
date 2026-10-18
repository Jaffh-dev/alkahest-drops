package com.jaffh.alkahestcore.dropscore.server.drops;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataResolver;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Warns about loot functions of a drop that load but would not do what the file author expects: functions that
 * read loot parameters the format never provides, {@code set_count} replacing the rolled count, and vanilla
 * enchantment bonuses stacking with the drop's own bonus. It never skips a drop.
 */
public final class LootFunctionChecker {
    /** Stands in for the condition of a {@code reference} condition during validation. */
    private static final LootItemCondition ANY_REFERENCED_CONDITION = LootItemRandomChanceCondition.randomChance(1.0F).build();

    /** Stands in for the item modifier of a {@code reference} function during validation. */
    private static final LootItemFunction ANY_REFERENCED_FUNCTION = LootDataManager.createComposite(new LootItemFunction[0]);

    /**
     * Resolves {@code reference} conditions and functions and {@code set_loot_table} tables during validation. They
     * are resolved by vanilla when the drop happens and cannot be looked up while dropscore files load, so they are
     * not reported as unknown.
     */
    private static final LootDataResolver REFERENCE_STAND_INS = new LootDataResolver() {
        /**
         * Returns a stand-in for any referenced element.
         *
         * @param id  Id and type of the referenced element. Never null.
         * @param <T> Element type.
         * @return A condition that always passes, an item modifier that does nothing or the empty loot table.
         */
        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getElement(LootDataId<T> id) {
            if (id.type() == LootDataType.PREDICATE) return (T) ANY_REFERENCED_CONDITION;
            if (id.type() == LootDataType.MODIFIER) return (T) ANY_REFERENCED_FUNCTION;
            if (id.type() == LootDataType.TABLE) return (T) LootTable.EMPTY;
            return null;
        }
    };

    private final DropFileFormat format;

    /**
     * Creates a checker for one file format.
     *
     * @param format Format of the files being read. Never null.
     */
    public LootFunctionChecker(DropFileFormat format) {
        this.format = format;
    }

    /**
     * Reports a warning for each problem of the functions of one drop.
     *
     * @param functions       Parsed functions, in file order. Never null.
     * @param json            JSON array the functions were parsed from, same order. Never null.
     * @param hasEnchantBonus Whether the drop has the format's bonus key ({@code on_looting} or {@code on_fortune}).
     * @param ctx             Context of the drop. Never null.
     */
    public void check(List<LootItemFunction> functions, JsonArray json, boolean hasEnchantBonus, ParseContext ctx) {
        for (int i = 0; i < functions.size(); i++) {
            LootItemFunction function = functions.get(i);
            ParseContext functionCtx = ctx.at("functions", i);
            warnMissingParams(function, functionCtx);
            LootItemFunctionType type = function.getType();
            if (type == LootItemFunctions.SET_COUNT && !isAddingCount(json.get(i))) {
                functionCtx.warn("replaces the rolled count, so 'count' and the " + format.bonusKey()
                        + " count bonuses are lost; set \"add\": true to add to the rolled count instead");
            }
            if (type == format.enchantBonusFunction() && hasEnchantBonus) {
                functionCtx.warn("adds a vanilla enchantment bonus on top of '" + format.bonusKey()
                        + "', so the drop gets both bonuses");
            }
        }
    }

    /**
     * Reports each loot parameter a function or one of its conditions reads but the format's loot context never
     * holds. Vanilla does not fail on them: the function silently does nothing.
     *
     * @param function    Function to validate. Never null.
     * @param functionCtx Context of the function. Never null.
     */
    private void warnMissingParams(LootItemFunction function, ParseContext functionCtx) {
        ValidationContext validation = new ValidationContext(format.lootParams(), REFERENCE_STAND_INS);
        function.validate(validation);
        for (Map.Entry<String, String> problem : validation.getProblems().entries()) {
            functionCtx.atSuffix(problem.getKey()).warn(problem.getValue() + " (" + format.filesName()
                    + "), so it has no effect");
        }
    }

    /**
     * Tells whether a {@code set_count} function adds to the count instead of replacing it.
     *
     * @param json JSON object of the function. Never null.
     * @return True when {@code "add": true} is set.
     */
    private static boolean isAddingCount(JsonElement json) {
        return json.isJsonObject() && GsonHelper.getAsBoolean(json.getAsJsonObject(), "add", false);
    }
}
