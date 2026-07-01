package com.truongcm.territorydefense.feature.combat.tower.types;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.UUID;

/**
 * THÁP PHÁO PHÒNG THỦ (ARTILLERY TOWER) - THEO MASTER GDD FINAL V30
 * Đại diện: Ghast (Sử dụng khối sọ khổng lồ DRAGON_HEAD / DRAGON_WALL_HEAD).
 * Đặc tính: Tầm bắn cực xa (20.0 blocks), tốc độ nạp đạn siêu chậm (60 ticks = 3.0 giây / phát).
 * Kỹ năng độc quyền: Đại bác bộc phá nổ gây choáng diện rộng (Stun 1.5s) trong bán kính 4.0 blocks.
 */
public class ArtilleryTower extends Tower {

    public ArtilleryTower(UUID towerId, Location location, UUID ownerCoreId, int level) {
        super(towerId, location, ownerCoreId, TowerType.ARTILLERY, level);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.GRAY + "Tháp Pháo (Ghast)";
    }

    @Override
    public double getScanningRadius() {
        return 20.0; // Tầm đánh xa nhất vương quốc
    }

    @Override
    public int getAttackSpeedTicks() {
        return 60; // 3.0 giây nạp đạn
    }

    @Override
    public double getDamage() {
        // Tịnh tiến sát thương bộc phá: 40.0 -> 60.0 -> 88.0 -> 120.0 -> 160.0 DMG theo cấp độ
        return switch (level) {
            case 1 -> 10.0;
            case 2 -> 14.0;
            case 3 -> 18.0;
            case 4 -> 22.0;
            case 5 -> 26.0;
            default -> 10.0;
        };
    }

    /**
     * Khai hỏa: Bắn đại pháo năng lượng gây bộc nổ sát thương AoE cực khủng và làm choáng quái vật lân cận.
     */
    @Override
    public void performAttack(LivingEntity target, TerritoryCore core) {
        Location origin = getLocation().clone().add(0.5, 1.25, 0.5);
        double finalDamage = getFinalDamage(target);

        Location impactLoc = target.getLocation();

        // Hiệu ứng vụ nổ lớn rực rỡ
        impactLoc.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, impactLoc, 3, 0.2, 0.2, 0.2, 0.1);
        impactLoc.getWorld().playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);

        // Quét sát thương diện rộng AoE bán kính 4.0 blocks (theo đúng thông số GDD)
        double aoeRadius = 4.0;
        Collection<Entity> potentialTargets = impactLoc.getWorld().getNearbyEntities(impactLoc, aoeRadius, aoeRadius, aoeRadius);

        for (Entity entity : potentialTargets) {
            if (!(entity instanceof LivingEntity living)) continue;

            boolean isRaidMob = living.hasMetadata("td_raid_mob") || (com.truongcm.territorydefense.feature.core.PDCKeys.RAID_MOB_TAG != null && living.getPersistentDataContainer().has(com.truongcm.territorydefense.feature.core.PDCKeys.RAID_MOB_TAG, org.bukkit.persistence.PersistentDataType.BYTE));
            if (isValidTarget(living, core, TerritoryDefense.getInstance())
                    || (isRaidMob && !living.isDead() && living.isValid())) {

                // Gây sát thương AoE diện rộng
                if (core != null && core.getOwnerUUID() != null) {
                    living.setMetadata("td_last_tower_damager_uuid", new FixedMetadataValue(TerritoryDefense.getInstance(), core.getOwnerUUID().toString()));
                }
                living.damage(finalDamage);
                living.setMetadata("td_last_damaged_by_tower", new FixedMetadataValue(TerritoryDefense.getInstance(), true));

                // Áp hiệu ứng làm choáng (Stun) thông qua Slowness cấp cực đại trong 1.5 giây (30 ticks)
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 10));
            }
        }
    }
}