package com.jaffh.alkahestdrops.gametest;

import com.jaffh.alkahestdrops.AlkahestDrops;
import com.jaffh.alkahestdrops.api.AddedDrop;
import com.jaffh.alkahestdrops.api.AlkahestDropsApi;
import com.jaffh.alkahestdrops.api.DropRemoval;
import com.jaffh.alkahestdrops.api.DropTrigger;
import com.jaffh.alkahestdrops.api.TargetDrops;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * GameTests for {@link AlkahestDropsApi}, the read API: added drops with their settings, removals of other namespaces
 * applied as in game, removals listed per trigger, block drops, experience drops, and the server-thread check.
 */
@GameTestHolder(AlkahestDrops.ID)
@PrefixGameTestTemplate(false)
public class ReadApiGameTests {

    /**
     * The silverfish's death drops list the emerald with its file, key, chance, count and trigger.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void readApiListsMobDrops(GameTestHelper helper) {
        TargetDrops view = AlkahestDropsApi.getMobDrops(EntityType.SILVERFISH, DropTrigger.DEATH);
        AddedDrop emerald = dropWithKey(view, "mob:dstest_a:silverfish_on_drop#0");
        DropTestKit.check(emerald != null, "emerald missing from " + view.drops());
        DropTestKit.check(emerald.item() == Items.EMERALD && emerald.chance() == 1.0 && emerald.minCount() == 1
                && emerald.maxCount() == 1 && emerald.trigger() == DropTrigger.DEATH && !emerald.isConditional()
                && !emerald.hasEnchantBonus() && emerald.fileId().toString().equals("dstest_a:silverfish_on_drop"),
                "emerald view " + emerald);
        DropTestKit.check(dropWithKey(view, "mob:dstest_a:any_barrier#0") != null, "the * barrier must apply too");
        helper.succeed();
    }

    /**
     * A drop that another namespace always removes is left out, as in game; both removal files of the husk are listed
     * and match their items.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void readApiAppliesOtherNamespaceRemovals(GameTestHelper helper) {
        TargetDrops view = AlkahestDropsApi.getMobDrops(EntityType.HUSK, DropTrigger.DEATH);
        DropTestKit.check(dropWithKey(view, "mob:dstest_a:husk_flesh_and_diamond#0") != null, "the flesh must stay");
        DropTestKit.check(dropWithKey(view, "mob:dstest_a:husk_flesh_and_diamond#1") == null, "the diamond must be removed");
        DropRemoval diamond = removalOf(view, "dstest_b:husk_remove_diamond");
        DropRemoval flesh = removalOf(view, "dstest_a:husk_remove_flesh");
        DropTestKit.check(diamond != null && "minecraft:diamond".equals(diamond.items()) && diamond.removes(Items.DIAMOND)
                && !diamond.removes(Items.ROTTEN_FLESH) && !diamond.isConditional(), "diamond removal " + diamond);
        DropTestKit.check(flesh != null && flesh.removes(Items.ROTTEN_FLESH), "flesh removal " + flesh);
        helper.succeed();
    }

    /**
     * Each trigger reads its own drops and removals: the sheep's shear drops and shear removal are not part of its death
     * drops, and the other way round.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void readApiReadsEachTrigger(GameTestHelper helper) {
        TargetDrops shear = AlkahestDropsApi.getMobDrops(EntityType.SHEEP, DropTrigger.SHEAR);
        TargetDrops death = AlkahestDropsApi.getMobDrops(EntityType.SHEEP, DropTrigger.DEATH);
        AddedDrop string = dropWithKey(shear, "mob:dstest_a:sheep_shear#0");
        DropTestKit.check(string != null && string.trigger() == DropTrigger.SHEAR, "shear string " + string);
        DropTestKit.check(dropWithKey(shear, "mob:dstest_a:sheep_shear#1") == null, "the shear emerald must be removed");
        DropTestKit.check(dropWithKey(death, "mob:dstest_a:sheep_shear#0") == null, "shear drops must not be death drops");
        DropTestKit.check(removalOf(shear, "dstest_b:sheep_shear_remove_emerald") != null, "shear removal missing");
        DropTestKit.check(removalOf(shear, "dstest_b:sheep_death_remove_string") == null, "death removal listed on shear");
        DropTestKit.check(removalOf(death, "dstest_b:sheep_death_remove_string") != null, "death removal missing");
        helper.succeed();
    }

    /**
     * Block drops, experience drops with an enchantment bonus, and conditional {@code *} removals of a tag file.
     *
     * @param helper Helper of the test.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void readApiReadsBlocksExperienceAndTags(GameTestHelper helper) {
        AddedDrop diamond = dropWithKey(AlkahestDropsApi.getBlockDrops(Blocks.SANDSTONE), "block:dstest_a:sandstone_diamond#0");
        DropTestKit.check(diamond != null && diamond.item() == Items.DIAMOND, "sandstone diamond " + diamond);

        AddedDrop experience = dropWithKey(AlkahestDropsApi.getMobDrops(EntityType.CHICKEN, DropTrigger.DEATH),
                "mob:dstest_a:chicken_looting_experience#0");
        DropTestKit.check(experience != null && experience.isExperience() && experience.minCount() == 4
                && experience.hasEnchantBonus(), "chicken experience " + experience);

        DropRemoval wiped = removalOf(AlkahestDropsApi.getMobDrops(EntityType.COW, DropTrigger.DEATH), "dstest_b:wiped_unless_player");
        DropTestKit.check(wiped != null && "*".equals(wiped.items()) && wiped.isConditional() && wiped.removes(Items.BEEF),
                "cow tag removal " + wiped);
        helper.succeed();
    }

    /**
     * Reading from another thread is refused, since the drop tables are built on the server thread.
     *
     * @param helper Helper of the test.
     * @throws InterruptedException If the test thread is interrupted while waiting.
     */
    @GameTest(template = DropTestKit.ARENA)
    public static void readApiRefusesOtherThreads(GameTestHelper helper) throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                AlkahestDropsApi.getBlockDrops(Blocks.STONE);
            } catch (Throwable e) {
                thrown.set(e);
            }
        });
        reader.start();
        reader.join();
        DropTestKit.check(thrown.get() instanceof IllegalStateException, "expected IllegalStateException, got " + thrown.get());
        helper.succeed();
    }

    /**
     * Finds an added drop by its key.
     *
     * @param view View of a target. Never null.
     * @param key  Drop key. Never null.
     * @return The drop, or null when the view lacks it.
     */
    @Nullable
    private static AddedDrop dropWithKey(TargetDrops view, String key) {
        return view.drops().stream().filter(drop -> drop.dropKey().equals(key)).findFirst().orElse(null);
    }

    /**
     * Finds the first removal entry of a file.
     *
     * @param view   View of a target. Never null.
     * @param fileId Removal file id, such as {@code dstest_b:husk_remove_diamond}. Never null.
     * @return The entry, or null when the view lacks it.
     */
    @Nullable
    private static DropRemoval removalOf(TargetDrops view, String fileId) {
        return view.removals().stream().filter(removal -> removal.fileId().toString().equals(fileId)).findFirst().orElse(null);
    }
}
