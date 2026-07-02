package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidHusk extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.HUSK;
    }

    @Override
    public double getBaseHp() {
        return 120.0;
    }

    @Override
    public double getCoinReward() {
        return 120.0;
    }
}
