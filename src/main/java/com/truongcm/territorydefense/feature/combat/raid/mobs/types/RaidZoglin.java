package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidZoglin extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.ZOGLIN;
    }

    @Override
    public double getBaseHp() {
        return 250.0;
    }

    @Override
    public double getCoinReward() {
        return 400.0;
    }
}
