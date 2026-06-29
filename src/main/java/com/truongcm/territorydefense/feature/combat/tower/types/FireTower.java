package com.truongcm.territorydefense.feature.combat.tower.types;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * THÁP LỬA PHÒNG THỦ (FIRE TOWER) - THEO MASTER GDD FINAL V30
 * Đại diện: Blaze (Sử dụng khối sọ quỷ WITHER_SKELETON_SKULL / WITHER_SKELETON_WALL_SKULL).
 * Đặc tính: Tầm bắn xa (15.0 blocks), tốc độ bắn trung bình-chậm (30 ticks = 1.5 giây / phát).
 * Kỹ năng độc quyền: Bắn cầu lửa thiêu đốt mục tiêu, kích nổ hoả ngục bộc phá khi quái bị tiêu diệt.
 */
public class FireTower extends Tower {

    public FireTower(UUID towerId, Location location, UUID ownerCoreId, int level) {
        super(towerId, location, ownerCoreId, TowerType.FIRE, level);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.RED + "Tháp Lửa (Blaze)";
    }

    @Override
    public double getScanningRadius() {
        return 15.0;
    }

    @Override
    public int getAttackSpeedTicks() {
        return 30; // 1.5 giây
    }

    @Override
    public double getDamage() {
        // Tịnh tiến sát thương: 20.0 -> 30.0 -> 44.0 -> 60.0 -> 80.0 DMG theo cấp độ
        return switch (level) {
            case 1 -> 20.0;
            case 2 -> 30.0;
            case 3 -> 44.0;
            case 4 -> 60.0;
            case 5 -> 80.0;
            default -> 20.0;
        };
    }

    /**
     * Khai hỏa: Triệu hồi cầu lửa nhỏ bay thẳng vào mục tiêu, thiêu đốt đối thủ 4 giây.
     */
    @Override
    public void performAttack(LivingEntity target, TerritoryCore core) {
        Location origin = getLocation().clone().add(0.5, 1.25, 0.5);
        double finalDamage = getFinalDamage(target);

        Vector direction = target.getEyeLocation().toVector().subtract(origin.toVector()).normalize();

        // Triệu hồi SmallFireball bay đi
        SmallFireball fireball = origin.getWorld().spawn(origin, SmallFireball.class);
        fireball.setDirection(direction);
        fireball.setIsIncendiary(false); // Chống bắt lửa phá hủy địa hình khối block
        fireball.setMetadata("td_tower_projectile", new FixedMetadataValue(TerritoryDefense.getInstance(), true));

        // Gây sát thương và thiêu đốt
        target.damage(finalDamage);
        target.setFireTicks(80); // Đốt cháy trong 4 giây (20 ticks = 1s)
        target.setMetadata("td_last_damaged_by_tower", new FixedMetadataValue(TerritoryDefense.getInstance(), true));

        // Đánh dấu cờ bộc nổ khi chết để lắng nghe trong CombatDamageTracker
        target.setMetadata("td_explode_on_death", new FixedMetadataValue(TerritoryDefense.getInstance(), level));

        origin.getWorld().playSound(origin, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.2f);
    }
}