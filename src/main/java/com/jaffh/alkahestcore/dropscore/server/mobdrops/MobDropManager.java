package com.jaffh.alkahestcore.dropscore.server.mobdrops;

import com.jaffh.alkahestcore.dropscore.common.drops.DropTrigger;
import com.jaffh.alkahestcore.dropscore.server.drops.DropDataException;
import com.jaffh.alkahestcore.dropscore.server.drops.DropFileFormat;
import com.jaffh.alkahestcore.dropscore.server.drops.DropIssue;
import com.jaffh.alkahestcore.dropscore.server.drops.DropManager;
import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.jaffh.alkahestcore.dropscore.server.drops.ParseContext;
import com.jaffh.alkahestcore.dropscore.server.drops.ParsedDropFile;
import com.jaffh.alkahestcore.dropscore.server.drops.TargetMatcher;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Datapack reload listener for {@code data/<namespace>/mob_drops/*.json}, with rules indexed by entity type.
 */
public final class MobDropManager extends DropManager<EntityType<?>> {
    /** The listener registered on every reload; it holds the rules of the current datapacks. */
    public static final MobDropManager INSTANCE = new MobDropManager();

    /**
     * Creates the listener for the {@code mob_drops} datapack folder. Patterns ({@code mod:*}, {@code #tag},
     * {@code *}) never match players: their drops are their inventory.
     */
    private MobDropManager() {
        super("mob_drops", DropFileFormat.MOB,
                new TargetMatcher<>(ForgeRegistries.ENTITY_TYPES, EntityType::is, type -> type != EntityType.PLAYER),
                "mob");
    }

    /**
     * Returns the death drop table of an entity type, built on its first lookup after a reload.
     *
     * @param type Entity type of the dying mob. Never null.
     * @return The table, {@link DropTable#EMPTY} when no file targets this type.
     */
    public static DropTable getTable(EntityType<?> type) {
        return INSTANCE.tableOf(type);
    }

    /**
     * Returns the drop table of one trigger of an entity type.
     *
     * @param type    Entity type of the mob. Never null.
     * @param trigger Trigger of the drop. Never null.
     * @return The table, {@link DropTable#EMPTY} when no rule of that trigger targets this type.
     */
    public static DropTable getTable(EntityType<?> type, DropTrigger trigger) {
        return INSTANCE.tableOf(type, trigger);
    }

    /**
     * Returns the problems found by the last load of mob drop files.
     *
     * @return An immutable list, empty when every file was valid.
     */
    public static List<DropIssue> getIssues() {
        return INSTANCE.issues();
    }

    /**
     * Rejects removal files that name players.
     *
     * @param parsed Parsed file. Never null.
     * @param target Resolved target. Never null.
     * @param ctx    Context of the {@code entity} key. Never null.
     * @throws DropDataException If a removal file targets players.
     */
    @Override
    protected void checkTarget(ParsedDropFile parsed, TargetMatcher.Target<EntityType<?>> target, ParseContext ctx) throws DropDataException {
        if (parsed instanceof ParsedDropFile.Removals
                && target instanceof TargetMatcher.Target.Values<EntityType<?>> values
                && values.values().contains(EntityType.PLAYER)) {
            throw ctx.error("removal_on_player");
        }
    }
}
