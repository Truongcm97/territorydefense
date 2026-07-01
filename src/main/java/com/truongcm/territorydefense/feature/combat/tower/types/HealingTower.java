package com.truongcm.territorydefense.feature.combat.tower.types;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/**
 * THÁP HỒI PHỤC PHÒNG THỦ (HEALING TOWER) - THEO MASTER GDD FINAL V30
 * Đại diện: Evoker (Sử dụng khối sọ ma thuật PIGLIN_HEAD / PIGLIN_WALL_HEAD).
 * Đặc tính: Tầm quét trung bình (14.0 blocks), tần số phát sóng chậm (80 ticks = 4.0 giây / nhịp).
 * Kỹ năng độc quyền: Phát sóng năng lượng hồi sinh lực cho toàn bộ đồng minh lân cận,
 * đồng thời phản hồi 10% sát thương cận chiến từ kẻ địch.
 */
public class HealingTower extends Tower {

    public HealingTower(UUID towerId, Location location, UUID ownerCoreId, int level) {
        super(towerId, location, ownerCoreId, TowerType.HEALING, level);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.LIGHT_PURPLE + "Tháp Hồi Phục (Evoker)";
    }

    @Override
    public double getScanningRadius() {
        return 14.0;
    }

    @Override
    public int getAttackSpeedTicks() {
        return 80; // 4.0 giây
    }

    @Override
    public double getDamage() {
        // Tịnh tiến năng lực trị thương: 15 -> 22 -> 33 -> 45 -> 60 HP hồi phục theo cấp độ
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
     * Khai hỏa: Thay vì tấn công quái vật, tháp phát ra vòng hào quang trị thương màu hồng rực rỡ,
     * phục hồi sinh lực định kỳ cho toàn bộ đồng minh, lính gác và chủ sở hữu đứng gần.
     */
    @Override
    public void performAttack(LivingEntity target, TerritoryCore core) {
        Location origin = getLocation().clone().add(0.5, 1.25, 0.5);
        double healAmount = getDamage();

        // Quét tìm tất cả các thực thể đồng minh trong ranh giới 14 blocks
        Collection<Entity> nearby = origin.getWorld().getNearbyEntities(origin, 14.0, 14.0, 14.0);
        int healedCount = 0;

        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living)) continue;

            boolean isAlly = false;

            // A. Mục tiêu là người chơi
            if (living instanceof Player player) {
                String playerAlly = TerritoryDefense.getInstance().getAllianceManager().getPlayerAlliance(player.getUniqueId());
                if (player.getUniqueId().equals(core.getOwnerUUID())
                        || (core.getAllyId() != null && core.getAllyId().equals(playerAlly))) {
                    isAlly = true;
                }
            }

            // B. Mục tiêu là lính gác hoặc NPC đồng minh
            if (!isAlly && living.getPersistentDataContainer().has(PDCKeys.OWNER_CORE_ID, org.bukkit.persistence.PersistentDataType.STRING)) {
                String ownerCoreIdStr = living.getPersistentDataContainer().get(PDCKeys.OWNER_CORE_ID, org.bukkit.persistence.PersistentDataType.STRING);
                if (ownerCoreIdStr != null && ownerCoreIdStr.equalsIgnoreCase(core.getCoreId().toString())) {
                    isAlly = true;
                }
            }

            // Thực hiện hồi máu nếu là đồng minh đang bị thương
            if (isAlly && living.getHealth() < living.getMaxHealth()) {
                double newHealth = Math.min(living.getMaxHealth(), living.getHealth() + healAmount);
                living.setHealth(newHealth);

                // Spawn hạt trái tim màu đỏ lãng mạn
                living.getWorld().spawnParticle(org.bukkit.Particle.HEART, living.getLocation().add(0, 1.2, 0), 3, 0.2, 0.2, 0.2, 0.1);
                healedCount++;
            }
        }

        if (healedCount > 0) {
            origin.getWorld().playSound(origin, Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 0.8f, 1.5f);
        }
    }
}