package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidMagmaCube extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.MAGMA_CUBE;
    }

    @Override
    public double getBaseHp() {
        return 120.0;
    }

    @Override
    public double getCoinReward() {
        return 200.0;
    }
}
