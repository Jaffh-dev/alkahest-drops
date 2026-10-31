package com.jaffh.alkahestdrops.server.mobdrops;

import com.jaffh.alkahestdrops.AlkahestDrops;
import com.jaffh.alkahestdrops.server.drops.RealPlayers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Records every player who damages a mob with per-player drops, in the mob's persistent data so the record survives
 * chunk unloads, and finds those players again when the mob dies.
 */
public class HitTracker {
    private static final String ATTACKERS_KEY = AlkahestDrops.ID + ".attackers";

    /**
     * Records the attacking player of a mob whose table has per-player drops. Runs at LOWEST priority, after other
     * mods could cancel the hit or reduce it to nothing.
     *
     * @param event Hurt event of the entity.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel) || event.getAmount() <= 0) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!MobDropManager.getTable(entity.getType()).hasPerPlayerDrops()) return;
        record(entity, player);
    }

    /**
     * Adds a player to the attackers of a mob, once.
     *
     * @param entity Damaged mob. Never null.
     * @param player Attacking player. Never null.
     */
    public static void record(LivingEntity entity, Player player) {
        List<UUID> attackers = attackerIdsOf(entity);
        if (attackers.contains(player.getUUID())) return;

        ListTag list = entity.getPersistentData().getList(ATTACKERS_KEY, Tag.TAG_INT_ARRAY);
        list.add(NbtUtils.createUUID(player.getUUID()));
        entity.getPersistentData().put(ATTACKERS_KEY, list);
    }

    /**
     * Returns the ids of every player recorded as an attacker of a mob.
     *
     * @param entity Mob. Never null.
     * @return The player ids in the order of their first hit; empty when none.
     */
    public static List<UUID> attackerIdsOf(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        ListTag list = data.getList(ATTACKERS_KEY, Tag.TAG_INT_ARRAY);
        List<UUID> ids = new ArrayList<>(list.size());
        for (Tag tag : list) {
            ids.add(NbtUtils.loadUUID(tag));
        }
        return ids;
    }

    /**
     * Returns the attackers of a dying mob that can get a per-player drop: the recorded players who are online,
     * plus the killer when it is a player that was not recorded (hit before a reload added per-player drops).
     * Fake players (machines of other mods) are never included.
     *
     * @param entity Dying mob. Never null.
     * @param server Server of the mob. Never null.
     * @param killer Entity that killed the mob, or null.
     * @return The online attacking players, each once; empty when none.
     */
    static List<ServerPlayer> onlineAttackersOf(LivingEntity entity, MinecraftServer server, @Nullable Entity killer) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID id : attackerIdsOf(entity)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) players.add(player);
        }
        if (killer instanceof ServerPlayer player && RealPlayers.isReal(player) && !players.contains(player)) {
            players.add(player);
        }
        return players;
    }
}
