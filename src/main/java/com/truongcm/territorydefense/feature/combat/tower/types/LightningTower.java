package com.truongcm.territorydefense.feature.combat.tower.types;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * THÁP SÉT PHÒNG THỦ (LIGHTNING TOWER) - THEO MASTER GDD FINAL V30
 * Tất cả thông số đọc từ config.yml → tower-settings.types.lightning
 */
public class LightningTower extends Tower {

    private static final String CFG = "tower-settings.types.lightning";

    public LightningTower(UUID towerId, Location location, UUID ownerCoreId, int level) {
        super(towerId, location, ownerCoreId, TowerType.LIGHTNING, level);
    }

    @Override
    public String getDisplayName() {
        return TerritoryDefense.getInstance().getConfig().getString(CFG + ".display-name", "&bTháp Điện (Creeper)");
    }

    @Override
    public double getScanningRadius() {
        return TerritoryDefense.getInstance().getConfig().getDouble(CFG + ".scanning-radius", 8.0);
    }

    @Override
    public int getAttackSpeedTicks() {
        return TerritoryDefense.getInstance().getConfig().getInt(CFG + ".attack-speed-ticks", 15);
    }

    @Override
    public double getFepCost() {
        return TerritoryDefense.getInstance().getConfig().getDouble(CFG + ".fep-cost", 6.0);
    }

    @Override
    public double getDamage() {
        FileConfiguration cfg = TerritoryDefense.getInstance().getConfig();
        List<Double> damageList = cfg.getDoubleList(CFG + ".damage");
        if (damageList != null && level >= 1 && level <= damageList.size()) {
            return damageList.get(level - 1);
        }
        return switch (level) {
            case 2 -> 15.0; case 3 -> 21.0; case 4 -> 29.0; case 5 -> 38.0;
            default -> 10.0;
        };
    }

    /**
     * Khai hỏa: Gọi sấm sét từ hư không đánh thẳng xuống mục tiêu chính,
     * đồng thời vẽ tia điện 3D lan truyền dòng năng lượng sang N mục tiêu lân cận.
     */
    @Override
    public void performAttack(LivingEntity target, TerritoryCore core) {
        FileConfiguration cfg = TerritoryDefense.getInstance().getConfig();
        int chainTargets = cfg.getInt(CFG + ".special.chain-targets", 5);
        double chainRange = cfg.getDouble(CFG + ".special.chain-range", 6.0);
        double chainDamageRatio = cfg.getDouble(CFG + ".special.chain-damage-ratio", 0.70);

        Location origin = getLocation().clone().add(0.5, 1.25, 0.5);
        double finalDamage = getFinalDamage(target);

        // 1. Giật sét và vẽ tia điện nối tới mục tiêu chính
        drawLightningBeam(origin, target.getLocation().add(0, 1.0, 0));
        executeLightningStrike(target, finalDamage);

        // 2. Thuật toán lan truyền dòng điện (Chain-Targets)
        int chainCount = 0;
        Collection<Entity> nearby = target.getNearbyEntities(chainRange, chainRange, chainRange);
        Location lastChainLoc = target.getLocation().add(0, 1.0, 0);

        for (Entity entity : nearby) {
            if (chainCount >= chainTargets) break;
            if (!(entity instanceof LivingEntity living) || living.equals(target)) continue;

            boolean isRaidMob = living.hasMetadata("td_raid_mob") || (com.truongcm.territorydefense.feature.core.PDCKeys.RAID_MOB_TAG != null && living.getPersistentDataContainer().has(com.truongcm.territorydefense.feature.core.PDCKeys.RAID_MOB_TAG, org.bukkit.persistence.PersistentDataType.BYTE));
            if (isValidTarget(living, core, TerritoryDefense.getInstance())
                    || (isRaidMob && !living.isDead() && living.isValid())) {

                Location nextChainLoc = living.getLocation().add(0, 1.0, 0);
                drawLightningBeam(lastChainLoc, nextChainLoc);
                executeLightningStrike(living, finalDamage * chainDamageRatio);

                lastChainLoc = nextChainLoc;
                chainCount++;
            }
        }
    }

    /**
     * Áp sát thương vật lý và kích hoạt hiệu ứng sấm sét âm thanh rực rỡ lên mục tiêu
     */
    private void executeLightningStrike(LivingEntity victim, double dmg) {
        if (ownerCoreId != null) {
            com.truongcm.territorydefense.feature.core.TerritoryCore core = TerritoryDefense.getInstance().getCoreManager().getCoreById(ownerCoreId);
            if (core != null && core.getOwnerUUID() != null) {
                victim.setMetadata("td_last_tower_damager_uuid", new FixedMetadataValue(TerritoryDefense.getInstance(), core.getOwnerUUID().toString()));
            }
        }
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