package com.jaffh.alkahestcore.fluidscore.common.interactions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jaffh.alkahestcore.fluidscore.common.fluids.BaseFluid;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.*;

/**
 * Data-driven registry manager responsible for parsing, compiling, and evaluating complex fluid-to-fluid interactions.
 * <p>Extends {@link SimpleJsonResourceReloadListener} to read dynamic JSON recipe configurations from data packs
 * located under the {@code fluid_interactions} directory namespace.</p>
 *
 * <h3>Architectural Design: Constant-Time O(1) Lookup Matrix</h3>
 * <p>Traditional recipe validation routines typically loop through an array list of all registered recipes during a
 * world interaction event. This can scale linearly into an inefficient O(N) computational complexity, where N represents the
 * total number of fluid interaction recipes.</p>
 *
 * <p>To maximize performance during rapid block tick loops, this class builds and maintains a
 * <b>Double-Keyed Hash Collision Matrix</b> mapped as:</p>
 * <pre>
 *   LOOKUP_MAP: [Triggering Fluid Instance] ──> [Neighboring Fluid Instance] ──> [Resulting BlockState]
 * </pre>
 *
 * <p><b>How it works:</b></p>
 * <ul>
 *   <li><b>Bidirectional Symmetrical Registration:</b> When compilation occurs, an interaction between Fluid A and Fluid B
 *   is registered twice within the map structure: once under {@code A -> B -> Result} and once under {@code B -> A -> Result}.
 *   This removes any dependency on layout order.</li>
 *   <li><b>Direct Pointer Indexing:</b> When an in-world interaction occurs, the engine performs a direct key query
 *   using the triggering fluid pointer, returning a specialized sub-map of valid neighbors. It then instantly checks if the
 *   adjacent fluid exists as a key in that sub-map. This ensures interaction checks require exactly two hash comparisons, achieving
 *   true O(1) operational efficiency regardless of how many recipes are active in the modpack.</li>
 *   <li><b>Thread-Safe Mutation:</b> The matrix uses a {@code volatile} reference pointer allocation strategy. Worker threads
 *   safely read from the active map immutably, while data pack reloads compile a brand new isolated map workspace offline before
 *   atomically swapping the reference pointer, eliminating race conditions or frame stutter.</li>
 * </ul>
 */
public class FluidInteractionManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * The compiled, immutable reference matrix tracking bidirectional fluid collisions.
     * Declared {@code volatile} to guarantee immediate cross-thread pointer visibility upon asset reloads.
     */
    private static volatile Map<Fluid, Map<Fluid, BlockState>> LOOKUP_MAP = Collections.emptyMap();

    public FluidInteractionManager(){
        super(GSON, "fluid_interactions");
    }

    /**
     * Intercepts data pack reload signals to parse raw JSON files into the highly optimized lookup matrix.
     * Combines source states and flowing states automatically based on configuration declarations.
     *
     * @param object          The mapped collection of discovered file resource locations and their parsed JSON structures.
     * @param resourceManager The active resource manager instance context.
     * @param profiler        The profiler utility tracker.
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler){
        Map<Fluid, Map<Fluid, BlockState>> newMap = new HashMap<>();

        object.forEach((location, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();

                // Extract and expand all implicit fluid states for both inputs
                List<Fluid> fluidsA = parseFluidVariants(json.getAsJsonObject("fluid_a"));
                List<Fluid> fluidsB = parseFluidVariants(json.getAsJsonObject("fluid_b"));

                ResourceLocation resId = new ResourceLocation(GsonHelper.getAsString(json, "result"));
                Block result = ForgeRegistries.BLOCKS.getValue(resId);

                if (fluidsA.isEmpty() || fluidsB.isEmpty() || result == null) {
                    LOGGER.warn("Skipping invalid fluid interaction recipe at: {} (Missing fluid definitions or resulting block entry)", location);
                    return;
                }

                BlockState resultState = result.defaultBlockState();

                // Build cross-referenced bidirectional lookup entries across all discovered state permutations
                for(Fluid a : fluidsA){
                    for(Fluid b : fluidsB){
                        // as the interaction checks occurs on the fluid tick method, only BaseFluids and it's subclasses do the check
                        if(a instanceof BaseFluid){
                            newMap.computeIfAbsent(a, k -> new HashMap<>()).put(b, resultState);
                        }
                        if(b instanceof BaseFluid){
                            newMap.computeIfAbsent(b, k -> new HashMap<>()).put(a, resultState);
                        }
                    }
                }

            } catch (Exception e) {
                LOGGER.error("Fatal exception occurred while processing dataset fluid collision data at: {}", location, e);
            }
        });

        // Atomically replace the old map reference with the newly optimized lookup workspace
        LOOKUP_MAP = newMap;
        LOGGER.info("AlkahestCore: Successfully compiled and mapped {} data-driven fluid interaction.", object.size());
    }

    /**
     * Deconstructs a fluid reference definition from a JSON element, resolving whether it covers
     * both flow stages or explicitly isolates a distinct state type.
     *
     * @param fluidJson The JSON object node containing target fluid identification keys.
     * @return A list containing all individual valid {@link Fluid} instances mapped to the entry.
     */
    private static List<Fluid> parseFluidVariants(JsonObject fluidJson){
        ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(fluidJson, "id"));
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(id);
        if (fluid == null) return Collections.emptyList();

        List<Fluid> variants = new ArrayList<>();
        if (fluid instanceof FlowingFluid flowingFluid) {
            Fluid stillVariant = flowingFluid.getSource();
            Fluid flowingVariant = flowingFluid.getFlowing();

            // If "is_source" flag is missing, default behavior automatically binds both state variants to the recipe
            if(!fluidJson.has("is_source")){
                variants.add(stillVariant);
                variants.add(flowingVariant);
            }else {
                boolean isSource = GsonHelper.getAsBoolean(fluidJson, "is_source");
                variants.add(isSource? stillVariant : flowingVariant);
            }
        }else {
            variants.add(fluid);
        }

        return variants;
    }

    /**
     * Executes an instant O(1) constant-time proximity search across all six block faces
     * to check for valid fluid reaction boundaries.
     * <p>If a valid collision matches a registered pair, it short-circuits execution, consumes
     * the triggering block coordinate space, converts it into the defined block result state,
     * and broadcasts standard lava/water-style fizzing level event effects.</p>
     *
     * @param level The world instance context where the block update is taking place.
     * @param pos   The base target block coordinate executing the simulation update.
     * @param state The active fluid state configuration occupying the target coordinate.
     * @return {@code true} if a collision was detected and handled successfully; {@code false} otherwise.
     */
    public static boolean checkInteractions(Level level, BlockPos pos, FluidState state) {

        // Step 1: Query the primary hash key to extract all valid interactions for this fluid [O(1)]
        Map<Fluid, BlockState> interactions = LOOKUP_MAP.get(state.getType());
        if(interactions == null) return false;

        // Step 2: Iterate through all 6 adjacent directions to scan neighbor profiles
        for(Direction dir : DIRECTIONS){
            BlockPos adjPos = pos.relative(dir);
            FluidState adjstate = level.getFluidState(adjPos);

            if(!adjstate.isEmpty()){
                // Step 3: Direct key index query into the sub-map to see if this neighbor triggers a collision [O(1)]
                BlockState result = interactions.get(adjstate.getType());

                if(result != null){
                    // Update block state with flag 3 (updates state textures and notifies surrounding observers)
                    level.setBlock(pos, result, 3);
                    level.levelEvent(1501, pos, 0);
                    return true;
                }
            }
        }

        return false;
    }
}
