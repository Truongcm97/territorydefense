package com.truongcm.territorydefense.feature.combat.raid;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.block.Block;
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

        // Kích hoạt quét Snapshot Pre-Raid nếu có Builder hoạt động
        com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getActiveBuilders().get(coreId);
        if (builder != null) {
            builder.getLastPreRaidSnapshot().clear();
            Location coreLoc = core.getLocation();
            int radius = plugin.getCoreManager().getCoreRadius(core);
            int scanHeightBelow = plugin.getConfig().getInt("builder-settings.scan-height-below", 5);
            int scanHeightAbove = plugin.getConfig().getInt("builder-settings.scan-height-above", 15);
            class LocationDelta {
                final int dx, dy, dz;
                LocationDelta(int dx, int dy, int dz) {
                    this.dx = dx;
                    this.dy = dy;
                    this.dz = dz;
                }
            }

            Queue<LocationDelta> scanQueue = new LinkedList<>();
            for (int dy = -scanHeightBelow; dy <= scanHeightAbove; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        scanQueue.add(new LocationDelta(dx, dy, dz));
                    }
                }
            }

            new BukkitRunnable() {
                private static final int BLOCKS_PER_TICK = 1500;

                @Override
                public void run() {
                    if (!activeCampaigns.containsKey(coreId)) {
                        cancel();
                        return;
                    }

                    int processed = 0;
                    while (!scanQueue.isEmpty() && processed < BLOCKS_PER_TICK) {
                        LocationDelta delta = scanQueue.poll();
                        Block block = coreLoc.getWorld().getBlockAt(
                            coreLoc.getBlockX() + delta.dx, 
                            coreLoc.getBlockY() + delta.dy, 
                            coreLoc.getBlockZ() + delta.dz
                        );
                        Material type = block.getType();
                        if (type != Material.AIR && type != Material.CAVE_AIR && type != Material.VOID_AIR && type != Material.CONDUIT) {
                            String blockDataStr = block.getBlockData().getAsString();
                            TerritoryCore.BlockSnapshot snap = new TerritoryCore.BlockSnapshot(
                                delta.dx, delta.dy, delta.dz, type.name(), blockDataStr
                            );
                            campaign.getPreRaidSnapshot().add(snap);
                            builder.getLastPreRaidSnapshot().add(snap);
                        }
                        processed++;
                    }

                    if (scanQueue.isEmpty()) {
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }

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
        
        // Huỷ bỏ nhiệm vụ spawn dãn cách và dọn sạch quái còn sống
        campaign.cleanup();
        
        // Kích hoạt NPC Mason tái thiết lãnh thổ
        com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getActiveBuilders().get(core.getCoreId());
        if (builder != null && !campaign.getPreRaidSnapshot().isEmpty()) {
            Player owner = Bukkit.getPlayer(core.getOwnerUUID());
            builder.startRebuild(core, campaign.getPreRaidSnapshot(), owner);
        }

        activeCampaigns.remove(core.getCoreId());

        // Ghi nhận số lượng đợt raid phục vụ tính toán độ khó luỹ tiến 5%
        core.setTotalRaidCount(core.getTotalRaidCount() + 1);
        if (campaign.isPurchased) {
            core.setRaidCallCount(core.getRaidCallCount() + 1);
        }
        plugin.getCoreManager().saveAllCores();

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

        private final org.bukkit.boss.BossBar bossBar;
        private int totalWaveMobs = 0;
        private long waveStartTime = 0;
        private final long waveDurationLimitMillis;
        private final List<TerritoryCore.BlockSnapshot> preRaidSnapshot = new java.util.ArrayList<>();

        private final Map<UUID, Integer> waveDirectShards = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<UUID, Integer> waveHarvestedShards = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<UUID, Double> waveCoinsEarned = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<UUID, Integer> waveMobsContributed = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<UUID, Integer> waveMobsMissed = new java.util.concurrent.ConcurrentHashMap<>();

        public void addWaveDirectShards(UUID playerUuid, int amount) {
            waveDirectShards.put(playerUuid, waveDirectShards.getOrDefault(playerUuid, 0) + amount);
        }

        public void addWaveHarvestedShards(UUID playerUuid, int amount) {
            waveHarvestedShards.put(playerUuid, waveHarvestedShards.getOrDefault(playerUuid, 0) + amount);
        }

        public void addWaveCoinsEarned(UUID playerUuid, double amount) {
            waveCoinsEarned.put(playerUuid, waveCoinsEarned.getOrDefault(playerUuid, 0.0) + amount);
        }

        public void incrementWaveMobsContributed(UUID playerUuid) {
            waveMobsContributed.put(playerUuid, waveMobsContributed.getOrDefault(playerUuid, 0) + 1);
        }

        public void incrementWaveMobsMissed(UUID playerUuid) {
            waveMobsMissed.put(playerUuid, waveMobsMissed.getOrDefault(playerUuid, 0) + 1);
        }

        public List<TerritoryCore.BlockSnapshot> getPreRaidSnapshot() {
            return preRaidSnapshot;
        }

        public void cleanup() {
            java.util.List<Player> playersToReset = new java.util.ArrayList<>();
            if (bossBar != null) {
                playersToReset.addAll(bossBar.getPlayers());
                try {
                    bossBar.removeAll();
                } catch (Exception ignored) {}
            }
            if (spawnTask != null) {
                try {
                    spawnTask.cancel();
                } catch (Exception ignored) {}
                spawnTask = null;
            }
            pendingSpawnQueue.clear();
            synchronized (aliveMobs) {
                for (Entity mob : aliveMobs) {
                    if (mob.isValid()) {
                        removeNoCollision(mob);
                        mob.remove();
                    }
                }
                aliveMobs.clear();
            }
            for (Player player : playersToReset) {
                try {
                    player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                } catch (Exception ignored) {}
            }
        }

        public ActiveRaidCampaign(TerritoryCore core, boolean isPurchased, int purchasedIndex) {
            this.core = core;
            this.isPurchased = isPurchased;
            this.purchasedIndex = purchasedIndex;
            // Đọc giới hạn thời gian wave từ config
            int waveMinutes = plugin.getConfig().getInt("raid-settings.wave-duration-limit-minutes", 10);
            this.waveDurationLimitMillis = (long) waveMinutes * 60 * 1000L;
            // Cấp 5 có 5 Wave, các cấp còn lại có 3 Wave theo GDD
            this.maxWaves = (core.getLevel() == 5) ? 5 : 3;

            // Khởi tạo BossBar hiển thị thông tin
            this.bossBar = Bukkit.createBossBar(
                ChatColor.RED + "Đợt Raid Lãnh Thổ | Đang chuẩn bị...",
                org.bukkit.boss.BarColor.RED,
                org.bukkit.boss.BarStyle.SOLID
            );

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

            // Khởi chạy vòng lặp cập nhật UI (BossBar & Scoreboard): Chạy mỗi 2 giây (40 ticks)
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!activeCampaigns.containsKey(core.getCoreId())) {
                        cancel();
                        return;
                    }
                    updateBossBar();
                }
            }.runTaskTimer(plugin, 20L, 40L);
        }

        private void updateMobCustomName(org.bukkit.entity.LivingEntity mob) {
            try {
                double maxHp = mob.getMaxHealth();
                double currentHp = mob.getHealth();
                if (mob.hasMetadata("td_intended_max_hp") && mob.hasMetadata("td_actual_max_hp")) {
                    double intended = mob.getMetadata("td_intended_max_hp").get(0).asDouble();
                    double actual = mob.getMetadata("td_actual_max_hp").get(0).asDouble();
                    if (actual > 0) {
                        maxHp = intended;
                        currentHp = mob.getHealth() * (intended / actual);
                    }
                }
                mob.setCustomName(ChatColor.RED + "Quái Công Thành [HP: " + String.format("%.0f", Math.max(0.0, currentHp)) + "/" + String.format("%.0f", maxHp) + "]");
                mob.setCustomNameVisible(true);
            } catch (Throwable ignored) {}
        }

        private void updateRaidScoreboard(Player player) {
            try {
                org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
                org.bukkit.scoreboard.Scoreboard board = player.getScoreboard();
                
                if (board == manager.getMainScoreboard() || board.getObjective("td_raid_sb") == null) {
                    board = manager.getNewScoreboard();
                }

                org.bukkit.scoreboard.Objective obj = board.getObjective("td_raid_sb");
                if (obj == null) {
                    obj = board.registerNewObjective("td_raid_sb", "dummy", ChatColor.translateAlternateColorCodes('&', "&e&lRaid Lãnh Thổ"));
                    obj.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
                }

                for (String entry : board.getEntries()) {
                    board.resetScores(entry);
                }

                UUID pUuid = player.getUniqueId();
                int kills = waveMobsContributed.getOrDefault(pUuid, 0);
                double coins = waveCoinsEarned.getOrDefault(pUuid, 0.0);
                int shards = waveDirectShards.getOrDefault(pUuid, 0) + waveHarvestedShards.getOrDefault(pUuid, 0);

                int remaining = aliveMobs.size() + pendingSpawnQueue.size();
                long elapsed = System.currentTimeMillis() - waveStartTime;
                long remainingTimeSeconds = Math.max(0, (waveDurationLimitMillis - elapsed) / 1000);
                String timeStr = String.format("%02d:%02d", remainingTimeSeconds / 60, remainingTimeSeconds % 60);

                List<String> lines = new ArrayList<>();
                lines.add(ChatColor.GRAY + "----------------------");
                lines.add(ChatColor.GOLD + "● Trạng thái Lõi:");
                lines.add(ChatColor.WHITE + "  Máu Lõi: " + ChatColor.GREEN + String.format("%.0f", core.getTempHealth()) + ChatColor.GRAY + "/" + String.format("%.0f", core.getMaxShieldCapacity()));
                lines.add(ChatColor.WHITE + "  Giáp ảo: " + ChatColor.AQUA + String.format("%.0f", core.getShield()));
                lines.add(ChatColor.WHITE + "  PEP (FEP): " + ChatColor.LIGHT_PURPLE + String.format("%.0f", core.getFep()) + ChatColor.GRAY + "/" + String.format("%.0f", core.getMaxFepCapacity()));
                lines.add(ChatColor.GOLD + "● Trạng thái Wave:");
                lines.add(ChatColor.WHITE + "  Đợt Raid: " + ChatColor.YELLOW + currentWave + ChatColor.GRAY + "/" + maxWaves);
                lines.add(ChatColor.WHITE + "  Quái còn lại: " + ChatColor.RED + remaining);
                lines.add(ChatColor.WHITE + "  Hết giờ: " + ChatColor.WHITE + timeStr);
                lines.add(ChatColor.GOLD + "● Chiến tích của bạn:");
                lines.add(ChatColor.WHITE + "  Tiêu diệt: " + ChatColor.GREEN + kills + " quái");
                lines.add(ChatColor.GOLD + "● Phần thưởng tạm tính:");
                lines.add(ChatColor.WHITE + "  Tiền vàng: " + ChatColor.YELLOW + String.format("%.1f", coins) + " Xu");
                lines.add(ChatColor.WHITE + "  Shards nhận: " + ChatColor.AQUA + shards + " Shards");
                lines.add(ChatColor.GRAY + "---------------------- ");

                int score = lines.size();
                for (String line : lines) {
                    obj.getScore(line).setScore(score);
                    score--;
                }

                player.setScoreboard(board);
            } catch (Throwable t) {
                plugin.getLogger().warning("Lỗi cập nhật Scoreboard cho " + player.getName() + ": " + t.getMessage());
            }
        }

        private void applyNoCollision(LivingEntity mob) {
            try {
                org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                org.bukkit.scoreboard.Team team = scoreboard.getTeam("td_raid_team");
                if (team == null) {
                    team = scoreboard.registerNewTeam("td_raid_team");
                    team.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
                }
                team.addEntry(mob.getUniqueId().toString());
            } catch (Throwable ignored) {}
        }

        private void removeNoCollision(Entity mob) {
            try {
                org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                org.bukkit.scoreboard.Team team = scoreboard.getTeam("td_raid_team");
                if (team != null) {
                    team.removeEntry(mob.getUniqueId().toString());
                }
            } catch (Throwable ignored) {}
        }

        private boolean isFlyingMob(LivingEntity mob) {
            return isFlyingMobType(mob.getType());
        }

        private void tickMobAI() {
            synchronized (aliveMobs) {
                for (Entity entity : aliveMobs) {
                    if (!(entity instanceof org.bukkit.entity.Mob mob) || !mob.isValid()) continue;

                    // Định kỳ đồng bộ Custom Name hiển thị máu ảo
                    updateMobCustomName(mob);

                    Location mobLoc = mob.getLocation();
                    Location coreLoc = core.getLocation();

                    // Ép quái di chuyển công phá Lõi
                    double distToCore = mobLoc.distance(coreLoc);
                    boolean isFlying = isFlyingMob(mob);

                    if (isFlying) {
                        if (distToCore > 1.0) {
                            org.bukkit.util.Vector flyDir = coreLoc.toVector().subtract(mobLoc.toVector()).normalize();
                            mob.setVelocity(flyDir.multiply(0.35D));
                        }
                    } else {
                        // Tối ưu hóa TPS: Chỉ tính toán lại đường đi (pathfinding) mỗi 3 giây cho mỗi thực thể, 
                        // phân bổ so le tải trọng CPU dựa trên Entity ID để tránh lag giật tick đột ngột
                        long secondCounter = System.currentTimeMillis() / 1000;
                        boolean shouldPathfind = (mob.getEntityId() + secondCounter) % 3 == 0;
                        
                        if (shouldPathfind) {
                            if (distToCore > 3.0) {
                                if (mob.getType() == org.bukkit.entity.EntityType.SLIME || mob.getType() == org.bukkit.entity.EntityType.MAGMA_CUBE) {
                                    org.bukkit.util.Vector slimeDir = coreLoc.toVector().subtract(mobLoc.toVector()).normalize();
                                    float yaw = (float) Math.toDegrees(Math.atan2(-slimeDir.getX(), slimeDir.getZ()));
                                    mob.setRotation(yaw, mob.getLocation().getPitch());
                                    if (mob.isOnGround()) {
                                        mob.setVelocity(slimeDir.multiply(0.2D).setY(0.3D));
                                    }
                                } else {
                                    mob.getPathfinder().moveTo(coreLoc, 1.25D);
                                }
                            } else {
                                mob.getPathfinder().moveTo(coreLoc, 1.25D);
                            }
                        }
                    }

                    // Tối ưu hóa AI phá khối vật cản và xử lý kẹt: Chạy so le mỗi 2 giây dựa trên Entity ID
                    boolean shouldCheckBlocksAndStuck = (mob.getEntityId() + System.currentTimeMillis() / 1000) % 2 == 0;
                    double coreAttackRange = plugin.getConfig().getDouble("raid-settings.core-attack-range", 4.0);
                    int mobAiAttackInterval = plugin.getConfig().getInt("raid-settings.mob-ai-attack-interval-ticks", 20);
                    int blockBreakThreshold = plugin.getConfig().getInt("raid-settings.block-break-ticks-threshold", 3);
                    double stuckDistThreshold = plugin.getConfig().getDouble("raid-settings.stuck-distance-threshold", 0.2);
                    int stuckSecondsThreshold = plugin.getConfig().getInt("raid-settings.stuck-seconds-threshold", 2);
                    
                    if (shouldCheckBlocksAndStuck) {
                        // AI phá vật cản: Tìm khối block cản đường phía trước hoặc dọc đường đi đến Lõi
                        org.bukkit.util.Vector direction = coreLoc.toVector().subtract(mobLoc.toVector()).normalize();
                        for (double d = 0.5; d <= 1.5; d += 0.5) {
                            Location checkLoc = mobLoc.clone().add(direction.clone().multiply(d));
                            org.bukkit.block.Block block = checkLoc.getBlock();
                            if (block.getType().isSolid() && block.getType() != Material.CONDUIT && block.getType() != Material.BEDROCK) {
                                int breakTicks = mob.getMetadata("td_break_ticks").stream().findFirst().map(m -> m.asInt()).orElse(0);
                                if (breakTicks >= blockBreakThreshold) {
                                    block.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 15, block.getBlockData());
                                    block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 1.0f);
                                    block.setType(Material.AIR);
                                    mob.setMetadata("td_break_ticks", new org.bukkit.metadata.FixedMetadataValue(plugin, 0));
                                } else {
                                    block.getWorld().spawnParticle(org.bukkit.Particle.CRIT, block.getLocation().add(0.5, 0.5, 0.5), 5);
                                    block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5f, 1.2f);
                                    mob.setMetadata("td_break_ticks", new org.bukkit.metadata.FixedMetadataValue(plugin, breakTicks + 1));
                                }
                                break;
                            }
                        }
                    }

                    // Khoảng cách tới Lõi
                    double distance = mobLoc.distance(coreLoc);
                    if (distance <= coreAttackRange) {
                        int damageTicks = mob.getMetadata("td_attack_ticks").stream().findFirst().map(m -> m.asInt()).orElse(0);
                        if (damageTicks >= mobAiAttackInterval) {
                            mob.setMetadata("td_attack_ticks", new org.bukkit.metadata.FixedMetadataValue(plugin, 0));
                            
                            double damage = plugin.getConfig().getDouble("raid-settings.mob-core-damage.DEFAULT", 50.0);
                            if (mob.getType() == EntityType.GIANT) damage = plugin.getConfig().getDouble("raid-settings.mob-core-damage.GIANT", 300.0);
                            else if (mob.getType() == EntityType.RAVAGER) damage = plugin.getConfig().getDouble("raid-settings.mob-core-damage.RAVAGER", 150.0);

                            double currentHealth = core.getTempHealth();
                            double newHealth = Math.max(0.0, currentHealth - damage);
                            core.setTempHealth(newHealth);

                            coreLoc.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, coreLoc.clone().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.1);
                            coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.0f);

                            broadcastToAlliance(core, ChatColor.RED + "[Cảnh báo] Lõi đang bị " + mob.getName() + ChatColor.RED + " cắn phá! HP Lõi còn lại: " + String.format("%.0f", newHealth) + "/" + core.getMaxShieldCapacity());

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

                    if (shouldCheckBlocksAndStuck) {
                        // --- KIỂM TRA BỊ KẸT HOẶC LỌT HỐ SÂU ---
                        Location lastLoc = mob.hasMetadata("td_last_loc") ? (Location) mob.getMetadata("td_last_loc").get(0).value() : null;
                        int stuckSeconds = mob.hasMetadata("td_stuck_seconds") ? mob.getMetadata("td_stuck_seconds").get(0).asInt() : 0;

                        if (lastLoc != null && lastLoc.getWorld() != null && lastLoc.getWorld().equals(mobLoc.getWorld()) && mobLoc.distance(lastLoc) < stuckDistThreshold) {
                            stuckSeconds += 2;
                        } else {
                            stuckSeconds = 0;
                        }
                        mob.setMetadata("td_last_loc", new org.bukkit.metadata.FixedMetadataValue(plugin, mobLoc.clone()));
                        mob.setMetadata("td_stuck_seconds", new org.bukkit.metadata.FixedMetadataValue(plugin, stuckSeconds));

                        if (stuckSeconds >= stuckSecondsThreshold * 2) {
                            if (isFlying) {
                                if (stuckSeconds >= 6) {
                                    org.bukkit.util.Vector stuckDirection = coreLoc.toVector().subtract(mobLoc.toVector()).normalize();
                                    Location newLoc = mobLoc.clone().add(stuckDirection.multiply(1.5));
                                    if (newLoc.getY() > newLoc.getWorld().getMinHeight() && newLoc.getY() < newLoc.getWorld().getMaxHeight()) {
                                        mob.teleport(newLoc);
                                        mob.setMetadata("td_stuck_seconds", new org.bukkit.metadata.FixedMetadataValue(plugin, 0));
                                    }
                                }
                            } else {
                                org.bukkit.block.Block feetBlock = mobLoc.getBlock();
                                org.bukkit.block.Block belowBlock = feetBlock.getRelative(org.bukkit.block.BlockFace.DOWN);

                                org.bukkit.util.Vector stuckDirection = coreLoc.toVector().subtract(mobLoc.toVector()).normalize();
                                Location checkLoc = mobLoc.clone().add(stuckDirection.multiply(1.2));
                                org.bukkit.block.Block frontFeetBlock = checkLoc.getBlock();
                                org.bukkit.block.Block frontHeadBlock = checkLoc.clone().add(0, 1, 0).getBlock();

                                boolean isHole = feetBlock.getType().isAir() || feetBlock.isLiquid() || belowBlock.getType().isAir() || belowBlock.isLiquid();
                                boolean isFrontBlocked = frontFeetBlock.getType().isSolid() || frontHeadBlock.getType().isSolid();

                                if (isHole || isFrontBlocked) {
                                    if (!feetBlock.getType().isSolid() && feetBlock.getType() != Material.CONDUIT) {
                                        feetBlock.setType(Material.DIRT);
                                        mob.teleport(mobLoc.clone().add(0, 1.0, 0));
                                        feetBlock.getWorld().playSound(feetBlock.getLocation(), Sound.BLOCK_GRAVEL_PLACE, 1.0f, 1.0f);
                                        stuckSeconds = 0;
                                        mob.setMetadata("td_stuck_seconds", new org.bukkit.metadata.FixedMetadataValue(plugin, stuckSeconds));
                                    }
                                }
                            }
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

            this.totalWaveMobs = pendingSpawnQueue.size();
            this.waveStartTime = System.currentTimeMillis();

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
            // Sức mạnh quái tăng theo call count và total raid count — đọc từ config
            double callScalingFactor = plugin.getConfig().getDouble("raid-settings.call-scaling-factor", 1.20);
            double totalRaidScalingFactor = plugin.getConfig().getDouble("raid-settings.total-raid-scaling-factor", 1.05);
            double callScaling = Math.pow(callScalingFactor, core.getRaidCallCount());
            double totalRaidScaling = Math.pow(totalRaidScalingFactor, core.getTotalRaidCount());
            double finalHpMultiplier = staticMult * dynamicMult * callScaling * totalRaidScaling;

            double radius = core.getRadius();
            Location cLoc = core.getLocation();

            // Định nghĩa sẵn 1 hoặc 2 góc spawn làm "cổng tấn công" cho toàn bộ wave này
            int spawnPointsCount = (Math.random() < 0.5) ? 1 : 2;
            double[] spawnAngles = new double[spawnPointsCount];
            for (int k = 0; k < spawnPointsCount; k++) {
                spawnAngles[k] = Math.random() * Math.PI * 2;
            }

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
                            sendWaveSummary();
                            broadcastToAlliance(core, ChatColor.GREEN + "Đã quét sạch đợt " + currentWave + ". Hồi sức chuẩn bị đợt kế tiếp sau " + (plugin.getConfig().getInt("raid-settings.wave-delay-ticks", 900) / 20) + " giây!");
                            core.getLocation().getWorld().playSound(core.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (activeCampaigns.containsKey(core.getCoreId())) {
                                        launchNextWave();
                                    }
                                }
                            }.runTaskLater(plugin, (long) plugin.getConfig().getInt("raid-settings.wave-delay-ticks", 900));
                        }
                        return;
                    }

                    // Đọc batch size từ config
                    int batchMin = plugin.getConfig().getInt("raid-settings.spawn-batch-min", 3);
                    int batchMax = plugin.getConfig().getInt("raid-settings.spawn-batch-max", 5);
                    int spawnCountThisTick = Math.min(pendingSpawnQueue.size(), batchMin + (int)(Math.random() * (batchMax - batchMin + 1)));
                    for (int i = 0; i < spawnCountThisTick; i++) {
                        if (pendingSpawnQueue.isEmpty()) break;
                        EntityType type = pendingSpawnQueue.remove(0);

                        boolean isFlying = isFlyingMobType(type);
                        
                        // Lấy ngẫu nhiên một trong số các góc tấn công được xác định trước cho wave này
                        double baseAngle = spawnAngles[(int) (Math.random() * spawnAngles.length)];
                        // Thêm một dao động góc nhỏ (±0.25 rad ~ ±15 độ) để phân bổ quái thành một toán quân
                        double angle = baseAngle + (Math.random() * 0.5 - 0.25);
                        
                        double spawnDistance = radius + 1.0; // Cách lãnh thổ đúng 1 block

                        Location spawnLoc = findSmartSpawnLocation(cLoc, spawnDistance, angle, isFlying);
                        boolean success = spawnSingleMob(type, spawnLoc, finalHpMultiplier);
                        if (!success) {
                            pendingSpawnQueue.add(0, type);
                        }
                    }
                }
            };
            spawnTask.runTaskTimer(plugin, 0L, (long) plugin.getConfig().getInt("raid-settings.spawn-interval-ticks", 40));
        }

        /**
         * Nhận diện thực thể bay trong Raid để tối ưu hóa độ cao spawn
         */
        private boolean isFlyingMobType(EntityType type) {
            String name = type.name();
            return name.equals("VEX") || 
                   name.equals("BLAZE") || 
                   name.equals("WITHER") ||
                   name.equals("ENDER_DRAGON") ||
                   name.equals("ALLAY") ||
                   name.equals("BAT") ||
                   name.equals("BEE") ||
                   name.equals("PARROT") ||
                   name.equals("PHANTOM") ||
                   name.equals("GHAST");
        }

        /**
         * Tìm vị trí spawn thông minh chống kẹt, cách lãnh thổ đúng khoảng cách chỉ định (1 block).
         * Đối với quái thường, quét cao độ để tìm điểm đáp đất cứng rỗng 2 block.
         * Đối với quái bay, tìm vị trí lơ lửng trên không.
         * Nếu góc chỉ định bị bít kín địa hình, tự động thử xoay 8 góc xung quanh.
         */
        private Location findSmartSpawnLocation(Location coreLoc, double targetDistance, double initialAngle, boolean isFlying) {
            org.bukkit.World world = coreLoc.getWorld();
            if (world == null) return coreLoc.clone().add(0, 5, 0);

            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = initialAngle + (attempt * (Math.PI / 4)); // Thử xoay 45 độ mỗi lần
                double x = coreLoc.getX() + targetDistance * Math.cos(angle);
                double z = coreLoc.getZ() + targetDistance * Math.sin(angle);

                int chunkX = (int) x >> 4;
                int chunkZ = (int) z >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    world.getChunkAt(chunkX, chunkZ).load();
                }

                if (isFlying) {
                    int highestY = world.getHighestBlockYAt((int) x, (int) z);
                    double y = highestY + 10.0;
                    if (y > world.getMaxHeight() - 5) {
                        y = world.getMaxHeight() - 5;
                    }
                    org.bukkit.block.Block feet = world.getBlockAt((int) x, (int) y, (int) z);
                    org.bukkit.block.Block head = world.getBlockAt((int) x, (int) y + 1, (int) z);
                    if (feet.getType().isAir() && head.getType().isAir()) {
                        return new Location(world, x, y, z);
                    }
                } else {
                    int startY = coreLoc.getBlockY();
                    for (int dy = 0; dy <= 16; dy++) {
                        int checkY = startY + dy;
                        if (checkY < world.getMaxHeight() - 1) {
                            org.bukkit.block.Block feet = world.getBlockAt((int) x, checkY, (int) z);
                            org.bukkit.block.Block head = world.getBlockAt((int) x, checkY + 1, (int) z);
                            org.bukkit.block.Block ground = world.getBlockAt((int) x, checkY - 1, (int) z);
                            if (feet.getType().isAir() && head.getType().isAir() && ground.getType().isSolid()) {
                                return new Location(world, x, checkY, z);
                            }
                        }
                        if (dy > 0) {
                            int checkYDown = startY - dy;
                            if (checkYDown > world.getMinHeight()) {
                                org.bukkit.block.Block feet = world.getBlockAt((int) x, checkYDown, (int) z);
                                org.bukkit.block.Block head = world.getBlockAt((int) x, checkYDown + 1, (int) z);
                                org.bukkit.block.Block ground = world.getBlockAt((int) x, checkYDown - 1, (int) z);
                                if (feet.getType().isAir() && head.getType().isAir() && ground.getType().isSolid()) {
                                    return new Location(world, x, checkYDown, z);
                                }
                            }
                        }
                    }
                }
            }

            double fallbackX = coreLoc.getX() + targetDistance * Math.cos(initialAngle);
            double fallbackZ = coreLoc.getZ() + targetDistance * Math.sin(initialAngle);
            if (isFlying) {
                return new Location(world, fallbackX, coreLoc.getY() + 10.0, fallbackZ);
            } else {
                try {
                    int highestY = world.getHighestBlockYAt((int) fallbackX, (int) fallbackZ);
                    return new Location(world, fallbackX, highestY + 1.0, fallbackZ);
                } catch (Exception e) {
                    return new Location(world, fallbackX, coreLoc.getY(), fallbackZ);
                }
            }
        }

        /**
         * Triệu hồi một thực thể quái cụ thể và đóng dấu PDC an toàn tuyệt đối.
         */
        private boolean spawnSingleMob(EntityType type, Location loc, double hpMultiplier) {
            LivingEntity mob = null;
            try {
                // Thử spawn với SpawnReason.RAID để vượt qua bộ lọc chặn tự nhiên của các plugin bảo vệ khác
                mob = (LivingEntity) loc.getWorld().spawn(loc, type.getEntityClass(), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.RAID);
            } catch (Throwable t) {
                // Fallback nếu không tương thích phiên bản
                try {
                    mob = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
                } catch (Throwable ignored) {}
            }

            if (mob == null || !mob.isValid()) {
                getLogger().warning("[Raid] Triệu hồi quái vật loại " + type.name() + " thất bại hoặc bị hủy bởi hệ thống/plugin khác tại " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                return false;
            }

            // Đóng cờ Metadata để hệ thống dễ quản lý và chống hack
            mob.getPersistentDataContainer().set(PDCKeys.RAID_MOB_TAG, PersistentDataType.BYTE, (byte) 1);
            mob.getPersistentDataContainer().set(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING, core.getCoreId().toString());
            mob.setMetadata("td_raid_mob", new FixedMetadataValue(plugin, true));   // <-- để nhận dạng khi bảo vệ NPC
            mob.setMetadata("td_owner_core", new FixedMetadataValue(plugin, core.getCoreId().toString()));
            mob.setMetadata("td_raid_call_count", new FixedMetadataValue(plugin, core.getRaidCallCount()));

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

            // Ghi nhận Metadata máu ảo cho quái vật để phục vụ cho CombatDamageTracker
            try {
                double actualHp = mob.getMaxHealth();
                org.bukkit.attribute.AttributeInstance maxHealthAttr = null;
                try {
                    java.lang.reflect.Field field = org.bukkit.attribute.Attribute.class.getField("GENERIC_MAX_HEALTH");
                    org.bukkit.attribute.Attribute attrEnum = (org.bukkit.attribute.Attribute) field.get(null);
                    maxHealthAttr = mob.getAttribute(attrEnum);
                } catch (Throwable ignored) {}
                if (maxHealthAttr == null) {
                    try {
                        maxHealthAttr = mob.getAttribute(org.bukkit.Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft("generic.max_health")));
                    } catch (Throwable ignored) {}
                }
                if (maxHealthAttr != null) {
                    actualHp = maxHealthAttr.getValue();
                }
                if (actualHp <= 0) actualHp = mob.getMaxHealth();
                mob.setMetadata("td_intended_max_hp", new FixedMetadataValue(plugin, scaledHp));
                mob.setMetadata("td_actual_max_hp", new FixedMetadataValue(plugin, actualHp));
            } catch (Throwable ignored) {}

            // Tắt va chạm (chống kẹt lẫn nhau) không ảnh hưởng CPU server
            applyNoCollision(mob);

            // Gắn tên hiển thị chuyên nghiệp
            updateMobCustomName(mob);

            // Đăng ký bể theo dõi đóng góp sát thương phòng AFK
            plugin.getCombatDamageTracker().registerRaidMob(mob, scaledHp);
            aliveMobs.add(mob);
            return true;
        }

        private void sendWaveSummary() {
            java.util.Set<UUID> participants = new java.util.HashSet<>();
            participants.addAll(waveDirectShards.keySet());
            participants.addAll(waveHarvestedShards.keySet());
            participants.addAll(waveCoinsEarned.keySet());
            participants.addAll(waveMobsContributed.keySet());
            participants.addAll(waveMobsMissed.keySet());

            // Thêm cả các thành viên liên minh online để họ nắm thông tin
            List<UUID> members = plugin.getAllianceManager().getAllianceMembers(core.getAllyId());
            for (UUID mUuid : members) {
                Player p = Bukkit.getPlayer(mUuid);
                if (p != null && p.isOnline()) {
                    participants.add(mUuid);
                }
            }

            for (UUID pUuid : participants) {
                Player player = Bukkit.getPlayer(pUuid);
                if (player == null || !player.isOnline()) continue;

                int directShards = waveDirectShards.getOrDefault(pUuid, 0);
                int harvestedShards = waveHarvestedShards.getOrDefault(pUuid, 0);
                double coins = waveCoinsEarned.getOrDefault(pUuid, 0.0);
                int contributed = waveMobsContributed.getOrDefault(pUuid, 0);
                int missed = waveMobsMissed.getOrDefault(pUuid, 0);

                // Nếu người chơi này không tham gia đánh phát nào và không nhận được gì thì bỏ qua, tránh làm phiền
                if (directShards == 0 && harvestedShards == 0 && coins == 0.0 && contributed == 0 && missed == 0) {
                    continue;
                }

                player.sendMessage(ChatColor.DARK_GRAY + "========================================");
                player.sendMessage(ChatColor.GOLD + "   ★ TỔNG HỢP CHIẾN TÍCH ĐỢT " + currentWave + " ★");
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "  ● Trạng thái phòng thủ:");
                player.sendMessage(ChatColor.GRAY + "    ↳ Số quái tiêu diệt đạt chuẩn đóng góp: " + ChatColor.GREEN + contributed);
                if (missed > 0) {
                    player.sendMessage(ChatColor.GRAY + "    ↳ Số quái không đạt ngưỡng đóng góp (15%): " + ChatColor.RED + missed);
                }
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "  ● Phần thưởng đã nhận:");
                if (directShards > 0) {
                    player.sendMessage(ChatColor.GRAY + "    ↳ Nhận trực tiếp: " + ChatColor.AQUA + "+" + directShards + " Shards");
                }
                if (harvestedShards > 0) {
                    player.sendMessage(ChatColor.GRAY + "    ↳ Nạp tự động vào Lõi: " + ChatColor.LIGHT_PURPLE + "+" + harvestedShards + " Shards");
                }
                if (coins > 0) {
                    player.sendMessage(ChatColor.GRAY + "    ↳ Tiền vàng (Kinh tế Vault): " + ChatColor.GOLD + "+" + String.format("%.1f", coins) + " Xu");
                }
                player.sendMessage(ChatColor.DARK_GRAY + "========================================");
                
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
            }

            // Reset chuẩn bị cho Wave tiếp theo
            waveDirectShards.clear();
            waveHarvestedShards.clear();
            waveCoinsEarned.clear();
            waveMobsContributed.clear();
            waveMobsMissed.clear();
        }

        /**
         * Ghi nhận khi có quái bị tiêu diệt và tự động kích hoạt Wave tiếp sau 45s dọn dẹp.
         */
        public void registerMobKill(Entity entity) {
            aliveMobs.remove(entity);
            removeNoCollision(entity);

            // SỬA LỖI WAVE 2: Dùng spawnCompleted thay vì spawnTask == null để tránh race condition
            // (spawnTask.cancel() không đồng bộ, spawnTask chưa kịp null khi con quái cuối chết)
            if (aliveMobs.isEmpty() && pendingSpawnQueue.isEmpty() && spawnCompleted) {
                // Gửi bảng tóm tắt
                sendWaveSummary();

                // Wave thành công! Chờ 45 giây đếm ngược hồi sức
                broadcastToAlliance(core, ChatColor.GREEN + "Đã quét sạch đợt " + currentWave + ". Hồi sức chuẩn bị đợt kế tiếp sau " + (plugin.getConfig().getInt("raid-settings.wave-delay-ticks", 900) / 20) + " giây!");
                core.getLocation().getWorld().playSound(core.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (activeCampaigns.containsKey(core.getCoreId())) {
                            launchNextWave();
                        }
                    }
                }.runTaskLater(plugin, (long) plugin.getConfig().getInt("raid-settings.wave-delay-ticks", 900));
            }
        }

        /**
         * Phân tích ma trận bàn quái theo đúng Wave Spawning Profile trong tài liệu GDD.
         */
        private void buildWaveSpawnQueue() {
            pendingSpawnQueue.clear();
            int lvl = core.getLevel();

            // Tính số lượng quái tịnh tiến theo công thức: base * Cấp Lõi ^ power (cấu hình từ config)
            double waveBase = plugin.getConfig().getDouble("raid-settings.wave-mob-formula-base", 25);
            double wavePower = plugin.getConfig().getDouble("raid-settings.wave-mob-formula-power", 1.75);
            int expectedTotal = (int) (waveBase * Math.pow(lvl, wavePower));

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
                addMobsToQueue(EntityType.PHANTOM, currentWave);
            } else {
                // Cấp 3, 4, 5 bổ sung thêm Boss lớn và đa dạng các phân lớp
                addMobsToQueue(EntityType.VINDICATOR, 12 + currentWave * 4);
                addMobsToQueue(EntityType.SKELETON, 6 + currentWave * 2);
                addMobsToQueue(EntityType.GHAST, 2 + currentWave);
                addMobsToQueue(EntityType.RAVAGER, 1 + currentWave);
                addMobsToQueue(EntityType.EVOKER, currentWave);
                addMobsToQueue(EntityType.PHANTOM, currentWave);
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

        private void updateBossBar() {
            if (bossBar == null) return;

            List<UUID> members = plugin.getAllianceManager() != null ? 
                    plugin.getAllianceManager().getAllianceMembers(core.getAllyId()) : new ArrayList<>();
            Set<Player> currentWatchers = new java.util.HashSet<>();

            Player owner = Bukkit.getPlayer(core.getOwnerUUID());
            if (owner != null && owner.isOnline()) {
                currentWatchers.add(owner);
            }

            for (UUID memberUuid : members) {
                if (memberUuid.equals(core.getOwnerUUID())) continue;
                Player player = Bukkit.getPlayer(memberUuid);
                if (player != null && player.isOnline()) {
                    TerritoryCore standingCore = plugin.getCoreManager().getCoreByLocationRange(player.getLocation());
                    if (standingCore != null && standingCore.getCoreId().equals(core.getCoreId())) {
                        currentWatchers.add(player);
                    }
                }
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (currentWatchers.contains(player)) {
                    if (!bossBar.getPlayers().contains(player)) {
                        bossBar.addPlayer(player);
                    }
                    updateRaidScoreboard(player);
                } else {
                    if (bossBar.getPlayers().contains(player)) {
                        bossBar.removePlayer(player);
                        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                    }
                }
            }

            int remaining = aliveMobs.size() + pendingSpawnQueue.size();
            double progress = 1.0;
            if (totalWaveMobs > 0) {
                progress = Math.max(0.0, Math.min(1.0, (double) remaining / totalWaveMobs));
            }
            bossBar.setProgress(progress);

            long elapsed = System.currentTimeMillis() - waveStartTime;
            long remainingTimeSeconds = Math.max(0, (waveDurationLimitMillis - elapsed) / 1000);

            if (waveStartTime > 0 && remainingTimeSeconds <= 0 && remaining > 0) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        endRaid(core, false);
                    }
                }.runTask(plugin);
                return;
            }

            String timeStr = String.format("%02d:%02d", remainingTimeSeconds / 60, remainingTimeSeconds % 60);

            String title = ChatColor.RED + "Raid Lãnh Thổ (Cấp " + core.getLevel() + ") " +
                           ChatColor.GOLD + "Đợt: " + currentWave + "/" + maxWaves +
                           ChatColor.YELLOW + " | Quái còn lại: " + remaining +
                           ChatColor.GRAY + " | Hết giờ: " + timeStr;
            bossBar.setTitle(title);
        }
    }

    private void broadcastToAlliance(TerritoryCore core, String message) {
        Player owner = Bukkit.getPlayer(core.getOwnerUUID());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(message);
        }

        if (plugin.getAllianceManager() != null) {
            List<UUID> members = plugin.getAllianceManager().getAllianceMembers(core.getAllyId());
            for (UUID memberUuid : members) {
                if (memberUuid.equals(core.getOwnerUUID())) continue;
                Player player = Bukkit.getPlayer(memberUuid);
                if (player != null && player.isOnline()) {
                    player.sendMessage(message);
                }
            }
        }
    }

    public boolean isRaidActive(TerritoryCore core) {
        return core != null && activeCampaigns.containsKey(core.getCoreId());
    }

    public ActiveRaidCampaign getActiveRaid(TerritoryCore core) {
        return core == null ? null : activeCampaigns.get(core.getCoreId());
    }

    public Map<UUID, ActiveRaidCampaign> activeCampaigns() {
        return activeCampaigns;
    }
}