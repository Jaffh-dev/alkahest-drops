package com.jaffh.alkahestcore.dropscore.server.command;

import com.jaffh.alkahestcore.dropscore.common.drops.DropBehaviorType;
import com.jaffh.alkahestcore.dropscore.common.drops.DropEntry;
import com.jaffh.alkahestcore.dropscore.common.drops.DropTracking;
import com.jaffh.alkahestcore.dropscore.server.drops.DropFileFormat;
import com.jaffh.alkahestcore.dropscore.server.drops.DropManager;
import com.jaffh.alkahestcore.dropscore.server.drops.ItemTarget;
import com.jaffh.alkahestcore.dropscore.server.drops.ParsedDropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.RemovalEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * Formats the drop and removal lines shared by {@code /dropscore sources} and {@code /dropscore drops}.
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
                .append(CommandText.text("[" + manager.kind() + "] ", ChatFormatting.GRAY))
                .append(CommandText.fileId(file.fileId(), manager, ChatFormatting.YELLOW))
                .append(CommandText.text(" → " + file.target(), ChatFormatting.WHITE));
    }

    /**
     * Describes the amount, chance and behavior of a drop.
     *
     * @param entry         Drop. Never null.
     * @param format        Format of the drop's file, which names its one-time drops. Never null.
     * @param isConditional Whether the file or the drop has conditions.
     * @return Such as {@code "1–3 (5%)"}, {@code "3–7 XP (100%), max_health"} or
     *         {@code "1 (50%), functions, pity 20, per player, conditional"}.
     */
    static String describeDrop(DropEntry entry, DropFileFormat format, boolean isConditional) {
        int min = entry.count().min();
        int max = entry.count().max();
        StringBuilder text = new StringBuilder(min == max ? String.valueOf(min) : min + "–" + max);
        if (entry.isExperience()) text.append(" XP");
        text.append(" (").append(CommandText.percent(entry.chance())).append(")");
        DropBehaviorType type = entry.behavior().type();
        if (type != DropBehaviorType.UNIFORM) text.append(", ").append(type.name().toLowerCase(Locale.ROOT));
        if (entry.hasFunctions()) text.append(", functions");
        DropTracking tracking = entry.tracking();
        if (tracking.hasPity()) text.append(", pity ").append(tracking.pity());
        if (tracking.isFirstDrop()) text.append(", ").append(format.firstDropKey().replace('_', ' '));
        if (tracking.isPerPlayer()) text.append(", per player");
        if (isConditional) text.append(", conditional");
        return text.toString();
    }

    /**
     * Describes how a removal entry names its target.
     *
     * @param entry Removal entry. Never null.
     * @return Such as {@code "experience"}, {@code "by id"}, {@code "by #minecraft:logs"} or {@code "by *"}.
     */
    static String describeRemovalMatch(RemovalEntry entry) {
        ItemTarget target = entry.target();
        if (target == null) return "experience";
        if (target instanceof ItemTarget.Tagged tagged) return "by #" + tagged.tag().location();
        if (target instanceof ItemTarget.Any) return "by *";
        return "by id";
    }

    /**
     * Names what a removal entry removes.
     *
     * @param entry Removal entry. Never null.
     * @return The item name in bold yellow, or {@code "#tag"}, {@code "every item"} or {@code "experience"} in yellow.
     */
    static MutableComponent removedThing(RemovalEntry entry) {
        ItemTarget target = entry.target();
        if (target == null) return CommandText.bold("Experience", ChatFormatting.GREEN);
        if (target instanceof ItemTarget.Exact exact) return CommandText.itemName(exact.item());
        if (target instanceof ItemTarget.Tagged tagged) return CommandText.text("#" + tagged.tag().location(), ChatFormatting.YELLOW);
        return CommandText.text("every item", ChatFormatting.YELLOW);
    }
}
