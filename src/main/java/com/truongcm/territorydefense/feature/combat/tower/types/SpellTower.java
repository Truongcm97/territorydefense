package com.truongcm.territorydefense.feature.combat.tower.types;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.UUID;

/**
 * THÁP MA PHÁP PHÒNG THỦ (SPELL TOWER) - THEO MASTER GDD FINAL V30
 * Đại diện: Witch (Sử dụng khối sọ PLAYER_HEAD / PLAYER_WALL_HEAD).
 * Đặc tính: Tầm quét trung bình (10.0 blocks), tần số cộng hưởng chậm (100 ticks = 5.0 giây / nhịp).
 * Kỹ năng độc quyền: Không gây sát thương trực tiếp, liên tục phát sóng hào quang cộng hưởng sát thương (Damage Buff AoE)
 * gia tăng [5%, 8%, 12%, 15%, 20%] lực bắn cho toàn bộ các tháp canh phòng ngự lân cận.
 */
public class SpellTower extends Tower {

    private static final String CFG = "tower-settings.types.spell";

    public SpellTower(UUID towerId, Location location, UUID ownerCoreId, int level) {
        super(towerId, location, ownerCoreId, TowerType.SPELL, level);
    }

    @Override
    public String getDisplayName() {
        return TerritoryDefense.getInstance().getConfig().getString(CFG + ".display-name", "&5Tháp Phép (Witch)");
    }

    @Override
    public double getScanningRadius() {
        return TerritoryDefense.getInstance().getConfig().getDouble(CFG + ".scanning-radius", 12.0);
    }

    @Override
    public int getAttackSpeedTicks() {
        return TerritoryDefense.getInstance().getConfig().getInt(CFG + ".attack-speed-ticks", 100);
    }

    @Override
    public double getDamage() {
        FileConfiguration cfg = TerritoryDefense.getInstance().getConfig();
        List<Integer> buffList = cfg.getIntegerList(CFG + ".damage-buff-percent");
        if (buffList != null && level >= 1 && level <= buffList.size()) {
            return buffList.get(level - 1) / 100.0;
        }
        return switch (level) {
            case 2 -> 0.08; case 3 -> 0.12; case 4 -> 0.15; case 5 -> 0.20;
            default -> 0.05;
        };
    }

    /**
     * Khai hỏa: Tháp phép liên tục phát ra sóng cộng hưởng phép thuật màu tím rực rỡ,
     * nạp đầy năng lực ma pháp cho trận pháp tháp phòng thủ lân cận.
     */
    @Override
    public void performAttack(LivingEntity target, TerritoryCore core) {
        Location origin = getLocation().clone().add(0.5, 1.25, 0.5);

        // Spawn các hạt hiệu ứng ma pháp màu tím rực rỡ tỏa tròn xung quanh tháp phép
        origin.getWorld().spawnParticle(org.bukkit.Particle.WITCH, origin, 30, 1.5, 1.5, 1.5, 0.1);
        origin.getWorld().playSound(origin, Sound.ENTITY_WITCH_CELEBRATE, 0.6f, 1.2f);
    }
}