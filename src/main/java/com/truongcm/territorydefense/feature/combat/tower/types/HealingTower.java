package com.truongcm.territorydefense.feature.combat.tower.types;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * THÁP HỒI PHỤC PHÒNG THỦ (HEALING TOWER) - THEO MASTER GDD FINAL V30
 * Đại diện: Evoker (Sử dụng khối sọ ma thuật PIGLIN_HEAD / PIGLIN_WALL_HEAD).
 * Đặc tính: Tầm quét trung bình (14.0 blocks), tần số phát sóng chậm (80 ticks = 4.0 giây / nhịp).
 * Kỹ năng độc quyền: Phát sóng năng lượng hồi sinh lực cho toàn bộ đồng minh lân cận,
 * đồng thời phản hồi 10% sát thương cận chiến từ kẻ địch.
 */
public class HealingTower extends Tower {

    private static final String CFG = "tower-settings.types.healing";

    public HealingTower(UUID towerId, Location location, UUID ownerCoreId, int level) {
        super(towerId, location, ownerCoreId, TowerType.HEALING, level);
    }

    @Override
    public String getDisplayName() {
        return TerritoryDefense.getInstance().getConfig().getString(CFG + ".display-name", "&dTháp Hồi (Evoker)");
    }

    @Override
    public double getScanningRadius() {
        return TerritoryDefense.getInstance().getConfig().getDouble(CFG + ".scanning-radius", 14.0);
    }

    @Override
    public int getAttackSpeedTicks() {
        return TerritoryDefense.getInstance().getConfig().getInt(CFG + ".attack-speed-ticks", 80);
    }

    @Override
    public double getDamage() {
        FileConfiguration cfg = TerritoryDefense.getInstance().getConfig();
        List<Double> healingList = cfg.getDoubleList(CFG + ".healing-amount");
        if (healingList != null && level >= 1 && level <= healingList.size()) {
            return healingList.get(level - 1);
        }
        return switch (level) {
            case 2 -> 26.0; case 3 -> 39.0; case 4 -> 54.0; case 5 -> 72.0;
            default -> 18.0;
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
        double scanRadius = getScanningRadius();

        Collection<Entity> nearby = origin.getWorld().getNearbyEntities(origin, scanRadius, scanRadius, scanRadius);
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