package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidWitherSkeleton extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.WITHER_SKELETON;
    }

    @Override
    public double getBaseHp() {
        return 150.0;
    }

    @Override
    public double getCoinReward() {
        return 300.0;
    }
}
