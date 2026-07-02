package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidWitch extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.WITCH;
    }

    @Override
    public double getBaseHp() {
        return 130.0;
    }

    @Override
    public double getCoinReward() {
        return 300.0;
    }
}
