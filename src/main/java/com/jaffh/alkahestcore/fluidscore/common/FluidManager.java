package com.jaffh.alkahestcore.fluidscore.common;

import com.jaffh.alkahestcore.fluidscore.common.blocks.CustomLiquidBlock;
import com.jaffh.alkahestcore.fluidscore.common.fluids.AdvancedFluid;
import com.jaffh.alkahestcore.fluidscore.common.fluids.BaseFluid;
import com.jaffh.alkahestcore.fluidscore.common.fluids.CustomFluid;
import com.jaffh.alkahestcore.fluidscore.common.hooks.AdvancedFluidHook;
import com.jaffh.alkahestcore.fluidscore.common.hooks.BaseFluidHook;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The primary orchestration engine for fluid registration within the AlkahestCore framework.
 * <p>This manager unifies and abstracts the simultaneous creation of a fluid's lifecycle components:
 * It's <b>Still Source</b>, <b>Flowing Stream</b>, physical <b>Liquid Block</b>, and inventory <b>Bucket Item</b>.
 * It enforces rigid naming conventions and automatically wires deferred registrations directly to the
 * appropriate mod lifecycle event bus, drastically reducing boilerplate code while preventing registry desynchronization.</p>
 */
public class FluidManager {
    // The designated namespace (Mod ID) under which all entries are registered
    private final String MOD_ID;

    // Deferred registry tracking registries for fluids, physical boundaries, and items
    private final DeferredRegister<Fluid> FLUIDS;
    private final DeferredRegister<Block> BLOCKS;
    private final DeferredRegister<Item> ITEMS;

    private final List<FluidContainer> containers = new ArrayList<>();

    /**
     * Initializes the manager engine for a specific mod context.
     * Registers internal deferred registries into the active lifecycle of the provided event bus.
     *
     * @param modID        The target namespace string (e.g., "alkahestcore") used for registry paths.
     * @param modEventBus  The unique, FML-provided mod event bus responsible for firing registry events.
     */
    public FluidManager(String modID, IEventBus modEventBus){
        this.MOD_ID = modID;

        this.FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, modID);
        this.BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, modID);
        this.ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, modID);

        FLUIDS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    /**
     * Registers a standard framework fluid backed by custom behavior interceptor hooks.
     *
     * @param attributes       The cosmetic, physical, and technical attributes assigned to the fluid.
     * @param baseHookFactory  A factory callback mapping the newly initialized fluid to its behavior hooks.
     * @param blockFactory     A factory generating the custom {@link LiquidBlock} instance for world interactions.
     * @param bucketFactory    A factory generating the custom {@link BucketItem} container instance.
     * @return A {@link FluidContainer} holding lazy-evaluated registry wrappers for all four created components.
     */
    public <T extends BaseFluid> FluidContainer registerBaseFluid(
            BaseFluid.Attributes attributes,
            Function<BaseFluid, BaseFluidHook> baseHookFactory,
            Function<RegistryObject<Fluid>, ? extends LiquidBlock> blockFactory,
            Function<RegistryObject<Fluid>, ? extends BucketItem> bucketFactory
    ){
        return registerInternal(attributes,
                (props, attrs) -> new BaseFluid.Source(props, attrs, baseHookFactory) {
                },
                (props, attrs) -> new BaseFluid.Flowing(props, attrs, baseHookFactory),
                blockFactory, bucketFactory
        );
    }

    /**
     * Registers an advanced physics-oriented fluid capable of overriding microphysics, custom flow calculations,
     * and complex rendering boundaries.
     *
     * @param attributes          The cosmetic, physical, and technical attributes assigned to the fluid.
     * @param baseHookFactory     A factory callback supplying base simulation hook configurations.
     * @param advancedHookFactory A factory callback supplying extended advanced microphysics behavior overrides.
     * @param blockFactory        A factory generating the custom {@link LiquidBlock} instance for world interactions.
     * @param bucketFactory       A factory generating the custom {@link BucketItem} container instance.
     * @return A {@link FluidContainer} holding lazy-evaluated registry wrappers for all four created components.
     */
    public <T extends AdvancedFluid> FluidContainer registerAdvancedFluid(
            BaseFluid.Attributes attributes,
            Function<BaseFluid, BaseFluidHook> baseHookFactory,
            Function<AdvancedFluid, AdvancedFluidHook> advancedHookFactory,
            Function<RegistryObject<Fluid>, ? extends LiquidBlock> blockFactory,
            Function<RegistryObject<Fluid>, ? extends BucketItem> bucketFactory
    ) {
        return registerInternal(attributes,
                (props, attrs) -> new AdvancedFluid.Source(props, attrs, baseHookFactory, advancedHookFactory),
                (props, attrs) -> new AdvancedFluid.Flowing(props, attrs, baseHookFactory, advancedHookFactory),
                blockFactory, bucketFactory
                );
    }

    /**
     * Registers a completely configuration-driven fluid instance, utilizing standardized, out-of-the-box
     * default factories for blocks and buckets to allow rapid asset generation with zero boilerplate.
     *
     * @param attributes  The cosmetic, physical, and technical attributes assigned to the fluid.
     * @param configs     The data-driven properties defining world hazards, damage scaling, and item retention rules.
     * @param creativeTab The creative inventory tab target where the resulting bucket item should reside.
     * @return A {@link FluidContainer} holding lazy-evaluated registry wrappers for all four created components.
     */
    public <T extends CustomFluid> FluidContainer registerCustomFluid(
            BaseFluid.Attributes attributes,
            CustomFluid.Configs configs,
            CreativeModeTab creativeTab
    ){
        Function<RegistryObject<Fluid>, LiquidBlock> defaultBlock = (stillFluid) ->
                new CustomLiquidBlock(stillFluid,
                        BlockBehaviour.Properties.of(Material.WATER)
                                .noCollission()
                                .strength(100.0f)
                                .noDrops()
                                .lightLevel(state -> attributes.luminosity()), configs);

        Function<RegistryObject<Fluid>, BucketItem> defaultBucket = (stillFluid) ->
                new BucketItem(stillFluid, new Item.Properties().stacksTo(configs.bucketStackSize()).tab(creativeTab).craftRemainder(Items.BUCKET));

        BiFunction<BaseFluid.Properties, BaseFluid.Attributes, CustomFluid> stillFactory = (props, attrs) -> new CustomFluid.Source(props, attrs, configs);
        BiFunction<BaseFluid.Properties, BaseFluid.Attributes, CustomFluid> flowingFactory = (props, attrs) -> new CustomFluid.Flowing(props, attrs, configs);

        return registerInternal(attributes, stillFactory, flowingFactory, defaultBlock, defaultBucket);
    }

    /**
     * Internal factory hub routing. Orchestrates resource key assembly, constructs foundational
     * properties, and securely submits allocation queries into the deferred registry queues.
     *
     * @param attributes     The source attribute profiles.
     * @param stillFactory   The internal provider mapping properties to a source liquid object.
     * @param flowingFactory The internal provider mapping properties to a flowing liquid object.
     * @param blockFactory   The factory generating the associated physical block object.
     * @param bucketFactory  The factory generating the associated container bucket item object.
     * @return A compiled {@link FluidContainer} containing all registered entries.
     */
    private <T extends Fluid> FluidContainer registerInternal(
            BaseFluid.Attributes attributes,
            BiFunction<BaseFluid.Properties, BaseFluid.Attributes, T> stillFactory,
            BiFunction<BaseFluid.Properties, BaseFluid.Attributes, T> flowingFactory,
            Function<RegistryObject<Fluid>, ? extends LiquidBlock> blockFactory,
            Function<RegistryObject<Fluid>, ? extends BucketItem> bucketFactory
    ){
        String name = attributes.fluidName();

        ResourceLocation stillId = new ResourceLocation(MOD_ID, name);
        ResourceLocation flowingId = new ResourceLocation(MOD_ID, name + "_flowing");

        RegistryObject<Fluid> stillHolder = RegistryObject.create(stillId, ForgeRegistries.FLUIDS);
        RegistryObject<Fluid> flowingHolder = RegistryObject.create(flowingId, ForgeRegistries.FLUIDS);

        RegistryObject<Block> blockHolder = RegistryObject.create(new ResourceLocation(MOD_ID, name + "_block"), ForgeRegistries.BLOCKS);
        RegistryObject<Item> bucketHolder = RegistryObject.create(new ResourceLocation(MOD_ID, name + "_bucket"), ForgeRegistries.ITEMS);

        BaseFluid.Properties properties = new BaseFluid.Properties(
                this.MOD_ID, stillHolder, flowingHolder, bucketHolder, () -> (LiquidBlock) blockHolder.get()
        );

        FLUIDS.register(name, () -> stillFactory.apply(properties, attributes));
        FLUIDS.register(name + "_flowing", () -> flowingFactory.apply(properties, attributes));
        BLOCKS.register(name + "_block", () -> blockFactory.apply(stillHolder));
        ITEMS.register(name + "_bucket", () -> bucketFactory.apply(stillHolder));

        FluidContainer container = new FluidContainer(stillHolder, flowingHolder, blockHolder, bucketHolder);

        this.containers.add(container);

        return container;
    }

    /**
     * An immutable tracking record container designed to capture and hold lazy-evaluated
     * registry references for an entire fluid suite execution loop.
     *
     * @param still   The registry lookup handle tracking the non-flowing source fluid block.
     * @param flowing The registry lookup handle tracking the flowing streaming variations.
     * @param block   The registry lookup handle tracking the physical block placement inside worlds.
     * @param bucket  The registry lookup handle tracking the inventory fluid container item.
     */
    public record FluidContainer(
            RegistryObject<Fluid> still,
            RegistryObject<Fluid> flowing,
            RegistryObject<Block> block,
            RegistryObject<Item> bucket
    ){}

    public List<FluidContainer> getContainers() {
        return this.containers;
    }
}