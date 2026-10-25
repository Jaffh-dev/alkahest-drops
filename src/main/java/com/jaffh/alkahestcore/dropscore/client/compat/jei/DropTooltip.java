package com.jaffh.alkahestcore.dropscore.client.compat.jei;

import com.jaffh.alkahestcore.dropscore.common.drops.DropBehavior;
import com.jaffh.alkahestcore.dropscore.common.drops.DropCount;
import com.jaffh.alkahestcore.dropscore.common.drops.DropNumbers;
import com.jaffh.alkahestcore.dropscore.common.drops.DropTracking;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonus;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedCondition;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedDrop;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.Locale;

/**
 * Builds the tooltip of one drop. The short view says what the drop is: the chance bar, the count with its behavior,
 * the rolls, whether it has a Looting/Fortune bonus or conditions, and its per-player settings. Holding Shift replaces
 * it with the details: how the behavior works, the bonus per level and every condition. A removal file that may
 * remove the drop shows as a condition ("Not sneaking"). Both views end with the mod or datapack that added the drop.
 */
final class DropTooltip {
    private static final ItemStack CONDITIONS_ICON = new ItemStack(Items.BARRIER);
    /** Pity: the drop comes with time. */
    private static final ItemStack PITY_ICON = new ItemStack(Items.CLOCK);
    /** One-time drop: there is only one dragon egg (drawn on a challenge frame, it is too dark for the tooltip). */
    private static final ItemStack FIRST_DROP_ICON = new ItemStack(Items.DRAGON_EGG);
    private static final String SKULL_OWNER_TAG = "SkullOwner";
    private static final String INDENT = "  ";

    /**
     * Prevents instantiation of this utility class.
     */
    private DropTooltip() {
    }

    /**
     * Adds the lines of a drop, under the item's own tooltip (experience drops have no item tooltip).
     *
     * @param tooltip    Tooltip being built. Never null.
     * @param drop       Drop to explain. Never null.
     * @param format     Wording of the drop's module. Never null.
     * @param isDetailed Whether Shift is held, which shows the details instead of the summary.
     */
    static void addTo(ITooltipBuilder tooltip, SyncedDrop drop, DropDisplayFormat format, boolean isDetailed) {
        if (drop.isExperience()) tooltip.add(DropDisplayFormat.text("experience").withStyle(ChatFormatting.GREEN));
        if (isDetailed) {
            addDetails(tooltip, drop, format);
        } else {
            addSummary(tooltip, drop, format);
        }
        tooltip.add(DropDisplayFormat.text("added_by", modNameOf(drop.source().id().getNamespace())).withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Adds the short view.
     *
     * @param tooltip Tooltip being built. Never null.
     * @param drop    Drop. Never null.
     * @param format  Wording of the drop's module. Never null.
     */
    private static void addSummary(ITooltipBuilder tooltip, SyncedDrop drop, DropDisplayFormat format) {
        DropTracking tracking = drop.tracking();
        if (!tracking.isFirstDrop()) tooltip.add(new ChanceBar(drop.chance()));
        DropBehavior behavior = drop.behavior();
        tooltip.add(DropDisplayFormat.text(drop.isExperience() ? "amount" : "count",
                        Component.literal(DropNumbers.range(drop.count())).withStyle(ChatFormatting.WHITE), behaviorName(behavior))
                .withStyle(ChatFormatting.GRAY));
        if (behavior.type().needsRollSettings()) {
            tooltip.add(DropDisplayFormat.text("rolls", Component.literal(String.valueOf(behavior.rolls())).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (hasBonus(drop)) {
            tooltip.add(new IconLine(format.bonusIcon(),
                    DropDisplayFormat.text("has_bonus", format.bonusEnchantmentName()).withStyle(ChatFormatting.AQUA)));
        }
        if (hasConditions(drop)) {
            tooltip.add(new IconLine(CONDITIONS_ICON, DropDisplayFormat.text("has_conditions").withStyle(ChatFormatting.DARK_RED)));
        }
        if (tracking.hasPity() && !tracking.isFirstDrop()) {
            tooltip.add(new IconLine(PITY_ICON, format.moduleText("pity", tracking.pity()).withStyle(ChatFormatting.GREEN)));
        }
        if (tracking.isFirstDrop()) {
            tooltip.add(new IconLine(FIRST_DROP_ICON, format.moduleText("first_drop").withStyle(ChatFormatting.LIGHT_PURPLE), true));
        }
        if (tracking.isPerPlayer()) {
            tooltip.add(new IconLine(viewerHead(), DropDisplayFormat.text("per_player").withStyle(ChatFormatting.YELLOW)));
        }
        tooltip.add(DropDisplayFormat.text("hint.details").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /**
     * Adds the details shown while Shift is held.
     *
     * @param tooltip Tooltip being built. Never null.
     * @param drop    Drop. Never null.
     * @param format  Wording of the drop's module. Never null.
     */
    private static void addDetails(ITooltipBuilder tooltip, SyncedDrop drop, DropDisplayFormat format) {
        tooltip.add(behaviorDescription(drop.behavior(), drop.count()).withStyle(ChatFormatting.GRAY));
        if (hasBonus(drop)) {
            tooltip.add(new IconLine(format.bonusIcon(),
                    DropDisplayFormat.text("bonus_header", format.bonusEnchantmentName()).withStyle(ChatFormatting.AQUA)));
            addBonusLines(tooltip, drop);
        }
        if (hasConditions(drop)) {
            tooltip.add(new IconLine(CONDITIONS_ICON, DropDisplayFormat.text("conditions_header").withStyle(ChatFormatting.DARK_RED)));
            if (drop.isExperience()) tooltip.add(conditionLine(format.moduleText("experience_condition")));
            for (SyncedCondition condition : drop.conditions()) tooltip.add(conditionLine(ConditionText.of(condition)));
            if (drop.hasHiddenConditions()) {
                tooltip.add(conditionLine(DropDisplayFormat.text("conditions_hidden").withStyle(ChatFormatting.ITALIC)));
            }
        }
    }

    /**
     * Adds the per-level bonus lines that apply: chance, count (its min), and for roll-based drops rolls, roll chance
     * and increment. Caps are not shown (the header says values may be capped).
     *
     * @param tooltip Tooltip being built. Never null.
     * @param drop    Drop with a bonus. Never null.
     */
    private static void addBonusLines(ITooltipBuilder tooltip, SyncedDrop drop) {
        EnchantBonus bonus = drop.enchantBonus();
        if (hasChanceBonus(drop)) tooltip.add(bonusLine("bonus.chance", DropNumbers.percent(bonus.chance().bonusPerLevel())));
        EnchantBonus.CountBonus count = bonus.count();
        if (count.bonusMinPerLevel() > 0 || count.bonusMaxPerLevel() > 0) {
            tooltip.add(countBonusLine(count));
        }
        if (!drop.behavior().type().needsRollSettings()) return;
        EnchantBonus.RollBonus roll = bonus.roll();
        if (roll.bonusRollsPerLevel() > 0) tooltip.add(bonusLine("bonus.rolls", String.valueOf(roll.bonusRollsPerLevel())));
        if (roll.bonusRollChancePerLevel() > 0) {
            tooltip.add(bonusLine("bonus.roll_chance", DropNumbers.percent(roll.bonusRollChancePerLevel())));
        }
        if (roll.bonusIncrementPerLevel() > 0) {
            tooltip.add(bonusLine("bonus.increment", String.valueOf(roll.bonusIncrementPerLevel())));
        }
    }

    /**
     * Builds the count bonus line from its min. A bonus with a min of 0, such as vanilla's {@code looting_enchant}
     * (0 to 1 more per level), shows its max as "up to +1" instead, since "+0" would read as no bonus.
     *
     * @param count Count bonus, raising the min or the max. Never null.
     * @return The line.
     */
    private static Component countBonusLine(EnchantBonus.CountBonus count) {
        if (count.bonusMinPerLevel() > 0) return bonusLine("bonus.count", String.valueOf(count.bonusMinPerLevel()));
        return bonusLine("bonus.count", DropDisplayFormat.text("bonus.up_to", "+" + count.bonusMaxPerLevel()));
    }

    /**
     * Builds one indented bonus line, such as "Chance: +10% per level": the label in aqua, the value in white, the
     * rest in gray.
     *
     * @param key   Translation key of the label, such as {@code bonus.chance} ("Chance:"). Never null.
     * @param value Value added per level, without its sign. Never null.
     * @return The line.
     */
    private static Component bonusLine(String key, String value) {
        return bonusLine(key, Component.literal("+" + value));
    }

    /**
     * Builds one indented bonus line from a value text, such as "up to +1".
     *
     * @param key   Translation key of the label. Never null.
     * @param value Value added per level, with its sign. Never null.
     * @return The line.
     */
    private static Component bonusLine(String key, MutableComponent value) {
        MutableComponent perLevel = DropDisplayFormat.text("bonus.per_level", value.withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY);
        return Component.literal(INDENT)
                .append(DropDisplayFormat.text(key).withStyle(ChatFormatting.AQUA))
                .append(" ")
                .append(perLevel);
    }

    /**
     * Builds one indented condition line, in red.
     *
     * @param condition Condition text. Never null.
     * @return The line.
     */
    private static Component conditionLine(MutableComponent condition) {
        return Component.literal(INDENT).append(condition).withStyle(ChatFormatting.RED);
    }

    /**
     * Returns the short name of a behavior, shown after the count.
     *
     * @param behavior Behavior. Never null.
     * @return Such as "Uniform", "Fixed rolls" or "Max health - Linear".
     */
    private static Component behaviorName(DropBehavior behavior) {
        MutableComponent name = DropDisplayFormat.text("behavior_name." + lower(behavior.type()));
        if (!behavior.type().isScaled()) return name;
        return name.append(" - ").append(DropDisplayFormat.text("function." + lower(behavior.scaleFormula().function())));
    }

    /**
     * Describes how a behavior rolls the count.
     *
     * @param behavior Behavior. Never null.
     * @param count    Count range of the drop. Never null.
     * @return Such as "Fixed rolls: starts at 0 and rolls 6 times at 50%, each success adds 1 (at most 6)" or
     *         "Hardness: count follows linear growth based on block hardness, kept between 0 and 64".
     */
    private static MutableComponent behaviorDescription(DropBehavior behavior, DropCount count) {
        String key = "behavior_desc." + lower(behavior.type());
        return switch (behavior.type()) {
            case UNIFORM -> DropDisplayFormat.text(key, count.min(), count.max());
            case FIXED_ROLLS, CHAINED_ROLLS -> DropDisplayFormat.text(key, count.min(), behavior.rolls(),
                    DropNumbers.percent(behavior.chancePerRoll()), behavior.incrementValue(), count.max());
            case MAX_HEALTH, HARDNESS -> DropDisplayFormat.text(key,
                    DropDisplayFormat.text("growth." + lower(behavior.scaleFormula().function())), count.min(), count.max());
        };
    }

    /**
     * Tells whether the bonus enchantment changes anything about a drop.
     *
     * @param drop Drop. Never null.
     * @return True when its chance, count or (for roll-based drops) rolls grow with the enchantment level.
     */
    private static boolean hasBonus(SyncedDrop drop) {
        EnchantBonus bonus = drop.enchantBonus();
        EnchantBonus.CountBonus count = bonus.count();
        EnchantBonus.RollBonus roll = bonus.roll();
        boolean hasRollBonus = roll.bonusRollsPerLevel() > 0 || roll.bonusRollChancePerLevel() > 0 || roll.bonusIncrementPerLevel() > 0;
        return hasChanceBonus(drop) || count.bonusMinPerLevel() > 0 || count.bonusMaxPerLevel() > 0
                || (hasRollBonus && drop.behavior().type().needsRollSettings());
    }

    /**
     * Tells whether the bonus enchantment raises a drop's chance; one-time drops ignore their chance.
     *
     * @param drop Drop. Never null.
     * @return True when the chance grows with the enchantment level.
     */
    private static boolean hasChanceBonus(SyncedDrop drop) {
        return drop.enchantBonus().chance().bonusPerLevel() > 0 && !drop.tracking().isFirstDrop();
    }

    /**
     * Tells whether a drop needs anything beyond its chance. Experience always does: vanilla only drops it for a
     * player kill or a player break with the right tool.
     *
     * @param drop Drop. Never null.
     * @return True when the drop has conditions, shown or hidden.
     */
    private static boolean hasConditions(SyncedDrop drop) {
        return drop.isExperience() || drop.hasConditions();
    }

    /**
     * Returns the icon of per-player drops: a player head with the viewer's own skin, so it reads as "your own drop".
     *
     * @return A new head of the local player, or a plain player head when no player is loaded.
     */
    private static ItemStack viewerHead() {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            head.getOrCreateTag().put(SKULL_OWNER_TAG, NbtUtils.writeGameProfile(new CompoundTag(), player.getGameProfile()));
        }
        return head;
    }

    /**
     * Returns the display name of the mod that owns a namespace.
     *
     * @param namespace Namespace of a loot table, drop file or target. Never null.
     * @return The mod's name, such as "Minecraft", or the namespace itself for a datapack.
     */
    private static String modNameOf(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo())
                .map(IModInfo::getDisplayName)
                .orElse(namespace);
    }

    /**
     * Returns the lowercase name of an enum constant, as used in translation keys.
     *
     * @param value Enum constant. Never null.
     * @return Such as {@code "fixed_rolls"}.
     */
    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
