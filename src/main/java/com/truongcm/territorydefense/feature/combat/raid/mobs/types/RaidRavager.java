package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidRavager extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.RAVAGER;
    }

    @Override
    public double getBaseHp() {
        return 1500.0;
    }

    @Override
    public double getCoinReward() {
        return 6000.0;
    }
}
