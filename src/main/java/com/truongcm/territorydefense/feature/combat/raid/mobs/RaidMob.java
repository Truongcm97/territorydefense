package com.truongcm.territorydefense.feature.combat.raid.mobs;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

public abstract class RaidMob {
    public abstract EntityType getType();
    public abstract double getBaseHp();
    public abstract double getCoinReward();

    public void onSpawn(LivingEntity entity, boolean isMiniBoss) {
        // Default empty implementation, can be overridden by specific mob types
    }
}
