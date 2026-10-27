package com.jaffh.alkahestdrops.server.command;

import com.jaffh.alkahestdrops.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestdrops.server.drops.DropIssue;
import com.jaffh.alkahestdrops.server.drops.DropLoadReport;
import com.jaffh.alkahestdrops.server.drops.DropManager;
import com.jaffh.alkahestdrops.server.drops.ParsedDropFile;
import com.jaffh.alkahestdrops.server.mobdrops.MobDropManager;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Registers {@code /alkdrops} (permission level 2) with its debug subcommands: {@code validate}, {@code files},
 * {@code sources}, {@code drops}, {@code simulate} and {@code reset}, plus {@code help}.
 */
public final class AlkahestDropsCommand {
    /** Modules whose files the commands read, mobs first. */
    static final List<DropManager<?>> MANAGERS = List.of(MobDropManager.INSTANCE, BlockDropManager.INSTANCE);

    /** Suggests every namespace with a loaded, skipped or reported file. */
    static final SuggestionProvider<CommandSourceStack> NAMESPACES =
            (context, builder) -> SharedSuggestionProvider.suggest(knownNamespaces(), builder);

    private static final int PERMISSION_LEVEL = 2;

    /**
     * Prevents instantiation of this utility class.
     */
    private AlkahestDropsCommand() {
    }

    /**
     * Registers the command tree.
     *
     * @param event Command registration event of the Forge bus.
     */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("alkdrops")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(ValidateCommand.build())
                .then(FilesCommand.build())
                .then(SourcesCommand.build(event.getBuildContext()))
                .then(DropsCommand.build())
                .then(SimulateCommand.build(event.getBuildContext()))
                .then(ResetCommand.build())
                .then(HelpCommand.build()));
    }

    /**
     * Collects the namespaces of every file the last loads saw.
     *
     * @return The namespaces, sorted.
     */
    private static Set<String> knownNamespaces() {
        Set<String> namespaces = new TreeSet<>();
        for (DropManager<?> manager : MANAGERS) {
            DropLoadReport report = manager.report();
            for (ParsedDropFile file : report.files()) namespaces.add(file.fileId().getNamespace());
            for (ResourceLocation fileId : report.skippedFileIds()) namespaces.add(fileId.getNamespace());
            for (DropIssue issue : report.issues()) namespaces.add(issue.fileId().getNamespace());
        }
        return namespaces;
    }
}
