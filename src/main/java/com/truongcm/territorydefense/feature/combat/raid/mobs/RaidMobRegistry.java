package com.truongcm.territorydefense.feature.combat.raid.mobs;

import com.truongcm.territorydefense.feature.combat.raid.mobs.types.*;
import org.bukkit.entity.EntityType;
import java.util.HashMap;
import java.util.Map;

public final class RaidMobRegistry {

    private static final Map<EntityType, RaidMob> REGISTRY = new HashMap<>();

    static {
        register(new RaidZombie());
        register(new RaidSkeleton());
        register(new RaidSpider());
        register(new RaidCaveSpider());
        register(new RaidCreeper());
        register(new RaidHusk());
        register(new RaidDrowned());
        register(new RaidWitch());
        register(new RaidPillager());
        register(new RaidVindicator());
        register(new RaidPhantom());
        register(new RaidGhast());
        register(new RaidEvoker());
        register(new RaidRavager());
        register(new RaidPiglin());
        register(new RaidPiglinBrute());
        register(new RaidWitherSkeleton());
        register(new RaidBlaze());
        register(new RaidIllusioner());
        register(new RaidVex());
        register(new RaidHoglin());
        register(new RaidGiant());
        register(new RaidWither());
        register(new RaidZombifiedPiglin());
        register(new RaidZoglin());
        register(new RaidStray());
        register(new RaidSlime());
        register(new RaidMagmaCube());
        register(new RaidWarden());
    }

    private static void register(RaidMob mob) {
        REGISTRY.put(mob.getType(), mob);
    }

    public static RaidMob getMob(EntityType type) {
        return REGISTRY.get(type);
    }

    public static double getBaseHp(EntityType type) {
        RaidMob mob = REGISTRY.get(type);
        return mob != null ? mob.getBaseHp() : 100.0;
    }

    public static double getCoinReward(EntityType type) {
        RaidMob mob = REGISTRY.get(type);
        return mob != null ? mob.getCoinReward() : 100.0;
    }
}
