package com.truongcm.territorydefense.feature.combat.raid.rewards;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.raid.model.ActiveRaidCampaign;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public class RaidRewardCalculator {

    public static void sendWaveSummary(TerritoryDefense plugin, ActiveRaidCampaign campaign, TerritoryCore core) {
        Set<UUID> participants = new HashSet<>();
        participants.addAll(campaign.waveDirectShards.keySet());
        participants.addAll(campaign.waveHarvestedShards.keySet());
        participants.addAll(campaign.waveCoinsEarned.keySet());
        participants.addAll(campaign.waveMobsContributed.keySet());
        participants.addAll(campaign.waveMobsMissed.keySet());

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

            int directShards = campaign.waveDirectShards.getOrDefault(pUuid, 0);
            int harvestedShards = campaign.waveHarvestedShards.getOrDefault(pUuid, 0);
            double coins = campaign.waveCoinsEarned.getOrDefault(pUuid, 0.0);
            int contributed = campaign.waveMobsContributed.getOrDefault(pUuid, 0);
            int missed = campaign.waveMobsMissed.getOrDefault(pUuid, 0);

            // Nếu người chơi này không tham gia đánh phát nào và không nhận được gì thì bỏ qua, tránh làm phiền
            if (directShards == 0 && harvestedShards == 0 && coins == 0.0 && contributed == 0 && missed == 0) {
                continue;
            }

            player.sendMessage(ChatColor.DARK_GRAY + "========================================");
            player.sendMessage(ChatColor.GOLD + "   ★ TỔNG HỢP CHIẾN TÍCH ĐỢT " + campaign.getCurrentWave() + " ★");
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
        campaign.waveDirectShards.clear();
        campaign.waveHarvestedShards.clear();
        campaign.waveCoinsEarned.clear();
        campaign.waveMobsContributed.clear();
        campaign.waveMobsMissed.clear();
    }
}
