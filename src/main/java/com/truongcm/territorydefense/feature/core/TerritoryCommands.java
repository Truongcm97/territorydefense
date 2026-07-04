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
import org.bukkit.Sound;
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

        // Kiểm tra lệnh Admin mở GUI
        if (subCommand.equals("admin")) {
            if (!player.hasPermission("territorydefense.admin")) {
                player.sendMessage(ChatColor.RED + "Bạn không có quyền sử dụng lệnh này!");
                return true;
            }
            player.closeInventory();
            player.openInventory(new com.truongcm.territorydefense.feature.web.AdminCoreManagerGui(plugin, com.truongcm.territorydefense.feature.web.AdminCoreManagerGui.AdminTab.MAIN_HUB).getInventory());
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
            return true;
        }

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

        // Lệnh dọn dẹp và tái tạo lại toàn bộ Hologram Lõi và Tháp trên toàn server cho Admin
        if (subCommand.equals("rebuildholograms") || subCommand.equals("rebuildhlg")) {
            if (!player.hasPermission("territorydefense.admin")) {
                player.sendMessage(ChatColor.RED + "Bạn không có quyền sử dụng lệnh này!");
                return true;
            }

            com.truongcm.territorydefense.feature.core.HologramManager.initialize();
            player.sendMessage(ChatColor.GREEN + "[Bảo vệ] Đã quét sạch toàn bộ hologram rác và tái tạo thành công toàn bộ Hologram Lõi và Tháp trên toàn server!");
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
            case "copydesign", "saochep" -> handleCopyDesignCommand(player, args);
            case "shareblueprint", "chiase" -> handleShareBlueprintCommand(player, args);
            case "sellblueprint", "banbanve" -> handleSellBlueprintCommand(player, args);
            case "accepttax" -> handleAcceptTaxCommand(player);
            case "migrate" -> handleMigrateCommand(player);
            case "getcore" -> handleGetCoreCommand(player);
            case "getstarter" -> handleGetStarterCommand(player);
            case "resetdifficulty" -> {
                if (!player.hasPermission("territorydefense.admin")) {
                    player.sendMessage(ChatColor.RED + "Bạn không có quyền sử dụng lệnh này!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Sử dụng: /territory resetdifficulty <tên_người_chơi>");
                    return true;
                }
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null || target.getUniqueId() == null) {
                    player.sendMessage(ChatColor.RED + "Không tìm thấy thông tin của người chơi này!");
                    return true;
                }
                UUID targetUUID = target.getUniqueId();
                TerritoryCore targetCore = null;
                for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
                    if (c.getOwnerUUID() != null && c.getOwnerUUID().equals(targetUUID)) {
                        targetCore = c;
                        break;
                    }
                }
                if (targetCore != null) {
                    targetCore.setTotalRaidCount(0);
                    targetCore.setRaidCallCount(0);
                    targetCore.setPermanentRaidMultiplier(1.0);
                    targetCore.setTemporaryRaidMultiplier(1.0);
                    targetCore.setCompletedRaids(0);
                    plugin.getCoreManager().saveAllCores();
                    player.sendMessage(ChatColor.GREEN + "[Admin] Đã reset độ khó (Raid/Call Count, Multiplier) về mặc định cho Lõi của " + args[1]);
                } else {
                    player.sendMessage(ChatColor.RED + "Không tìm thấy Lõi Lãnh Thổ đang hoạt động nào thuộc sở hữu của người chơi " + args[1]);
                }
            }
            case "recall" -> {
                if (args.length < 2) {
                    // Tự thu hồi
                    TerritoryCore selfCore = null;
                    for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
                        if (c.getOwnerUUID() != null && c.getOwnerUUID().equals(player.getUniqueId())) {
                            selfCore = c;
                            break;
                        }
                    }
                    if (selfCore == null) {
                        player.sendMessage(ChatColor.RED + "Bạn không sở hữu Lõi Lãnh Thổ nào đang hoạt động!");
                        return true;
                    }
                    if (plugin.getRaidSession() != null && plugin.getRaidSession().activeCampaigns().containsKey(selfCore.getCoreId())) {
                        player.sendMessage(ChatColor.RED + "Không thể tự thu hồi Lõi trong khi đang diễn ra đợt Raid!");
                        return true;
                    }
                    plugin.getCoreManager().removeCore(selfCore.getLocation(), player, true);
                } else {
                    // Admin cưỡng chế thu hồi
                    if (!player.hasPermission("territorydefense.admin")) {
                        player.sendMessage(ChatColor.RED + "Bạn không có quyền cưỡng chế thu hồi Lõi của người chơi khác!");
                        return true;
                    }
                    @SuppressWarnings("deprecation")
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    if (target == null || target.getUniqueId() == null) {
                        player.sendMessage(ChatColor.RED + "Không tìm thấy thông tin của người chơi này!");
                        return true;
                    }
                    UUID targetUUID = target.getUniqueId();
                    TerritoryCore targetCore = null;
                    for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
                        if (c.getOwnerUUID() != null && c.getOwnerUUID().equals(targetUUID)) {
                            targetCore = c;
                            break;
                        }
                    }
                    if (targetCore == null) {
                        player.sendMessage(ChatColor.RED + "Không tìm thấy Lõi Lãnh Thổ nào của " + args[1] + " đang hoạt động!");
                        return true;
                    }
                    if (target.isOnline() && target.getPlayer() != null) {
                        plugin.getCoreManager().removeCore(targetCore.getLocation(), target.getPlayer(), true);
                        player.sendMessage(ChatColor.GREEN + "[Admin] Đã thu hồi Lõi của " + args[1] + " và gửi trả vào hòm đồ của họ.");
                    } else {
                        plugin.getCoreManager().removeCore(targetCore.getLocation(), player, true);
                        player.sendMessage(ChatColor.GREEN + "[Admin] Người chơi " + args[1] + " đang offline. Đã thu hồi Lõi của họ và gửi trả vào hòm đồ của BẠN (Admin) để giữ hộ.");
                    }
                }
            }
            case "save" -> {
                if (!player.hasPermission("territorydefense.admin")) {
                    player.sendMessage(ChatColor.RED + "Bạn không có quyền sử dụng lệnh này!");
                    return true;
                }
                player.sendMessage(ChatColor.YELLOW + "[Hệ thống] Đang thực hiện lưu lại toàn bộ dữ liệu...");
                plugin.saveAllData();
                player.sendMessage(ChatColor.GREEN + "[Hệ thống] Đã hoàn thành lưu trữ toàn bộ dữ liệu thành công!");
            }
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

    private void handleCopyDesignCommand(Player player, String[] args) {
        TerritoryCore playerCore = null;
        for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
            if (c.getOwnerUUID().equals(player.getUniqueId())) {
                playerCore = c;
                break;
            }
        }
        if (playerCore == null) {
            player.sendMessage(ChatColor.RED + "Bạn phải sở hữu một Lõi lãnh thổ để lưu bản vẽ!");
            return;
        }

        com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getActiveBuilders().get(playerCore.getCoreId());
        if (builder == null) {
            player.sendMessage(ChatColor.RED + "Bạn cần thuê 7Gao trước khi lưu bản vẽ!");
            return;
        }

        int slotIndex = 0;
        boolean isSlotSpecified = false;

        // Cú pháp: /territory copydesign [slot 1-54] [tên_bản_vẽ]
        if (args.length >= 2) {
            try {
                slotIndex = Integer.parseInt(args[1]) - 1;
                if (slotIndex < 0 || slotIndex >= 54) {
                    player.sendMessage(ChatColor.RED + "Số slot phải nằm trong khoảng từ 1 đến 54!");
                    return;
                }
                isSlotSpecified = true;
            } catch (NumberFormatException ignored) {}
        }

        if (isSlotSpecified) {
            String customName = null;
            if (args.length >= 3) {
                customName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            }
            // Tự copy thiết kế của mình kèm tên tùy chỉnh nếu có
            builder.startScanAndSave(playerCore, slotIndex, player, customName);
        } else {
            player.sendMessage(ChatColor.RED + "Bạn không thể sao chép thiết kế trực tiếp từ người chơi khác! Vui lòng mua bản thiết kế tại Cửa Hàng Bản Vẽ ở GUI Lõi.");
        }
    }

    private void handleShareBlueprintCommand(Player player, String[] args) {
        TerritoryCore playerCore = null;
        for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
            if (c.getOwnerUUID().equals(player.getUniqueId())) {
                playerCore = c;
                break;
            }
        }
        if (playerCore == null) {
            player.sendMessage(ChatColor.RED + "Bạn phải sở hữu một Lõi lãnh thổ!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Trạng thái chia sẻ bản vẽ hiện tại: " + (playerCore.isPublicBlueprintShared() ? ChatColor.GREEN + "CÔNG KHAI" : ChatColor.RED + "RIÊNG TƯ"));
            player.sendMessage(ChatColor.GRAY + "Gõ: /territory shareblueprint <on|off> để thay đổi.");
            return;
        }

        String toggle = args[1].toLowerCase();
        boolean share = toggle.equals("on") || toggle.equals("true") || toggle.equals("yes");
        playerCore.setPublicBlueprintShared(share);
        plugin.getCoreManager().registerCore(playerCore.getLocation(), playerCore);
        player.sendMessage(ChatColor.GREEN + "Đã thay đổi trạng thái chia sẻ bản vẽ sang: " + (share ? ChatColor.GREEN + "CÔNG KHAI" : ChatColor.RED + "RIÊNG TƯ"));
    }

    private void handleSellBlueprintCommand(Player player, String[] args) {
        TerritoryCore playerCore = null;
        for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
            if (c.getOwnerUUID().equals(player.getUniqueId())) {
                playerCore = c;
                break;
            }
        }
        if (playerCore == null) {
            player.sendMessage(ChatColor.RED + "Bạn phải sở hữu một Lõi lãnh thổ!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Giá bán bản vẽ hiện tại: " + ChatColor.GOLD + String.format("%,.0f", playerCore.getBlueprintPrice()) + " Xu.");
            player.sendMessage(ChatColor.GRAY + "Gõ: /territory sellblueprint <giá_tiền> để thiết lập giá bán.");
            return;
        }

        try {
            double price = Double.parseDouble(args[1]);
            if (price < 0) {
                player.sendMessage(ChatColor.RED + "Giá bán bản vẽ không được âm!");
                return;
            }
            playerCore.setBlueprintPrice(price);
            plugin.getCoreManager().registerCore(playerCore.getLocation(), playerCore);
            player.sendMessage(ChatColor.GREEN + "Đã thiết lập giá bán bản vẽ thiết kế là: " + ChatColor.GOLD + String.format("%,.0f", price) + " Xu.");
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Giá bán không hợp lệ!");
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "========== HỆ THỐNG LÃNH THỔ (TERRITORY HELP) ==========");
        player.sendMessage(ChatColor.YELLOW + "/territory : " + ChatColor.WHITE + "Mở nhanh giao diện GUI Lõi Lãnh thổ sở hữu.");
        player.sendMessage(ChatColor.YELLOW + "/territory boundary : " + ChatColor.WHITE + "Bật/Tắt hiển thị ranh giới hạt ảo.");
        player.sendMessage(ChatColor.YELLOW + "/territory copydesign [slot 1-54] [tên_bản_vẽ] : " + ChatColor.WHITE + "Quét và lưu bản thiết kế lãnh thổ hiện tại.");
        player.sendMessage(ChatColor.YELLOW + "/territory shareblueprint <on|off> : " + ChatColor.WHITE + "Bật/tắt chia sẻ bản vẽ công khai.");
        player.sendMessage(ChatColor.YELLOW + "/territory sellblueprint <giá> : " + ChatColor.WHITE + "Thiết lập giá bán bản vẽ thiết kế.");
        player.sendMessage(ChatColor.YELLOW + "/territory accepttax : " + ChatColor.WHITE + "Chấp thuận đóng thuế nộp phạt cho phe thắng.");
        player.sendMessage(ChatColor.YELLOW + "/territory migrate : " + ChatColor.WHITE + "Từ chối đóng thuế, thu hồi Lõi đi tị nạn đất mới.");
        player.sendMessage(ChatColor.YELLOW + "/territory getstarter : " + ChatColor.WHITE + "Nhận Lõi Năng Lượng Biển khởi đầu.");
        player.sendMessage(ChatColor.YELLOW + "/territory recall : " + ChatColor.WHITE + "Tự thu hồi Lõi và Tháp của bản thân khi bị lỗi.");
        if (player.hasPermission("territorydefense.admin")) {
            player.sendMessage(ChatColor.RED + "/territory resetstarter <tên> : " + ChatColor.WHITE + "Reset lượt nhận Lõi (Admin).");
            player.sendMessage(ChatColor.RED + "/territory resetdifficulty <tên> : " + ChatColor.WHITE + "Reset độ khó thăng tiến của quái (Admin).");
            player.sendMessage(ChatColor.RED + "/territory recall <tên> : " + ChatColor.WHITE + "Cưỡng chế thu hồi Lõi của người chơi khác (Admin).");
            player.sendMessage(ChatColor.RED + "/territory getcore : " + ChatColor.WHITE + "Lấy dữ liệu Lõi đang đứng gần (Admin).");
            player.sendMessage(ChatColor.RED + "/territory save : " + ChatColor.WHITE + "Lưu trữ thủ công toàn bộ dữ liệu hệ thống (Admin).");
            player.sendMessage(ChatColor.RED + "/territory rebuildholograms : " + ChatColor.WHITE + "Dọn dẹp và tái tạo toàn bộ Hologram server (Admin).");
        }
        player.sendMessage(ChatColor.GOLD + "========================================================");
    }
}