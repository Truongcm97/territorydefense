package com.truongcm.territorydefense.feature.core;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import com.truongcm.territorydefense.feature.core.ui.CoreGui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * BỘ ĐIỀU PHỐI LỆNH LÃNH THỔ (TERRITORY COMMANDS) - PHIÊN BẢN HỖ TRỢ CHƠI SOLO/ALLIANCE ĐỘNG
 * Đã sửa đổi lệnh resetstarter hỗ trợ reset cứu hộ cho cả người chơi Online và Offline dứt điểm.
 */
public class TerritoryCommands implements CommandExecutor {

    private final TerritoryDefense plugin;

    public TerritoryCommands(TerritoryDefense plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Lệnh này chỉ có thể thực hiện bởi người chơi trong game!");
            return true;
        }

        if (args.length == 0) {
            openCoreMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Kiểm tra lệnh Reset Starter dành cho Admin
        // ĐÃ NÂNG CẤP: Hỗ trợ khôi phục Lõi lỗi cho cả người chơi đang Offline
        if (subCommand.equals("resetstarter")) {
            if (!player.hasPermission("territorydefense.admin")) {
                player.sendMessage(ChatColor.RED + "Bạn không có quyền sử dụng lệnh này!");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Sử dụng: /territory resetstarter <tên_người_chơi>");
                return true;
            }

            // Sử dụng OfflinePlayer thay vì Bukkit.getPlayer() để chấp nhận mọi người chơi offline
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target == null || target.getUniqueId() == null) {
                player.sendMessage(ChatColor.RED + "Không tìm thấy thông tin của người chơi này!");
                return true;
            }

            UUID targetUUID = target.getUniqueId();

            // 1. Đồng thời giải phóng cưỡng chế lãnh thổ lỗi kẹt (Thực thi cơ chế quét RAM + File dứt điểm)
            plugin.getCoreManager().forcePurgePlayerCore(targetUUID);

            // 2. Reset quyền nhận Starter cho người chơi
            if (target.isOnline() && target.getPlayer() != null) {
                target.getPlayer().getPersistentDataContainer().remove(PDCKeys.RECEIVED_STARTER_KEY);
            }

            player.sendMessage(ChatColor.YELLOW + "Đã reset quyền nhận Starter và quét sạch Lõi Ma (RAM + File) cho: " + ChatColor.WHITE + args[1]);
            return true;
        }

        switch (subCommand) {
            case "boundary" -> {
                // Tinh giản trải nghiệm: Gõ "/territory boundary" hoặc "/territory boundary toggle" đều tự động bật/tắt hiển thị hạt bảo vệ
                boolean isViewing = plugin.getBorderVisualizer().toggleBoundary(player.getUniqueId());
                if (isViewing) {
                    player.sendMessage(ChatColor.GREEN + "[Hệ thống] Đã bật hiển thị hạt ranh giới bảo vệ Lãnh Thổ.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "[Hệ thống] Đã tắt hiển thị hạt ranh giới bảo vệ.");
                }
            }
            case "accepttax" -> handleAcceptTaxCommand(player);
            case "migrate" -> handleMigrateCommand(player);
            case "getcore" -> handleGetCoreCommand(player);
            case "getstarter" -> handleGetStarterCommand(player);
            case "help" -> showHelp(player);
            default -> player.sendMessage(ChatColor.RED + "Lệnh không hợp lệ! Gõ /territory help để xem trợ giúp.");
        }

        return true;
    }

    private void handleGetStarterCommand(Player player) {
        if (player.getPersistentDataContainer().has(PDCKeys.RECEIVED_STARTER_KEY, PersistentDataType.BYTE)) {
            player.sendMessage(ChatColor.RED + "Bạn đã nhận Lõi Khởi Đầu từ trước rồi!");
            return;
        }

        if (plugin.getCoreManager().getOwnedCoreCount(player.getUniqueId()) > 0) {
            player.sendMessage(ChatColor.RED + "Bạn đã sở hữu một Lãnh thổ rồi!");
            return;
        }

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta() &&
                    item.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE)) {
                player.sendMessage(ChatColor.RED + "Bạn đã có Lõi Khởi Đầu trong túi đồ rồi!");
                return;
            }
        }

        ItemStack starterCore = plugin.getCoreManager().createCoreItem();
        player.getInventory().addItem(starterCore);
        player.getPersistentDataContainer().set(PDCKeys.RECEIVED_STARTER_KEY, PersistentDataType.BYTE, (byte) 1);
        player.sendMessage(ChatColor.GREEN + "Bạn đã nhận được Lõi Khởi Đầu! Hãy tìm nơi đặt nó xuống.");
    }

    private void handleGetCoreCommand(Player player) {
        if (!player.hasPermission("territorydefense.admin")) {
            player.sendMessage(ChatColor.RED + "Bạn không có quyền thực hiện lệnh này!");
            return;
        }

        TerritoryCore core = plugin.getCoreManager().getCoreByLocationRange(player.getLocation());
        if (core != null) {
            player.sendMessage(ChatColor.GREEN + "=== THÔNG TIN LÕI GẦN BẠN ===");
            player.sendMessage(ChatColor.YELLOW + "Core ID: " + ChatColor.WHITE + core.getCoreId().toString());
            player.sendMessage(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + core.getLevel());
            player.sendMessage(ChatColor.YELLOW + "Owner Alliance: " + ChatColor.WHITE + plugin.getCoreManager().getCoreAlliance(core));
            player.sendMessage(ChatColor.GRAY + "Gợi ý: /territory raid start " + core.getCoreId().toString());
        } else {
            player.sendMessage(ChatColor.RED + "Không tìm thấy Lõi (Core) nào tại vị trí này!");
        }
    }

    /**
     * Mở nhanh giao diện điều khiển Lõi.
     */
    private void openCoreMenu(Player player) {
        TerritoryCore core = plugin.getCoreManager().getCoreByLocationRange(player.getLocation());
        if (core == null) {
            player.sendMessage(ChatColor.RED + "Bạn phải đứng bên trong ranh giới bảo vệ của Lõi để mở Menu!");
            return;
        }

        // BỎ QUA KIỂM TRA LIÊN MINH NẾU LÀ CHỦ SỞ HỮU
        if (!core.getOwnerUUID().equals(player.getUniqueId())) {
            String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
            String coreAlly = plugin.getCoreManager().getCoreAlliance(core);

            if (coreAlly == null || playerAlly == null || !coreAlly.equals(playerAlly)) {
                player.sendMessage(ChatColor.RED + "Lãnh thổ này không thuộc quyền sở hữu của Liên minh của bạn!");
                return;
            }
        }

        player.openInventory(new CoreGui(plugin, core, CoreGui.CoreTab.LOGISTICS).getInventory());
    }

    /**
     * Chấp thuận đóng thuế nộp phạt cho phe thắng.
     */
    private void handleAcceptTaxCommand(Player player) {
        TerritoryCore core = plugin.getCoreManager().getCoreByLocationRange(player.getLocation());
        if (core == null) {
            player.sendMessage(ChatColor.RED + "Bạn phải đứng gần Lõi chính bại trận để thực thi lệnh nộp thuế!");
            return;
        }

        String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAlly = plugin.getCoreManager().getCoreAlliance(core);
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());
        boolean isAlly = coreAlly != null && playerAlly != null && coreAlly.equals(playerAlly);

        if (!isOwner && !isAlly) {
            player.sendMessage(ChatColor.RED + "Bạn không có quyền quyết định vận mệnh của Lõi này!");
            return;
        }

        plugin.getSiegeSession().executeAcceptTax(player, core);
    }

    /**
     * Từ chối nộp thuế, thực hiện di dời đóng gói tị nạn.
     */
    private void handleMigrateCommand(Player player) {
        TerritoryCore core = plugin.getCoreManager().getCoreByLocationRange(player.getLocation());
        if (core == null) {
            player.sendMessage(ChatColor.RED + "Bạn phải đứng gần Lõi chính để thực hiện di dời đóng gói tị nạn!");
            return;
        }

        String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAlly = plugin.getCoreManager().getCoreAlliance(core);
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());
        boolean isAlly = coreAlly != null && playerAlly != null && coreAlly.equals(playerAlly);

        if (!isOwner && !isAlly) {
            player.sendMessage(ChatColor.RED + "Bạn không phải chủ quản hoặc Leader sở hữu Lõi này!");
            return;
        }

        plugin.getSiegeSession().executeRefuseTaxAndMigrate(player, core);
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "========== HỆ THỐNG LÃNH THỔ (TERRITORY HELP) ==========");
        player.sendMessage(ChatColor.YELLOW + "/territory : " + ChatColor.WHITE + "Mở nhanh giao diện GUI Lõi Lãnh thổ sở hữu.");
        player.sendMessage(ChatColor.YELLOW + "/territory boundary : " + ChatColor.WHITE + "Bật/Tắt hiển thị ranh giới hạt ảo.");
        player.sendMessage(ChatColor.YELLOW + "/territory accepttax : " + ChatColor.WHITE + "Chấp thuận đóng thuế nộp phạt cho phe thắng.");
        player.sendMessage(ChatColor.YELLOW + "/territory migrate : " + ChatColor.WHITE + "Từ chối đóng thuế, thu hồi Lõi đi tị nạn đất mới.");
        player.sendMessage(ChatColor.YELLOW + "/territory getstarter : " + ChatColor.WHITE + "Nhận Lõi Năng Lượng Biển khởi đầu.");
        if (player.hasPermission("territorydefense.admin")) {
            player.sendMessage(ChatColor.RED + "/territory resetstarter <tên> : " + ChatColor.WHITE + "Reset lượt nhận Lõi (Admin).");
            player.sendMessage(ChatColor.RED + "/territory getcore : " + ChatColor.WHITE + "Lấy dữ liệu Lõi đang đứng gần (Admin).");
        }
        player.sendMessage(ChatColor.GOLD + "========================================================");
    }
}