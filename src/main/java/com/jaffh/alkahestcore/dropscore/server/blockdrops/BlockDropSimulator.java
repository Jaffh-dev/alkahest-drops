package com.jaffh.alkahestcore.dropscore.server.blockdrops;

import com.jaffh.alkahestcore.dropscore.server.drops.DropTable;
import com.jaffh.alkahestcore.dropscore.server.drops.IDropSimulator;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulatedDrop;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationActor;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationFlag;
import com.jaffh.alkahestcore.dropscore.server.drops.SimulationSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Simulates breaks of one block state without placing it: the block's loot table (with loot modifiers, dropscore's
 * included) or, for blocks without a loot table, the player-break fallback, then the experience. The breaker is a
 * fake player holding a copy of the real player's tool, nobody (a netherite pickaxe, no player) or a TNT explosion.
 * A tool that cannot harvest the block drops nothing, as in survival. Creative mode is ignored.
 */
public final class BlockDropSimulator implements IDropSimulator {
    /** Radius of a TNT explosion; {@code survives_explosion} keeps a drop with a chance of 1 / radius. */
    private static final float TNT_EXPLOSION_RADIUS = 4.0F;

    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final SimulationActor actor;
    @Nullable
    private final Player player;
    private final ItemStack tool;
    @Nullable
    private final LootTable lootTable;
    private final DropTable table;
    private final boolean canHarvest;

    /**
     * Creates a simulator from its resolved parts.
     *
     * @param setup  Level, position and actor of the simulation. Never null.
     * @param state  Broken block state. Never null.
     * @param player Breaking player, or null for nobody or an explosion.
     * @param tool   Tool of every break, with the forced Fortune level applied; empty for an explosion. Never null.
     */
    private BlockDropSimulator(SimulationSetup setup, BlockState state, @Nullable Player player, ItemStack tool) {
        this.level = setup.level();
        this.pos = new BlockPos(setup.origin());
        this.state = state;
        this.actor = setup.actor();
        this.player = player;
        this.tool = tool;
        this.lootTable = state.getBlock().getLootTable() == BuiltInLootTables.EMPTY
                ? null
                : level.getServer().getLootTables().get(state.getBlock().getLootTable());
        this.table = BlockDropManager.getTable(state.getBlock());
        this.canHarvest = actor instanceof SimulationActor.Explosion || canHarvest(player, tool);
    }

    /**
     * Creates a simulator for a block state.
     *
     * @param state Block state to break. Never null.
     * @param setup Level, position, actor, flags and forced Fortune of the simulation. Never null.
     * @return The simulator.
     * @throws IllegalArgumentException If the setup does not fit a block break: an entity actor, {@code burning},
     *                                  {@code sneaking} without a player, or Fortune without a tool.
     */
    public static BlockDropSimulator create(BlockState state, SimulationSetup setup) {
        SimulationActor actor = getActor(setup);

        Player player = actor instanceof SimulationActor.AsPlayer asPlayer ? setup.createPlayer(asPlayer) : null;
        ItemStack tool = toolOf(actor, player);
        if (setup.enchantLevel().isPresent()) {
            if (tool.isEmpty()) throw new IllegalArgumentException("Fortune needs a tool: hold one in the main hand");
            setFortune(tool, setup.enchantLevel().getAsInt());
        }
        return new BlockDropSimulator(setup, state, player, tool);
    }

    /**
     * Returns the actor of the setup after checking that it and the flags fit a block break.
     *
     * @param setup Setup of the simulation. Never null.
     * @return The actor: a player, nobody or an explosion.
     * @throws IllegalArgumentException If the actor is an entity, {@code burning} is set, or {@code sneaking} is set
     *                                  without a player.
     */
    private static SimulationActor getActor(SimulationSetup setup) {
        SimulationActor actor = setup.actor();
        if (actor instanceof SimulationActor.AsEntity) throw new IllegalArgumentException("blocks are broken by 'player', 'none' or 'explosion'");
        if (setup.has(SimulationFlag.BURNING)) throw new IllegalArgumentException("'burning' only applies to mobs");
        if (setup.has(SimulationFlag.SNEAKING) && !(actor instanceof SimulationActor.AsPlayer)) {
            throw new IllegalArgumentException("'sneaking' needs a player: use 'by player'");
        }
        return actor;
    }

    /**
     * Returns the tool of the actor. A player's tool is the fake player's own main-hand stack, so equipment
     * conditions see the same Fortune as the loot context.
     *
     * @param actor  Actor of the simulation. Never null.
     * @param player Fake player of a player actor, or null.
     * @return The tool; a netherite pickaxe for nobody, empty for an explosion.
     */
    private static ItemStack toolOf(SimulationActor actor, @Nullable Player player) {
        if (player != null) return player.getMainHandItem();
        if (actor instanceof SimulationActor.Explosion) return ItemStack.EMPTY;
        return new ItemStack(Items.NETHERITE_PICKAXE);
    }

    /**
     * Sets the Fortune level of a tool, replacing the one it has.
     *
     * @param tool         Tool to enchant. Never null, not empty.
     * @param fortuneLevel Level to set; 0 removes Fortune.
     */
    private static void setFortune(ItemStack tool, int fortuneLevel) {
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(tool);
        if (fortuneLevel > 0) {
            enchantments.put(Enchantments.BLOCK_FORTUNE, fortuneLevel);
        } else {
            enchantments.remove(Enchantments.BLOCK_FORTUNE);
        }
        EnchantmentHelper.setEnchantments(enchantments, tool);
    }

    /**
     * Tells whether the breaker gets drops, as in survival.
     *
     * @param player Breaking player, or null.
     * @param tool   Tool of the break. Never null.
     * @return True when the block needs no tool or the tool is correct (with Forge's harvest check for players).
     */
    private boolean canHarvest(@Nullable Player player, ItemStack tool) {
        if (player != null) return state.canHarvestBlock(level, pos, player);
        return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    }

    /**
     * Rolls one break: nothing when the tool cannot harvest the block, otherwise the drops and the experience.
     *
     * @return The items and experience of the break.
     */
    @Override
    public SimulatedDrop simulateOnce() {
        if (!canHarvest) return new SimulatedDrop(List.of(), 0);

        LootContext.Builder builder = BlockBreakHandler.lootContextBuilder(level, pos, state, player, tool);
        if (actor instanceof SimulationActor.Explosion) {
            builder.withParameter(LootContextParams.EXPLOSION_RADIUS, TNT_EXPLOSION_RADIUS);
        }
        LootContext context = builder.create(LootContextParamSets.BLOCK);
        return new SimulatedDrop(rollItems(context), rollExperience(context));
    }

    /**
     * Rolls the items of one break. The loot table runs loot modifiers, so dropscore's own modifier applies the
     * block's drop table; blocks without a loot table use the fallback of player breaks.
     *
     * @param context Loot context of the break. Never null.
     * @return The dropped stacks.
     */
    private List<ItemStack> rollItems(LootContext context) {
        if (lootTable != null) return lootTable.getRandomItems(context);
        if (player == null || table.dropFiles().isEmpty()) return List.of();
        return BlockDropApplier.apply(table, List.of(), context);
    }

    /**
     * Returns the experience of one break. As in vanilla, only player breaks give experience.
     *
     * @param context Loot context of the break. Never null.
     * @return The experience, 0 without a player.
     */
    private int rollExperience(LootContext context) {
        if (player == null) return 0;
        int fortuneLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
        int silkTouchLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool);
        int experience = state.getExpDrop(level, pos, fortuneLevel, silkTouchLevel);
        if (!table.affectsExperience()) return experience;
        return table.applyExperience(experience, context, BlockDropApplier.rollContextOf(context));
    }

    /**
     * Returns the name of the simulated block.
     *
     * @return The translated block name.
     */
    @Override
    public Component targetName() {
        return state.getBlock().getName();
    }

    /**
     * Describes the breaker, the tool and the Fortune level, and warns when the tool cannot harvest the block.
     *
     * @return Such as {@code "player: Dev, sneaking, tool: Diamond Pickaxe, Fortune 3"} or {@code "TNT explosion"}.
     */
    @Override
    public String describeSetup() {
        if (actor instanceof SimulationActor.Explosion) return "TNT explosion (no tool, no experience)";

        StringBuilder text = new StringBuilder();
        if (player != null) {
            text.append("player: ").append(player.getName().getString());
            if (player.isCrouching()) text.append(", sneaking");
        } else {
            text.append("no player (no experience)");
        }
        text.append(", tool: ").append(tool.isEmpty() ? "empty hand" : tool.getHoverName().getString())
                .append(", Fortune ").append(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool));
        if (!canHarvest) text.append(", cannot harvest it: nothing drops");
        return text.toString();
    }
}
