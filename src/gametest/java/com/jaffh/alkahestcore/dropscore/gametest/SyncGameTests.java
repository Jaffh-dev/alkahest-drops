package com.jaffh.alkahestcore.dropscore.gametest;

import com.jaffh.alkahestcore.AlkahestCore;
import com.jaffh.alkahestcore.dropscore.common.drops.DropBehavior;
import com.jaffh.alkahestcore.dropscore.common.drops.DropCount;
import com.jaffh.alkahestcore.dropscore.common.drops.EnchantBonus;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedDrop;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedCondition;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedDropRules;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedDropSource;
import com.jaffh.alkahestcore.dropscore.common.drops.SyncedTarget;
import com.jaffh.alkahestcore.dropscore.common.network.DropRulesAssembler;
import com.jaffh.alkahestcore.dropscore.common.network.DropRulesSyncPacket;
import com.jaffh.alkahestcore.dropscore.server.sync.SyncedDropFactory;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * GameTests for the drop summary sent to clients for JEI: vanilla loot table analysis, vanilla experience, dropscore
 * drops merged per target, removals, and the packet round trip. Rules come from {@code dstest_a} and {@code dstest_b}.
 */
@GameTestHolder(AlkahestCore.ID)
@PrefixGameTestTemplate(false)
public class SyncGameTests {
    private static final double EPSILON = 1e-6;
    private static final int PAYLOAD_LIMIT = 1_048_576;
    /** Most bytes a batch adds around its targets: two batch numbers and two list sizes, as var ints. */
    private static final int BATCH_HEADER_BYTES = 20;

    /**
     * The zombie loot table reads as vanilla defines it: rotten flesh 0–2 with +0–1 per Looting level, and iron
     * ingot, carrot and potato sharing a 2.5% (+1% per level) chance that needs a player kill (a described condition).
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void zombieLootTableIsAnalyzed(GameTestHelper helper) {
        SyncedTarget zombie = targetOf(rules(helper).mobs(), "minecraft:zombie");
        SyncedDrop flesh = lootDropOf(zombie, Items.ROTTEN_FLESH);
        DropTestKit.check(Math.abs(flesh.chance() - 1.0) < EPSILON, "flesh chance " + flesh.chance());
        DropTestKit.check(flesh.count().equals(new DropCount(0, 2)), "flesh count " + flesh.count());
        DropTestKit.check(flesh.enchantBonus().count().bonusMinPerLevel() == 0
                && flesh.enchantBonus().count().bonusMaxPerLevel() == 1, "flesh looting bonus " + flesh.enchantBonus().count());
        DropTestKit.check(flesh.source().id().equals(new ResourceLocation("entities/zombie")), "flesh source " + flesh.source());

        SyncedDrop iron = lootDropOf(zombie, Items.IRON_INGOT);
        DropTestKit.check(Math.abs(iron.chance() - 0.025 / 3) < EPSILON, "iron chance " + iron.chance());
        DropTestKit.check(Math.abs(iron.enchantBonus().chance().bonusPerLevel() - 0.01 / 3) < EPSILON,
                "iron looting chance " + iron.enchantBonus().chance());
        DropTestKit.check(iron.conditions().equals(List.of(SyncedCondition.of(SyncedCondition.Kind.KILLED_BY_PLAYER, false)))
                && !iron.hasHiddenConditions(), "iron conditions " + iron.conditions());
        DropTestKit.check(experienceOf(zombie) != null && experienceOf(zombie).count().equals(new DropCount(5, 5)), "zombie experience");
        helper.succeed();
    }

    /**
     * On the husk, a same-namespace removal hides the loot table's rotten flesh but keeps the file's own flesh, a
     * removal of another namespace hides the file's diamond, the {@code *} file's barrier is merged in, and a file's
     * global condition shows on its drops.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void removalsAndPatternFilesApplyPerTarget(GameTestHelper helper) {
        SyncedTarget husk = targetOf(rules(helper).mobs(), "minecraft:husk");
        DropTestKit.check(findDrop(husk, drop -> isLoot(drop) && drop.stack().is(Items.ROTTEN_FLESH)) == null, "loot flesh not removed");
        SyncedDrop flesh = findDrop(husk, drop -> isDropscore(drop) && drop.stack().is(Items.ROTTEN_FLESH));
        DropTestKit.check(flesh != null && flesh.count().equals(new DropCount(3, 3)), "dstest_a flesh missing");
        DropTestKit.check(findDrop(husk, drop -> drop.stack().is(Items.DIAMOND)) == null, "diamond not removed by dstest_b");
        SyncedDrop barrier = findDrop(husk, drop -> drop.stack().is(Items.BARRIER));
        DropTestKit.check(barrier != null && barrier.source().id().equals(new ResourceLocation("dstest_a", "any_barrier")), "* barrier not merged");
        SyncedDrop emerald = findDrop(targetOf(rules(helper).mobs(), "minecraft:creeper"), drop -> drop.stack().is(Items.EMERALD));
        DropTestKit.check(emerald != null && emerald.conditions().contains(SyncedCondition.of(SyncedCondition.Kind.KILLED_BY_PLAYER, false)),
                "creeper file condition " + emerald);
        helper.succeed();
    }

    /**
     * Experience: an unconditional removal hides the vanilla experience and other namespaces' experience but keeps
     * its own namespace's; a conditional removal shows as its negated condition ("Not sneaking").
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void experienceFollowsRemovals(GameTestHelper helper) {
        SyncedDropRules rules = rules(helper);
        SyncedTarget quartz = targetOf(rules.blocks(), "minecraft:nether_quartz_ore");
        DropTestKit.check(experienceOf(quartz) == null, "vanilla quartz experience not removed");
        List<SyncedDrop> quartzExperience = quartz.drops().stream().filter(SyncedDrop::isExperience).toList();
        DropTestKit.check(quartzExperience.size() == 1 && quartzExperience.get(0).count().equals(new DropCount(3, 3)),
                "expected only dstest_b's 3 XP, got " + quartzExperience);

        SyncedDrop emerald = experienceOf(targetOf(rules.blocks(), "minecraft:emerald_ore"));
        DropTestKit.check(emerald != null && emerald.count().equals(new DropCount(3, 7)), "emerald ore experience " + emerald);
        DropTestKit.check(emerald.conditions().equals(List.of(SyncedCondition.of(SyncedCondition.Kind.SNEAKING, true)))
                && !emerald.hasHiddenConditions(), "emerald experience conditions " + emerald.conditions());
        SyncedDrop sheep = experienceOf(targetOf(rules.mobs(), "minecraft:sheep"));
        DropTestKit.check(sheep != null && sheep.conditions().equals(List.of(SyncedCondition.of(SyncedCondition.Kind.SNEAKING, true))),
                "sheep experience conditions " + sheep);
        DropTestKit.check(experienceOf(targetOf(rules.mobs(), "minecraft:chicken")) == null, "chicken experience not removed");
        helper.succeed();
    }

    /**
     * Every block with drops gets a page, also blocks no file names; the coal ore keeps its silk-touch drop (with its
     * Silk Touch condition) while a removal hides the coal; a tag file's drop is on the tagged block.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void blockPagesFollowNamedTargets(GameTestHelper helper) {
        SyncedDropRules rules = rules(helper);
        SyncedTarget coal = targetOf(rules.blocks(), "minecraft:coal_ore");
        DropTestKit.check(findDrop(coal, drop -> drop.stack().is(Items.COAL)) == null, "coal not removed");
        SyncedDrop silkTouch = lootDropOf(coal, Items.COAL_ORE);
        SyncedCondition silk = new SyncedCondition(SyncedCondition.Kind.TOOL_ENCHANTMENTS, false, List.of(new ResourceLocation("silk_touch")));
        DropTestKit.check(silkTouch.conditions().equals(List.of(silk)), "coal ore conditions " + silkTouch.conditions());
        SyncedTarget log = targetOf(rules.blocks(), "minecraft:oak_log");
        SyncedDrop shard = findDrop(log, drop -> drop.stack().is(Items.PRISMARINE_SHARD));
        DropTestKit.check(shard != null && shard.source().id().equals(new ResourceLocation("dstest_a", "logs_tag")), "log tag drop " + shard);
        lootDropOf(targetOf(rules.blocks(), "minecraft:stone"), Items.COBBLESTONE);
        DropTestKit.check(findTarget(rules.blocks(), "minecraft:air") == null, "air has a page");
        helper.succeed();
    }

    /**
     * The summary split into small batches, each written and read back, joins into a summary that writes the same
     * bytes as the original; a batch 0 drops a summary cut short.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void batchesJoinBackIntoTheSummary(GameTestHelper helper) {
        SyncedDropRules rules = rules(helper);
        List<DropRulesSyncPacket> packets = DropRulesSyncPacket.split(rules, 2048);
        DropTestKit.check(packets.size() > 3, "expected several batches, got " + packets.size());
        DropRulesAssembler assembler = new DropRulesAssembler();
        DropTestKit.check(assembler.accept(roundTrip(packets.get(0))) == null, "first batch completed the summary");
        SyncedDropRules joined = null;
        for (DropRulesSyncPacket packet : packets) joined = assembler.accept(roundTrip(packet));
        DropTestKit.check(joined != null, "last batch did not complete the summary");
        byte[] original = encode(DropRulesSyncPacket.split(rules, Integer.MAX_VALUE).get(0));
        byte[] rebuilt = encode(DropRulesSyncPacket.split(joined, Integer.MAX_VALUE).get(0));
        DropTestKit.check(Arrays.equals(original, rebuilt), "joined summary differs from the original");
        helper.succeed();
    }

    /**
     * With the real batch size, every batch of the full summary stays within the batch budget, below the 1 MiB custom
     * payload limit.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void batchesStayBelowThePayloadLimit(GameTestHelper helper) {
        checkBatchSizes(DropRulesSyncPacket.split(rules(helper), DropRulesSyncPacket.MAX_BATCH_BYTES));
        helper.succeed();
    }

    /**
     * Drops with large NBT (about 4 KB each, 2.4 MB in all) are split into more, smaller batches, each within the
     * batch budget, instead of one oversized packet.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void largeNbtDropsSplitBySize(GameTestHelper helper) {
        ItemStack heavy = new ItemStack(Items.PAPER);
        heavy.getOrCreateTag().putString("padding", "x".repeat(4000));
        SyncedDrop drop = new SyncedDrop(heavy, 1.0, new DropCount(1, 1), DropBehavior.UNIFORM, EnchantBonus.NONE,
                SyncedDrop.UNTRACKED, List.of(), false, SyncedDropSource.lootTable(new ResourceLocation("entities/test")));
        List<SyncedTarget> mobs = new ArrayList<>();
        for (int i = 0; i < 600; i++) mobs.add(new SyncedTarget(new ResourceLocation("test", "mob_" + i), List.of(drop)));

        List<DropRulesSyncPacket> packets = DropRulesSyncPacket.split(new SyncedDropRules(mobs, List.of()),
                DropRulesSyncPacket.MAX_BATCH_BYTES);
        DropTestKit.check(packets.size() >= 5, "expected at least 5 batches, got " + packets.size());
        checkBatchSizes(packets);
        helper.succeed();
    }

    /**
     * Checks that every batch fits the batch budget plus its small header, so it stays below the payload limit.
     *
     * @param packets Batches. Never null.
     */
    private static void checkBatchSizes(List<DropRulesSyncPacket> packets) {
        for (DropRulesSyncPacket packet : packets) {
            int size = encode(packet).length;
            DropTestKit.check(size <= DropRulesSyncPacket.MAX_BATCH_BYTES + BATCH_HEADER_BYTES && size < PAYLOAD_LIMIT,
                    "batch " + packet.batch() + " is " + size + " bytes");
        }
    }

    /**
     * Writes a packet and reads it back.
     *
     * @param packet Packet. Never null.
     * @return The decoded copy.
     */
    private static DropRulesSyncPacket roundTrip(DropRulesSyncPacket packet) {
        return DropRulesSyncPacket.decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(encode(packet))));
    }

    /**
     * Builds the summary of the loaded files and loot tables.
     *
     * @param helper Helper of the test.
     * @return The summary.
     */
    private static SyncedDropRules rules(GameTestHelper helper) {
        return SyncedDropFactory.rulesOf(helper.getLevel().getServer());
    }

    /**
     * Writes a packet into bytes.
     *
     * @param packet Packet to write. Never null.
     * @return The written bytes.
     */
    private static byte[] encode(DropRulesSyncPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    /**
     * Finds a target, failing the test when it is missing.
     *
     * @param targets Synced targets of one module. Never null.
     * @param id      Target id. Never null.
     * @return The target.
     */
    private static SyncedTarget targetOf(List<SyncedTarget> targets, String id) {
        SyncedTarget target = findTarget(targets, id);
        DropTestKit.check(target != null, id + " not synced");
        return target;
    }

    /**
     * Finds a target.
     *
     * @param targets Synced targets of one module. Never null.
     * @param id      Target id. Never null.
     * @return The target, or null.
     */
    @Nullable
    private static SyncedTarget findTarget(List<SyncedTarget> targets, String id) {
        ResourceLocation targetId = new ResourceLocation(id);
        for (SyncedTarget target : targets) {
            if (target.id().equals(targetId)) return target;
        }
        return null;
    }

    /**
     * Finds the loot table drop of an item, failing the test when it is missing.
     *
     * @param target Target. Never null.
     * @param item   Dropped item. Never null.
     * @return The drop.
     */
    private static SyncedDrop lootDropOf(SyncedTarget target, Item item) {
        SyncedDrop drop = findDrop(target, candidate -> isLoot(candidate) && candidate.stack().is(item));
        DropTestKit.check(drop != null, "no loot table drop of " + item + " on " + target.id());
        return drop;
    }

    /**
     * Finds the vanilla experience of a target.
     *
     * @param target Target. Never null.
     * @return The experience drop, or null.
     */
    @Nullable
    private static SyncedDrop experienceOf(SyncedTarget target) {
        return findDrop(target, drop -> drop.source().kind() == SyncedDropSource.Kind.EXPERIENCE);
    }

    /**
     * Finds the first drop of a target that matches a test.
     *
     * @param target  Target. Never null.
     * @param matches Test of the drop. Never null.
     * @return The drop, or null.
     */
    @Nullable
    private static SyncedDrop findDrop(SyncedTarget target, Predicate<SyncedDrop> matches) {
        for (SyncedDrop drop : target.drops()) {
            if (matches.test(drop)) return drop;
        }
        return null;
    }

    /**
     * Tells whether a drop comes from the target's loot table.
     *
     * @param drop Drop. Never null.
     * @return True for loot table drops.
     */
    private static boolean isLoot(SyncedDrop drop) {
        return drop.source().kind() == SyncedDropSource.Kind.LOOT_TABLE;
    }

    /**
     * Tells whether a drop comes from a dropscore file.
     *
     * @param drop Drop. Never null.
     * @return True for dropscore drops.
     */
    private static boolean isDropscore(SyncedDrop drop) {
        return drop.source().kind() == SyncedDropSource.Kind.DROPSCORE;
    }
}
