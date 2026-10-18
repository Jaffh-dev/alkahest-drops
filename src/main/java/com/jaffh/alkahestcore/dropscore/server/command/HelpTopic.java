package com.jaffh.alkahestcore.dropscore.server.command;

import java.util.List;
import java.util.Locale;

import static com.jaffh.alkahestcore.dropscore.server.command.HelpSection.argument;
import static com.jaffh.alkahestcore.dropscore.server.command.HelpSection.example;

/**
 * Defines the {@code /dropscore help} topics, one per subcommand, with their text.
 */
enum HelpTopic {
    /** {@code /dropscore validate}. */
    VALIDATE("Lists the errors and warnings that the last /reload found in the dropscore files.", List.of(
            HelpSection.usage("/dropscore validate [errors | warnings] [namespace] [page <n>]"),
            HelpSection.arguments(
                    argument("errors", "only errors: the file, drop or entry was skipped."),
                    argument("warnings", "only warnings: a value was clamped or ignored, the rest still loads."),
                    argument("namespace", "only the files of one datapack namespace, such as test_mobs."),
                    argument("page <n>", "another page of a long list; the arrows at the bottom run it for you.")),
            HelpSection.examples(
                    example("/dropscore validate", "every problem"),
                    example("/dropscore validate errors", "only the errors"),
                    example("/dropscore validate warnings test_mobs", "the warnings of test_mobs")))),
    /** {@code /dropscore files}. */
    FILES("Shows which drop and removal files dropscore loaded, and which it skipped.", List.of(
            HelpSection.usage("/dropscore files [namespace] [page <n>]"),
            HelpSection.arguments(
                    argument("no namespace", "the number of drop and removal files of each namespace, for mobs and "
                            + "blocks. Click a namespace to list its files."),
                    argument("namespace", "every file of that namespace: module, kind, file id, target and size."),
                    argument("page <n>", "another page of a long list; the arrows at the bottom run it for you.")),
            HelpSection.examples(
                    example("/dropscore files", "the file counts of every namespace"),
                    example("/dropscore files test_blocks", "the files of test_blocks")))),
    /** {@code /dropscore sources}. */
    SOURCES("Shows every file that adds an item or experience, and every file that removes it.", List.of(
            HelpSection.usage("/dropscore sources <<item> | experience> [page <n>]"),
            HelpSection.arguments(
                    argument("item", "item id, such as minecraft:diamond."),
                    argument("experience", "experience drops and experience removals instead of an item."),
                    argument("page <n>", "another page of a long list; the arrows at the bottom run it for you.")),
            HelpSection.examples(
                    example("/dropscore sources minecraft:rotten_flesh", "who adds or removes rotten flesh"),
                    example("/dropscore sources experience", "who adds or removes experience")))),
    /** {@code /dropscore drops}. */
    DROPS("Shows every drop and removal dropscore applies to a mob or block, from files that name it by id, mod:*, "
            + "#tag or *.", List.of(
            HelpSection.usage("/dropscore drops <entity | block> [page <n>]"),
            HelpSection.arguments(
                    argument("entity | block", "mob or block id, such as minecraft:zombie or minecraft:stone. An id that "
                            + "names both, such as minecraft:tnt, shows both."),
                    argument("page <n>", "another page of a long list; the arrows at the bottom run it for you.")),
            HelpSection.examples(
                    example("/dropscore drops minecraft:zombie", "everything dropscore does to zombie drops"),
                    example("/dropscore drops minecraft:stone", "everything dropscore does to stone drops")))),
    /** {@code /dropscore simulate}. */
    SIMULATE("Rolls the loot table and the dropscore rules many times, without killing or breaking anything, "
            + "and reports what dropped.", List.of(
            HelpSection.usage(
                    "/dropscore simulate entity <entity> [kills] [looting] [by <player | none | <killer>>] [sneaking] "
                            + "[burning] [only <experience | <filter>>] [drop [no-text]]",
                    "/dropscore simulate block <block> [breaks] [fortune] [by <player | none | explosion>] [sneaking] "
                            + "[only <experience | <filter>>] [drop [no-text]]"),
            HelpSection.arguments(
                    argument("entity <entity>", "mob to kill, such as minecraft:zombie."),
                    argument("block <block>", "block to break, with optional states, such as minecraft:wheat[age=7]."),
                    argument("kills / breaks", "number of runs, 1 to 1000000; 1 when missing."),
                    argument("looting / fortune", "forced enchantment level, 0 to 255, only after kills / breaks. "
                            + "Without it, the level of the tool in the actor's main hand."),
                    argument("by player", "you: a copy with your name, equipment and position (the default for "
                            + "players). From the console, a blank player."),
                    argument("by none", "no player: generic damage for mobs, a netherite pickaxe for blocks (the "
                            + "console's default). No experience."),
                    argument("by <killer>", "mobs only: a mob of that type kills, so killed_by_player fails and no "
                            + "experience drops."),
                    argument("by explosion", "blocks only: a TNT explosion, without tool or experience."),
                    argument("sneaking", "the actor crouches; needs a player or a killer."),
                    argument("burning", "mobs only: the mob dies on fire."),
                    argument("only experience", "shows only the experience."),
                    argument("only <filter>", "shows only the items matching an id, #tag or NBT; every run is still "
                            + "rolled in full."),
                    argument("drop", "also drops what the report shows at your position."),
                    argument("drop no-text", "drops without any chat output, for datapack functions."),
                    argument("page <n>", "/dropscore simulate page <n> shows another page of your last report, "
                            + "without rolling again.")),
            HelpSection.examples(
                    example("/dropscore simulate entity minecraft:zombie 1000", "1000 zombie kills by you"),
                    example("/dropscore simulate entity minecraft:cow 1000 3 only minecraft:beef", "beef with Looting III"),
                    example("/dropscore simulate block minecraft:diamond_ore 500 3 by none", "diamond ore with Fortune III"),
                    example("/dropscore simulate entity minecraft:zombie drop no-text", "one silent kill, for functions")))),
    /** {@code /dropscore reset}. */
    RESET("Resets drop progress, so first_kill and first_break drops drop again and pity counters start over.", List.of(
            HelpSection.usage("/dropscore reset <player> [first | pity] [entities | blocks | <entity | block>]"),
            HelpSection.arguments(
                    argument("player", "whose progress: a name or a selector such as @s (you) or @a."),
                    argument("first", "only first kills and first breaks."),
                    argument("pity", "only pity counters. Both first and pity when missing."),
                    argument("no target", "every drop."),
                    argument("entities", "every mob drop."),
                    argument("blocks", "every block drop."),
                    argument("entity | block", "the drops that mob or block can give, such as minecraft:zombie. "
                            + "Drops of tag, mod:* and * files are reset for every mob or block they match.")),
            HelpSection.examples(
                    example("/dropscore reset @s", "everything of yours"),
                    example("/dropscore reset Steve first", "every first kill and first break of Steve"),
                    example("/dropscore reset @a pity blocks", "the block pity counters of everyone"),
                    example("/dropscore reset @s minecraft:horse", "your progress on horses"))));

    private final String summary;
    private final List<HelpSection> sections;

    /**
     * Creates a topic.
     *
     * @param summary  One sentence on what the subcommand does. Never null.
     * @param sections Sections of the page, in order. Never null.
     */
    HelpTopic(String summary, List<HelpSection> sections) {
        this.summary = summary;
        this.sections = sections;
    }

    /**
     * Returns the subcommand name, which is also the topic's argument.
     *
     * @return Such as {@code simulate}.
     */
    String keyword() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns one sentence on what the subcommand does.
     *
     * @return The summary.
     */
    String summary() {
        return summary;
    }

    /**
     * Returns the sections of the page.
     *
     * @return The sections, in order.
     */
    List<HelpSection> sections() {
        return sections;
    }
}
