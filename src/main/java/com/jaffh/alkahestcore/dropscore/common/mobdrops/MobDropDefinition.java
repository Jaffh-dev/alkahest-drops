package com.jaffh.alkahestcore.dropscore.common.mobdrops;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;


public class MobDropDefinition {

    public EntityType<?> entityType;
    public Item drop;
    public CompoundTag nbtTag;
    public double chance;

    public DropBehaviour dropBehaviour;
    public MobDropJsonStructure.Count count;
    public MobDropJsonStructure.OnLooting onLooting;
    public LootItemCondition[] conditions;

    public static class DropBehaviour {
        public DropBehaviourType type;
        public int rolls;
        public double chancePerRoll;
        public int incrementValue;
    }
}
