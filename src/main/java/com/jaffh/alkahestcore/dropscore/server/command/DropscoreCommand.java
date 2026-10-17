package com.jaffh.alkahestcore.dropscore.server.command;

import com.jaffh.alkahestcore.dropscore.server.blockdrops.BlockDropManager;
import com.jaffh.alkahestcore.dropscore.server.drops.DropIssue;
import com.jaffh.alkahestcore.dropscore.server.drops.DropLoadReport;
import com.jaffh.alkahestcore.dropscore.server.drops.DropManager;
import com.jaffh.alkahestcore.dropscore.server.drops.ParsedDropFile;
import com.jaffh.alkahestcore.dropscore.server.mobdrops.MobDropManager;
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
 * Registers {@code /dropscore} (permission level 2) with its debug subcommands: {@code validate}, {@code files},
 * {@code sources} and {@code simulate}, plus {@code help}.
 */
public final class DropscoreCommand {
    /** Modules whose files the commands read, mobs first. */
    static final List<DropManager<?>> MANAGERS = List.of(MobDropManager.INSTANCE, BlockDropManager.INSTANCE);

    /** Suggests every namespace with a loaded, skipped or reported file. */
    static final SuggestionProvider<CommandSourceStack> NAMESPACES =
            (context, builder) -> SharedSuggestionProvider.suggest(knownNamespaces(), builder);

    private static final int PERMISSION_LEVEL = 2;

    /**
     * Prevents instantiation of this utility class.
     */
    private DropscoreCommand() {
    }

    /**
     * Registers the command tree.
     *
     * @param event Command registration event of the Forge bus.
     */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dropscore")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .then(ValidateCommand.build())
                .then(FilesCommand.build())
                .then(SourcesCommand.build())
                .then(SimulateCommand.build())
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
