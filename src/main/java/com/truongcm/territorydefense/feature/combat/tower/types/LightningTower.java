package com.truongcm.territorydefense.feature.combat.tower.types;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.UUID;

/**
 * THÁP SÉT PHÒNG THỦ (LIGHTNING TOWER) - THEO MASTER GDD FINAL V30
 * Đại diện: Charged Creeper (Sử dụng khối CREEPER_HEAD / CREEPER_WALL_HEAD).
 * Đặc tính: Tầm bắn ngắn (3.0 blocks), tốc độ giật sét siêu nhanh (10 ticks = 0.5 giây / phát).
 * Kỹ năng độc quyền: Giật sét chuỗi lan tỏa (Chain Lightning) tối đa 5 mục tiêu gần kề.
 * NÂNG CẤP V30: Tích hợp thuật toán vẽ dòng điện hạt 3D (Lightning Beam) kết nối các mục tiêu cực kỳ rực rỡ.
 */
public class LightningTower extends Tower {

    public LightningTower(UUID towerId, Location location, UUID ownerCoreId, int level) {
        super(towerId, location, ownerCoreId, TowerType.LIGHTNING, level);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.AQUA + "Tháp Điện (Creeper)";
    }

    @Override
    public double getScanningRadius() {
        return 3.0; // Tầm quét cực cận theo GDD
    }

    @Override
    public int getAttackSpeedTicks() {
        return 10; // Tốc độ bắn 0.5 giây
    }

    @Override
    public double getDamage() {
        // Tịnh tiến sát thương: 8.0 -> 12.0 -> 17.0 -> 24.0 -> 32.0 DMG theo cấp độ
        return switch (level) {
            case 1 -> 8.0;
            case 2 -> 12.0;
            case 3 -> 17.0;
            case 4 -> 24.0;
            case 5 -> 32.0;
            default -> 8.0;
        };
    }

    /**
     * Khai hỏa: Gọi sấm sét từ hư không đánh thẳng xuống mục tiêu chính,
     * đồng thời vẽ tia điện 3D lan truyền dòng năng lượng sang tối đa 5 mục tiêu lân cận.
     */
    @Override
    public void performAttack(LivingEntity target, TerritoryCore core) {
        // Tâm bắn xuất phát từ đỉnh đầu quái Creeper Head (cao hơn móng block 0.25 để thẩm mỹ)
        Location origin = getLocation().clone().add(0.5, 1.25, 0.5);
        double finalDamage = getFinalDamage(target);

        // 1. Giật sét và vẽ tia điện nối tới mục tiêu chính
        drawLightningBeam(origin, target.getLocation().add(0, 1.0, 0));
        executeLightningStrike(target, finalDamage);

        // 2. Thuật toán lan truyền dòng điện (Chain-Targets) tối đa 5 mục tiêu lân cận trong tầm 6.0 block
        int chainCount = 0;
        double chainRange = 6.0;
        Collection<Entity> nearby = target.getNearbyEntities(chainRange, chainRange, chainRange);

        Location lastChainLoc = target.getLocation().add(0, 1.0, 0);

        for (Entity entity : nearby) {
            if (chainCount >= 5) break; // Giới hạn lan truyền 5 mục tiêu theo đúng thiết kế GDD
            if (!(entity instanceof LivingEntity living) || living.equals(target)) continue;

            // Sử dụng bộ lọc đồng minh an toàn kế thừa từ lớp cha Tower
            if (isValidTarget(living, core, TerritoryDefense.getInstance())
                    || (living.hasMetadata("td_raid_mob") && !living.isDead() && living.isValid())) {

                Location nextChainLoc = living.getLocation().add(0, 1.0, 0);

                // Vẽ dòng điện nối từ mắt xích trước sang mắt xích sau
                drawLightningBeam(lastChainLoc, nextChainLoc);
                executeLightningStrike(living, finalDamage * 0.7); // Quái lan nhận 70% sát thương theo cân bằng GDD

                lastChainLoc = nextChainLoc;
                chainCount++;
            }
        }
    }

    /**
     * Áp sát thương vật lý và kích hoạt hiệu ứng sấm sét âm thanh rực rỡ lên mục tiêu
     */
    private void executeLightningStrike(LivingEntity victim, double dmg) {
        victim.damage(dmg);
        // Hiệu ứng sét ảo không phá địa hình và không gây cháy rừng
        victim.getWorld().strikeLightningEffect(victim.getLocation());
        victim.setMetadata("td_last_damaged_by_tower", new FixedMetadataValue(TerritoryDefense.getInstance(), true));
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
    }

    /**
     * Thuật toán kết xuất đường truyền dòng điện 3D (Laser Beam) bằng hạt bụi ma thuật điện xanh rực rỡ
     */
    private void drawLightningBeam(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null || !loc1.getWorld().equals(loc2.getWorld())) return;

        double distance = loc1.distance(loc2);
        Vector vector = loc2.toVector().subtract(loc1.toVector()).normalize();

        // Vẽ dãn cách mỗi 0.3 block để tia sét hiển thị sắc nét, liên tục nhưng không tốn tài nguyên mạng
        for (double d = 0; d < distance; d += 0.3) {
            Location point = loc1.clone().add(vector.clone().multiply(d));

            // Kết hợp hạt hiệu ứng Electric Spark và Spark để tạo chiều sâu dòng điện rực sáng
            point.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0, 0, 0, 0.0);
            if (d % 0.9 == 0) {
                point.getWorld().spawnParticle(Particle.CRIT, point, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
    }
}