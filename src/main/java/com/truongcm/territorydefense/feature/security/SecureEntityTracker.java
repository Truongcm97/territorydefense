package com.truongcm.territorydefense.feature.security;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * THẨM ĐỊNH BẢO MẬT THỰC THỂ (SECURE ENTITY TRACKER)
 * Hoạt động như bức tường lửa an ninh ở tầng triệu hồi (Spawner Firewall).
 * Xác thực mã băm bảo mật (td_entity_secure_hash) độc bản trên mọi NPC, lính, và quái Raid,
 * chặn đứng và loại bỏ lập tức mọi thực thể triệu hồi lậu bởi hacker.
 */
public class SecureEntityTracker implements Listener {

    /**
     * Tạo chữ ký băm điện tử độc bản và lưu trực tiếp vào PDC của thực thể.
     * Cấu trúc mã băm: "TD-[LOẠI]-[UUIDv4]-[TIMESTAMP]"
     *
     * @param entity Thực thể cần gắn chữ ký bảo mật
     * @param stampType Định danh phân loại thực thể (FARMER, TOWER, MERCENARY, RAID_MOB)
     */
    public void stampSecureHash(Entity entity, String stampType) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String uniqueHash = "TD-" + stampType.toUpperCase() + "-" + UUID.randomUUID() + "-" + System.currentTimeMillis();

        // Ghi bền vững vào PersistentDataContainer (PDC)
        pdc.set(PDCKeys.ENTITY_SECURE_HASH, PersistentDataType.STRING, uniqueHash);

        // Đồng bộ lưu tạm thời vào Metadata RAM của thực thể để truy xuất tốc độ cao
        entity.setMetadata("td_secure_flag", new FixedMetadataValue(TerritoryDefense.getInstance(), uniqueHash));
    }

    /**
     * Thẩm định an ninh tầng Spawner với độ ưu tiên tuyệt đối (LOWEST - xử lý sớm nhất).
     * Bất kỳ nỗ lực tạo quái Custom không qua hệ thống Lõi đều bị triệt tiêu ngay lập tức.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSecureEntitySpawnVerify(EntitySpawnEvent event) {
        Entity entity = event.getEntity();

        // Chỉ quét các thực thể sống, bỏ qua Người chơi tự nhiên
        if (!(entity instanceof LivingEntity) || entity instanceof Player) {
            return;
        }

        // Kiểm tra xem thực thể có mang nhãn thực thể Custom của plugin hay không
        if (entity.hasMetadata("td_custom_entity")) {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();

            // THẨM ĐỊNH 1: Kiểm tra sự tồn tại của khóa mã băm an ninh PDC
            if (!pdc.has(PDCKeys.ENTITY_SECURE_HASH, PersistentDataType.STRING)) {
                event.setCancelled(true);
                entity.remove();
                logSecurityAlert(entity, "THIẾU CHỮ KÝ SỐ PDC!");
                return;
            }

            // THẨM ĐỊNH 2: Đối chiếu chữ ký giữa PDC (Bền vững) và Metadata (RAM) để chống giả mạo gói tin
            String pdcHash = pdc.get(PDCKeys.ENTITY_SECURE_HASH, PersistentDataType.STRING);
            if (!entity.hasMetadata("td_secure_flag")) {
                event.setCancelled(true);
                entity.remove();
                logSecurityAlert(entity, "SAI LỆCH METADATA VÀ PDC!");
                return;
            }

            String metaHash = entity.getMetadata("td_secure_flag").get(0).asString();
            if (pdcHash == null || !pdcHash.equals(metaHash)) {
                event.setCancelled(true);
                entity.remove();
                logSecurityAlert(entity, "MÃ BĂM KHÔNG TRÙNG KHỚP!");
            }
        }
    }

    /**
     * Ghi nhận log cảnh báo an ninh bảo mật trực tiếp lên Console hệ thống.
     */
    private void logSecurityAlert(Entity entity, String reason) {
        TerritoryDefense.getInstance().getLogger().severe(String.format(
                "%s[TD-SECURITY] CẢNH BÁO GIAN LẬN: Phát hiện triệu hồi lậu thực thể loại %s tại tọa độ [X:%d, Y:%d, Z:%d]! Lý do hủy: %s%s",
                ChatColor.RED,
                entity.getType().name(),
                entity.getLocation().getBlockX(),
                entity.getLocation().getBlockY(),
                entity.getLocation().getBlockZ(),
                reason,
                ChatColor.RESET
        ));
    }
}