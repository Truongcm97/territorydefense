package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidPiglin extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.PIGLIN;
    }

    @Override
    public double getBaseHp() {
        return 100.0;
    }

    @Override
    public double getCoinReward() {
        return 150.0;
    }
}
