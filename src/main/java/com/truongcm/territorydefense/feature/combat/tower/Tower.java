package com.truongcm.territorydefense.feature.combat.tower;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * LỚP TRỪU TƯỢNG THÁP CANH (TOWER BASE MODEL)
 * Định nghĩa bộ khung thuộc tính và hành vi hoạt động tĩnh cho 7 loại tháp.
 * Đug nâng cấp chuẩn V30: Sửa lỗi kiểm toán Solo (Lỗi 10). Nếu không có bang,
 * tháp vẫn tự vệ bắn quái và người lạ bình thường, tránh lỗi null liên minh làm tê liệt tháp.
 */
public abstract class Tower {

    public enum TowerType {
        LIGHTNING,  // Tháp Điện
        ARROW,      // Tháp Cung
        FIRE,       // Tháp Lửa
        FROST,      // Tháp Băng
        ARTILLERY,  // Tháp Pháo
        HEALING,    // Tháp Hồi
        SPELL       // Tháp Phép
    }

    protected final UUID towerId;
    protected final Location location;
    protected final UUID ownerCoreId;
    protected final TowerType type;
    protected int level;
    protected long lastShotTime;

    public Tower(UUID towerId, Location location, UUID ownerCoreId, TowerType type, int level) {
        this.towerId = towerId;
        this.location = location;
        this.ownerCoreId = ownerCoreId;
        this.type = type;
        this.level = level;
        this.lastShotTime = 0;
    }

    public abstract String getDisplayName();
    public abstract double getScanningRadius();
    public abstract int getAttackSpeedTicks();
    public abstract double getDamage();
    public abstract void performAttack(LivingEntity target, TerritoryCore core);

    /**
     * BỘ LỌC MỤC TIÊU TOÀN DIỆN (ADVANCED TARGET FILTER) - SỬA LỖI 10 CHƠI SOLO
     * Bảo hộ an toàn cho chủ sở hữu, người cùng bang và thú nuôi liên quan.
     * Tấn công quái vật thù địch và người chơi lạ đột nhập.
     */
    public boolean isValidTarget(LivingEntity entity, TerritoryCore core, TerritoryDefense plugin) {
        if (entity == null || entity.isDead() || core == null || plugin == null) {
            return false;
        }

        // 1. Trường hợp mục tiêu là Người chơi (Player)
        if (entity instanceof Player targetPlayer) {
            // Chặn bắn chính chủ sở hữu Lõi lãnh thổ
            if (targetPlayer.getUniqueId().equals(core.getOwnerUUID())) {
                return false;
            }

            // Chặn bắn người chơi đồng minh (Nếu có liên minh)
            String playerAlly = plugin.getAllianceManager().getPlayerAlliance(targetPlayer.getUniqueId());
            String coreAlly = core.getAllyId();

            if (coreAlly != null && playerAlly != null && coreAlly.equalsIgnoreCase(playerAlly)) {
                return false;
            }

            // Tấn công nếu là người chơi lạ đột nhập
            return true;
        }

        // 2. Kiểm duyệt bảo vệ thú nuôi thuần hóa (Pets/Summons)
        if (entity instanceof Tameable tameable) {
            if (tameable.isTamed() && tameable.getOwner() != null) {
                UUID ownerUUID = tameable.getOwner().getUniqueId();
                if (ownerUUID.equals(core.getOwnerUUID())) {
                    return false; // Chặn bắn thú nuôi của chủ nhà
                }

                String ownerAlly = plugin.getAllianceManager().getPlayerAlliance(ownerUUID);
                String coreAlly = core.getAllyId();
                if (coreAlly != null && ownerAlly != null && coreAlly.equalsIgnoreCase(ownerAlly)) {
                    return false; // Chặn bắn thú nuôi của đồng minh
                }
            }
        }

        // 3. Kiểm duyệt lính đánh thuê / NPC gác thành của phe mình
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING)) {
            String ownerCoreIdStr = pdc.get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING);
            if (ownerCoreIdStr != null) {
                try {
                    UUID ownerCoreId = UUID.fromString(ownerCoreIdStr);
                    if (ownerCoreId.equals(core.getCoreId())) {
                        return false; // Chặn bắn lính của Lõi này
                    }

                    // Chặn bắn lính gác của đồng minh
                    String coreAlly = core.getAllyId();
                    if (coreAlly != null) {
                        for (TerritoryCore activeCore : plugin.getCoreManager().getAllActiveCores()) {
                            if (activeCore.getCoreId().equals(ownerCoreId)) {
                                if (coreAlly.equalsIgnoreCase(activeCore.getAllyId())) {
                                    return false;
                                }
                                break;
                            }
                        }
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // 4. Mặc định tấn công toàn bộ Quái vật hung ác (Zombie, Skeleton, Creeper, Slime, MagmaCube, Phantom,...)
        if (entity instanceof org.bukkit.entity.Enemy || entity.hasMetadata("td_raid_mob") || entity.hasMetadata("td_npc_attacker")) {
            return true;
        }

        return false;
    }

    public double getFinalDamage(LivingEntity target) {
        // Giảm sát thương cơ bản của trụ đi 80% (chỉ còn 20% sát thương gốc)
        double baseDamage = getDamage() * 0.20;
        double buffedDamage = TerritoryDefense.getInstance().getTowerManager().applySpellBuffModifier(getLocation(), baseDamage);
        
        if (target instanceof org.bukkit.entity.Player) {
            // Sát thương lên Player về 20%
            return buffedDamage * 0.20;
        } else {
            // Sát thương lên Quái về 100%
            return buffedDamage * 1.0;
        }
    }

    // --- GETTERS & SETTERS ---
    public UUID getTowerId() { return towerId; }
    public Location getLocation() { return location; }
    public UUID getOwnerCoreId() { return ownerCoreId; }
    public TowerType getType() { return type; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public long getLastShotTime() { return lastShotTime; }
    public void setLastShotTime(long lastShotTime) { this.lastShotTime = lastShotTime; }
}