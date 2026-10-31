package com.jaffh.alkahestdrops.server.command;

import com.jaffh.alkahestdrops.common.drops.TriggerRules;
import com.jaffh.alkahestdrops.common.drops.DropActions;
import com.jaffh.alkahestdrops.common.drops.DropBehaviorType;
import com.jaffh.alkahestdrops.common.drops.DropEntry;
import com.jaffh.alkahestdrops.common.drops.DropNumbers;
import com.jaffh.alkahestdrops.common.drops.DropTracking;
import com.jaffh.alkahestdrops.api.DropTrigger;
import com.jaffh.alkahestdrops.common.drops.TriggerSettings;
import com.jaffh.alkahestdrops.server.drops.DropFileFormat;
import com.jaffh.alkahestdrops.server.drops.DropManager;
import com.jaffh.alkahestdrops.server.drops.ItemTarget;
import com.jaffh.alkahestdrops.server.drops.ParsedDropFile;
import com.jaffh.alkahestdrops.server.drops.RemovalEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Formats the drop and removal lines shared by {@code /alkdrops sources} and {@code /alkdrops drops}.
 */
final class RuleText {

    /**
     * Prevents instantiation of this utility class.
     */
    private RuleText() {
    }

    /**
     * Starts a result line with its sign, module, file id and target.
     *
     * @param sign    {@code "+ "} for drops, {@code "- "} for removals. Never null.
     * @param color   Color of the sign. Never null.
     * @param file    File of the line. Never null.
     * @param manager Module of the file. Never null.
     * @return Such as {@code "+ [mob] ns:zombie → minecraft:zombie"}: the module in gray, the file id in yellow and
     *         the target in white.
     */
    static MutableComponent fileLine(String sign, ChatFormatting color, ParsedDropFile file, DropManager<?> manager) {
        return CommandText.text(sign, color)
                .append(CommandText.kindTag(manager))
                .append(CommandText.fileId(file.fileId(), manager, ChatFormatting.YELLOW))
                .append(CommandText.text(" → " + file.target(), ChatFormatting.WHITE));
    }

    /**
     * Describes the amount, chance and behavior of a drop.
     *
     * @param entry         Drop. Never null.
     * @param format        Format of the drop's file, which names its one-time drops. Never null.
     * @param isConditional Whether the file or the drop has conditions.
     * @return Such as "1–3 (5%)", "3–7 XP (100%), max_health" or
     *         "1 (50%), functions, pity 20, per player, on shear, on drop: glow, conditional", in the player's
     *         language.
     */
    static MutableComponent describeDrop(DropEntry entry, DropFileFormat format, boolean isConditional) {
        MutableComponent text = CommandText.text(DropNumbers.range(entry.count()));
        if (entry.isExperience()) text.append(" ").append(CommandText.lang("rule.xp"));
        text.append(" (" + CommandText.percent(entry.chance()) + ")");
        DropBehaviorType type = entry.behavior().type();
        if (type != DropBehaviorType.UNIFORM) text.append(", " + type.name().toLowerCase(Locale.ROOT));
        if (entry.hasFunctions()) text.append(", ").append(CommandText.lang("rule.functions"));
        DropTracking tracking = entry.tracking();
        if (tracking.hasPity()) text.append(", ").append(CommandText.lang("rule.pity", tracking.pity()));
        if (tracking.isFirstDrop()) text.append(", ").append(CommandText.lang("rule." + format.firstDropKey()));
        if (tracking.isPerPlayer()) text.append(", ").append(CommandText.lang("rule.per_player"));
        if (!entry.trigger().isDeath()) text.append(", ").append(trigger(entry.trigger()));
        if (entry.onDrop().hasActions()) text.append(", ").append(CommandText.lang("rule.on_drop", actionNames(entry.onDrop())));
        if (isConditional) text.append(", ").append(conditional());
        return text;
    }

    /**
     * Lists the actions a drop runs when it spawns.
     *
     * @param actions Actions of the drop, with at least one on. Never null.
     * @return Such as "glow, run_function mypack:rare_drop".
     */
    private static String actionNames(DropActions actions) {
        List<String> names = new ArrayList<>(2);
        if (actions.shouldGlow()) names.add("glow");
        if (actions.runFunction() != null) names.add("run_function " + actions.runFunction());
        return String.join(", ", names);
    }

    /**
     * Names the trigger of a drop with its tick setting.
     *
     * @param settings Trigger settings of the drop. Never null.
     * @return Such as "on shear", "on hit, cooldown 20 ticks" or "periodic, every 6000 ticks".
     */
    static MutableComponent trigger(TriggerSettings settings) {
        MutableComponent text = CommandText.lang("rule.trigger." + settings.trigger().id());
        if (settings.ticks() > 0) {
            text.append(", ").append(CommandText.lang("rule.ticks." + TriggerRules.of(settings.trigger()).ticksKey(), settings.ticks()));
        }
        return text;
    }

    /**
     * Returns the trigger note of a removal line.
     *
     * @param trigger Trigger whose drops the removal file removes. Never null.
     * @return Such as ", on shear", or empty text for death.
     */
    static MutableComponent removalTrigger(DropTrigger trigger) {
        if (trigger == DropTrigger.DEATH) return CommandText.text("");
        return CommandText.text(", ", ChatFormatting.WHITE).append(CommandText.lang("rule.trigger." + trigger.id()));
    }

    /**
     * Returns the note of a drop or removal that has conditions.
     *
     * @return "conditional", in the player's language.
     */
    static MutableComponent conditional() {
        return CommandText.lang("rule.conditional");
    }

    /**
     * Describes how a removal entry names its target.
     *
     * @param entry Removal entry. Never null.
     * @return Such as "experience", "by id", "by #minecraft:logs" or "by *", in the player's language.
     */
    static Component describeRemovalMatch(RemovalEntry entry) {
        ItemTarget target = entry.target();
        if (target == null) return CommandText.lang("rule.match.experience");
        if (target instanceof ItemTarget.Tagged tagged) return CommandText.lang("rule.match.tag", "#" + tagged.tag().location());
        if (target instanceof ItemTarget.Any) return CommandText.lang("rule.match.any");
        return CommandText.lang("rule.match.id");
    }

    /**
     * Names what a removal entry removes.
     *
     * @param entry Removal entry. Never null.
     * @return The item name in bold yellow, "#tag" or "every item" in yellow, or "Experience" in bold green.
     */
    static MutableComponent removedThing(RemovalEntry entry) {
        ItemTarget target = entry.target();
        if (target == null) return CommandText.experienceName();
        if (target instanceof ItemTarget.Exact exact) return CommandText.itemName(exact.item());
        if (target instanceof ItemTarget.Tagged tagged) return CommandText.text("#" + tagged.tag().location(), ChatFormatting.YELLOW);
        return CommandText.lang("rule.every_item").withStyle(ChatFormatting.YELLOW);
    }

    /**
     * Counts the drop lines and removal lines of a result: the numbers in green and red, the words in gray.
     *
     * @param dropCount    Number of drops.
     * @param removalCount Number of removal entries.
     * @return Such as "3 drops, 1 removal", in the player's language.
     */
    static MutableComponent counts(int dropCount, int removalCount) {
        return CommandText.lang(dropCount == 1 ? "rule.drops.one" : "rule.drops.many",
                        CommandText.text(String.valueOf(dropCount), ChatFormatting.GREEN))
                .withStyle(ChatFormatting.GRAY)
                .append(CommandText.text(", ", ChatFormatting.GRAY))
                .append(CommandText.lang(removalCount == 1 ? "rule.removals.one" : "rule.removals.many",
                        CommandText.text(String.valueOf(removalCount), ChatFormatting.RED)).withStyle(ChatFormatting.GRAY));
    }
}
