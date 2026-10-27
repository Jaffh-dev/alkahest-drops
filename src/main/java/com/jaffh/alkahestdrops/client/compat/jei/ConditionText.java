package com.jaffh.alkahestdrops.client.compat.jei;

import com.jaffh.alkahestdrops.common.drops.SyncedCondition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Turns synced conditions into readable text, such as "Killed by player", "Not sneaking" or "Requires Silk Touch".
 */
final class ConditionText {
    private static final String LIST_SEPARATOR = ", ";

    /**
     * Prevents instantiation of this utility class.
     */
    private ConditionText() {
    }

    /**
     * Describes a condition.
     *
     * @param condition Condition. Never null.
     * @return The translated text.
     */
    static MutableComponent of(SyncedCondition condition) {
        String key = "condition." + condition.kind().name().toLowerCase(Locale.ROOT) + (condition.isNegated() ? ".not" : "");
        return switch (condition.kind()) {
            case TOOL_ITEMS -> DropDisplayFormat.text(key, join(condition.ids(), id -> BuiltInRegistries.ITEM.get(id).getDescription()));
            case TOOL_TAG -> DropDisplayFormat.text(key, "#" + condition.ids().get(0));
            case TOOL_ENCHANTMENTS -> DropDisplayFormat.text(key, join(condition.ids(), ConditionText::enchantmentName));
            default -> DropDisplayFormat.text(key);
        };
    }

    /**
     * Returns the name of an enchantment.
     *
     * @param id Enchantment id. Never null.
     * @return Its translated name, or the id when it is not registered here.
     */
    private static Component enchantmentName(ResourceLocation id) {
        Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(id);
        return enchantment != null ? Component.translatable(enchantment.getDescriptionId()) : Component.literal(id.toString());
    }

    /**
     * Joins the names of ids into one text.
     *
     * @param ids    Ids, at least one. Never null.
     * @param nameOf Name of one id. Never null.
     * @return Such as "Shears, Diamond Axe".
     */
    private static Component join(List<ResourceLocation> ids, Function<ResourceLocation, Component> nameOf) {
        MutableComponent names = Component.empty();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) names.append(LIST_SEPARATOR);
            names.append(nameOf.apply(ids.get(i)));
        }
        return names;
    }
}
