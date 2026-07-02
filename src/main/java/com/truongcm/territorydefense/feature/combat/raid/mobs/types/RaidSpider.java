package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidSpider extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.SPIDER;
    }

    @Override
    public double getBaseHp() {
        return 80.0;
    }

    @Override
    public double getCoinReward() {
        return 100.0;
    }
}
