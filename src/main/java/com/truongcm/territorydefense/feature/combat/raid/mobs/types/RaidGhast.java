package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidGhast extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.GHAST;
    }

    @Override
    public double getBaseHp() {
        return 100.0;
    }

    @Override
    public double getCoinReward() {
        return 700.0;
    }
}
