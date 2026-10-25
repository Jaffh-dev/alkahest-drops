package com.jaffh.alkahestcore.dropscore.common.network;

import com.jaffh.alkahestcore.dropscore.common.drops.SyncedDropRules;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedTarget;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Joins the batches of one drop summary back together. A batch 0 always starts a new summary, so a summary cut short
 * (such as by a reload while it was being sent) is dropped instead of being mixed with the next one. Not thread-safe:
 * use it from one thread.
 */
public final class DropRulesAssembler {
    private final List<SyncedTarget> mobs = new ArrayList<>();
    private final List<SyncedTarget> blocks = new ArrayList<>();
    private int nextBatch = -1;

    /**
     * Adds a received batch.
     *
     * @param packet Received batch. Never null.
     * @return The whole summary when this was its last batch; null while batches are missing or when the batch is out
     *         of order (it is ignored until the next batch 0).
     */
    @Nullable
    public SyncedDropRules accept(DropRulesSyncPacket packet) {
        if (packet.batch() == 0) {
            mobs.clear();
            blocks.clear();
            nextBatch = 0;
        }
        if (packet.batch() != nextBatch) {
            nextBatch = -1;
            return null;
        }
        mobs.addAll(packet.rules().mobs());
        blocks.addAll(packet.rules().blocks());
        nextBatch++;
        if (nextBatch < packet.batchCount()) return null;

        SyncedDropRules rules = new SyncedDropRules(mobs, blocks);
        mobs.clear();
        blocks.clear();
        nextBatch = -1;
        return rules;
    }
}
