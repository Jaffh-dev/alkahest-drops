package com.jaffh.alkahestdrops.client.compat.jei;

import com.jaffh.alkahestdrops.common.drops.DropBehavior;
import com.jaffh.alkahestdrops.common.drops.DropCount;
import com.jaffh.alkahestdrops.common.drops.DropNumbers;
import com.jaffh.alkahestdrops.common.drops.DropTracking;
import com.jaffh.alkahestdrops.common.drops.EnchantBonus;
import com.jaffh.alkahestdrops.common.drops.EnchantBonusEntry;
import com.jaffh.alkahestdrops.common.drops.SyncedCondition;
import com.jaffh.alkahestdrops.common.drops.SyncedDrop;
import com.jaffh.alkahestdrops.common.drops.TriggerRules;
import com.jaffh.alkahestdrops.common.drops.TriggerSettings;
import java.util.Locale;
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
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

/**
 * Builds the tooltip of one drop. The short view says what the drop is: the chance bar, the count with its behavior,
 * the rolls, whether it has an enchantment bonus or conditions, and its per-player settings. Holding Shift replaces it
 * with the details: how the behavior works, the bonus per level of each enchantment and every condition. A removal file
 * that may remove the drop shows as a condition ("Not sneaking"). Both views end with the mod or datapack that added
 * the drop.
 */
final class DropTooltip {
    private static final ItemStack CONDITIONS_ICON = new ItemStack(Items.BARRIER);
    /** Enchantment bonus: an enchanted book, which shows the glint. */
    private static final ItemStack BONUS_ICON = new ItemStack(Items.ENCHANTED_BOOK);
    /** Pity: the drop comes with time. */
    private static final ItemStack PITY_ICON = new ItemStack(Items.CLOCK);
    /** One-time drop: there is only one dragon egg (drawn on a challenge frame, it is too dark for the tooltip). */
    private static final ItemStack FIRST_DROP_ICON = new ItemStack(Items.DRAGON_EGG);

    private static final String SKULL_OWNER_TAG = "SkullOwner";
    private static final String INDENT = "  ";
    private static final String BONUS_LINE_INDENT = INDENT + INDENT;

    /**
     * Prevents instantiation of this utility class.
     */
    private DropTooltip() {}

    /**
     * Adds the lines of a drop, under the item's own tooltip (experience drops have no item tooltip).
     *
     * @param tooltip    Tooltip being built. Never null.
     * @param drop       Drop to explain. Never null.
     * @param format     Wording of the drop's module. Never null.
     * @param isDetailed Whether Shift is held, which shows the details instead of the summary.
     */
    static void addTo(ITooltipBuilder tooltip, SyncedDrop drop, DropDisplayFormat format, boolean isDetailed) {
        if (drop.isExperience())
            tooltip.add(DropDisplayFormat.text("experience").withStyle(ChatFormatting.GREEN));
        if (isDetailed) {
            addDetails(tooltip, drop, format);
        } else {
            addSummary(tooltip, drop, format);
        }
        tooltip.add(
                DropDisplayFormat.text("added_by", modNameOf(drop.source().id().getNamespace()))
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Creates text that names the action of a drop: the module's own wording on death or break ("kills",
     * "breaks"), else the trigger's ("shears", "hits").
     *
     * @param drop   Drop. Never null.
     * @param format Wording of the drop's module. Never null.
     * @param key    Key without the module or trigger suffix, such as {@code pity}. Never null.
     * @param args   Values of the key's placeholders. Never null.
     * @return The text.
     */
    private static MutableComponent actionText(SyncedDrop drop, DropDisplayFormat format, String key, Object... args) {
        if (drop.trigger().isDeath()) return format.moduleText(key, args);
        return DropDisplayFormat.text(key + "." + drop.trigger().trigger().id(), args);
    }

    /**
     * Formats a number of ticks as seconds, or minutes from one minute up.
     *
     * @param ticks Ticks, 1 or more.
     * @return Such as "1.5 s" or "5 min".
     */
    private static MutableComponent durationOf(int ticks) {
        double seconds = ticks / 20.0;
        if (seconds < 60) return DropDisplayFormat.text("time.seconds", DropNumbers.number(seconds));
        return DropDisplayFormat.text("time.minutes", DropNumbers.number(seconds / 60));
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
        tooltip.add(DropDisplayFormat.text(
                        drop.isExperience() ? "amount" : "count",
                        Component.literal(DropNumbers.range(drop.count())).withStyle(ChatFormatting.WHITE),
                        behaviorName(behavior))
                .withStyle(ChatFormatting.GRAY));
        if (behavior.type().needsRollSettings()) {
            tooltip.add(DropDisplayFormat.text(
                            "rolls",
                            Component.literal(String.valueOf(behavior.rolls())).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GRAY));
        }
        TriggerSettings trigger = drop.trigger();
        if (trigger.ticks() > 0) {
            tooltip.add(DropDisplayFormat.text(
                            "ticks." + TriggerRules.of(trigger.trigger()).ticksKey(),
                            durationOf(trigger.ticks()).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (hasBonus(drop)) {
            tooltip.add(
                    new IconLine(BONUS_ICON, DropDisplayFormat.text("has_bonus").withStyle(ChatFormatting.AQUA)));
        }
        if (hasConditions(drop)) {
            tooltip.add(new IconLine(
                    CONDITIONS_ICON, DropDisplayFormat.text("has_conditions").withStyle(ChatFormatting.DARK_RED)));
        }
        if (tracking.hasPity() && !tracking.isFirstDrop()) {
            tooltip.add(new IconLine(
                    PITY_ICON, actionText(drop, format, "pity", tracking.pity()).withStyle(ChatFormatting.GREEN)));
        }
        if (tracking.isFirstDrop()) {
            tooltip.add(new IconLine(
                    FIRST_DROP_ICON,
                    actionText(drop, format, "first_drop").withStyle(ChatFormatting.LIGHT_PURPLE),
                    true));
        }
        if (tracking.isPerPlayer()) {
            tooltip.add(new IconLine(
                    viewerHead(), DropDisplayFormat.text("per_player").withStyle(ChatFormatting.YELLOW)));
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
            tooltip.add(new IconLine(
                    BONUS_ICON, DropDisplayFormat.text("bonus_header").withStyle(ChatFormatting.AQUA)));
            for (EnchantBonusEntry entry : drop.enchantBonuses().entries()) {
                if (!hasBonus(drop, entry.bonus())) continue;
                tooltip.add(Component.literal(INDENT)
                        .append(Component.translatable(entry.enchantment().getDescriptionId()))
                        .withStyle(ChatFormatting.GRAY));
                addBonusLines(tooltip, drop, entry.bonus());
            }
        }
        if (hasConditions(drop)) {
            tooltip.add(new IconLine(
                    CONDITIONS_ICON, DropDisplayFormat.text("conditions_header").withStyle(ChatFormatting.DARK_RED)));
            if (drop.isExperience()) tooltip.add(conditionLine(format.moduleText("experience_condition")));
            for (SyncedCondition condition : drop.conditions()) tooltip.add(conditionLine(ConditionText.of(condition)));
            if (drop.hasHiddenConditions()) {
                tooltip.add(conditionLine(
                        DropDisplayFormat.text("conditions_hidden").withStyle(ChatFormatting.ITALIC)));
            }
        }
    }

    /**
     * Adds the per-level bonus lines of one enchantment that apply: chance, count (its min), and for roll-based drops
     * rolls, roll chance and increment. Caps are not shown (the header says values may be capped).
     *
     * @param tooltip Tooltip being built. Never null.
     * @param drop    Drop with a bonus. Never null.
     * @param bonus   Bonus of one enchantment of the drop. Never null.
     */
    private static void addBonusLines(ITooltipBuilder tooltip, SyncedDrop drop, EnchantBonus bonus) {
        if (hasChanceBonus(drop, bonus))
            tooltip.add(
                    bonusLine("bonus.chance", DropNumbers.percent(bonus.chance().bonusPerLevel())));
        EnchantBonus.CountBonus count = bonus.count();
        if (count.bonusMinPerLevel() > 0 || count.bonusMaxPerLevel() > 0) {
            tooltip.add(countBonusLine(count));
        }
        if (!drop.behavior().type().needsRollSettings()) return;
        EnchantBonus.RollBonus roll = bonus.roll();
        if (roll.bonusRollsPerLevel() > 0)
            tooltip.add(bonusLine("bonus.rolls", String.valueOf(roll.bonusRollsPerLevel())));
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
     * Builds one bonus line, indented below its enchantment, such as "Chance: +10% per level": the label in aqua, the
     * value in white, the rest in gray.
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
        return Component.literal(BONUS_LINE_INDENT)
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
        return name.append(" - ")
                .append(DropDisplayFormat.text(
                        "function." + lower(behavior.scaleFormula().function())));
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
            case FIXED_ROLLS, CHAINED_ROLLS -> DropDisplayFormat.text(
                    key,
                    count.min(),
                    behavior.rolls(),
                    DropNumbers.percent(behavior.chancePerRoll()),
                    behavior.incrementValue(),
                    count.max());
            case MAX_HEALTH, HARDNESS, DAMAGE_TAKEN -> DropDisplayFormat.text(
                    key,
                    DropDisplayFormat.text(
                            "growth." + lower(behavior.scaleFormula().function())),
                    count.min(),
                    count.max());
        };
    }

    /**
     * Tells whether any enchantment changes anything about a drop.
     *
     * @param drop Drop. Never null.
     * @return True when some enchantment bonus shows at least one line.
     */
    private static boolean hasBonus(SyncedDrop drop) {
        return drop.enchantBonuses().entries().stream().anyMatch(entry -> hasBonus(drop, entry.bonus()));
    }

    /**
     * Tells whether one enchantment changes anything about a drop.
     *
     * @param drop  Drop. Never null.
     * @param bonus Bonus of one enchantment of the drop. Never null.
     * @return True when its chance, count or (for roll-based drops) rolls grow with the enchantment level.
     */
    private static boolean hasBonus(SyncedDrop drop, EnchantBonus bonus) {
        EnchantBonus.CountBonus count = bonus.count();
        EnchantBonus.RollBonus roll = bonus.roll();
        boolean hasRollBonus = roll.bonusRollsPerLevel() > 0
                || roll.bonusRollChancePerLevel() > 0
                || roll.bonusIncrementPerLevel() > 0;
        return hasChanceBonus(drop, bonus)
                || count.bonusMinPerLevel() > 0
                || count.bonusMaxPerLevel() > 0
                || (hasRollBonus && drop.behavior().type().needsRollSettings());
    }

    /**
     * Tells whether one enchantment raises a drop's chance; one-time drops ignore their chance.
     *
     * @param drop  Drop. Never null.
     * @param bonus Bonus of one enchantment of the drop. Never null.
     * @return True when the chance grows with the enchantment level.
     */
    private static boolean hasChanceBonus(SyncedDrop drop, EnchantBonus bonus) {
        return bonus.chance().bonusPerLevel() > 0 && !drop.tracking().isFirstDrop();
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
            head.getOrCreateTag()
                    .put(SKULL_OWNER_TAG, NbtUtils.writeGameProfile(new CompoundTag(), player.getGameProfile()));
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
        return ModList.get()
                .getModContainerById(namespace)
                .map(ModContainer::getModInfo)
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
