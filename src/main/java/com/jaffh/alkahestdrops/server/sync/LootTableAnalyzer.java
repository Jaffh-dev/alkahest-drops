package com.jaffh.alkahestdrops.server.sync;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jaffh.alkahestdrops.common.drops.DropBehavior;
import com.jaffh.alkahestdrops.common.drops.DropCount;
import com.jaffh.alkahestdrops.common.drops.EnchantBonus;
import com.jaffh.alkahestdrops.common.drops.EnchantBonusEntry;
import com.jaffh.alkahestdrops.common.drops.EnchantBonuses;
import com.jaffh.alkahestdrops.common.drops.SyncedDrop;
import com.jaffh.alkahestdrops.common.drops.TriggerSettings;
import com.jaffh.alkahestdrops.common.drops.SyncedDropSource;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootDataResolver;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a loot table into the drops JEI shows: one entry per item, with its chance, count range, enchantment
 * bonuses and other conditions. The table is serialized back to JSON with vanilla's own serializer,
 * so datapack changes and modded entry types are read the same way, without access to private fields.
 * <p>
 * The chance of an entry is its share of the pool's weights, times the random chance conditions, over the pool's
 * rolls. It is exact for the usual tables and an approximation for unusual ones (entries whose conditions change the
 * weights, {@code alternatives} children, nested tables). Runs once per datapack load.
 */
final class LootTableAnalyzer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = Deserializers.createLootTableSerializer().create();
    private static final int MAX_DEPTH = 4;
    private static final ResourceLocation ITEM = new ResourceLocation("item");
    private static final ResourceLocation TAG = new ResourceLocation("tag");
    private static final ResourceLocation LOOT_TABLE = new ResourceLocation("loot_table");
    private static final ResourceLocation ALTERNATIVES = new ResourceLocation("alternatives");
    private static final ResourceLocation GROUP = new ResourceLocation("group");
    private static final ResourceLocation SEQUENCE = new ResourceLocation("sequence");

    private final LootDataResolver lootData;
    private final ConditionDescriber conditionDescriber;

    /**
     * Creates an analyzer for one module.
     *
     * @param lootData           Loot tables of the server. Never null.
     * @param conditionDescriber Describes the conditions of the module. Never null.
     */
    LootTableAnalyzer(LootDataResolver lootData, ConditionDescriber conditionDescriber) {
        this.lootData = lootData;
        this.conditionDescriber = conditionDescriber;
    }

    /**
     * Lists the drops of a loot table, including the drops of the tables it references.
     *
     * @param tableId Loot table id. Never null.
     * @return The drops in table order; empty for a missing or empty table.
     */
    List<SyncedDrop> analyze(ResourceLocation tableId) {
        List<SyncedDrop> drops = new ArrayList<>();
        addTable(tableId, 1.0, 0, new Output(SyncedDropSource.lootTable(tableId), drops));
        return drops;
    }

    /**
     * Adds the drops of one table.
     *
     * @param tableId Loot table id. Never null.
     * @param factor  Chance that this table is rolled at all (below 1 for referenced tables).
     * @param depth   Number of references followed to get here.
     * @param output  Receives the drops. Never null.
     */
    private void addTable(ResourceLocation tableId, double factor, int depth, Output output) {
        if (depth > MAX_DEPTH) return;
        LootTable table = lootData.getLootTable(tableId);
        if (table == LootTable.EMPTY) return;
        JsonObject json;
        try {
            json = GSON.toJsonTree(table).getAsJsonObject();
        } catch (RuntimeException e) {
            LOGGER.warn("[alkahestdrops] Cannot read loot table {} for JEI: {}", tableId, e.toString());
            return;
        }
        List<JsonElement> tableFunctions = LootJson.array(json, "functions");
        for (JsonObject pool : LootJson.objects(json, "pools")) {
            addPool(pool, tableFunctions, factor, depth, output);
        }
    }

    /**
     * Adds the drops of one pool.
     *
     * @param pool           Serialized pool. Never null.
     * @param tableFunctions Functions of the whole table, applied after the pool's. Never null.
     * @param factor         Chance that the table is rolled at all.
     * @param depth          Number of references followed to get here.
     * @param output         Receives the drops. Never null.
     */
    private void addPool(JsonObject pool, List<JsonElement> tableFunctions, double factor, int depth, Output output) {
        LootNumberRange rolls = LootNumberRange.of(pool.get("rolls"), 1);
        LootConditionSummary poolConditions = LootConditionSummary.of(LootJson.array(pool, "conditions"));
        List<JsonElement> outerFunctions = new ArrayList<>(LootJson.array(pool, "functions"));
        outerFunctions.addAll(tableFunctions);

        List<Choice> choices = new ArrayList<>();
        for (JsonObject entry : LootJson.objects(pool, "entries")) flatten(entry, List.of(), choices);
        double totalWeight = 0;
        for (Choice choice : choices) totalWeight += choice.weight();
        if (totalWeight <= 0) return;

        for (Choice choice : choices) {
            for (Leaf leaf : choice.leaves()) {
                double pick = leaf.weight() / (totalWeight - choice.weight() + leaf.weight());
                LootConditionSummary conditions = poolConditions.and(LootConditionSummary.of(leaf.conditions()));
                double chance = factor * rolls.chanceOfAtLeastOne(pick * conditions.factor());
                if (leaf.tableId() != null) {
                    addTable(leaf.tableId(), chance, depth + 1, output);
                    continue;
                }
                List<JsonElement> functions = new ArrayList<>(leaf.functions());
                functions.addAll(outerFunctions);
                LootCountSummary count = LootCountSummary.of(functions);
                EnchantBonuses bonus = bonusesOf(conditions, count, factor * pick);
                DropCount range = new DropCount(count.count().min(), count.count().max() * Math.max(1, rolls.maxInt()));
                ConditionDescriber.Description described = conditionDescriber.describeJson(conditions.others());
                for (Item item : leaf.items()) {
                    output.drops().add(new SyncedDrop(new ItemStack(item), Math.min(1.0, chance), range, DropBehavior.UNIFORM,
                            bonus, SyncedDrop.UNTRACKED, described.conditions(), described.hasHidden(), output.source(),
                            TriggerSettings.DEATH));
                }
            }
        }
    }

    /**
     * Builds the enchantment bonuses of one entry from its random chances and count functions, one entry per
     * registered enchantment they scale with.
     *
     * @param conditions  Summary of the entry's and pool's conditions. Never null.
     * @param count       Summary of the entry's functions. Never null.
     * @param chanceScale Factor of the chance bonus: the chance the table is rolled times the entry's pick chance.
     * @return The bonuses, chance bonuses first.
     */
    private static EnchantBonuses bonusesOf(LootConditionSummary conditions, LootCountSummary count, double chanceScale) {
        Set<ResourceLocation> ids = new LinkedHashSet<>(conditions.bonuses().keySet());
        ids.addAll(count.bonuses().keySet());
        List<EnchantBonusEntry> entries = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
            if (enchantment == null) continue;
            EnchantBonus.ChanceBonus chance = conditions.bonuses().get(id);
            EnchantBonus.ChanceBonus scaledChance = chance == null ? EnchantBonus.ChanceBonus.NONE
                    : new EnchantBonus.ChanceBonus(chance.bonusPerLevel() * chanceScale, chance.maxBonus() * chanceScale);
            entries.add(new EnchantBonusEntry(enchantment, new EnchantBonus(scaledChance, EnchantBonus.RollBonus.NONE,
                    count.bonuses().getOrDefault(id, EnchantBonus.CountBonus.NONE))));
        }
        return entries.isEmpty() ? EnchantBonuses.NONE : new EnchantBonuses(entries);
    }

    /**
     * Turns one entry into the choices of a pool's weighted pick. A {@code group} or {@code sequence} adds each child
     * as its own choice; an {@code alternatives} entry is one choice that resolves to one of its children; an expanded
     * tag is one choice per item.
     *
     * @param entry     Serialized entry. Never null.
     * @param inherited Conditions of the composite entries around it. Never null.
     * @param choices   Receives the choices. Never null.
     */
    private void flatten(JsonObject entry, List<JsonElement> inherited, List<Choice> choices) {
        ResourceLocation type = LootJson.id(entry, "type");
        List<JsonElement> conditions = new ArrayList<>(inherited);
        conditions.addAll(LootJson.array(entry, "conditions"));
        if (ALTERNATIVES.equals(type)) {
            List<Choice> children = new ArrayList<>();
            for (JsonObject child : LootJson.objects(entry, "children")) flatten(child, conditions, children);
            List<Leaf> leaves = new ArrayList<>();
            double weight = 0;
            for (Choice child : children) {
                leaves.addAll(child.leaves());
                weight = Math.max(weight, child.weight());
            }
            if (!leaves.isEmpty()) choices.add(new Choice(weight, leaves));
            return;
        }
        if (GROUP.equals(type) || SEQUENCE.equals(type)) {
            for (JsonObject child : LootJson.objects(entry, "children")) flatten(child, conditions, choices);
            return;
        }

        double weight = LootJson.number(entry, "weight", 1);
        List<JsonElement> functions = LootJson.array(entry, "functions");
        ResourceLocation name = LootJson.id(entry, "name");
        if (TAG.equals(type) && name != null) {
            List<Item> items = itemsOfTag(name);
            if (LootJson.bool(entry, "expand", false)) {
                for (Item item : items) choices.add(Choice.of(new Leaf(weight, List.of(item), null, conditions, functions)));
            } else {
                choices.add(Choice.of(new Leaf(weight, items, null, conditions, functions)));
            }
        } else if (ITEM.equals(type) && name != null) {
            Item item = BuiltInRegistries.ITEM.get(name);
            List<Item> items = item == Items.AIR ? List.of() : List.of(item);
            choices.add(Choice.of(new Leaf(weight, items, null, conditions, functions)));
        } else if (LOOT_TABLE.equals(type) && name != null) {
            choices.add(Choice.of(new Leaf(weight, List.of(), name, conditions, functions)));
        } else {
            choices.add(Choice.of(new Leaf(weight, List.of(), null, conditions, functions)));
        }
    }

    /**
     * Returns the items of a tag.
     *
     * @param tagId Item tag id. Never null.
     * @return The items, in tag order.
     */
    private static List<Item> itemsOfTag(ResourceLocation tagId) {
        List<Item> items = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tagId))) {
            items.add(holder.value());
        }
        return items;
    }

    /**
     * Represents where the drops of one analysis go.
     *
     * @param source Source given to every drop: the analyzed table, also for drops of referenced tables. Never null.
     * @param drops  Receives the drops. Never null.
     */
    private record Output(SyncedDropSource source, List<SyncedDrop> drops) {
    }

    /**
     * Represents one choice of a pool's weighted pick.
     *
     * @param weight Weight of the choice.
     * @param leaves What the choice can give: one leaf, or the children of an {@code alternatives} entry. Never null.
     */
    private record Choice(double weight, List<Leaf> leaves) {
        /**
         * Creates the choice of a single leaf.
         *
         * @param leaf The leaf. Never null.
         * @return The choice, with the leaf's weight.
         */
        static Choice of(Leaf leaf) {
            return new Choice(leaf.weight(), List.of(leaf));
        }
    }

    /**
     * Represents an entry that gives items, rolls another table or gives nothing.
     *
     * @param weight     Weight of the entry.
     * @param items      Items it gives together (several for a tag that is not expanded). Never null.
     * @param tableId    Referenced loot table, or null.
     * @param conditions Its conditions, with those of the composite entries around it. Never null.
     * @param functions  Its own functions. Never null.
     */
    private record Leaf(double weight, List<Item> items, ResourceLocation tableId, List<JsonElement> conditions,
                        List<JsonElement> functions) {
    }
}
