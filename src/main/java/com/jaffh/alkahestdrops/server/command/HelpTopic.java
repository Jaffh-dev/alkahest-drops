package com.jaffh.alkahestdrops.server.command;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

import static com.jaffh.alkahestdrops.server.command.HelpSection.argument;
import static com.jaffh.alkahestdrops.server.command.HelpSection.describedArgument;
import static com.jaffh.alkahestdrops.server.command.HelpSection.example;

/**
 * Defines the {@code /alkdrops help} topics, one per subcommand, with the keys of their text.
 */
enum HelpTopic {
    /** {@code /alkdrops validate}. */
    VALIDATE(List.of(
            HelpSection.usage("/alkdrops validate [errors | warnings] [namespace] [page <n>]"),
            HelpSection.arguments(
                    argument("errors", "validate.arg.errors"),
                    argument("warnings", "validate.arg.warnings"),
                    argument("namespace", "validate.arg.namespace"),
                    argument("page <n>", "arg.page")),
            HelpSection.examples(
                    example("/alkdrops validate", "validate.example.all"),
                    example("/alkdrops validate errors", "validate.example.errors"),
                    example("/alkdrops validate warnings test_mobs", "validate.example.warnings")))),
    /** {@code /alkdrops files}. */
    FILES(List.of(
            HelpSection.usage("/alkdrops files [namespace] [page <n>]"),
            HelpSection.arguments(
                    describedArgument("no_namespace", "files.arg.no_namespace"),
                    argument("namespace", "files.arg.namespace"),
                    argument("page <n>", "arg.page")),
            HelpSection.examples(
                    example("/alkdrops files", "files.example.all"),
                    example("/alkdrops files test_blocks", "files.example.namespace")))),
    /** {@code /alkdrops sources}. */
    SOURCES(List.of(
            HelpSection.usage("/alkdrops sources <<item> | experience> [page <n>]"),
            HelpSection.arguments(
                    argument("item", "sources.arg.item"),
                    argument("experience", "sources.arg.experience"),
                    argument("page <n>", "arg.page")),
            HelpSection.examples(
                    example("/alkdrops sources minecraft:rotten_flesh", "sources.example.item"),
                    example("/alkdrops sources experience", "sources.example.experience")))),
    /** {@code /alkdrops drops}. */
    DROPS(List.of(
            HelpSection.usage("/alkdrops drops <entity | block> [page <n>]"),
            HelpSection.arguments(
                    argument("entity | block", "drops.arg.target"),
                    argument("page <n>", "arg.page")),
            HelpSection.examples(
                    example("/alkdrops drops minecraft:zombie", "drops.example.zombie"),
                    example("/alkdrops drops minecraft:stone", "drops.example.stone")))),
    /** {@code /alkdrops simulate}. */
    SIMULATE(List.of(
            HelpSection.usage(
                    "/alkdrops simulate entity <entity> [on <trigger>] [kills] [enchant <enchantments>] [by <player | none | <killer>>] [sneaking] "
                            + "[burning] [only <experience | <filter>>] [drop [no-text]]",
                    "/alkdrops simulate block <block> [breaks] [enchant <enchantments>] [by <player | none | explosion>] [sneaking] "
                            + "[only <experience | <filter>>] [drop [no-text]]"),
            HelpSection.arguments(
                    argument("entity <entity>", "simulate.arg.entity"),
                    argument("block <block>", "simulate.arg.block"),
                    argument("on <trigger>", "simulate.arg.on"),
                    argument("kills / breaks", "simulate.arg.runs"),
                    argument("enchant <enchantments>", "simulate.arg.enchant"),
                    argument("by player", "simulate.arg.by_player"),
                    argument("by none", "simulate.arg.by_none"),
                    argument("by <killer>", "simulate.arg.by_killer"),
                    argument("by explosion", "simulate.arg.by_explosion"),
                    argument("sneaking", "simulate.arg.sneaking"),
                    argument("burning", "simulate.arg.burning"),
                    argument("only experience", "simulate.arg.only_experience"),
                    argument("only <filter>", "simulate.arg.only_filter"),
                    argument("drop", "simulate.arg.drop"),
                    argument("drop no-text", "simulate.arg.drop_no_text"),
                    argument("page <n>", "simulate.arg.page")),
            HelpSection.examples(
                    example("/alkdrops simulate entity minecraft:zombie 1000", "simulate.example.zombie"),
                    example("/alkdrops simulate entity minecraft:cow 1000 enchant [{id:\"minecraft:looting\",lvl:3}] only minecraft:beef", "simulate.example.cow"),
                    example("/alkdrops simulate entity minecraft:sheep on shear 100", "simulate.example.shear"),
                    example("/alkdrops simulate block minecraft:diamond_ore 500 enchant [{id:\"minecraft:fortune\",lvl:3}] by none", "simulate.example.diamond_ore"),
                    example("/alkdrops simulate entity minecraft:zombie drop no-text", "simulate.example.silent")))),
    /** {@code /alkdrops reset}. */
    RESET(List.of(
            HelpSection.usage("/alkdrops reset <player> [first | pity] [entities | blocks | <entity | block>]"),
            HelpSection.arguments(
                    argument("player", "reset.arg.player"),
                    argument("first", "reset.arg.first"),
                    argument("pity", "reset.arg.pity"),
                    describedArgument("no_target", "reset.arg.no_target"),
                    argument("entities", "reset.arg.entities"),
                    argument("blocks", "reset.arg.blocks"),
                    argument("entity | block", "reset.arg.target")),
            HelpSection.examples(
                    example("/alkdrops reset @s", "reset.example.self"),
                    example("/alkdrops reset Steve first", "reset.example.first"),
                    example("/alkdrops reset @a pity blocks", "reset.example.pity_blocks"),
                    example("/alkdrops reset @s minecraft:horse", "reset.example.horse"))));

    private final List<HelpSection> sections;

    /**
     * Creates a topic.
     *
     * @param sections Sections of the page, in order. Never null.
     */
    HelpTopic(List<HelpSection> sections) {
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
     * @return The translatable summary.
     */
    Component summary() {
        return CommandText.lang("help." + keyword() + ".summary");
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
