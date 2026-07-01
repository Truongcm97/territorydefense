package com.truongcm.territorydefense.feature.combat.tower.types;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * THÁP BĂNG PHÒNG THỦ (FROST TOWER) - THEO MASTER GDD FINAL V30
 * Đại diện: Stray (Sử dụng khối sọ xác ướp tuyết ZOMBIE_HEAD / ZOMBIE_WALL_HEAD).
 * Đặc tính: Tầm bắn trung bình (12.0 blocks), tốc độ bắn chậm (40 ticks = 2.0 giây / phát).
 * Kỹ năng độc quyền: Làm chậm cực mạnh (Slowness II) tốc độ di chuyển của đối thủ trong 3 giây.
 * NÂNG CẤP V30: Tích hợp hiệu ứng luồng gió tuyết ma pháp 3D (Frost Beam Particle Line) rực rỡ kết nối tới mục tiêu.
 */
public class FrostTower extends Tower {

    public FrostTower(UUID towerId, Location location, UUID ownerCoreId, int level) {
        super(towerId, location, ownerCoreId, TowerType.FROST, level);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.BLUE + "Tháp Băng (Stray)";
    }

    @Override
    public double getScanningRadius() {
        return 12.0;
    }

    @Override
    public int getAttackSpeedTicks() {
        return 40; // 2.0 giây
    }

    @Override
    public double getDamage() {
        // Tịnh tiến sát thương: 10.0 -> 15.0 -> 22.0 -> 30.0 -> 40.0 DMG theo cấp độ
        return switch (level) {
            case 1 -> 4.0;
            case 2 -> 6.0;
            case 3 -> 8.0;
            case 4 -> 10.0;
            case 5 -> 12.0;
            default -> 4.0;
        };
    }

    /**
     * Khai hỏa: Phun luồng gió tuyết buốt giá kết nối 3D trực tiếp tới mục tiêu,
     * gây sát thương và áp hiệu ứng Slowness II.
     */
    @Override
    public void performAttack(LivingEntity target, TerritoryCore core) {
        // Điểm bắn xuất phát từ đỉnh sọ Stray Head (cao hơn móng block 0.25 để thẩm mỹ)
        Location origin = getLocation().clone().add(0.5, 1.25, 0.5);
        double finalDamage = getFinalDamage(target);

        // Vẽ tia băng tuyết ma pháp 3D kết nối tháp tới quái vật
        drawFrostBeam(origin, target.getLocation().add(0, 1.0, 0));

        // Gây sát thương băng giá
        if (core != null && core.getOwnerUUID() != null) {
            target.setMetadata("td_last_tower_damager_uuid", new FixedMetadataValue(TerritoryDefense.getInstance(), core.getOwnerUUID().toString()));
        }
        target.damage(finalDamage);
        target.setMetadata("td_last_damaged_by_tower", new FixedMetadataValue(TerritoryDefense.getInstance(), true));

        // Áp hiệu ứng làm chậm cực mạnh (Slowness II, thời gian 3 giây = 60 ticks)
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));

        // Hiệu ứng hạt tuyết buốt giá tỏa tròn bung nở quanh mục tiêu khi trúng đòn
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1.0, 0), 15, 0.3, 0.3, 0.3, 0.05);
        origin.getWorld().playSound(origin, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
        origin.getWorld().playSound(origin, Sound.BLOCK_POWDER_SNOW_BREAK, 0.8f, 1.2f);
    }

    /**
     * Thuật toán kết xuất luồng gió lạnh tuyết rơi 3D (Frost Ray Beam) bằng hạt tuyết mịn
     */
    private void drawFrostBeam(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null || !loc1.getWorld().equals(loc2.getWorld())) return;

        double distance = loc1.distance(loc2);
        Vector vector = loc2.toVector().subtract(loc1.toVector()).normalize();

        // Vẽ dãn cách mỗi 0.25 block để tia băng nhìn dày dặn, sắc nét và mát lạnh
        for (double d = 0; d < distance; d += 0.25) {
            Location point = loc1.clone().add(vector.clone().multiply(d));

            // Kết hợp hạt tuyết và crit để tạo độ lấp lánh của luồng băng giá
            point.getWorld().spawnParticle(Particle.SNOWFLAKE, point, 1, 0.02, 0.02, 0.02, 0.0);
            if (d % 0.75 == 0) {
                point.getWorld().spawnParticle(Particle.CRIT, point, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
    }
}