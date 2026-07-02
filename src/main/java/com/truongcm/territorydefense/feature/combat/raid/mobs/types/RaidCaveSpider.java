package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidCaveSpider extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.CAVE_SPIDER;
    }

    @Override
    public double getBaseHp() {
        return 60.0;
    }

    @Override
    public double getCoinReward() {
        return 120.0;
    }
}
