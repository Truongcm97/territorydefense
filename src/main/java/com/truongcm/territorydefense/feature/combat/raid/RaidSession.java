package com.truongcm.territorydefense.feature.combat.raid;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.bukkit.Bukkit.getLogger;

/**
 * ĐIỀU PHỐI ĐỢT RAID PVE (RAID SESSION CONTROLLER)
 * Chịu trách nhiệm quản lý chu kỳ Raid, tính toán số lượng quái theo công thức hình học,
 * spawn dãn cách cụm (Staggered Spawning) chống lag TPS và kiểm soát vòng đời quái công thành.
 *
 * ĐÃ SỬA LỖI 1: Tối ưu hóa cao độ Spawn Y bằng thuật toán quét khối cứng thông minh,
 * khắc phục hoàn toàn lỗi quái bị kẹt trên nóc địa ngục (Nether Bedrock), hang động hoặc ngọn cây.
 */

public class RaidSession implements Listener {

    private final TerritoryDefense plugin;

    // Lưu trữ các đợt Raid đang diễn ra thời gian thực
    // Key: Core UUID, Value: Chiến dịch Raid hiện tại
    private final Map<UUID, ActiveRaidCampaign> activeCampaigns = new ConcurrentHashMap<>();

    // Ghi nhớ giờ đã trigger raid cho từng Core trong ngày (tránh trigger trùng 2 lần trong 1 giờ)
    // Key: Core UUID, Value: giờ cuối cùng đã trigger (0-23)
    private final Map<UUID, Integer> lastTriggeredHour = new ConcurrentHashMap<>();

    public RaidSession(TerritoryDefense plugin) {
        this.plugin = plugin;

        // ===================================================================
        // RAID AUTO-SPAWN SCHEDULER
        // Lịch cố định: Raid xảy ra đúng đầu mỗi giờ thực (0h, 1h, 2h ... 23h)
        // Điều kiện: Chủ lãnh thổ phải đang ONLINE tại thời điểm kiểm tra
        // Nếu offline: bỏ qua hoàn toàn, không tích lũy debt
        // Kiểm tra mỗi phút (1200 ticks) để bắt đúng đầu giờ
        // ===================================================================
        new BukkitRunnable() {
            @Override
            public void run() {
                // Lấy giờ thực tế hiện tại theo múi giờ server
                Calendar now = Calendar.getInstance(TimeZone.getDefault());
                int currentHour = now.get(Calendar.HOUR_OF_DAY);
                int currentMinute = now.get(Calendar.MINUTE);

                // Chỉ trigger trong khoảng phút 0 đến 1 (đầu giờ ±1 phút)
                // để tránh trượt giờ nếu task Bukkit bị delay nhẹ
                if (currentMinute > 1) return;

                for (TerritoryCore core : plugin.getCoreManager().getAllActiveCores()) {
                    UUID coreId = core.getCoreId();

                    // --- Kiểm tra chủ lãnh thổ phải đang online ---
                    Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                    if (owner == null || !owner.isOnline()) {
                        continue; // Offline → không raid lãnh thổ này
                    }

                    // --- Kiểm tra đã trigger trong giờ này chưa ---
                    int lastHour = lastTriggeredHour.getOrDefault(coreId, -1);
                    if (lastHour == currentHour) {
                        continue; // Đã raid trong giờ này rồi, bỏ qua
                    }

                    // --- Kiểm tra không đang bị raid hoặc hòa bình ---
                    if (activeCampaigns.containsKey(coreId)) continue;
                    if (plugin.getCoreManager().isUnderPeaceProtection(coreId)) continue;

                    // --- Kích hoạt raid và ghi nhớ giờ trigger ---
                    lastTriggeredHour.put(coreId, currentHour);
                    plugin.getCoreManager().setLastRaidTime(coreId, System.currentTimeMillis());
                    plugin.getCoreManager().saveAllCores();
                    startRaid(core, false, 0);
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Kiểm tra mỗi 1 phút
    }


    /**
     * Khởi động một chiến dịch Raid PvE cho Lõi chỉ định.
     * @param core Lõi chính diễn ra trận đấu
     * @param isPurchased true nếu đây là đợt Raid người chơi chủ động mua thêm bằng tiền quỹ
     */
    public void startRaid(TerritoryCore core, boolean isPurchased, int purchasedIndex) {
        if (core == null) return;
        UUID coreId = core.getCoreId();

        if (activeCampaigns.containsKey(coreId)) {
            return; // Đang có trận chiến diễn ra, từ chối tạo trùng lặp
        }

        ActiveRaidCampaign campaign = new ActiveRaidCampaign(core, isPurchased, purchasedIndex);
        activeCampaigns.put(coreId, campaign);

        // Kích hoạt trạng thái Lõi đang bị Raid trong RAM để chặn tẩu tán tài sản
        core.setRaidActive(true);
        core.setTempHealth(core.getMaxShieldCapacity());

        // Phát loa thông báo khẩn cấp cho Liên minh sở hữu
        broadcastToAlliance(core, ChatColor.RED + "=============================================");
        broadcastToAlliance(core, ChatColor.RED + " CẢNH BÁO XÂM NHẬP: LŨ QUÁI CÔNG THÀNH ĐANG TIẾP CẬN!");
        broadcastToAlliance(core, ChatColor.YELLOW + " Cấp độ Lõi: " + core.getLevel() + " | Tổng số đợt công: " + (core.getLevel() == 5 ? 5 : 3));
        broadcastToAlliance(core, ChatColor.RED + "=============================================");

        core.getLocation().getWorld().playSound(core.getLocation(), Sound.EVENT_RAID_HORN, 2.0f, 1.0f);

        // Đếm ngược 5 giây chuẩn bị trước khi Wave 1 xuất phát
        new BukkitRunnable() {
            @Override
            public void run() {
                campaign.launchNextWave();
            }
        }.runTaskLater(plugin, 100L);
    }

    /**
     * Kết tụ an toàn đợt Raid, thu dọn tàn dư và khôi phục trạng thái Lõi.
     */
    public void endRaid(TerritoryCore core, boolean success) {
        ActiveRaidCampaign campaign = activeCampaigns().get(core.getCoreId());
        if (campaign == null) return;

        // Hoàn nguyên máu Lõi về giá trị định mức theo RAM an toàn
        core.revertHealth();
        activeCampaigns.remove(core.getCoreId());

        if (success) {
            broadcastToAlliance(core, ChatColor.GREEN + "Chúc mừng! Các chiến binh đã đẩy lùi hoàn toàn đợt Raid thành công.");
            core.getLocation().getWorld().playSound(core.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
        } else {
            broadcastToAlliance(core, ChatColor.RED + "Lãnh Thổ Thất Thủ! Lõi chính đã bị phá hủy cấu trúc tạm thời.");
            core.getLocation().getWorld().playSound(core.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f);
        }
    }

    /**
     * Theo dõi sự kiện quái chết để khấu trừ quân số Wave hiện hành.
     */
    @EventHandler
    public void onRaidMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Kiểm tra an toàn: Đảm bảo Key không bị null trước khi gọi has()
        if (PDCKeys.RAID_MOB_TAG == null) {
            getLogger().warning("PDCKeys.RAID_MOB_TAG chưa được khởi tạo!");
            return;
        }

        if (!entity.getPersistentDataContainer().has(PDCKeys.RAID_MOB_TAG, PersistentDataType.BYTE)) {
            return;
        }

        // Kiểm tra an toàn cho OWNER_CORE_ID
        if (PDCKeys.OWNER_CORE_ID == null) return;

        String coreIdStr = entity.getPersistentDataContainer().get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING);
        if (coreIdStr == null) return;

        UUID coreId;
        try {
            coreId = UUID.fromString(coreIdStr);
        } catch (IllegalArgumentException e) {
            return; // Dữ liệu bị lỗi, bỏ qua
        }

        ActiveRaidCampaign campaign = activeCampaigns.get(coreId);
        if (campaign != null) {
            campaign.registerMobKill(entity);
        }
    }

    // --- LỚP NỘI BỘ QUẢN LÝ TIẾN TRÌNH RAID CAMPAIGN ---

    public class ActiveRaidCampaign {
        private final TerritoryCore core;
        private final boolean isPurchased;
        private final int purchasedIndex;
        private int currentWave = 0;
        private final int maxWaves;

        private final Set<Entity> aliveMobs = Collections.synchronizedSet(new HashSet<>());
        private final List<EntityType> pendingSpawnQueue = Collections.synchronizedList(new ArrayList<>());
        private BukkitRunnable spawnTask = null;
        // Flag để đánh dấu spawn queue đã hoàn tất (tránh race condition với spawnTask.cancel())
        private volatile boolean spawnCompleted = false;

        public ActiveRaidCampaign(TerritoryCore core, boolean isPurchased, int purchasedIndex) {
            this.core = core;
            this.isPurchased = isPurchased;
            this.purchasedIndex = purchasedIndex;
            // Cấp 5 có 5 Wave, các cấp còn lại có 3 Wave theo GDD
            this.maxWaves = (core.getLevel() == 5) ? 5 : 3;

            // Khởi chạy vòng lặp quản lý AI quái: Chạy mỗi 1 giây (20 ticks)
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!activeCampaigns.containsKey(core.getCoreId())) {
                        cancel();
                        return;
                    }
                    tickMobAI();
                }
            }.runTaskTimer(plugin, 20L, 20L);
        }

        private void tickMobAI() {
            synchronized (aliveMobs) {
                for (Entity entity : aliveMobs) {
                    if (!(entity instanceof org.bukkit.entity.Mob mob) || !mob.isValid()) continue;

                    Location mobLoc = mob.getLocation();
                    Location coreLoc = core.getLocation();

                    // Ép quái di chuyển công phá Lõi
                    mob.getPathfinder().moveTo(coreLoc, 1.25D);

                    // Khoảng cách tới Lõi
                    double distance = mobLoc.distance(coreLoc);
                    if (distance <= 4.0) {
                        // Tấn công trực tiếp vào Lõi (Không phá block, tấn công tempHealth)
                        int damageTicks = mob.getMetadata("td_attack_ticks").stream().findFirst().map(m -> m.asInt()).orElse(0);
                        if (damageTicks >= 20) { // Mỗi 1 giây tấn công 1 lần
                            mob.setMetadata("td_attack_ticks", new org.bukkit.metadata.FixedMetadataValue(plugin, 0));
                            
                            // Gây sát thương vào Lõi
                            double damage = 50.0; // Sát thương mặc định
                            if (mob.getType() == EntityType.GIANT) damage = 300.0;
                            else if (mob.getType() == EntityType.RAVAGER) damage = 150.0;

                            double currentHealth = core.getTempHealth();
                            double newHealth = Math.max(0.0, currentHealth - damage);
                            core.setTempHealth(newHealth);

                            // Hiệu ứng cắn phá Lõi
                            coreLoc.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, coreLoc.clone().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.1);
                            coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.0f);

                            broadcastToAlliance(core, ChatColor.RED + "[Cảnh báo] Lõi đang bị " + mob.getName() + ChatColor.RED + " cắn phá! HP Lõi còn lại: " + String.format("%.0f", newHealth) + "/" + core.getMaxShieldCapacity());

                            // Nếu cạn máu -> Thất bại
                            if (newHealth <= 0.0) {
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        endRaid(core, false);
                                    }
                                }.runTask(plugin);
                                return;
                            }
                        } else {
                            mob.setMetadata("td_attack_ticks", new org.bukkit.metadata.FixedMetadataValue(plugin, damageTicks + 1));
                        }
                    }
                }
            }
        }

        /**
         * Kích hoạt đợt Wave tiếp theo.
         */
        public void launchNextWave() {
            currentWave++;
            if (currentWave > maxWaves) {
                endRaid(core, true);
                return;
            }

            broadcastToAlliance(core, ChatColor.GOLD + ">>> BẮT ĐẦU ĐỢT " + currentWave + "/" + maxWaves + " <<<");
            core.getLocation().getWorld().playSound(core.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.8f);

            // 1. Tạo danh sách các loại quái cần Spawn dựa trên ma trận cấp Lõi và Wave
            buildWaveSpawnQueue();

            // 2. Chạy cơ chế STAGGERED SPAWNING: Chia nhỏ quái spawn cách nhau 2 giây tránh lag giật tick
            startStaggeredSpawning();
        }

        /**
         * Chạy tiến trình spawn dãn cách 40 ticks/lần (2 giây) rải rác ngoài rìa ranh giới.
         */
        private void startStaggeredSpawning() {
            if (spawnTask != null) spawnTask.cancel();

            // Lấy hệ số tăng trưởng độ khó (Static & Dynamic Mult)
            double staticMult = plugin.getConfig().getDouble("raid-settings.static-multiplier." + core.getLevel(), 1.0);
            double dynamicMult = 1.0;
            if (isPurchased) {
                dynamicMult = plugin.getConfig().getDouble("raid-settings.dynamic-multiplier." + purchasedIndex, 1.2);
            }
            double finalHpMultiplier = staticMult * dynamicMult;

            double radius = core.getRadius();
            Location cLoc = core.getLocation();

            spawnCompleted = false; // Reset flag khi bắt đầu wave mới
            spawnTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (pendingSpawnQueue.isEmpty()) {
                        cancel();
                        spawnTask = null;
                        spawnCompleted = true; // Đánh dấu spawn queue đã hoàn tất
                        // Nếu tất cả quái đã chết trong lúc spawn, kích hoạt wave tiếp theo
                        if (aliveMobs.isEmpty() && activeCampaigns.containsKey(core.getCoreId())) {
                            broadcastToAlliance(core, ChatColor.GREEN + "Đã quét sạch đợt " + currentWave + ". Hồi sức chuẩn bị đợt kế tiếp sau 45 giây!");
                            core.getLocation().getWorld().playSound(core.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (activeCampaigns.containsKey(core.getCoreId())) {
                                        launchNextWave();
                                    }
                                }
                            }.runTaskLater(plugin, 900L);
                        }
                        return;
                    }

                    // Mỗi đợt dãn cách chỉ spawn 3 - 5 con theo GDD để bảo vệ tài nguyên
                    int spawnCountThisTick = Math.min(pendingSpawnQueue.size(), 3 + (int)(Math.random() * 3));
                    for (int i = 0; i < spawnCountThisTick; i++) {
                        if (pendingSpawnQueue.isEmpty()) break;
                        EntityType type = pendingSpawnQueue.remove(0);

                        // Tính toán tọa độ spawn hình tròn nằm ở rìa ranh giới bảo vệ (bán kính r + 3)
                        double angle = Math.random() * Math.PI * 2;
                        double spawnX = cLoc.getX() + (radius + 3.0) * Math.cos(angle);
                        double spawnZ = cLoc.getZ() + (radius + 3.0) * Math.sin(angle);

                        // SỬA LỖI 1: Quét tìm khối block cứng an toàn gần cao độ của Lõi để chống quái kẹt nóc/Void
                        double spawnY = findSafeSpawnY(cLoc, spawnX, spawnZ);

                        Location spawnLoc = new Location(cLoc.getWorld(), spawnX, spawnY, spawnZ);
                        spawnSingleMob(type, spawnLoc, finalHpMultiplier);
                    }
                }
            };
            spawnTask.runTaskTimer(plugin, 0L, 40L); // 40 ticks = 2 giây
        }

        /**
         * Thuật toán tìm kiếm bề mặt đứng vững an toàn xung quanh cao độ Lõi
         * Chạy vòng lặp quét từ độ cao Lõi lên/xuống tối đa 16 Block để tìm không gian trống cao 2 block rỗng
         */
        private double findSafeSpawnY(Location coreLoc, double x, double z) {
            org.bukkit.World world = coreLoc.getWorld();
            if (world == null) return coreLoc.getY();

            int startY = coreLoc.getBlockY();
            // Quét luân phiên lên và xuống để tìm điểm đáp đất gần cao độ Lõi nhất
            for (int dy = 0; dy <= 16; dy++) {
                // Quét phía trên
                int checkY = startY + dy;
                if (checkY < world.getMaxHeight() - 1) {
                    org.bukkit.block.Block feet = world.getBlockAt((int) x, checkY, (int) z);
                    org.bukkit.block.Block head = world.getBlockAt((int) x, checkY + 1, (int) z);
                    org.bukkit.block.Block ground = world.getBlockAt((int) x, checkY - 1, (int) z);
                    if (feet.getType().isAir() && head.getType().isAir() && ground.getType().isSolid()) {
                        return checkY;
                    }
                }

                // Quét phía dưới (nếu dy > 0)
                if (dy > 0) {
                    checkY = startY - dy;
                    if (checkY > world.getMinHeight()) {
                        org.bukkit.block.Block feet = world.getBlockAt((int) x, checkY, (int) z);
                        org.bukkit.block.Block head = world.getBlockAt((int) x, checkY + 1, (int) z);
                        org.bukkit.block.Block ground = world.getBlockAt((int) x, checkY - 1, (int) z);
                        if (feet.getType().isAir() && head.getType().isAir() && ground.getType().isSolid()) {
                            return checkY;
                        }
                    }
                }
            }

            // Nếu không tìm thấy đất trống an toàn, fallback dùng độ cao bề mặt hoặc cao độ Lõi gốc
            try {
                return world.getHighestBlockYAt((int) x, (int) z) + 1.0;
            } catch (Exception e) {
                return coreLoc.getY();
            }
        }

        /**
         * Triệu hồi một thực thể quái cụ thể và đóng dấu PDC an toàn tuyệt đối.
         */
        private void spawnSingleMob(EntityType type, Location loc, double hpMultiplier) {
            LivingEntity mob = (LivingEntity) loc.getWorld().spawnEntity(loc, type);

            // Đóng cờ Metadata để hệ thống dễ quản lý và chống hack
            mob.getPersistentDataContainer().set(PDCKeys.RAID_MOB_TAG, PersistentDataType.BYTE, (byte) 1);
            mob.getPersistentDataContainer().set(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING, core.getCoreId().toString());
            mob.setMetadata("td_owner_core", new FixedMetadataValue(plugin, core.getCoreId().toString()));

            // Stamp mã băm bảo mật PDC chống gian lận
            plugin.getSecureEntityTracker().stampSecureHash(mob, "RAID_MOB");

            // Đăng ký lượng máu gốc cực kỳ chính xác theo GDD
            double baseHp = getBaseHpForType(type);
            double scaledHp = baseHp * hpMultiplier;

            // Áp dụng chỉ số máu mới lên quái vật với cơ chế tương thích chéo phiên bản Paper
            try {
                org.bukkit.attribute.AttributeInstance maxHealthAttr = null;
                
                // Cách 1: Thử bằng Enum cũ trực tiếp trước (Dành cho Paper 1.20.4 trở xuống)
                try {
                    java.lang.reflect.Field field = org.bukkit.attribute.Attribute.class.getField("GENERIC_MAX_HEALTH");
                    org.bukkit.attribute.Attribute attrEnum = (org.bukkit.attribute.Attribute) field.get(null);
                    maxHealthAttr = mob.getAttribute(attrEnum);
                } catch (Throwable ignored) {}

                // Cách 2: Registry lookup fallback (Dành cho Paper 1.20.6+)
                if (maxHealthAttr == null) {
                    try {
                        maxHealthAttr = mob.getAttribute(org.bukkit.Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft("generic.max_health")));
                    } catch (Throwable ignored) {}
                }
                
                if (maxHealthAttr != null) {
                    maxHealthAttr.setBaseValue(scaledHp);
                    mob.setHealth(scaledHp);
                }
            } catch (Throwable t) {
                // Nuốt hoàn toàn IllegalArgumentException từ CraftAttributeMap để quái vật vẫn spawn bình thường với máu mặc định
                try {
                    mob.setHealth(Math.min(scaledHp, mob.getMaxHealth()));
                } catch (Throwable ignored) {}
            }

            // Gắn tên hiển thị chuyên nghiệp
            mob.setCustomName(ChatColor.RED + "Quái Công Thành [HP: " + String.format("%.0f", scaledHp) + "]");
            mob.setCustomNameVisible(true);

            // Đăng ký bể theo dõi đóng góp sát thương phòng AFK
            plugin.getCombatDamageTracker().registerRaidMob(mob, scaledHp);
            aliveMobs.add(mob);
        }

        /**
         * Ghi nhận khi có quái bị tiêu diệt và tự động kích hoạt Wave tiếp sau 45s dọn dẹp.
         */
        public void registerMobKill(Entity entity) {
            aliveMobs.remove(entity);

            // SỬA LỖI WAVE 2: Dùng spawnCompleted thay vì spawnTask == null để tránh race condition
            // (spawnTask.cancel() không đồng bộ, spawnTask chưa kịp null khi con quái cuối chết)
            if (aliveMobs.isEmpty() && pendingSpawnQueue.isEmpty() && spawnCompleted) {
                // Wave thành công! Chờ 45 giây đếm ngược hồi sức
                broadcastToAlliance(core, ChatColor.GREEN + "Đã quét sạch đợt " + currentWave + ". Hồi sức chuẩn bị đợt kế tiếp sau 45 giây!");
                core.getLocation().getWorld().playSound(core.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (activeCampaigns.containsKey(core.getCoreId())) {
                            launchNextWave();
                        }
                    }
                }.runTaskLater(plugin, 900L); // 45 giây = 900 Ticks
            }
        }

        /**
         * Phân tích ma trận bàn quái theo đúng Wave Spawning Profile trong tài liệu GDD.
         */
        private void buildWaveSpawnQueue() {
            pendingSpawnQueue.clear();
            int lvl = core.getLevel();

            // Tính số lượng quái tịnh tiến theo công thức: 25 * Cấp Lõi ^ 1.75
            int expectedTotal = (int) (25 * Math.pow(lvl, 1.75));

            // Xây dựng danh sách quái phân bổ theo cấu hình bảng GDD từng cấp Lõi
            if (lvl == 1) {
                if (currentWave == 1) {
                    addMobsToQueue(EntityType.VINDICATOR, 5);
                    addMobsToQueue(EntityType.SKELETON, 2);
                } else if (currentWave == 2) {
                    addMobsToQueue(EntityType.VINDICATOR, 6);
                    addMobsToQueue(EntityType.SKELETON, 3);
                    addMobsToQueue(EntityType.PHANTOM, 1);
                } else {
                    addMobsToQueue(EntityType.VINDICATOR, 4);
                    addMobsToQueue(EntityType.SKELETON, 4);
                    addMobsToQueue(EntityType.PHANTOM, 2);
                }
            } else if (lvl == 2) {
                addMobsToQueue(EntityType.VINDICATOR, 8 + currentWave * 2);
                addMobsToQueue(EntityType.SKELETON, 4 + currentWave);
                addMobsToQueue(EntityType.GHAST, currentWave);
                addMobsToQueue(EntityType.RAVAGER, currentWave / 2);
            } else {
                // Cấp 3, 4, 5 bổ sung thêm Boss lớn và đa dạng các phân lớp
                addMobsToQueue(EntityType.VINDICATOR, 12 + currentWave * 4);
                addMobsToQueue(EntityType.SKELETON, 6 + currentWave * 2);
                addMobsToQueue(EntityType.GHAST, 2 + currentWave);
                addMobsToQueue(EntityType.RAVAGER, 1 + currentWave);
                addMobsToQueue(EntityType.EVOKER, currentWave);
                if (currentWave == maxWaves) {
                    addMobsToQueue(EntityType.GIANT, 1); // Siêu Boss Giant xuất hiện ở đợt cuối
                }
            }
        }

        private void addMobsToQueue(EntityType type, int count) {
            for (int i = 0; i < count; i++) {
                pendingSpawnQueue.add(type);
            }
        }

        private double getBaseHpForType(EntityType type) {
            return switch (type) {
                case VINDICATOR -> 150.0;
                case GHAST -> 100.0;
                case RAVAGER -> 1500.0;
                case EVOKER -> 200.0;
                case SKELETON -> 120.0;
                case PHANTOM -> 150.0;
                case GIANT -> 10000.0;
                default -> 100.0;
            };
        }
    }

    private void broadcastToAlliance(TerritoryCore core, String message) {
        List<UUID> members = plugin.getAllianceManager().getAllianceMembers(core.getAllyId());

        for (UUID memberUuid : members) {
            Player player = Bukkit.getPlayer(memberUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    public Map<UUID, ActiveRaidCampaign> activeCampaigns() {
        return activeCampaigns;
    }
}