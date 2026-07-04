package com.truongcm.territorydefense.feature.combat.raid;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.raid.model.ActiveRaidCampaign;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.bukkit.Bukkit.getLogger;

/**
 * ĐIỀU PHỐI ĐỢT RAID PVE (RAID SESSION CONTROLLER)
 * Trách nhiệm: Bộ điều phối trung tâm (Engine) quản lý chu kỳ Raid, lập lịch giờ thực tế,
 * quản lý danh sách các campaign đang chạy, lắng nghe các sự kiện Bukkit liên quan đến Raid.
 */
public class RaidSession implements Listener {

    private final TerritoryDefense plugin;

    // Lưu trữ các đợt Raid đang diễn ra thời gian thực
    private final Map<UUID, ActiveRaidCampaign> activeCampaigns = new ConcurrentHashMap<>();

    // Ghi nhớ giờ đã trigger raid cho từng Core trong ngày
    private final Map<UUID, Integer> lastTriggeredHour = new ConcurrentHashMap<>();

    public RaidSession(TerritoryDefense plugin) {
        this.plugin = plugin;

        // RAID AUTO-SPAWN SCHEDULER (Chạy mỗi 1 phút)
        new BukkitRunnable() {
            @Override
            public void run() {
                Calendar now = Calendar.getInstance(TimeZone.getDefault());
                int currentHour = now.get(Calendar.HOUR_OF_DAY);
                int currentMinute = now.get(Calendar.MINUTE);

                if (currentMinute > 1) return;

                for (TerritoryCore core : plugin.getCoreManager().getAllActiveCores()) {
                    UUID coreId = core.getCoreId();

                    Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                    if (owner == null || !owner.isOnline()) {
                        continue;
                    }

                    int lastHour = lastTriggeredHour.getOrDefault(coreId, -1);
                    if (lastHour == currentHour) {
                        continue;
                    }

                    if (activeCampaigns.containsKey(coreId)) continue;
                    if (plugin.getCoreManager().isUnderPeaceProtection(coreId)) continue;

                    lastTriggeredHour.put(coreId, currentHour);
                    plugin.getCoreManager().setLastRaidTime(coreId, System.currentTimeMillis());
                    plugin.getCoreManager().saveAllCores();
                    startRaid(core, false, 0);
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L);
    }

    /**
     * Khởi động một chiến dịch Raid PvE cho Lõi chỉ định.
     */
    public void startRaid(TerritoryCore core, boolean isPurchased, int purchasedIndex) {
        if (core == null) return;
        UUID coreId = core.getCoreId();

        if (activeCampaigns.containsKey(coreId)) {
            return;
        }

        ActiveRaidCampaign campaign = new ActiveRaidCampaign(plugin, core, isPurchased, purchasedIndex);
        activeCampaigns.put(coreId, campaign);

        core.setRaidActive(true);
        core.setTempHealth(core.getMaxShieldCapacity());

        // Kích hoạt quét Snapshot Pre-Raid
        com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getActiveBuilders().get(coreId);
        if (builder != null) {
            builder.getLastPreRaidSnapshot().clear();
            Location coreLoc = core.getLocation();
            int radius = plugin.getCoreManager().getCoreRadius(core);
            int rawBelow = plugin.getConfig().getInt("builder-settings.scan-height-below", 5);
            int scanHeightBelow = rawBelow < 0 ? (coreLoc.getBlockY() - coreLoc.getWorld().getMinHeight()) : Math.abs(rawBelow);
            int scanHeightAbove = Math.abs(plugin.getConfig().getInt("builder-settings.scan-height-above", 15));
            
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

        broadcastToAlliance(core, ChatColor.RED + "=============================================");
        broadcastToAlliance(core, ChatColor.RED + " CẢNH BÁO XÂM NHẬP: LŨ QUÁI CÔNG THÀNH ĐANG TIẾP CẬN!");
        broadcastToAlliance(core, ChatColor.YELLOW + " Cấp độ Lõi: " + core.getLevel() + " | Tổng số đợt công: " + (core.getLevel() == 5 ? 5 : 3));
        broadcastToAlliance(core, ChatColor.RED + "=============================================");

        core.getLocation().getWorld().playSound(core.getLocation(), Sound.EVENT_RAID_HORN, 2.0f, 1.0f);

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
        ActiveRaidCampaign campaign = activeCampaigns.get(core.getCoreId());
        if (campaign == null) return;

        core.revertHealth();
        campaign.cleanup();
        
        com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getActiveBuilders().get(core.getCoreId());
        if (builder != null && !campaign.getPreRaidSnapshot().isEmpty()) {
            Player owner = Bukkit.getPlayer(core.getOwnerUUID());
            builder.startRebuild(core, campaign.getPreRaidSnapshot(), owner);
        }

        activeCampaigns.remove(core.getCoreId());

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
    @EventHandler(priority = EventPriority.HIGH)
    public void onRaidMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (PDCKeys.RAID_MOB_TAG == null) {
            getLogger().warning("PDCKeys.RAID_MOB_TAG chưa được khởi tạo!");
            return;
        }

        if (!entity.getPersistentDataContainer().has(PDCKeys.RAID_MOB_TAG, PersistentDataType.BYTE)) {
            return;
        }

        if (PDCKeys.OWNER_CORE_ID == null) return;

        String coreIdStr = entity.getPersistentDataContainer().get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING);
        if (coreIdStr == null) return;

        UUID coreId;
        try {
            coreId = UUID.fromString(coreIdStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        // Tính Shard reward theo loại quái (gộp từ CoreGameplayListener)
        int shardAmount = 1;
        if (entity.hasMetadata("td_elite_boss")) {
            shardAmount = 25;
        } else if (entity.getType() == org.bukkit.entity.EntityType.RAVAGER
                || entity.getType() == org.bukkit.entity.EntityType.EVOKER) {
            shardAmount = 3;
        }

        // Tìm core và cộng Shard
        TerritoryCore core = plugin.getCoreManager().getCoreByLocationRange(entity.getLocation());
        if (core != null && core.getCoreId().equals(coreId)) {
            plugin.getCoreManager().addShards(coreId, shardAmount);
            core.markDirty();
            entity.getLocation().getWorld().spawnParticle(
                org.bukkit.Particle.PORTAL, entity.getLocation(), 10, 0.2, 0.2, 0.2, 0.1);
            entity.getLocation().getWorld().playSound(
                entity.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.8f);
        }

        // Ghi nhận mob chết vào campaign và cộng Shard harvest
        ActiveRaidCampaign campaign = activeCampaigns.get(coreId);
        if (campaign != null) {
            campaign.registerMobKill(entity);
            if (core != null) {
                campaign.addWaveHarvestedShards(core.getOwnerUUID(), shardAmount);
            }
        }
    }


    public void broadcastToAlliance(TerritoryCore core, String message) {
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

    public ActiveRaidCampaign getActiveRaid(UUID coreId) {
        return coreId == null ? null : activeCampaigns.get(coreId);
    }

    public Map<UUID, ActiveRaidCampaign> activeCampaigns() {
        return activeCampaigns;
    }
}