package com.jaffh.alkahestcore.dropscore.common.mobdrops;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.*;

public class MobDropInjector {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMobDrop(LivingDropsEvent event){


        if(!(event.getEntityLiving().level instanceof ServerLevel serverLevel)) return;

        LivingEntity entity = event.getEntityLiving();
        if(entity == null) return;

        String entityId = ForgeRegistries.ENTITIES.getKey(entity.getType()).toString();
        Collection<ItemEntity> currentDrops = event.getDrops();

        removeItems(currentDrops, entityId);

        List<MobDropDefinition> rules = MobDropManager.getRulesForEntity(entityId);
        if(rules == null || rules.isEmpty()) return;

        int lootingLevel = event.getLootingLevel();
        Random random = serverLevel.getRandom();

        List<MobDropDefinition> activeRules = validateConditions(rules, serverLevel, event);
        if(activeRules.isEmpty()) return;

        for(MobDropDefinition rule : activeRules){
            ItemStack dropStack;

            if(lootingLevel > 0 && rule.onLooting != null){
                dropStack = lootingDrop(rule, random, lootingLevel);
            }else {
                dropStack = noLootingDrop(rule, random);
            }

            if (dropStack != null && !dropStack.isEmpty()){
                ItemEntity itemEntity = new ItemEntity(
                        serverLevel,
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        dropStack
                );
                currentDrops.add(itemEntity);
            }
        }
    }

    private static LootContext createLootContext(ServerLevel level, LivingDropsEvent event){
        LootContext.Builder builder = new LootContext.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, event.getEntityLiving())
                .withParameter(LootContextParams.ORIGIN, event.getEntityLiving().position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, event.getSource().getEntity());

        if(event.getSource().getEntity() instanceof Player player){
            builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
        }

        return builder.create(LootContextParamSets.ENTITY);
    }

    private static List<MobDropDefinition> validateConditions(List<MobDropDefinition> originalRules, ServerLevel level, LivingDropsEvent event){

        boolean needsContext = false;
        for(MobDropDefinition rule : originalRules){
            if(rule.conditions != null && rule.conditions.length > 0){
                needsContext = true;
                break;
            }
        }

        if(!needsContext){
            return originalRules;
        }

        LootContext lootContext = createLootContext(level,event);
        List<MobDropDefinition> validRules = new ArrayList<>(originalRules.size());

        for(MobDropDefinition rule : originalRules){
            boolean matches = true;

            if(rule.conditions != null){
                for(LootItemCondition condition : rule.conditions){
                    if(!condition.test(lootContext)){
                        matches = false;
                        break;
                    }
                }
            }

            if(matches){
                validRules.add(rule);
            }
        }

        return validRules;
    }

    private static void removeItems(Collection<ItemEntity> drops, String entityId){
        Set<Item> removals = MobDropManager.getRemovalsForEntity(entityId);

        if(removals.isEmpty()) return;

        drops.removeIf(itemEntity -> removals.contains(itemEntity.getItem().getItem()));
    }

    private static ItemStack lootingDrop(MobDropDefinition dropDefinition, Random random, int lootingLevel){

        MobDropJsonStructure.OnLooting looting = dropDefinition.onLooting;

        double chance = calculateChance(dropDefinition.chance, looting.bonusChance, lootingLevel);

        if(random.nextDouble() > chance){
            return ItemStack.EMPTY;
        }

        int min = calculateMin(dropDefinition.count.min, looting.bonusCount, lootingLevel);
        int max = calculateMax(dropDefinition.count.max, looting.bonusCount, lootingLevel);
        int finalCount = min;

        DropBehaviourType dropType = dropDefinition.dropBehaviour != null ? dropDefinition.dropBehaviour.type : DropBehaviourType.UNIFORM;

        if(dropType == DropBehaviourType.UNIFORM){
            finalCount = calculateUniform(min, max, random);
        }else {
            int rolls = calculateRolls(dropDefinition.dropBehaviour.rolls, looting.bonusRoll, lootingLevel);
            double rollChance = calculateRollChance(dropDefinition.dropBehaviour.chancePerRoll, looting.bonusRoll, lootingLevel);
            int incrementValue = dropDefinition.dropBehaviour.incrementValue;

            if (dropType == DropBehaviourType.FIXED_ROLLS) {
                finalCount = calculateFixedRolls(min, max, rolls, incrementValue, rollChance, random);
            } else if (dropType == DropBehaviourType.CHAINED_ROLLS) {
                finalCount = calculateChainedRolls(min, max, rolls, incrementValue, rollChance, random);
            }
        }

        return createFinalStack(dropDefinition, finalCount);
    }

    private static ItemStack noLootingDrop(MobDropDefinition dropDefinition, Random random){

        double chance = dropDefinition.chance;

        if(random.nextDouble() > chance){
            return ItemStack.EMPTY;
        }

        int min = dropDefinition.count.min;
        int max = dropDefinition.count.max;
        int finalCount = min;

        DropBehaviourType dropType = dropDefinition.dropBehaviour != null ? dropDefinition.dropBehaviour.type : DropBehaviourType.UNIFORM;

        if(dropType == DropBehaviourType.UNIFORM){
            finalCount = calculateUniform(min, max, random);
        } else{
            int rolls = dropDefinition.dropBehaviour.rolls;
            double rollChance = dropDefinition.dropBehaviour.chancePerRoll;
            int incrementValue = dropDefinition.dropBehaviour.incrementValue;

            if (dropType == DropBehaviourType.FIXED_ROLLS) {
                finalCount = calculateFixedRolls(min, max, rolls, incrementValue, rollChance, random);
            } else if (dropType == DropBehaviourType.CHAINED_ROLLS) {
                finalCount = calculateChainedRolls(min, max, rolls, incrementValue, rollChance, random);
            }
        }

        return createFinalStack(dropDefinition, finalCount);
    }

    private static int calculateUniform(int min, int max, Random random){
        if(min >= max) return min;

        return min + random.nextInt((max - min) + 1);
    }

    private static int calculateFixedRolls(int min, int max, int rolls, int incrementValue, double chancePerRoll, Random random){
        int totalItems = min;

        for(int i = 0; i<rolls; i++){
            if (totalItems >= max) break;
            if(random.nextDouble() < chancePerRoll){
                totalItems += incrementValue;
            }
        }

        return Math.min(totalItems, max);
    }

    private static int calculateChainedRolls(int min, int max, int rolls, int incrementValue, double chancePerRoll, Random random){
        int totalItems = min;

        for(int i = 0; i<rolls; i++){
            if (totalItems >= max) break;
            if(random.nextDouble() < chancePerRoll){
                totalItems += incrementValue;
            }else{
                break;
            }
        }

        return Math.min(totalItems, max);
    }

    private static double calculateChance(double chance, MobDropJsonStructure.BonusChance bonusChance, int lootingLevel){
        if(bonusChance != null){
            double bonus = bonusChance.bonusChancePerLevel * lootingLevel;
            return Math.min(chance + Math.min(bonus, bonusChance.maxBonusChance), 1.0);
        }

        return chance;
    }

    private static int calculateMin(int min, MobDropJsonStructure.BonusCount bonusCount, int lootingLevel){
        if(bonusCount != null){
            return min + Math.min(bonusCount.bonusMinPerLevel*lootingLevel, bonusCount.maxBonusMin);
        }

        return min;
    }

    private static int calculateMax(int max, MobDropJsonStructure.BonusCount bonusCount, int lootingLevel){
        if(bonusCount != null){
            return max + Math.min(bonusCount.bonusMaxPerLevel*lootingLevel, bonusCount.maxBonusMax);
        }

        return max;
    }

    private static int calculateRolls(int rolls, MobDropJsonStructure.BonusRoll bonusRolls, int lootingLevel){
        if(bonusRolls != null){
            return rolls + Math.min(bonusRolls.bonusRollsPerLevel*lootingLevel, bonusRolls.maxBonusRolls);
        }

        return rolls;
    }

    private static double calculateRollChance(double rollsChance, MobDropJsonStructure.BonusRoll bonusRolls, int lootingLevel){
        if(bonusRolls != null){
            double bonus = bonusRolls.bonusRollChancePerLevel * lootingLevel;
            return Math.min(rollsChance + Math.min(bonus, bonusRolls.maxBonusRollChance), 1.0);
        }

        return rollsChance;
    }

    private static ItemStack createFinalStack(MobDropDefinition rule, int count){
        if(count <= 0 || rule.drop == null) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(rule.drop, count);

        if(rule.nbtTag != null){
            stack.setTag(rule.nbtTag.copy());
        }
        return stack;
    }
}
