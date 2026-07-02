package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidWither extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.WITHER;
    }

    @Override
    public double getBaseHp() {
        return 3000.0;
    }

    @Override
    public double getCoinReward() {
        return 25000.0;
    }
}
