package com.truongcm.territorydefense.feature.combat.raid.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.raid.model.ActiveRaidCampaign;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * QUẢN LÝ TƯƠNG TÁC TRỰC QUAN (RAID DISPLAY UI MANAGER)
 * Chịu trách nhiệm hiển thị BossBar, Scoreboard, Chat thông báo và âm thanh.
 */
public class RaidDisplayManager {
    private final TerritoryDefense plugin;
    private final ActiveRaidCampaign campaign;
    private final TerritoryCore core;
    private final BossBar bossBar;

    public RaidDisplayManager(TerritoryDefense plugin, ActiveRaidCampaign campaign, TerritoryCore core) {
        this.plugin = plugin;
        this.campaign = campaign;
        this.core = core;

        this.bossBar = Bukkit.createBossBar(
            ChatColor.RED + "Đợt Raid Lãnh Thổ | Đang chuẩn bị...",
            BarColor.RED,
            BarStyle.SOLID
        );
    }

    public void cleanup() {
        if (bossBar != null) {
            try {
                bossBar.removeAll();
            } catch (Exception ignored) {}
        }
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public void updateMobCustomName(LivingEntity mob) {
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
            if (mob.hasMetadata("td_elite_boss")) {
                String prefix = plugin.getConfig().getString("raid-settings.elite-boss.display-name-prefix", "&6★ ELITE BOSS ★ ");
                prefix = ChatColor.translateAlternateColorCodes('&', prefix);
                mob.setCustomName(prefix + "[HP: " + String.format("%.0f", Math.max(0.0, currentHp)) + "/" + String.format("%.0f", maxHp) + "]");
            } else {
                mob.setCustomName(ChatColor.RED + "Quái Công Thành [HP: " + String.format("%.0f", Math.max(0.0, currentHp)) + "/" + String.format("%.0f", maxHp) + "]");
            }
            mob.setCustomNameVisible(true);
        } catch (Throwable ignored) {}
    }

    public void updateRaidScoreboard(Player player) {
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
            int kills = campaign.waveMobsContributed.getOrDefault(pUuid, 0);
            double coins = campaign.waveCoinsEarned.getOrDefault(pUuid, 0.0);
            int shards = campaign.waveDirectShards.getOrDefault(pUuid, 0) + campaign.waveHarvestedShards.getOrDefault(pUuid, 0);

            int remaining = campaign.getAliveMobs().size() + campaign.getPendingSpawnQueue().size();
            long elapsed = System.currentTimeMillis() - campaign.getWaveStartTime();
            long remainingTimeSeconds = Math.max(0, (campaign.getWaveDurationLimitMillis() - elapsed) / 1000);
            String timeStr = String.format("%02d:%02d", remainingTimeSeconds / 60, remainingTimeSeconds % 60);

            List<String> lines = new ArrayList<>();
            lines.add(ChatColor.GRAY + "----------------------");
            lines.add(ChatColor.GOLD + "● Trạng thái Lõi:");
            lines.add(ChatColor.WHITE + "  Máu Lõi: " + ChatColor.GREEN + String.format("%.0f", core.getTempHealth()) + ChatColor.GRAY + "/" + String.format("%.0f", core.getMaxShieldCapacity()));
            lines.add(ChatColor.WHITE + "  Giáp ảo: " + ChatColor.AQUA + String.format("%.0f", core.getShield()));
            lines.add(ChatColor.WHITE + "  PEP (FEP): " + ChatColor.LIGHT_PURPLE + String.format("%.0f", core.getFep()) + ChatColor.GRAY + "/" + String.format("%.0f", core.getMaxFepCapacity()));
            lines.add(ChatColor.GOLD + "● Trạng thái Wave:");
            lines.add(ChatColor.WHITE + "  Đợt Raid: " + ChatColor.YELLOW + campaign.getCurrentWave() + ChatColor.GRAY + "/" + campaign.getMaxWaves());
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

    public void updateBossBar() {
        if (bossBar == null) return;

        List<UUID> members = plugin.getAllianceManager() != null ?
                plugin.getAllianceManager().getAllianceMembers(core.getAllyId()) : new ArrayList<>();
        Set<Player> currentWatchers = new HashSet<>();

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

        int remaining = campaign.getAliveMobs().size() + campaign.getPendingSpawnQueue().size();
        double progress = 1.0;
        if (campaign.getTotalWaveMobs() > 0) {
            progress = Math.max(0.0, Math.min(1.0, (double) remaining / campaign.getTotalWaveMobs()));
        }
        bossBar.setProgress(progress);

        long elapsed = System.currentTimeMillis() - campaign.getWaveStartTime();
        long remainingTimeSeconds = Math.max(0, (campaign.getWaveDurationLimitMillis() - elapsed) / 1000);

        if (campaign.getWaveStartTime() > 0 && remainingTimeSeconds <= 0 && remaining > 0) {
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (plugin.getRaidSession() != null) {
                        plugin.getRaidSession().endRaid(core, false);
                    }
                }
            }.runTask(plugin);
            return;
        }

        String timeStr = String.format("%02d:%02d", remainingTimeSeconds / 60, remainingTimeSeconds % 60);

        String title = ChatColor.RED + "Raid Lãnh Thổ (Cấp " + core.getLevel() + ") " +
                       ChatColor.GOLD + "Đợt: " + campaign.getCurrentWave() + "/" + campaign.getMaxWaves() +
                       ChatColor.YELLOW + " | Quái còn lại: " + remaining +
                       ChatColor.GRAY + " | Hết giờ: " + timeStr;
        bossBar.setTitle(title);
    }

    public void broadcastToAlliance(String message) {
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
}
