package com.jaffh.alkahestcore.dropscore.common.mobdrops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.*;


public class MobDropManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Gson LOOT_GSON = Deserializers.createConditionSerializer().create();

    private static final Map<String, List<MobDropDefinition>> REGISTRY = new HashMap<>();

    private static final Map<String, Set<Item>> REMOVALS = new HashMap<>();

    public static List<MobDropDefinition> getRulesForEntity(String entityId) {
        return REGISTRY.getOrDefault(entityId, Collections.emptyList());
    }

    public static Set<Item> getRemovalsForEntity(String entityId) {
        return REMOVALS.getOrDefault(entityId, Collections.emptySet());
    }

    public MobDropManager(){
        super(GSON, "mob_drops");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {

        REGISTRY.clear();
        REMOVALS.clear();

        for(Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()){
            ResourceLocation fileId = entry.getKey();
            JsonElement jsonElement = entry.getValue();

            try{
                MobDropJsonStructure jsonStructure = GSON.fromJson(jsonElement, MobDropJsonStructure.class);

                if(!validateJsonStructure(jsonStructure, fileId)) continue;


                EntityType<?> entityType = getEntity(jsonStructure, fileId);
                if(entityType == null) continue;

                Item dropItem = getDropItem(jsonStructure, fileId);
                if(dropItem == null) continue;

                registerRemovals(jsonStructure, fileId);

                MobDropDefinition definition = new MobDropDefinition();
                definition.entityType = entityType;
                definition.drop = dropItem;
                definition.nbtTag = getNbt(jsonStructure, fileId);
                definition.chance = jsonStructure.chance;
                definition.count = jsonStructure.count;
                definition.dropBehaviour = getDropBehaviour(jsonStructure, fileId);
                definition.onLooting = jsonStructure.onLooting;
                definition.conditions = getConditions(jsonStructure, fileId);

                REGISTRY.computeIfAbsent(jsonStructure.entity, k -> new ArrayList<>()).add(definition);
            } catch (Exception e){
                LOGGER.error("Erro critico de parsing ao ler o arquivo de drop '{}': ", fileId, e);
            }
        }

        filterRegistryWithRemovals();

        int totalRules = REGISTRY.values().stream().mapToInt(List::size).sum();
        LOGGER.info("Alkahest Core carregou com sucesso {} regras de mob drops customizados.", totalRules);
    }

    private static boolean validateJsonStructure(MobDropJsonStructure jsonStructure, ResourceLocation fileId){
        if(jsonStructure == null) return false;
        if(jsonStructure.entity == null || jsonStructure.drop == null){
            LOGGER.error("Falha ao carregar o drop do arquivo '{}': Os campos 'entity' e 'drop' sao obrigatorios.", fileId);
            return false;
        }

        return true;
    }

    private static EntityType<?> getEntity(MobDropJsonStructure jsonStructure, ResourceLocation fileId){
        ResourceLocation entityResource = new ResourceLocation(jsonStructure.entity);

        if(ForgeRegistries.ENTITIES.containsKey(entityResource)){
            return ForgeRegistries.ENTITIES.getValue(entityResource);
        }

        LOGGER.warn("Arquivo '{}' ignorado: A entidade '{}' nao esta registrada no jogo.", fileId, jsonStructure.entity);
        return null;
    }

    private static Item getDropItem(MobDropJsonStructure jsonStructure, ResourceLocation fileId){
        ResourceLocation itemResource = new ResourceLocation(jsonStructure.drop);

        if(ForgeRegistries.ITEMS.containsKey(itemResource)){
            return ForgeRegistries.ITEMS.getValue(itemResource);
        }

        LOGGER.warn("Arquivo '{}' ignorado: O item de drop '{}' nao esta registrado no jogo.", fileId, jsonStructure.drop);
        return null;
    }

    private static MobDropDefinition.DropBehaviour getDropBehaviour(MobDropJsonStructure jsonStructure, ResourceLocation fileId){
        MobDropJsonStructure.DropBehaviour jsonBehaviour = jsonStructure.dropBehaviour;

        if(jsonBehaviour == null) return null;

        MobDropDefinition.DropBehaviour dropBehaviour = new MobDropDefinition.DropBehaviour();
        dropBehaviour.rolls = jsonBehaviour.rolls;
        dropBehaviour.chancePerRoll = jsonBehaviour.chancePerRoll;
        dropBehaviour.incrementValue = jsonBehaviour.incrementValue;

        DropBehaviourType type = DropBehaviourType.UNIFORM;

        if(jsonBehaviour.type != null){

            String sanitizedType = jsonBehaviour.type.trim().toUpperCase(Locale.ROOT);
            try {
                type = DropBehaviourType.valueOf(sanitizedType);
            } catch (IllegalArgumentException e){
                LOGGER.warn("No arquivo '{}': O comportamento '{}' e invalido.", fileId, jsonBehaviour.type);
            }
        }

        dropBehaviour.type = type;
        return dropBehaviour;
    }

    private static CompoundTag getNbt(MobDropJsonStructure jsonStructure, ResourceLocation fileId){
        if(jsonStructure.nbt != null && !jsonStructure.nbt.isJsonNull()){
            try {
                String nbtString;

                if(jsonStructure.nbt.isJsonPrimitive()){
                    nbtString = jsonStructure.nbt.getAsString();
                }

                else {
                    nbtString = jsonStructure.nbt.toString();
                }

                return TagParser.parseTag(nbtString);
            } catch (CommandSyntaxException e){
                LOGGER.error("Erro de sintaxe NBT no arquivo '{}': {}", fileId, e.getMessage());
            }
        }

        return null;
    }

    private static LootItemCondition[] getConditions(MobDropJsonStructure jsonStructure, ResourceLocation fileId){
        if(jsonStructure.conditions != null && !jsonStructure.conditions.isEmpty()){
            return LOOT_GSON.fromJson(jsonStructure.conditions, LootItemCondition[].class);
        }

        return new LootItemCondition[0];
    }

    private static void registerRemovals(MobDropJsonStructure jsonStructure, ResourceLocation fileId){
        if(jsonStructure.replaceDrops != null && !jsonStructure.replaceDrops.isEmpty()){

            Set<Item> removalSet = REMOVALS.computeIfAbsent(jsonStructure.entity, k -> new HashSet<>());

            for(String replaceId : jsonStructure.replaceDrops){
                ResourceLocation replaceResource = new ResourceLocation(replaceId);

                if(ForgeRegistries.ITEMS.containsKey(replaceResource)){
                    removalSet.add(ForgeRegistries.ITEMS.getValue(replaceResource));
                } else{
                    LOGGER.warn("No arquivo '{}': O item de substituicao '{}' nao existe no jogo.", fileId, replaceId);
                }
            }
        }
    }

    private static void filterRegistryWithRemovals(){

        for(Map.Entry<String, Set<Item>> entry : REMOVALS.entrySet()){
            String entityId = entry.getKey();
            Set<Item> itemsToRemove = entry.getValue();

            List<MobDropDefinition> customRules = REGISTRY.get(entityId);
            if(customRules != null && !customRules.isEmpty()){
                customRules.removeIf(definition -> itemsToRemove.contains(definition.drop));
            }
        }
    }
}
