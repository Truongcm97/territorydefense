package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidVex extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.VEX;
    }

    @Override
    public double getBaseHp() {
        return 50.0;
    }

    @Override
    public double getCoinReward() {
        return 100.0;
    }
}
