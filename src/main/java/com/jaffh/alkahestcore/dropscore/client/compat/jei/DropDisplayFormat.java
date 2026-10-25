package com.jaffh.alkahestcore.dropscore.client.compat.jei;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Defines the wording that differs between the mob and block drop categories: the bonus enchantment and the texts
 * that name kills or breaks.
 */
enum DropDisplayFormat {
    /** Mob drops: Looting, kills. */
    MOB("mob", Enchantments.MOB_LOOTING, Items.DIAMOND_SWORD),
    /** Block drops: Fortune, breaks. */
    BLOCK("block", Enchantments.BLOCK_FORTUNE, Items.DIAMOND_PICKAXE);

    private static final String KEY_PREFIX = "jei.alkahestcore.dropscore.";

    private final String moduleName;
    private final Enchantment bonusEnchantment;
    private final Item bonusTool;

    /**
     * Creates a format.
     *
     * @param moduleName       Suffix of the module's translation keys. Never null.
     * @param bonusEnchantment Enchantment of the {@code on_looting} / {@code on_fortune} bonus. Never null.
     * @param bonusTool        Tool drawn, enchanted, as the icon of the bonus. Never null.
     */
    DropDisplayFormat(String moduleName, Enchantment bonusEnchantment, Item bonusTool) {
        this.moduleName = moduleName;
        this.bonusEnchantment = bonusEnchantment;
        this.bonusTool = bonusTool;
    }

    /**
     * Creates a text shared by both modules.
     *
     * @param key  Key after {@code jei.alkahestcore.dropscore.}. Never null.
     * @param args Arguments of the text.
     * @return The translatable text.
     */
    static MutableComponent text(String key, Object... args) {
        return Component.translatable(KEY_PREFIX + key, args);
    }

    /**
     * Creates a text of this module, whose key ends with {@code .mob} or {@code .block}.
     *
     * @param key  Key after {@code jei.alkahestcore.dropscore.}, without the module suffix. Never null.
     * @param args Arguments of the text.
     * @return The translatable text.
     */
    MutableComponent moduleText(String key, Object... args) {
        return Component.translatable(KEY_PREFIX + key + "." + moduleName, args);
    }

    /**
     * Returns the icon of the bonus enchantment: a diamond tool enchanted with it, so it shows the enchantment glint.
     *
     * @return A new stack: an enchanted diamond sword for Looting, an enchanted diamond pickaxe for Fortune.
     */
    ItemStack bonusIcon() {
        ItemStack icon = new ItemStack(bonusTool);
        icon.enchant(bonusEnchantment, 1);
        return icon;
    }

    /**
     * Returns the name of the bonus enchantment.
     *
     * @return The translated name, such as "Looting".
     */
    MutableComponent bonusEnchantmentName() {
        return Component.translatable(bonusEnchantment.getDescriptionId());
    }
}
