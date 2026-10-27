package com.jaffh.alkahestdrops.server.drops;

import com.jaffh.alkahestdrops.AlkahestDrops;
import com.jaffh.alkahestdrops.api.event.OnDropEvent;
import com.jaffh.alkahestdrops.common.drops.DropActions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;

/**
 * Carries out what happens when an Alkahest Drops item spawns: the glow of {@code on_drop}, then the
 * {@link OnDropEvent}, then the {@code run_function} of {@code on_drop}, run as the item, at the item. While the
 * function runs, the real player the drop was rolled for carries {@link #PLAYER_TAG}, so the function can reach them
 * with {@code @a[tag=alkahestdrops.drop_player]}.
 */
public final class DropActionRunner {
    /** Entity tag of the drop's player while its function runs. */
    public static final String PLAYER_TAG = AlkahestDrops.ID + ".drop_player";

    /**
     * Prevents instantiation of this utility class.
     */
    private DropActionRunner() {
    }

    /**
     * Makes an item glow when its drop asks for it. Called as the item spawns, so the glow is there from its first
     * frame.
     *
     * @param item    Item entity that is spawning. Never null.
     * @param actions Actions of its drop. Never null.
     */
    public static void applyGlow(ItemEntity item, DropActions actions) {
        if (actions.shouldGlow()) item.setGlowingTag(true);
    }

    /**
     * Fires the event of a spawned drop and, unless a listener cancels it, runs the drop's function. A removed item
     * (merged into another, killed) is skipped.
     *
     * @param item   Spawned item entity, already in the level. Never null.
     * @param rolled Drop and roll of its stack. Never null.
     */
    public static void finishDrop(ItemEntity item, SpawningDropQueue.RolledFrom rolled) {
        if (item.isRemoved()) return;
        Player player = rolled.roll().player();
        OnDropEvent event = new OnDropEvent(item, rolled.roll().origin(), rolled.entry().tracking().key(),
                rolled.entry().trigger().trigger(), player);
        if (MinecraftForge.EVENT_BUS.post(event)) return;
        ResourceLocation function = rolled.entry().onDrop().runFunction();
        if (function != null && !item.isRemoved()) runFunction(item, function, player);
    }

    /**
     * Runs a function as an item, at the item, with permission level 2 and no command output. Machines' fake players
     * are not tagged. A function removed since the drop files were read is skipped.
     *
     * @param item       Spawned item entity. Never null.
     * @param functionId Function to run. Never null.
     * @param player     Player the drop was rolled for, or null.
     */
    private static void runFunction(ItemEntity item, ResourceLocation functionId, @Nullable Player player) {
        ServerFunctionManager functions = ((ServerLevel) item.level()).getServer().getFunctions();
        functions.get(functionId).ifPresent(function -> {
            boolean isTagged = player != null && RealPlayers.isReal(player) && player.addTag(PLAYER_TAG);
            try {
                functions.execute(function, sourceOf(functions, item));
            } finally {
                if (isTagged) player.removeTag(PLAYER_TAG);
            }
        });
    }

    /**
     * Builds the command source of a drop's function.
     *
     * @param functions Function manager of the server. Never null.
     * @param item      Dropped item. Never null.
     * @return The game loop source, as the item, at the item, in its level.
     */
    private static CommandSourceStack sourceOf(ServerFunctionManager functions, ItemEntity item) {
        return functions.getGameLoopSender()
                .withEntity(item)
                .withPosition(item.position())
                .withLevel((ServerLevel) item.level());
    }
}
