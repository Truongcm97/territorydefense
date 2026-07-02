package com.truongcm.territorydefense.feature.combat.tower.types;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * THÁP CUNG PHÒNG THỦ (ARROW TOWER) - THEO MASTER GDD FINAL V30
 * Đại diện: Skeleton (Sử dụng khối SKELETON_SKULL / SKELETON_WALL_SKULL).
 * Đặc tính: Tầm đánh xa (16.0 blocks), tốc độ bắn trung bình (20 ticks = 1.0 giây / phát).
 * Kỹ năng độc quyền: Bắn mũi tên xuyên thấu tối đa 3 mục tiêu thẳng hàng (Ray-Box Pierce Algorithm).
 */
public class ArrowTower extends Tower {

    private static final String CFG = "tower-settings.types.arrow";

    public ArrowTower(UUID towerId, Location location, UUID ownerCoreId, int level) {
        super(towerId, location, ownerCoreId, TowerType.ARROW, level);
    }

    @Override
    public String getDisplayName() {
        return TerritoryDefense.getInstance().getConfig().getString(CFG + ".display-name", "&eTháp Cung (Skeleton)");
    }

    @Override
    public double getScanningRadius() {
        return TerritoryDefense.getInstance().getConfig().getDouble(CFG + ".scanning-radius", 16.0);
    }

    @Override
    public int getAttackSpeedTicks() {
        return TerritoryDefense.getInstance().getConfig().getInt(CFG + ".attack-speed-ticks", 20);
    }

    @Override
    public double getDamage() {
        FileConfiguration cfg = TerritoryDefense.getInstance().getConfig();
        List<Double> damageList = cfg.getDoubleList(CFG + ".damage");
        if (damageList != null && level >= 1 && level <= damageList.size()) {
            return damageList.get(level - 1);
        }
        return switch (level) {
            case 2 -> 26.0; case 3 -> 39.0; case 4 -> 54.0; case 5 -> 72.0;
            default -> 18.0;
        };
    }

    /**
     * Khai hỏa phát bắn: Triệu hồi mũi tên vật lý có vận tốc cao, áp dụng thuật toán
     * quét vector tia để gây sát thương xuyên thấu tối đa 3 mục tiêu.
     */
    @Override
    public void performAttack(LivingEntity target, TerritoryCore core) {
        FileConfiguration cfg = TerritoryDefense.getInstance().getConfig();
        double arrowSpeed = cfg.getDouble(CFG + ".special.arrow-speed", 2.5);
        double pierceHitbox = cfg.getDouble(CFG + ".special.pierce-hitbox", 1.4);
        int maxPierce = cfg.getInt(CFG + ".special.pierce-targets", 3);
        double scanRadius = getScanningRadius();

        Location origin = getLocation().clone().add(0.5, 1.25, 0.5);
        double finalDamage = getFinalDamage(target);

        Vector direction = target.getEyeLocation().toVector().subtract(origin.toVector()).normalize();

        // 1. Triệu hồi thực thể mũi tên vật lý bay đi
        Arrow arrow = origin.getWorld().spawn(origin, Arrow.class);
        arrow.setVelocity(direction.multiply(arrowSpeed));
        arrow.setDamage(finalDamage / 4.0);
        arrow.setMetadata("td_tower_projectile", new FixedMetadataValue(TerritoryDefense.getInstance(), true));
        if (core != null && core.getOwnerUUID() != null) {
            arrow.setMetadata("td_tower_owner_uuid", new FixedMetadataValue(TerritoryDefense.getInstance(), core.getOwnerUUID().toString()));
        }

        origin.getWorld().playSound(origin, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.2f);

        // 2. THUẬT TOÁN XUYÊN THẤU (Pierce Ray-Cast Algorithm)
        int pierceCount = 0;
        Collection<Entity> potentialTargets = origin.getWorld().getNearbyEntities(origin, scanRadius, scanRadius, scanRadius);
        for (Entity entity : potentialTargets) {
            if (pierceCount >= maxPierce) break;
            if (!(entity instanceof LivingEntity living)) continue;

            boolean isRaidMob = living.hasMetadata("td_raid_mob") || (com.truongcm.territorydefense.feature.core.PDCKeys.RAID_MOB_TAG != null && living.getPersistentDataContainer().has(com.truongcm.territorydefense.feature.core.PDCKeys.RAID_MOB_TAG, org.bukkit.persistence.PersistentDataType.BYTE));
            boolean isEnemy = isValidTarget(living, core, TerritoryDefense.getInstance())
                    || (isRaidMob && !living.isDead() && living.isValid());

            if (isEnemy) {
                double distanceToRay = getDistanceToRay(living.getLocation().toVector(), origin.toVector(), direction);
                if (distanceToRay <= pierceHitbox) {
                    if (core != null && core.getOwnerUUID() != null) {
                        living.setMetadata("td_last_tower_damager_uuid", new FixedMetadataValue(TerritoryDefense.getInstance(), core.getOwnerUUID().toString()));
                    }
                    living.damage(finalDamage);
                    living.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, living.getLocation().add(0, 1.0, 0), 2);
                    living.setMetadata("td_last_damaged_by_tower", new FixedMetadataValue(TerritoryDefense.getInstance(), true));
                    pierceCount++;
                }
            }
        }
    }

    /**
     * Tính toán khoảng cách ngắn nhất từ một điểm vật lý tới một Ray (Tia) hướng bắn trong không gian 3D.
     */
    private double getDistanceToRay(Vector point, Vector rayOrigin, Vector rayDirection) {
        Vector w = point.clone().subtract(rayOrigin);
        double proj = w.dot(rayDirection);
        if (proj < 0.0) return w.length();
        Vector v = rayOrigin.clone().add(rayDirection.clone().multiply(proj));
        return point.distance(v);
    }
}