package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidEvoker extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.EVOKER;
    }

    @Override
    public double getBaseHp() {
        return 200.0;
    }

    @Override
    public double getCoinReward() {
        return 1200.0;
    }
}
