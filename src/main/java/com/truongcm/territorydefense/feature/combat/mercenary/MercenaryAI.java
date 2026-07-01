package com.truongcm.territorydefense.feature.combat.mercenary;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QUẢN LÝ LÍNH ĐÁNH THUÊ ĐỒNG MINH (MERCENARY AI SYSTEM)
 * Điều phối vòng đời triệu hồi, thăng tiến chỉ số GDD và cơ chế AI kép:
 * - Chế độ RAID (Aggressive): Chủ động tìm diệt Lõi/Tháp canh đối thủ.
 * - Chế độ DEFEND (Guard): Tuần tra 10 khối quanh Lõi ta, bảo vệ Farmer và người chơi.
 */
public class MercenaryAI extends BukkitRunnable implements Listener {

    public boolean spawnMercenary(Player player, TerritoryCore core) {
        return true;}

    public enum MercenaryType {
        MELEE,      // Lính Cận Chiến (Iron Golem)
        ARCHER,     // Lính Cung Thủ (Skeleton)
        SIEGE,      // Kỵ Binh Phá Thành (Ravager)
        SUPPORT     // Lính Hỗ Trợ (Allay)
    }

    private final TerritoryDefense plugin;

    // Bộ nhớ RAM lưu trữ danh sách lính đánh thuê đồng minh đang hoạt động
    // Key: Entity UUID, Value: Loại binh chủng tương ứng
    private final Map<UUID, ActiveMercenary> activeMercenaries = new ConcurrentHashMap<>();

    public MercenaryAI(TerritoryDefense plugin) {
        this.plugin = plugin;
        // Khởi chạy tác vụ quét AI định kỳ (Chạy mỗi 20 Ticks = 1.0 giây để lính đổi mục tiêu nhạy bén)
        this.runTaskTimer(plugin, 140L, 20L);
    }

    /**
     * Vòng lặp điều phối Pathfinder và xử lý trạng thái AI cho tất cả lính đánh thuê.
     */
    @Override
    public void run() {
        for (ActiveMercenary merc : activeMercenaries.values()) {
            Mob mob = merc.getEntity();
            if (mob == null || !mob.isValid() || mob.isDead()) {
                activeMercenaries.remove(merc.getUUID());
                continue;
            }

            TerritoryCore core = plugin.getCoreManager().getCoreByLocationRange(mob.getLocation());
            if (core == null) continue;

            // Xử lý AI theo chế độ đã lưu trong PDC (RAID hoặc DEFEND)
            if ("RAID".equalsIgnoreCase(merc.getMode())) {
                handleRaidBehavior(merc, core);
            } else {
                handleDefendBehavior(merc, core);
            }
        }
    }

    /**
     * AI STATE: RAID MODE (CHẾ ĐỘ CÔNG THÀNH)
     * AI chủ động tìm đường ngắn nhất tiếp cận mốc Lõi hoặc Tháp canh của đối thủ để triệt hạ.
     */
    private void handleRaidBehavior(ActiveMercenary merc, TerritoryCore core) {
        Mob mob = merc.getEntity();

        // Truy vết Lõi đối thủ (Nếu có chiến sự)
        TerritoryCore enemyCore = null;
        for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
            if (c.getAllyId() != null && !c.getAllyId().equals(core.getAllyId())) {
                enemyCore = c;
                break;
            }
        }

        if (enemyCore != null) {
            Location targetLoc = enemyCore.getLocation();

            // Nếu là Kỵ binh phá thành, ưu tiên húc thẳng vào khối móng
            if (merc.getType() == MercenaryType.SIEGE) {
                mob.getPathfinder().moveTo(targetLoc, 1.25);
            } else {
                // Các binh chủng khác hỗ trợ dọn dẹp quái thủ hoặc người chơi địch cản đường
                LivingEntity enemy = findNearestEnemy(mob, core.getAllyId(), 15.0);
                if (enemy != null) {
                    mob.setTarget(enemy);
                } else {
                    mob.getPathfinder().moveTo(targetLoc, 1.0);
                }
            }
        }
    }

    /**
     * AI STATE: DEFEND MODE (CHẾ ĐỘ PHÒNG NGỰ)
     * AI tuần tra quanh Lõi nhà (bán kính 10 khối), bảo hộ Farmer, người chơi và tiêu diệt quái Raid.
     */
    private void handleDefendBehavior(ActiveMercenary merc, TerritoryCore core) {
        Mob mob = merc.getEntity();
        Location coreLoc = core.getLocation();

        // 1. Ưu tiên bảo vệ: Quét tìm quái vật đang nhắm vào Farmer của liên minh
        LivingEntity threat = findThreatToCivilians(mob, core, 12.0);
        if (threat != null) {
            mob.setTarget(threat);
            return;
        }

        // 2. Tự vệ & Diệt xâm nhập: Tấn công quái vật hoặc người chơi lạ tiến sát ranh giới
        LivingEntity enemy = findNearestEnemy(mob, core.getAllyId(), 10.0);
        if (enemy != null) {
            mob.setTarget(enemy);
        } else {
            // 3. Nếu rảnh rỗi: Quay trở về tuần tra bán kính 10 khối quanh Lõi nhà
            if (mob.getLocation().distance(coreLoc) > 10.0) {
                mob.getPathfinder().moveTo(coreLoc, 1.0);
            } else if (Math.random() < 0.15) {
                // Đi dạo ngẫu nhiên quanh Lõi
                Location wander = coreLoc.clone().add((Math.random() - 0.5) * 8, 0, (Math.random() - 0.5) * 8);
                mob.getPathfinder().moveTo(wander, 0.85);
            }
        }

        // Đặc hiệu của lính hỗ trợ Allay: Định kỳ phục hồi giáp ảo cho đồng minh lân cận
        if (merc.getType() == MercenaryType.SUPPORT && Math.random() < 0.3) {
            performAllaySupportHeal(mob, core);
        }
    }

    /**
     * TRIỆU HỒI LÍNH ĐÁNH THUÊ MỚI (SPAWN & SCALING ATTRIBUTES):
     * Đồng bộ chỉ số HP và Sát thương theo cấp Lõi nhà dựa trên thuật toán tịnh tiến hình học.
     */
    public boolean spawnMercenary(TerritoryCore core, MercenaryType type, String mode) {
        UUID mercId = UUID.randomUUID();
        Location loc = core.getLocation().clone().add(0, 1.0, 0);

        EntityType entType = switch (type) {
            case MELEE -> EntityType.IRON_GOLEM;
            case ARCHER -> EntityType.SKELETON;
            case SIEGE -> EntityType.RAVAGER;
            case SUPPORT -> EntityType.ALLAY;
        };

        // Triệu hồi thực thể
        Mob mob = (Mob) loc.getWorld().spawnEntity(loc, entType);

        // Đóng cờ định danh
        mob.setMetadata("td_custom_entity", new FixedMetadataValue(plugin, true));
        mob.setMetadata("td_mercenary", new FixedMetadataValue(plugin, mercId.toString()));
        mob.setMetadata("td_ally_id", new FixedMetadataValue(plugin, core.getAllyId()));
        mob.setMetadata("td_owner_uuid", new FixedMetadataValue(plugin, core.getOwnerUUID().toString()));

        // Ghi trực tiếp chế độ AI vào PDC của thực thể để bảo toàn dữ liệu
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        pdc.set(PDCKeys.MERC_MODE, PersistentDataType.STRING, mode);

        // Ký chữ băm điện tử chống Hacker tạo lính lậu
        plugin.getSecureEntityTracker().stampSecureHash(mob, "MERCENARY");

        // THĂNG TIẾN CHỈ SỐ THEO CẤP LÕI (GDD Scaling):
        // HP = HP Gốc * [1 + (CoreLevel - 3) * 0.25]
        // Sát thương = Sát thương Gốc * [1 + (CoreLevel - 3) * 0.20]
        int lvl = core.getLevel();
        double statMultiplierHp = 1.0 + (lvl - 3) * 0.25;
        double statMultiplierDmg = 1.0 + (lvl - 3) * 0.20;

        double baseHp = switch (type) {
            case MELEE -> 500.0;
            case ARCHER -> 250.0;
            case SIEGE -> 1200.0;
            case SUPPORT -> 300.0;
        };

        double scaledHp = baseHp * statMultiplierHp;

        // Áp dụng lượng máu tịnh tiến
        org.bukkit.attribute.AttributeInstance hpAttr = null;
        try {
            // 1. Thử dùng Reflection để lấy Enum Attribute cho cả hai phiên bản cũ và mới
            java.lang.reflect.Field field;
            try {
                field = org.bukkit.attribute.Attribute.class.getField("GENERIC_MAX_HEALTH");
            } catch (NoSuchFieldException e) {
                field = org.bukkit.attribute.Attribute.class.getField("MAX_HEALTH");
            }
            org.bukkit.attribute.Attribute attrEnum = (org.bukkit.attribute.Attribute) field.get(null);
            hpAttr = mob.getAttribute(attrEnum);
        } catch (Exception ignored) {}

        if (hpAttr == null) {
            try {
                // 2. Thử dùng Registry cũ (1.20.4-1.20.6)
                hpAttr = mob.getAttribute(org.bukkit.Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft("generic.max_health")));
            } catch (Exception ignored) {}
        }

        if (hpAttr == null) {
            try {
                // 3. Thử dùng Registry mới (1.21+)
                hpAttr = mob.getAttribute(org.bukkit.Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft("max_health")));
            } catch (Exception ignored) {}
        }

        if (hpAttr != null) {
            hpAttr.setBaseValue(scaledHp);
            mob.setHealth(scaledHp);
        }

        // Tên hiển thị kèm chế độ chiến đấu trực quan
        mob.setCustomName(ChatColor.GOLD + "Lính Đồng Minh [" + type.name() + "] (" + mode + ")");
        mob.setCustomNameVisible(true);

        // Đăng ký vào RAM
        ActiveMercenary merc = new ActiveMercenary(mercId, mob, type, mode, scaledHp, statMultiplierDmg);
        activeMercenaries.put(mercId, merc);

        loc.getWorld().playSound(loc, Sound.ENTITY_HORSE_ARMOR, 1.0f, 1.0f);
        return true;
    }

    // --- CÁC THUẬT TOÁN TỐI ƯU HÓA QUÉT MỤC TIÊU ---

    private LivingEntity findNearestEnemy(Mob mob, String myAllyId, double radius) {
        Collection<Entity> targets = mob.getWorld().getNearbyEntities(mob.getLocation(), radius, radius, radius);
        LivingEntity best = null;
        double closest = Double.MAX_VALUE;

        for (Entity e : targets) {
            if (!(e instanceof LivingEntity living) || e.isDead()) continue;

            // 1. Quét tìm quái vật tự nhiên hoặc quái Raid
            if (e instanceof Monster || e.hasMetadata("td_raid_mob")) {
                double dist = mob.getLocation().distance(e.getLocation());
                if (dist < closest) {
                    closest = dist;
                    best = living;
                }
            }
            // 2. Quét tìm người chơi lạ khác liên minh (không cùng ALLYID)
            else if (e instanceof Player p) {
                String targetAlly = plugin.getAllianceManager().getPlayerAlliance(p.getUniqueId());
                if (myAllyId != null && !myAllyId.equals(targetAlly)) {
                    double dist = mob.getLocation().distance(p.getLocation());
                    if (dist < closest) {
                        closest = dist;
                        best = living;
                    }
                }
            }
        }
        return best;
    }

    private LivingEntity findThreatToCivilians(Mob mob, TerritoryCore core, double radius) {
        Collection<Entity> targets = mob.getWorld().getNearbyEntities(mob.getLocation(), radius, radius, radius);
        for (Entity e : targets) {
            if (e instanceof Mob hostile && e instanceof Monster) {
                LivingEntity target = hostile.getTarget();
                // Nếu quái đang nhắm bắn Farmer thuộc ranh giới của ta
                if (target != null && target.hasMetadata("td_farmer")) {
                    return hostile;
                }
            }
        }
        return null;
    }

    /**
     * Kỹ năng lính hỗ trợ Allay: Định kỳ bơm giáp ảo trực tiếp cho Lõi chính.
     */
    private void performAllaySupportHeal(Mob mob, TerritoryCore core) {
        double currentShield = core.getShield();
        double maxShield = core.getMaxShieldCapacity();

        if (currentShield < maxShield) {
            // Nạp 15.0 Shield HP mỗi lượt cho Lõi
            core.setShield(currentShield + 15.0);
            mob.getWorld().spawnParticle(org.bukkit.Particle.HEART, mob.getLocation().add(0, 0.5, 0), 3);
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ALLAY_ITEM_TAKEN, 0.5f, 1.5f);
        }
    }

    // --- BẮT SỰ KIỆN CHIẾN ĐẤU CỦA LÍNH ĐÁNH THUÊ ---

    /**
     * TĂNG SÁT THƯƠNG PHÁ CẤU TRÚC:
     * Chủng Kỵ binh (Ravager) gây sát thương x3 lên Lõi chính và Tháp canh đối thủ.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMercenaryDamageStructure(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!damager.hasMetadata("td_mercenary")) return;

        String idStr = damager.getMetadata("td_mercenary").get(0).asString();
        ActiveMercenary merc = activeMercenaries.get(UUID.fromString(idStr));

        if (merc != null && merc.getType() == MercenaryType.SIEGE) {
            // Nếu mục tiêu tấn công là Lõi hoặc Tháp canh
            Material targetType = event.getEntity().getLocation().getBlock().getType();
            if (targetType == Material.BEACON || targetType == Material.DISPENSER || targetType == Material.OBSIDIAN) {
                // x3 Sát thương phá cấu trúc theo GDD
                event.setDamage(event.getDamage() * 3.0);
                event.getEntity().getWorld().playSound(event.getEntity().getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 0.5f);
            }
        }
    }

    /**
     * CHẶN FRIENDLY FIRE & AI TARGET SAI LỆCH:
     * Ngăn chặn lính đánh thuê tấn công người chơi, Farmer và lính đồng minh trong bang.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMercenaryTargetValidation(EntityTargetLivingEntityEvent event) {
        Entity mob = event.getEntity();
        LivingEntity target = event.getTarget();
        if (target == null || !mob.hasMetadata("td_mercenary")) return;

        String myAllyId = mob.getMetadata("td_ally_id").get(0).asString();

        // 1. Chặn target người chơi đồng đội
        if (target instanceof Player player) {
            String pAlly = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
            if (myAllyId.equals(pAlly)) {
                event.setCancelled(true);
            }
        }
        // 2. Chặn target NPC Farmer phe mình
        else if (target.hasMetadata("td_farmer")) {
            event.setCancelled(true);
        }
        // 3. Chặn target lính đồng minh
        else if (target.hasMetadata("td_mercenary")) {
            String tAlly = target.getMetadata("td_ally_id").get(0).asString();
            if (myAllyId.equals(tAlly)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onMercenaryDeath(EntityDeathEvent event) {
        Entity mob = event.getEntity();
        if (mob.hasMetadata("td_mercenary")) {
            String uuidStr = mob.getMetadata("td_mercenary").get(0).asString();
            activeMercenaries.remove(UUID.fromString(uuidStr));
        }
    }

    // --- LỚP PHỤ TRỢ QUẢN LÝ LÍNH TRONG RAM ---

    private static class ActiveMercenary {
        private final UUID uuid;
        private final Mob entity;
        private final MercenaryType type;
        private final String mode;
        private final double maxHp;
        private final double dmgMultiplier;

        public ActiveMercenary(UUID uuid, Mob entity, MercenaryType type, String mode, double maxHp, double dmgMultiplier) {
            this.uuid = uuid;
            this.entity = entity;
            this.type = type;
            this.mode = mode;
            this.maxHp = maxHp;
            this.dmgMultiplier = dmgMultiplier;
        }

        public UUID getUUID() { return uuid; }
        public Mob getEntity() { return entity; }
        public MercenaryType getType() { return type; }
        public String getMode() { return mode; }
        public double getMaxHp() { return maxHp; }
        public double getDmgMultiplier() { return dmgMultiplier; }
    }
}