package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidGiant extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.GIANT;
    }

    @Override
    public double getBaseHp() {
        return 10000.0;
    }

    @Override
    public double getCoinReward() {
        return 60000.0;
    }
}
