package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidWarden extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.WARDEN;
    }

    @Override
    public double getBaseHp() {
        return 5000.0;
    }

    @Override
    public double getCoinReward() {
        return 30000.0;
    }
}
