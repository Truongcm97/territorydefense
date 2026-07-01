package com.truongcm.territorydefense.feature.combat.tower.types;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.UUID;

/**
 * THÁP CUNG PHÒNG THỦ (ARROW TOWER) - THEO MASTER GDD FINAL V30
 * Đại diện: Skeleton (Sử dụng khối SKELETON_SKULL / SKELETON_WALL_SKULL).
 * Đặc tính: Tầm đánh xa (16.0 blocks), tốc độ bắn trung bình (20 ticks = 1.0 giây / phát).
 * Kỹ năng độc quyền: Bắn mũi tên xuyên thấu tối đa 3 mục tiêu thẳng hàng (Ray-Box Pierce Algorithm).
 */
public class ArrowTower extends Tower {

    public ArrowTower(UUID towerId, Location location, UUID ownerCoreId, int level) {
        super(towerId, location, ownerCoreId, TowerType.ARROW, level);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.YELLOW + "Tháp Cung (Skeleton)";
    }

    @Override
    public double getScanningRadius() {
        return 16.0;
    }

    @Override
    public int getAttackSpeedTicks() {
        return 40; // Giãn cách bắn 2.0 giây (40 ticks)
    }

    @Override
    public double getDamage() {
        // Tịnh tiến sát thương: 15 -> 22 -> 33 -> 45 -> 60 DMG theo cấp độ
        return switch (level) {
            case 1 -> 5.0;
            case 2 -> 7.0;
            case 3 -> 9.0;
            case 4 -> 12.0;
            case 5 -> 15.0;
            default -> 5.0;
        };
    }

    /**
     * Khai hỏa phát bắn: Triệu hồi mũi tên vật lý có vận tốc cao, áp dụng thuật toán
     * quét vector tia để gây sát thương xuyên thấu tối đa 3 mục tiêu.
     */
    @Override
    public void performAttack(LivingEntity target, TerritoryCore core) {
        Location origin = getLocation().clone().add(0.5, 1.25, 0.5);
        double finalDamage = getFinalDamage(target);

        // Tính toán Vector hướng bắn từ tâm tháp tới vị trí quái vật mục tiêu
        Vector direction = target.getEyeLocation().toVector().subtract(origin.toVector()).normalize();

        // 1. Triệu hồi thực thể mũi tên vật lý bay đi
        Arrow arrow = origin.getWorld().spawn(origin, Arrow.class);
        arrow.setVelocity(direction.multiply(2.5)); // Đẩy vận tốc mũi tên bay nhanh mượt
        arrow.setDamage(finalDamage / 4.0); // Cân đối tỉ lệ lực bắn mặc định của Minecraft
        arrow.setMetadata("td_tower_projectile", new FixedMetadataValue(TerritoryDefense.getInstance(), true));
        if (core != null && core.getOwnerUUID() != null) {
            arrow.setMetadata("td_tower_owner_uuid", new FixedMetadataValue(TerritoryDefense.getInstance(), core.getOwnerUUID().toString()));
        }

        origin.getWorld().playSound(origin, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.2f);

        // 2. THUẬT TOÁN XUYÊN THẤU (Pierce Ray-Cast Algorithm):
        // Quét tất cả kẻ địch nằm dọc trên trục tia bắn trong tầm 16 blocks và gây sát thương đồng loạt.
        int pierceCount = 0;
        Collection<Entity> potentialTargets = origin.getWorld().getNearbyEntities(origin, 16.0, 16.0, 16.0);
        for (Entity entity : potentialTargets) {
            if (pierceCount >= 3) break; // Giới hạn xuyên tối đa 3 mục tiêu theo GDD
            if (!(entity instanceof LivingEntity living)) continue;

            boolean isRaidMob = living.hasMetadata("td_raid_mob") || (com.truongcm.territorydefense.feature.core.PDCKeys.RAID_MOB_TAG != null && living.getPersistentDataContainer().has(com.truongcm.territorydefense.feature.core.PDCKeys.RAID_MOB_TAG, org.bukkit.persistence.PersistentDataType.BYTE));
            boolean isEnemy = isValidTarget(living, core, TerritoryDefense.getInstance())
                    || (isRaidMob && !living.isDead() && living.isValid());

            if (isEnemy) {
                // Thẩm định khoảng cách vuông góc ngắn nhất từ mục tiêu tới tia bắn
                double distanceToRay = getDistanceToRay(living.getLocation().toVector(), origin.toVector(), direction);
                if (distanceToRay <= 1.4) { // Phạm vi hộp va chạm quét tia trúng đích
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