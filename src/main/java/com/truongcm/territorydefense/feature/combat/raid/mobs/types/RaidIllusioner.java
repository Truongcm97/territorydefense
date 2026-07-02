package com.truongcm.territorydefense.feature.combat.raid.mobs.types;

import com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMob;
import org.bukkit.entity.EntityType;

public class RaidIllusioner extends RaidMob {

    @Override
    public EntityType getType() {
        return EntityType.ILLUSIONER;
    }

    @Override
    public double getBaseHp() {
        return 150.0;
    }

    @Override
    public double getCoinReward() {
        return 600.0;
    }
}
