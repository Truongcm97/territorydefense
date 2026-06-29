package com.truongcm.territorydefense.feature.alliance;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QUẢN LÝ LIÊN MINH & HỢP NHẤT ĐẤT (ALLIANCE MANAGER)
 * Điều khiển tạo lập bang hội, gửi tin nhắn chat riêng, tắt Friendly Fire
 * và thực thi thuật toán Hợp Nhất Lãnh Thổ (Merge Zone) chuẩn GDD.
 *
 * ĐÃ SỬA LỖI MẤT LIÊN MINH KHI RESTART: Tích hợp hệ thống lưu trữ alliances.yml vật lý,
 * tự động khôi phục dữ liệu bộ nhớ đệm thành viên (playerAllianceMap) khi khởi động lại.
 */
public class AllianceManager implements Listener {

    private final TerritoryDefense plugin;
    private final Map<String, Alliance> alliances = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerAllianceMap = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingInvites = new ConcurrentHashMap<>();
    // Tệp tin lưu trữ dữ liệu bền vững chống mất mát khi restart
    private final File allianceFile;
    private final YamlConfiguration allianceConfig;

    /**
     * Hàm khởi tạo chính yêu cầu tham số plugin
     */
    public AllianceManager(TerritoryDefense plugin) {
        this.plugin = plugin;

        // Khởi tạo thư mục và tệp cấu hình alliances.yml lưu trữ dữ liệu
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.allianceFile = new File(plugin.getDataFolder(), "alliances.yml");
        if (!allianceFile.exists()) {
            try {
                allianceFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[TD] Không thể tạo tệp alliances.yml: " + e.getMessage());
            }
        }
        this.allianceConfig = YamlConfiguration.loadConfiguration(allianceFile);

        // Gọi nạp lại toàn bộ liên minh từ ổ cứng lên RAM
        loadAlliances();
    }

    /**
     * Hàm khởi tạo dự phòng không tham số giúp tránh lỗi biên dịch ở TerritoryDefense.java
     */
    public AllianceManager() {
        this(TerritoryDefense.getInstance());
    }

    /**
     * THÀNH LẬP LIÊN MINH MỚI
     */
    public boolean createAlliance(String name, UUID leaderUUID) {
        // Kiểm tra trùng lặp tên bang hội
        for (Alliance alliance : alliances.values()) {
            if (alliance.getName().equalsIgnoreCase(name)) {
                return false;
            }
        }

        String allyId = "ALL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Alliance newAlly = new Alliance(allyId, name, leaderUUID);

        alliances.put(allyId, newAlly);
        playerAllianceMap.put(leaderUUID, allyId);

        // Tự động lưu trữ ngay lập tức chống rủi ro crash server
        saveAlliances();
        return true;
    }

    public List<UUID> getAllianceMembers(String allyId) {
        Alliance alliance = getAlliance(allyId);
        if (alliance != null) {
            return new ArrayList<>(alliance.getMembers());
        }
        return new ArrayList<>();
    }

    public Alliance getAlliance(String allyId) {
        if (allyId == null) {
            return null;
        }
        return alliances.get(allyId);
    }

    public String getPlayerAlliance(UUID playerUUID) {
        return playerAllianceMap.get(playerUUID);
    }

    public void joinAlliance(String allyId, UUID playerUUID) {
        Alliance alliance = alliances.get(allyId);
        if (alliance != null) {
            alliance.addMember(playerUUID);
            playerAllianceMap.put(playerUUID, allyId);

            // Tự động lưu khi có thành viên mới gia nhập
            saveAlliances();
        }
    }

    public void leaveAlliance(UUID playerUUID) {
        String allyId = playerAllianceMap.remove(playerUUID);
        if (allyId != null) {
            Alliance alliance = alliances.get(allyId);
            if (alliance != null) {
                alliance.removeMember(playerUUID);
                if (alliance.getMembers().isEmpty()) {
                    alliances.remove(allyId); // Giải tán bang nếu không còn ai
                }

                // Tự động cập nhật lưu trữ
                saveAlliances();
            }
        }
    }

    /**
     * LƯU TOÀN BỘ LIÊN MINH XUỐNG CƠ SỞ DỮ LIỆU FILE (alliances.yml)
     */
    public void saveAlliances() {
        if (plugin == null) return;
        allianceConfig.set("alliances", null); // Reset dữ liệu cũ để tránh trùng lặp

        for (Map.Entry<String, Alliance> entry : alliances.entrySet()) {
            String allyId = entry.getKey();
            Alliance alliance = entry.getValue();
            String path = "alliances." + allyId;

            allianceConfig.set(path + ".name", alliance.getName());
            allianceConfig.set(path + ".leader", alliance.getLeader().toString());
            allianceConfig.set(path + ".bankBalance", alliance.getBankBalance());

            // Chuyển tập thành viên UUID thành chuỗi danh sách String để ghi file cấu hình
            List<String> memberStrings = new ArrayList<>();
            for (UUID memberUuid : alliance.getMembers()) {
                memberStrings.add(memberUuid.toString());
            }
            allianceConfig.set(path + ".members", memberStrings);
        }

        try {
            allianceConfig.save(allianceFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[TD] Lỗi nghiêm trọng không thể ghi dữ liệu Liên Minh vào alliances.yml: " + e.getMessage());
        }
    }

    /**
     * KHÔI PHỤC DỮ LIỆU LIÊN MINH KHI KHỞI ĐỘNG SERVER HOẶC LOAD PLUGIN
     */
    public void loadAlliances() {
        alliances.clear();
        playerAllianceMap.clear(); // Xóa sạch dữ liệu tạm cũ trên RAM

        if (allianceConfig == null || !allianceConfig.contains("alliances")) return;

        ConfigurationSection section = allianceConfig.getConfigurationSection("alliances");
        if (section == null) return;

        int loadedCount = 0;
        for (String allyId : section.getKeys(false)) {
            String path = "alliances." + allyId;
            try {
                String name = allianceConfig.getString(path + ".name");
                UUID leader = UUID.fromString(allianceConfig.getString(path + ".leader"));
                double bankBalance = allianceConfig.getDouble(path + ".bankBalance", 0.0);

                List<String> memberStrings = allianceConfig.getStringList(path + ".members");
                List<UUID> members = new ArrayList<>();
                for (String mStr : memberStrings) {
                    members.add(UUID.fromString(mStr));
                }

                // Tái thiết lập thực thể Alliance
                Alliance alliance = new Alliance(allyId, name, leader);
                alliance.setBankBalance(bankBalance);

                // Đồng bộ khôi phục bản đồ ánh xạ nhanh trên RAM chống mất trạng thái
                playerAllianceMap.put(leader, allyId);
                for (UUID mUUID : members) {
                    if (!mUUID.equals(leader)) {
                        alliance.addMember(mUUID);
                        playerAllianceMap.put(mUUID, allyId);
                    }
                }

                alliances.put(allyId, alliance);
                loadedCount++;
            } catch (Exception e) {
                if (plugin != null) {
                    plugin.getLogger().severe("[TD] Không thể nạp dữ liệu Liên Minh [" + allyId + "]: " + e.getMessage());
                }
            }
        }
        if (plugin != null) {
            plugin.getLogger().info("[TD] Đã khôi phục thành công " + loadedCount + " Liên Minh từ tệp dữ liệu alliances.yml.");
        }
    }
// Thêm vào AllianceManager.java

    /**
     * MỜI NGƯỜI CHƠI VÀO LIÊN MINH
     */
    public void sendInvite(Player inviter, Player target) {
        String allyId = getPlayerAlliance(inviter.getUniqueId());
        if (allyId == null) return;

        pendingInvites.put(target.getUniqueId(), allyId);
        target.sendMessage(ChatColor.YELLOW + "[Liên minh] Bạn đã được mời vào liên minh " + getAlliance(allyId).getName() + ". Dùng /ally accept để tham gia.");
    }

    /**
     * XÁC NHẬN LỜI MỜI (GỌI TỪ CMD HOẶC GUI)
     */
    public void acceptInvite(Player player) {
        String allyId = pendingInvites.remove(player.getUniqueId());
        if (allyId != null) {
            Alliance alliance = getAlliance(allyId);
            if (alliance != null) {
                alliance.addMember(player.getUniqueId());
                playerAllianceMap.put(player.getUniqueId(), allyId);
                saveAlliances();
                player.sendMessage(ChatColor.GREEN + "[Liên minh] Bạn đã gia nhập liên minh " + alliance.getName() + "!");
            }
        }
    }

    /**
     * ĐUỔI THÀNH VIÊN KHỎI LIÊN MINH
     */
    public void kickMember(UUID leaderUUID, UUID memberUUID) {
        String allyId = getPlayerAlliance(leaderUUID);
        Alliance alliance = getAlliance(allyId);

        if (alliance != null && alliance.getLeader().equals(leaderUUID)) {
            if (alliance.getMembers().contains(memberUUID)) {
                leaveAlliance(memberUUID);
                Player kicked = Bukkit.getPlayer(memberUUID);
                if (kicked != null) {
                    kicked.sendMessage(ChatColor.RED + "[Liên minh] Bạn đã bị đuổi khỏi liên minh!");
                }
            }
        }
    }
    /**
     * THUẬT TOÁN HỢP NHẤT LÃNH THỔ (MERGE ZONE)
     */
    public void mergeTerritories(String allyId, List<TerritoryCore> coresToMerge) {
        if (coresToMerge == null || coresToMerge.size() < 2) return;

        double totalFepStored = 0;
        int sumLevels = 0;

        for (TerritoryCore core : coresToMerge) {
            totalFepStored += core.getFep();
            sumLevels += core.getLevel();
        }

        // 1. Tính toán cấp độ trung bình cộng để gán chỉ số Lá chắn ảo
        double averageLevel = (double) sumLevels / coresToMerge.size();

        // Mô phỏng thiết lập lá chắn ảo tối đa thích ứng trung bình cộng cấp độ
        double mergedMaxShield = 1000.0 * Math.pow(2.5, averageLevel - 1);

        for (TerritoryCore core : coresToMerge) {
            // Thiết lập đồng bộ sức mạnh phòng thủ giáp ảo đồng đều theo mốc trung bình cộng
            core.setShield(mergedMaxShield);

            // Phân phối chia sẻ nguồn FEP cộng dồn đều cho các bể chứa
            core.setFep(totalFepStored / coresToMerge.size());
        }

        // Phát loa thông báo gộp đất hoàn tất
        coresToMerge.forEach(core -> {
            core.getLocation().getWorld().playSound(core.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        });

        Bukkit.getOnlinePlayers().forEach(player -> {
            String pAlly = getPlayerAlliance(player.getUniqueId());
            if (allyId.equals(pAlly)) {
                player.sendMessage(ChatColor.GREEN + "[Ngoại giao] Tiến trình gộp đất hoàn tất! Vách ngăn nội bộ đã được dọn sạch.");
                player.sendMessage(ChatColor.GREEN + " - Cấp độ Lõi trung bình: " + String.format("%.1f", averageLevel));
                player.sendMessage(ChatColor.GREEN + " - Lớp giáp ảo đồng bộ đạt: " + String.format("%.0f", mergedMaxShield) + " Shield HP.");
            }
        });
    }

    /**
     * CHẶN FRIENDLY FIRE (TẮT SÁT THƯƠNG ĐỒNG ĐỘI)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFriendlyDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player damager)) {
            return;
        }

        String allyVictim = getPlayerAlliance(victim.getUniqueId());
        String allyDamager = getPlayerAlliance(damager.getUniqueId());

        if (allyVictim != null && allyVictim.equals(allyDamager)) {
            damager.sendMessage(ChatColor.RED + "Friendly Fire đang tắt! Bạn không thể tấn công đồng đội.");
            event.setCancelled(true);
        }
    }

    /**
     * CHÈN PREFIX LIÊN MINH VÀO KHUNG CHAT (CHAT PREFIX SYNERGY)
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onAllianceChatPrefix(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String allyId = getPlayerAlliance(player.getUniqueId());

        if (allyId != null) {
            Alliance alliance = alliances.get(allyId);
            if (alliance != null) {
                String prefix = ChatColor.GOLD + "[" + alliance.getName() + "] " + ChatColor.RESET;
                event.setFormat(prefix + event.getFormat());
            }
        }
    }
}