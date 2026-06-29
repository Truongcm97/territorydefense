package com.truongcm.territorydefense.hook;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import com.truongcm.territorydefense.feature.alliance.Alliance;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CỔNG KẾT NỐI KHO CHUNG (SERVERCHEST HOOK & NATIVE GUI)
 * Triển khai hệ thống Rương chung liên minh bảo mật cao theo GDD:
 * - Tự động liên kết ServerChest/PlayerVaults hoặc kích hoạt kho ảo Native nếu thiếu plugin ngoài.
 * - Khóa khẩn cấp rút Shard khi đang bị Raid PvE hoặc Siege PvP.
 * - Phân quyền chặt chẽ: Thành viên chỉ nạp, Thủ lĩnh mới có quyền rút Shard/Item bảo mật.
 * - Ghi nhật ký tương tác an ninh độc lập ra file alliance_history.log.
 */
public final class ServerChestHook {

    // Bộ nhớ RAM lưu trữ kho chứa ảo Native nếu không sử dụng plugin ngoài
    private static final Map<String, Inventory> nativeGuildChests = new ConcurrentHashMap<>();

    // Theo dõi phiên mở rương ảo của người chơi: [PlayerUUID, AllianceID]
    private static final Map<UUID, String> activeChestSessions = new ConcurrentHashMap<>();

    /**
     * Kích hoạt mở kho chứa đồ chung an toàn bằng tên hoặc UUID viết ngắn của Liên minh.
     */
    public static void openGuildChest(Player player, String allianceId, String allianceName) {
        if (allianceId == null || allianceId.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Không xác định được Liên minh sở hữu để mở kho!");
            return;
        }

        TerritoryDefense plugin = TerritoryDefense.getInstance();

        // 1. Kiểm tra xem plugin ServerChest ngoài có đang hoạt động không
        if (Bukkit.getPluginManager().getPlugin("ServerChest") != null ||
                Bukkit.getPluginManager().getPlugin("PlayerVaults") != null) {

            logChestAction(player.getName(), allianceName, "MỞ KHO NGOÀI", "N/A", 0, "SUCCESS");
            try {
                // Đóng vai trò thực thi lệnh mở hòm đồ của plugin ServerChest/PlayerVaults
                boolean checkCmd = player.performCommand("chest " + allianceName);
                if (checkCmd) {
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                    return;
                }
            } catch (Exception ignored) {}
            // Fallback sang native chest nếu lệnh ngoài thất bại hoặc không được thực thi thành công
        }

        // 2. Kích hoạt Kho ảo Native bảo mật cao tích hợp sẵn
        {
            // 2. Kích hoạt Kho ảo Native bảo mật cao tích hợp sẵn
            Inventory gui = nativeGuildChests.computeIfAbsent(allianceId, k ->
                    Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Rương Liên Minh - " + allianceName)
            );

            activeChestSessions.put(player.getUniqueId(), allianceId);
            player.openInventory(gui);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
            logChestAction(player.getName(), allianceName, "MỞ KHO NATIVE", "NONE", 0, "SUCCESS");
        }
    }

    /**
     * Khởi chạy trình lắng nghe sự kiện của Kho ảo Native để kiểm soát an ninh.
     */
    public static void registerListener(TerritoryDefense plugin) {
        plugin.getServer().getPluginManager().registerEvents(new GuildChestSecurityListener(plugin), plugin);
    }

    /**
     * Ghi nhật ký tương tác an ninh độc lập ra file alliance_history.log.
     */
    public static void logChestAction(String playerName, String allyName, String action, String itemName, int amount, String status) {
        TerritoryDefense plugin = TerritoryDefense.getInstance();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File dataFolder = plugin.getDataFolder();
                if (!dataFolder.exists()) dataFolder.mkdirs();

                File logFile = new File(dataFolder, "alliance_history.log");
                if (!logFile.exists()) logFile.createNewFile();

                try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
                    String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                    out.printf("[%s] [PLAYER: %s] [ALLIANCE: %s] [ACTION: %s] [ITEM: %s] [QTY: %d] [STATUS: %s]%n",
                            timeStamp, playerName, allyName, action, itemName, amount, status);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Không thể ghi log giao dịch rương liên minh: " + e.getMessage());
            }
        });
    }

    // ==========================================
    // TRÌNH LẮNG NGHE AN NINH RƯƠNG LIÊN MINH (INTERNAL SECURITY LISTENER)
    // ==========================================
    private static class GuildChestSecurityListener implements Listener {
        private final TerritoryDefense plugin;

        public GuildChestSecurityListener(TerritoryDefense plugin) {
            this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onChestSecurityClick(InventoryClickEvent event) {
            Player player = (Player) event.getWhoClicked();
            UUID pId = player.getUniqueId();

            // Chỉ bắt các tương tác click nằm trong phiên mở Rương ảo Native của liên minh
            if (!activeChestSessions.containsKey(pId)) return;

            String allianceId = activeChestSessions.get(pId);
            Alliance alliance = plugin.getAllianceManager().getAlliance(allianceId);
            if (alliance == null) return;

            // Xác định xem click này có thuộc diện "Rút vật phẩm" (Withdraw) hay không
            boolean isWithdraw = false;
            ItemStack targetItem = null;

            // Kiểm tra xem click xảy ra ở kho trên hay kho của người chơi
            boolean clickedTopInventory = event.getClickedInventory() == event.getView().getTopInventory();

            if (clickedTopInventory) {
                // Nhấn trực tiếp vào rương chung để lấy đồ ra
                if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                    isWithdraw = true;
                    targetItem = event.getCurrentItem();
                }
            } else {
                // Shift click từ kho đồ cá nhân nhét vào rương chung (Sự kiện nạp - Deposit)
                if (event.isShiftClick()) {
                    // Shift click là hành động nạp đồ, cho phép tự do không khóa chặn
                    targetItem = event.getCurrentItem();
                }
            }

            if (isWithdraw && targetItem != null) {
                // KIỂM TRA 1: Khóa khẩn cấp trong trạng thái Raid PvE hoặc Siege PvP
                if (isAllianceUnderEmergencyLock(alliance)) {
                    player.sendMessage(ChatColor.RED + "AN NINH CẢNH BÁO: Lãnh thổ đang trong trạng thái chiến sự (Raid/Siege)! Tính năng rút đồ đã bị khóa chặt để bảo vệ tài sản.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    event.setCancelled(true);
                    logChestAction(player.getName(), alliance.getName(), "RÚT ĐỒ", targetItem.getType().name(), targetItem.getAmount(), "DENIED (EMERGENCY LOCK)");
                    return;
                }

                // KIỂM TRA 2: Thẩm định quyền rút vật phẩm bảo mật (Shard / Item có Secure ID)
                if (isSecureItem(targetItem)) {
                    UUID leaderUUID = alliance.getLeader();
                    if (!leaderUUID.equals(pId) && !player.hasPermission("territorydefense.admin")) {
                        player.sendMessage(ChatColor.RED + "Quyền hạn không đủ! Chỉ Thủ Lĩnh (Leader) mới có quyền rút Shard hoặc vật phẩm có mã bảo mật.");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        event.setCancelled(true);
                        logChestAction(player.getName(), alliance.getName(), "RÚT VẬT PHẨM BẢO MẬT", targetItem.getType().name(), targetItem.getAmount(), "DENIED (NO PERMISSION)");
                        return;
                    }
                }

                // Chấp thuận rút đồ và ghi log
                logChestAction(player.getName(), alliance.getName(), "RÚT ĐỒ", targetItem.getType().name(), targetItem.getAmount(), "SUCCESS");
            } else if (!clickedTopInventory && event.getCurrentItem() != null) {
                // Ghi nhận log nạp đồ (Deposit) thành công
                ItemStack depositItem = event.getCurrentItem();
                logChestAction(player.getName(), alliance.getName(), "NẠP ĐỒ", depositItem.getType().name(), depositItem.getAmount(), "SUCCESS");
            }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onChestSecurityDrag(InventoryDragEvent event) {
            Player player = (Player) event.getWhoClicked();
            if (!activeChestSessions.containsKey(player.getUniqueId())) return;

            // Chặn kéo thả lung tung trong rương ảo liên minh để tránh lỗi lách luật click
            event.setCancelled(true);
        }

        @EventHandler
        public void onChestClose(InventoryCloseEvent event) {
            Player player = (Player) event.getPlayer();
            // Gỡ phiên mở kho đồ của người chơi khỏi RAM khi đóng giao diện
            activeChestSessions.remove(player.getUniqueId());
        }

        /**
         * Kiểm tra xem bất kỳ ranh giới lãnh thổ nào của liên minh này có đang bị tấn công hay không.
         */
        private boolean isAllianceUnderEmergencyLock(Alliance alliance) {
            // Duyệt qua tất cả các Lõi của bang hội
            for (UUID coreUUID : alliance.getActiveCoreIds()) {
                for (TerritoryCore activeCore : plugin.getCoreManager().getAllActiveCores()) {
                    if (activeCore.getCoreId().equals(coreUUID)) {
                        // 1. Nếu Lõi đang bị quái Raid tấn công
                        if (activeCore.isRaidActive()) return true;

                        // 2. Nếu Lõi đang bị bang đối địch Tuyên chiến công thành
                        String myAlly = alliance.getAllyId();
                        if (plugin.getSiegeSession().isRegenPenalized(myAlly)) return true;
                    }
                }
            }
            return false;
        }

        /**
         * Kiểm tra xem vật phẩm có thuộc diện cần bảo vệ (Shard hoặc mang mã băm PDC độc bản) không.
         */
        private boolean isSecureItem(ItemStack item) {
            if (item.getType() == Material.PRISMARINE_SHARD) return true;

            if (item.hasItemMeta()) {
                var pdc = item.getItemMeta().getPersistentDataContainer();
                return pdc.has(PDCKeys.SECURE_ITEM_ID, PersistentDataType.STRING);
            }
            return false;
        }
    }
}