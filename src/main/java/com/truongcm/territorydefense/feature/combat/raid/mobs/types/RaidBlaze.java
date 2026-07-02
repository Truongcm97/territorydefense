package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidBlaze extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.BLAZE;
    }

    @Override
    public double getBaseHp() {
        return 100.0;
    }

    @Override
    public double getCoinReward() {
        return 250.0;
    }
}
