package com.jaffh.alkahestdrops.server.sync;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jaffh.alkahestdrops.common.drops.SyncedCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Turns loot conditions into the {@link SyncedCondition}s clients can describe: killed by a player (or a real
 * player), sneaking, burning, and the tool ({@code match_tool} items, tag or enchantments), also inside
 * {@code inverted} and {@code all_of}. Any other condition, or one with settings beyond these, is counted as hidden.
 * Conditions are read from their JSON (vanilla's serializer), so modded condition types are handled the same way.
 */
final class ConditionDescriber {
    private static final Gson GSON = Deserializers.createConditionSerializer().create();
    private static final ResourceLocation KILLED_BY_PLAYER = new ResourceLocation("killed_by_player");
    private static final ResourceLocation ENTITY_PROPERTIES = new ResourceLocation("entity_properties");
    private static final ResourceLocation MATCH_TOOL = new ResourceLocation("match_tool");
    private static final ResourceLocation INVERTED = new ResourceLocation("inverted");
    private static final ResourceLocation ALL_OF = new ResourceLocation("all_of");
    private static final String KILLED_BY_REAL_PLAYER = "killed_by_real_player";
    private static final String BROKEN_BY_REAL_PLAYER = "broken_by_real_player";
    private static final String THIS = "this";
    /** Entities of a mob's loot context that are the killer. */
    private static final Set<String> KILLERS = Set.of("killer", "killer_player", "direct_killer");

    private final boolean isBlock;

    /**
     * Creates a describer for one module.
     *
     * @param isBlock Whether the conditions belong to block drops, where {@code this} is the breaking player (for mobs
     *                it is the dying mob).
     */
    ConditionDescriber(boolean isBlock) {
        this.isBlock = isBlock;
    }

    /**
     * Describes loot condition objects.
     *
     * @param conditions Conditions that must all pass. Never null.
     * @return What clients can show of them.
     */
    Description describe(List<LootItemCondition> conditions) {
        List<JsonElement> json = new ArrayList<>(conditions.size());
        boolean hasHidden = false;
        for (LootItemCondition condition : conditions) {
            try {
                json.add(GSON.toJsonTree(condition, LootItemCondition.class));
            } catch (RuntimeException e) {
                hasHidden = true;
            }
        }
        Description described = describeJson(json);
        return hasHidden ? described.withHidden() : described;
    }

    /**
     * Describes serialized loot conditions.
     *
     * @param conditions Serialized conditions that must all pass. Never null.
     * @return What clients can show of them.
     */
    Description describeJson(List<JsonElement> conditions) {
        List<SyncedCondition> described = new ArrayList<>();
        boolean hasHidden = false;
        for (JsonElement condition : conditions) {
            if (!condition.isJsonObject() || !add(condition.getAsJsonObject(), false, described)) hasHidden = true;
        }
        return new Description(described, hasHidden);
    }

    /**
     * Adds the description of one condition.
     *
     * @param condition Serialized condition. Never null.
     * @param isNegated Whether the condition is inside an {@code inverted}.
     * @param out       Receives the described conditions. Never null.
     * @return False when the condition, or part of it, cannot be described.
     */
    private boolean add(JsonObject condition, boolean isNegated, List<SyncedCondition> out) {
        ResourceLocation type = LootJson.id(condition, "condition");
        if (type == null) return false;
        if (type.equals(INVERTED)) {
            JsonElement term = condition.get("term");
            if (term == null || !term.isJsonObject()) return false;
            List<SyncedCondition> inner = new ArrayList<>();
            if (!add(term.getAsJsonObject(), !isNegated, inner) || inner.size() != 1) return false;
            out.addAll(inner);
            return true;
        }
        if (type.equals(ALL_OF) && !isNegated) {
            boolean isComplete = true;
            for (JsonObject term : LootJson.objects(condition, "terms")) isComplete &= add(term, false, out);
            return isComplete;
        }
        if (type.equals(KILLED_BY_PLAYER)) return addOf(out, SyncedCondition.Kind.KILLED_BY_PLAYER, isNegated);
        if (type.getPath().equals(KILLED_BY_REAL_PLAYER))
            return addOf(out, SyncedCondition.Kind.KILLED_BY_REAL_PLAYER, isNegated);
        if (type.getPath().equals(BROKEN_BY_REAL_PLAYER))
            return addOf(out, SyncedCondition.Kind.BROKEN_BY_REAL_PLAYER, isNegated);
        if (type.equals(ENTITY_PROPERTIES)) return addEntityFlags(condition, isNegated, out);
        if (type.equals(MATCH_TOOL)) return addTool(condition, isNegated, out);
        return false;
    }

    /**
     * Describes an {@code entity_properties} condition that only checks the sneaking flag of the player or the burning
     * flag of the dying mob.
     *
     * @param condition Serialized condition. Never null.
     * @param isNegated Whether it is inside an {@code inverted}.
     * @param out       Receives the described condition. Never null.
     * @return False when it checks anything else.
     */
    private boolean addEntityFlags(JsonObject condition, boolean isNegated, List<SyncedCondition> out) {
        String entity = LootJson.string(condition, "entity", "");
        JsonObject predicate = objectOf(condition, "predicate");
        if (predicate == null || predicate.size() != 1) return false;
        JsonObject flags = objectOf(predicate, "flags");
        if (flags == null || flags.size() != 1) return false;
        boolean isPlayer = isBlock ? entity.equals(THIS) : KILLERS.contains(entity);
        if (isPlayer && flags.has("is_sneaking")) {
            return addOf(out, SyncedCondition.Kind.SNEAKING, isNegated != !LootJson.bool(flags, "is_sneaking", true));
        }
        if (!isBlock && entity.equals(THIS) && flags.has("is_on_fire")) {
            return addOf(out, SyncedCondition.Kind.BURNING, isNegated != !LootJson.bool(flags, "is_on_fire", true));
        }
        return false;
    }

    /**
     * Describes a {@code match_tool} condition that only checks the tool's items, tag or enchantments.
     *
     * @param condition Serialized condition. Never null.
     * @param isNegated Whether it is inside an {@code inverted}.
     * @param out       Receives the described condition. Never null.
     * @return False when it checks anything else.
     */
    private static boolean addTool(JsonObject condition, boolean isNegated, List<SyncedCondition> out) {
        JsonObject predicate = objectOf(condition, "predicate");
        if (predicate == null || predicate.size() != 1) return false;
        Map.Entry<String, JsonElement> check = predicate.entrySet().iterator().next();
        List<ResourceLocation> ids = new ArrayList<>();
        SyncedCondition.Kind kind;
        switch (check.getKey()) {
            case "items" -> {
                kind = SyncedCondition.Kind.TOOL_ITEMS;
                for (JsonElement item : LootJson.array(predicate, "items")) {
                    ResourceLocation id = item.isJsonPrimitive() ? ResourceLocation.tryParse(item.getAsString()) : null;
                    if (id == null) return false;
                    ids.add(id);
                }
            }
            case "tag" -> {
                kind = SyncedCondition.Kind.TOOL_TAG;
                ResourceLocation id = LootJson.id(predicate, "tag");
                if (id == null) return false;
                ids.add(id);
            }
            case "enchantments" -> {
                kind = SyncedCondition.Kind.TOOL_ENCHANTMENTS;
                for (JsonObject enchantment : LootJson.objects(predicate, "enchantments")) {
                    ResourceLocation id = LootJson.id(enchantment, "enchantment");
                    if (id == null) return false;
                    ids.add(id);
                }
            }
            default -> {
                return false;
            }
        }
        if (ids.isEmpty()) return false;
        out.add(new SyncedCondition(kind, isNegated, ids));
        return true;
    }

    /**
     * Adds a condition without ids.
     *
     * @param out       Receives the condition. Never null.
     * @param kind      Kind of condition. Never null.
     * @param isNegated Whether the drop needs it to fail.
     * @return Always true.
     */
    private static boolean addOf(List<SyncedCondition> out, SyncedCondition.Kind kind, boolean isNegated) {
        out.add(SyncedCondition.of(kind, isNegated));
        return true;
    }

    /**
     * Returns a child object.
     *
     * @param object Object to read. Never null.
     * @param key    Key of the child. Never null.
     * @return The child, or null when it is missing or not an object.
     */
    private static JsonObject objectOf(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    /**
     * Represents what clients can show of a set of conditions.
     *
     * @param conditions Conditions that could be described. Immutable, never null.
     * @param hasHidden  Whether some conditions could not be described.
     */
    record Description(List<SyncedCondition> conditions, boolean hasHidden) {
        /** No conditions. */
        static final Description NONE = new Description(List.of(), false);

        /**
         * Creates a description and stores an immutable copy of its conditions.
         *
         * @param conditions Conditions that could be described. Never null.
         * @param hasHidden  Whether some conditions could not be described.
         */
        public Description {
            conditions = List.copyOf(conditions);
        }

        /**
         * Returns this description, marked as having hidden conditions.
         *
         * @return A copy with {@code hasHidden} set.
         */
        Description withHidden() {
            return new Description(conditions, true);
        }

        /**
         * Turns the conditions of a removal file into the conditions the drop needs to survive it: the removal applies
         * when all its conditions pass, so the drop needs at least one to fail. A single described condition is
         * negated exactly ("Not sneaking"); anything more cannot be shown as a list of conditions, so it is hidden.
         *
         * @return The conditions the drop needs.
         */
        Description asRemoval() {
            if (conditions.size() == 1 && !hasHidden)
                return new Description(List.of(conditions.get(0).negate()), false);
            return new Description(List.of(), true);
        }
    }
}
