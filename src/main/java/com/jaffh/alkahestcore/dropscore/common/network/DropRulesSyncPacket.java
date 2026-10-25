package com.jaffh.alkahestcore.dropscore.common.network;

import com.jaffh.alkahestcore.dropscore.client.drops.ClientDropRules;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedDropRules;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedTarget;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server-to-client message carrying one batch of the drop summary, sent on login and after every reload so recipe
 * viewers can show the drops. The summary of every mob and block is too large for one custom payload (1 MiB), so it
 * is split into batches sent in order; the client publishes it once the last batch arrives
 * ({@link DropRulesAssembler}).
 *
 * @param batch      Index of this batch, from 0.
 * @param batchCount Number of batches of the summary, 1 or more.
 * @param rules      Targets of this batch. Never null.
 */
public record DropRulesSyncPacket(int batch, int batchCount, SyncedDropRules rules) {
    /**
     * Most encoded bytes of targets per batch: half the custom payload limit, so the batch header and a large last
     * target never push a batch over it.
     */
    public static final int MAX_BATCH_BYTES = 512 * 1024;

    /**
     * Splits a summary into batches of whole targets, mobs first, by encoded size: a batch is closed before a target
     * would take it over the limit, so drops with large NBT make smaller batches instead of an oversized packet.
     *
     * @param rules         Summary to split. Never null.
     * @param maxBatchBytes Most encoded bytes of targets per batch, above 0 (a larger target gets a batch of its own).
     * @return The batches, at least one, in send order.
     */
    public static List<DropRulesSyncPacket> split(SyncedDropRules rules, int maxBatchBytes) {
        Splitter splitter = new Splitter(maxBatchBytes);
        splitter.addTargets(rules.mobs(), true);
        splitter.addTargets(rules.blocks(), false);
        List<Batch> batches = splitter.batches;
        List<DropRulesSyncPacket> packets = new ArrayList<>(batches.size());
        for (int i = 0; i < batches.size(); i++) {
            Batch batch = batches.get(i);
            SyncedDropRules part = new SyncedDropRules(batch.mobs(), batch.blocks());
            packets.add(new DropRulesSyncPacket(i, batches.size(), part));
        }
        return packets;
    }

    /**
     * Fills batches with targets, measuring each target by writing it into one reused scratch buffer.
     */
    private static final class Splitter {
        private final int maxBatchBytes;
        private final List<Batch> batches = new ArrayList<>();
        private final FriendlyByteBuf scratch = new FriendlyByteBuf(Unpooled.buffer());

        /**
         * Creates a splitter with one empty batch.
         *
         * @param maxBatchBytes Most encoded bytes of targets per batch, above 0.
         */
        Splitter(int maxBatchBytes) {
            this.maxBatchBytes = maxBatchBytes;
            batches.add(new Batch());
        }

        /**
         * Adds the targets of one module, starting a new batch when the next target does not fit in the last one.
         *
         * @param targets Targets to add. Never null.
         * @param isMob   Whether the targets are mobs (else blocks).
         */
        void addTargets(List<SyncedTarget> targets, boolean isMob) {
            for (SyncedTarget target : targets) {
                int size = sizeOf(target);
                Batch batch = batches.get(batches.size() - 1);
                if (batch.bytes > 0 && batch.bytes + size > maxBatchBytes) {
                    batch = new Batch();
                    batches.add(batch);
                }
                (isMob ? batch.mobs() : batch.blocks()).add(target);
                batch.bytes += size;
            }
        }

        /**
         * Returns the encoded size of one target.
         *
         * @param target Target. Never null.
         * @return The size in bytes.
         */
        private int sizeOf(SyncedTarget target) {
            scratch.clear();
            DropSyncCodec.writeTarget(scratch, target);
            return scratch.writerIndex();
        }
    }

    /**
     * Collects the targets of one batch while the summary is split.
     */
    private static final class Batch {
        private final List<SyncedTarget> mobs = new ArrayList<>();
        private final List<SyncedTarget> blocks = new ArrayList<>();
        private int bytes;

        /**
         * Returns the mob targets of the batch.
         *
         * @return The mutable list.
         */
        List<SyncedTarget> mobs() {
            return mobs;
        }

        /**
         * Returns the block targets of the batch.
         *
         * @return The mutable list.
         */
        List<SyncedTarget> blocks() {
            return blocks;
        }
    }

    /**
     * Writes the message.
     *
     * @param buf Target buffer. Never null.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(batch);
        buf.writeVarInt(batchCount);
        DropSyncCodec.writeRules(buf, rules);
    }

    /**
     * Reads a message.
     *
     * @param buf Source buffer. Never null.
     * @return The message.
     */
    public static DropRulesSyncPacket decode(FriendlyByteBuf buf) {
        return new DropRulesSyncPacket(buf.readVarInt(), buf.readVarInt(), DropSyncCodec.readRules(buf));
    }

    /**
     * Gives the batch to the client cache, on the client's main thread (so batches are handled in order).
     *
     * @param packet  Received message. Never null.
     * @param context Network context of the message. Never null.
     */
    public static void handle(DropRulesSyncPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientDropRules.receive(packet)));
        context.get().setPacketHandled(true);
    }
}
