package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidVindicator extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.VINDICATOR;
    }

    @Override
    public double getBaseHp() {
        return 150.0;
    }

    @Override
    public double getCoinReward() {
        return 200.0;
    }
}
