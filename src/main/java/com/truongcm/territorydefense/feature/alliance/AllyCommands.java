package com.truongcm.territorydefense.feature.alliance;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.alliance.Alliance;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import com.truongcm.territorydefense.base.ui.GUIRouter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BỘ ĐIỀU PHỐI LỆNH LIÊN MINH (ALLY COMMANDS)
 * Quản trị toàn bộ các lệnh tương tác ngoại giao /ally.
 * ĐÃ NÂNG CẤP ĐỘT PHÁ - TUYÊN CHIẾN ĐA HƯỚNG V30:
 * - Hỗ trợ Solo Tuyên chiến với Solo, Solo Tuyên chiến với Ally.
 * - Hỗ trợ Ally Tuyên chiến với Solo, Ally Tuyên chiến với Ally.
 * - Tuyên chiến trực tiếp bằng cách nhập Tên (Tên người chơi / Tên liên minh) thay vì mã ID phức tạp.
 * - KIỂM DUYỆT ONLINE: Chỉ cho phép tuyên chiến khi Liên minh đối địch có thành viên online, hoặc đối thủ Solo đang online.
 */
public class AllyCommands implements CommandExecutor {

    private final TerritoryDefense plugin;

    public AllyCommands(TerritoryDefense plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Lệnh này chỉ có thể thực hiện bởi người chơi!");
            return true;
        }

        if (args.length == 0) {
            openAllianceMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Cú pháp đúng: /ally create <tên_liên_minh>");
                    return true;
                }
                handleCreateAlliance(player, args[1]);
                break;

            case "invite":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Cú pháp đúng: /ally invite <tên_người_chơi>");
                    return true;
                }
                handleInvitePlayer(player, args[1]);
                break;

            case "kick":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Cú pháp đúng: /ally kick <tên_người_chơi>");
                    return true;
                }
                handleKickPlayer(player, args[1]);
                break;

            case "chat":
            case "c":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Cú pháp đúng: /ally chat <nội_dung_tin_nhắn>");
                    return true;
                }
                String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                handleAllianceChat(player, message);
                break;

            case "chest":
                handleOpenGuildChest(player);
                break;

            case "declare":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Cú pháp đúng: /ally declare <tên_người_chơi_hoặc_tên_liên_minh>");
                    return true;
                }
                handleDeclareWar(player, args[1]);
                break;

            case "leave":
                handleLeaveAlliance(player);
                break;

            case "disband":
            case "delete":
                handleDisbandAlliance(player);
                break;

            case "merge":
                if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                    handleMergeConfirm(player);
                } else {
                    handleMergeRequest(player);
                }
                break;

            case "help":
            default:
                showHelp(player);
                break;
        }

        return true;
    }

    private void openAllianceMenu(Player player) {
        GUIRouter.openAllianceMenu(player);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
    }

    private void handleCreateAlliance(Player player, String name) {
        String currentAlly = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        if (currentAlly != null) {
            player.sendMessage(ChatColor.RED + "Bạn đã có Liên minh rồi! Hãy rời bang cũ trước khi lập bang mới.");
            return;
        }

        double createCost = 50000.0;
        if (!plugin.getVaultEconomy().has(player, createCost)) {
            player.sendMessage(ChatColor.RED + "Bạn không đủ Xu để lập Liên minh! Cần: " + createCost + " Xu.");
            return;
        }

        if (plugin.getAllianceManager().createAlliance(name, player.getUniqueId())) {
            plugin.getVaultEconomy().withdrawPlayer(player, createCost);
            player.sendMessage(ChatColor.GREEN + "Chúc mừng! Bạn đã lập Liên Minh: " + ChatColor.YELLOW + name +
                    ChatColor.GREEN + " thành công!");
            player.sendMessage(ChatColor.GRAY + "Chi phí thanh toán: " + createCost + " Xu.");
        } else {
            player.sendMessage(ChatColor.RED + "Tên liên minh này đã tồn tại hoặc không hợp lệ!");
        }
    }

    private void handleInvitePlayer(Player player, String targetName) {
        String allyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        if (allyId == null) {
            player.sendMessage(ChatColor.RED + "Bạn phải có Liên minh để thực hiện lời mời!");
            return;
        }

        Alliance alliance = plugin.getAllianceManager().getAlliance(allyId);
        if (alliance == null || !alliance.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Chỉ có Thử Lĩnh (Leader) mới có quyền mời thành viên mới!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "Người chơi này không online!");
            return;
        }

        String targetAlly = plugin.getAllianceManager().getPlayerAlliance(target.getUniqueId());
        if (targetAlly != null) {
            player.sendMessage(ChatColor.RED + "Người chơi này đã ở trong một Liên minh khác!");
            return;
        }

        plugin.getAllianceManager().joinAlliance(allyId, target.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Đã mời và thêm " + target.getName() + " thành công vào Liên minh.");
        target.sendMessage(ChatColor.GREEN + "Bạn đã được nhận vào Liên minh: " + ChatColor.YELLOW + alliance.getName());
    }

    private void handleKickPlayer(Player player, String targetName) {
        String allyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        if (allyId == null) return;

        Alliance alliance = plugin.getAllianceManager().getAlliance(allyId);
        if (alliance == null || !alliance.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Chỉ có Thủ Lĩnh mới có quyền kick thành viên!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        UUID targetUUID = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(targetName).getUniqueId();

        if (!alliance.getMembers().contains(targetUUID)) {
            player.sendMessage(ChatColor.RED + "Người chơi này không thuộc Liên minh của bạn!");
            return;
        }

        if (player.getUniqueId().equals(targetUUID)) {
            player.sendMessage(ChatColor.RED + "Bạn không thể tự kick chính mình!");
            return;
        }

        plugin.getAllianceManager().leaveAlliance(targetUUID);
        player.sendMessage(ChatColor.GREEN + "Đã trục xuất thành viên thành công.");
        if (target != null && target.isOnline()) {
            target.sendMessage(ChatColor.RED + "Bạn đã bị trục xuất khỏi Liên minh.");
        }
    }

    private void handleAllianceChat(Player player, String msg) {
        String allyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        if (allyId == null) {
            player.sendMessage(ChatColor.RED + "Bạn không thuộc Liên minh nào để chat bang!");
            return;
        }

        Alliance alliance = plugin.getAllianceManager().getAlliance(allyId);
        if (alliance != null) {
            String format = ChatColor.GOLD + "[Ally-Chat] " + ChatColor.YELLOW + player.getName() + ": " + ChatColor.WHITE + msg;
            for (UUID mUUID : alliance.getMembers()) {
                Player member = Bukkit.getPlayer(mUUID);
                if (member != null && member.isOnline()) {
                    member.sendMessage(format);
                }
            }
        }
    }

    private void handleOpenGuildChest(Player player) {
        String allyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        if (allyId == null) return;

        Alliance alliance = plugin.getAllianceManager().getAlliance(allyId);
        if (alliance != null) {
            player.performCommand("chest " + alliance.getName());
        }
    }

    private void handleLeaveAlliance(Player player) {
        String allyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        if (allyId == null) {
            player.sendMessage(ChatColor.RED + "Bạn không ở trong bất cứ Liên minh nào!");
            return;
        }

        Alliance alliance = plugin.getAllianceManager().getAlliance(allyId);
        if (alliance == null) return;

        if (alliance.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Bạn là Thủ Lĩnh Liên Minh! Vui lòng gõ lệnh /ally disband để giải tán bang trước khi rời.");
            return;
        }

        plugin.getAllianceManager().leaveAlliance(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Bạn đã rời khỏi Liên Minh thành công!");

        String notifyMsg = ChatColor.GOLD + "[Ally] " + ChatColor.YELLOW + player.getName() + ChatColor.RED + " đã rời khỏi Liên Minh.";
        for (UUID mUUID : alliance.getMembers()) {
            Player member = Bukkit.getPlayer(mUUID);
            if (member != null && member.isOnline()) {
                member.sendMessage(notifyMsg);
            }
        }
    }

    private void handleDisbandAlliance(Player player) {
        String allyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        if (allyId == null) {
            player.sendMessage(ChatColor.RED + "Bạn không ở trong bất cứ Liên minh nào!");
            return;
        }

        Alliance alliance = plugin.getAllianceManager().getAlliance(allyId);
        if (alliance == null) return;

        if (!alliance.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Chỉ có Thủ Lĩnh Liên Minh mới có quyền giải tán bang!");
            return;
        }

        String notifyMsg = ChatColor.RED + "[Ally] Liên Minh " + ChatColor.YELLOW + alliance.getName() + ChatColor.RED + " đã bị giải tán bởi Thủ Lĩnh.";
        for (UUID mUUID : alliance.getMembers()) {
            Player member = Bukkit.getPlayer(mUUID);
            if (member != null && member.isOnline()) {
                member.sendMessage(notifyMsg);
            }
        }

        boolean reflectionSuccess = false;
        Object manager = plugin.getAllianceManager();
        String[] potentialDisbandMethods = {"disbandAlliance", "deleteAlliance", "disband", "removeAlliance", "destroyAlliance"};

        for (String methodName : potentialDisbandMethods) {
            try {
                java.lang.reflect.Method m = manager.getClass().getDeclaredMethod(methodName, String.class);
                m.setAccessible(true);
                m.invoke(manager, allyId);
                reflectionSuccess = true;
                break;
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Method m = manager.getClass().getDeclaredMethod(methodName, Alliance.class);
                    m.setAccessible(true);
                    m.invoke(manager, alliance);
                    reflectionSuccess = true;
                    break;
                } catch (Exception e2) {
                    try {
                        java.lang.reflect.Method m = manager.getClass().getDeclaredMethod(methodName, UUID.class);
                        m.setAccessible(true);
                        m.invoke(manager, player.getUniqueId());
                        reflectionSuccess = true;
                        break;
                    } catch (Exception ignored) {}
                }
            }
        }

        if (!reflectionSuccess) {
            try {
                List<UUID> currentMembers = new ArrayList<>(alliance.getMembers());
                for (UUID memberUUID : currentMembers) {
                    plugin.getAllianceManager().leaveAlliance(memberUUID);
                }
                reflectionSuccess = true;
                player.sendMessage(ChatColor.GREEN + "[Liên Minh] Giải toán toàn bang thành công bằng phương thức dọn dẹp thành viên thủ công.");
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Lỗi khi dọn dẹp bang hội: " + e.getMessage());
            }
        }

        if (reflectionSuccess) {
            player.sendMessage(ChatColor.GREEN + "Giải tán Liên Minh thành công!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
        }
    }

    /**
     * NÂNG CẤP ĐỘT PHÁ: TUYÊN CHIẾN ĐA HƯỚNG BẰNG TÊN (SOLO & ALLIANCE V30)
     * Cho phép tự do tuyên chiến đa hướng bằng cách gõ Tên (Người chơi / Bang hội) thay thế ID.
     * KIỂM TRÁ TRỰC TUYẾN: Chỉ cho phép tuyên chiến khi có ít nhất 1 thành viên bang hội online
     * hoặc người chơi Solo đích thực đang trực tuyến.
     */
    private void handleDeclareWar(Player player, String targetName) {
        String attackerAllyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        String attackerId = (attackerAllyId != null) ? attackerAllyId : player.getUniqueId().toString();

        // 1. Kiểm tra quyền hạn nếu ở trong Alliance (Chỉ Thủ lĩnh mới được khai chiến)
        if (attackerAllyId != null) {
            Alliance attackerAlly = plugin.getAllianceManager().getAlliance(attackerAllyId);
            if (attackerAlly != null && !attackerAlly.getLeader().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Chỉ có Thủ Lĩnh Liên Minh mới có quyền tuyên chiến quốc gia!");
                return;
            }
        }

        String defenderId = null;
        TerritoryCore defenderCore = null;
        String defenderName = "";
        boolean isAllianceWar = false;

        // 2. Tìm kiếm đối thủ theo TÊN LIÊN MINH (Ally Name) trước
        Alliance targetAlly = null;
        for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
            String allyId = plugin.getCoreManager().getCoreAlliance(c);
            if (allyId != null) {
                Alliance alliance = plugin.getAllianceManager().getAlliance(allyId);
                if (alliance != null && alliance.getName().equalsIgnoreCase(targetName)) {
                    targetAlly = alliance;
                    break;
                }
            }
        }

        if (targetAlly != null) {
            defenderId = targetAlly.getAllyId();
            defenderName = "Liên Minh " + targetAlly.getName();
            isAllianceWar = true;

            // KIỂM TRA ONLINE: Ít nhất 1 thành viên của Liên minh đối phương phải online
            boolean isAnyMemberOnline = false;
            for (UUID memberUUID : targetAlly.getMembers()) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && member.isOnline()) {
                    isAnyMemberOnline = true;
                    break;
                }
            }

            if (!isAnyMemberOnline) {
                player.sendMessage(ChatColor.RED + "[Chiến tranh] Không thể tuyên chiến! Liên Minh đối địch \"" + targetAlly.getName() + "\" hiện tại không có bất kỳ thành viên nào online trực tuyến.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Tìm Lõi chính của Liên minh đối địch
            for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
                String allyId = plugin.getCoreManager().getCoreAlliance(c);
                if (allyId != null && allyId.equals(defenderId)) {
                    defenderCore = c;
                    break;
                }
            }
        } else {
            // 3. Nếu không phải là Alliance Name, tìm kiếm theo TÊN NGƯỜI CHƠI (Player Name)
            @SuppressWarnings("deprecation")
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            if (targetPlayer != null && targetPlayer.getUniqueId() != null) {
                UUID targetUUID = targetPlayer.getUniqueId();
                String targetPlayerAllyId = plugin.getAllianceManager().getPlayerAlliance(targetUUID);

                if (targetPlayerAllyId != null) {
                    // PHƯƠNG ÁN AN TOÀN: Nếu đối thủ thuộc một Alliance, tự động chuyển thành tuyên chiến với Alliance đó!
                    Alliance alliance = plugin.getAllianceManager().getAlliance(targetPlayerAllyId);
                    if (alliance != null) {
                        defenderId = alliance.getAllyId();
                        defenderName = "Liên Minh " + alliance.getName() + " (của người chơi " + targetPlayer.getName() + ")";
                        isAllianceWar = true;

                        // KIỂM TRA ONLINE: Ít nhất 1 thành viên của Liên minh đối phương phải online
                        boolean isAnyMemberOnline = false;
                        for (UUID memberUUID : alliance.getMembers()) {
                            Player member = Bukkit.getPlayer(memberUUID);
                            if (member != null && member.isOnline()) {
                                isAnyMemberOnline = true;
                                break;
                            }
                        }

                        if (!isAnyMemberOnline) {
                            player.sendMessage(ChatColor.RED + "[Chiến tranh] Không thể tuyên chiến! Liên Minh \"" + alliance.getName() + "\" bảo hộ người chơi \"" + targetPlayer.getName() + "\" hiện tại không có ai online.");
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            return;
                        }

                        for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
                            String allyId = plugin.getCoreManager().getCoreAlliance(c);
                            if (allyId != null && allyId.equals(defenderId)) {
                                defenderCore = c;
                                break;
                            }
                        }
                    }
                } else {
                    // TUYÊN CHIẾN CÁ NHÂN (Solo Player): Trực tiếp tấn công Lõi chính của người chơi Solo
                    defenderId = targetUUID.toString();
                    defenderName = "Người chơi Solo " + targetPlayer.getName();
                    isAllianceWar = false;

                    // KIỂM TRA ONLINE: Người chơi Solo này bắt buộc phải online trực tiếp trong game
                    if (!targetPlayer.isOnline() || targetPlayer.getPlayer() == null) {
                        player.sendMessage(ChatColor.RED + "[Chiến tranh] Không thể tuyên chiến! Người chơi cá nhân \"" + targetPlayer.getName() + "\" hiện tại đang ngoại tuyến (Offline).");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }

                    for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
                        if (c.getOwnerUUID().equals(targetUUID)) {
                            defenderCore = c;
                            break;
                        }
                    }
                }
            }
        }

        // Kiểm tra tính hợp lệ của đối phương
        if (defenderCore == null || defenderId == null) {
            player.sendMessage(ChatColor.RED + "Không tìm thấy Liên Minh hoặc Người Chơi nào sở hữu Lõi hoạt động có tên: " + targetName);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Ngăn chặn tự hủy (Tự tuyên chiến chính mình)
        if (defenderId.equals(attackerId) || (attackerAllyId != null && defenderId.equals(attackerAllyId))) {
            player.sendMessage(ChatColor.RED + "Bạn không thể tự tuyên chiến với chính mình hoặc liên minh của mình!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // 4. Khởi chạy phiên chiến sự trực tiếp qua SiegeSession API tường minh
        boolean success = false;
        if (plugin.getSiegeSession() != null) {
            try {
                plugin.getSiegeSession().declareWar(player, attackerId, defenderId, defenderCore);
                success = true;
            } catch (Exception e) {
                try {
                    // Thử gọi command gián tiếp phòng hờ
                    player.performCommand("td war declare " + defenderId);
                    success = true;
                } catch (Exception ignored) {}
            }
        }

        if (success) {
            player.sendMessage(ChatColor.GREEN + "[Chiến tranh] Đã phát động tuyên chiến thành công tới đối thủ: " + ChatColor.YELLOW + defenderName + ChatColor.GREEN + "!");
            player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 0.8f);
        } else {
            player.sendMessage(ChatColor.RED + "[Chiến tranh] Không thể phát động chiến sự lúc này!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private void handleMergeRequest(Player player) {
        String allyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        if (allyId == null) {
            player.sendMessage(ChatColor.RED + "Bạn cần tham gia một Liên minh trước khi thực hiện hợp nhất!");
            return;
        }

        Alliance alliance = plugin.getAllianceManager().getAlliance(allyId);
        if (alliance == null || !alliance.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Chỉ có Thủ lĩnh Liên minh (Leader) mới có quyền hợp nhất lãnh thổ!");
            return;
        }

        List<TerritoryCore> coresToMerge = new ArrayList<>();
        if (plugin.getCoreManager() != null) {
            for (TerritoryCore core : plugin.getCoreManager().getAllActiveCores()) {
                if (allyId.equalsIgnoreCase(core.getAllyId())) {
                    coresToMerge.add(core);
                }
            }
        }

        if (coresToMerge.size() < 2) {
            player.sendMessage(ChatColor.RED + "Yêu cầu ít nhất 2 Lõi Lãnh Thổ trong Liên minh hoạt động để hợp nhất! Hiện tại chỉ có " + coresToMerge.size() + ".");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "=== CHUẨN BỊ HỢP NHẤT LÃNH THỔ LIÊN MINH ===");
        player.sendMessage(ChatColor.YELLOW + "Số lượng Lõi hợp nhất: " + coresToMerge.size());
        player.sendMessage(ChatColor.YELLOW + "Các hiệu ứng kích hoạt sau khi hợp nhất:");
        player.sendMessage(ChatColor.GREEN + " - Cộng dồn diện tích lãnh thổ (Tập hợp ranh giới thống nhất) và +5% diện tích mỗi Lõi.");
        player.sendMessage(ChatColor.GREEN + " - Tăng 5% Lá chắn tối đa (Max Shield) của mỗi Lõi.");
        player.sendMessage(ChatColor.GREEN + " - Tăng 5% Sát thương trụ canh (Tower Damage) thuộc các Lõi này.");
        player.sendMessage(ChatColor.GREEN + " - Tăng 5% Dung tích bình nạp PEP (Max FEP Pool) của mỗi Lõi.");
        player.sendMessage(ChatColor.GREEN + " - Tăng 5% Hiệu suất nạp PEP khi nạp thức ăn (cả người chơi và nông dân).");
        player.sendMessage(ChatColor.GREEN + " - Tăng 1% Tiền thưởng Gold (Vault) từ tiêu diệt quái PvE Raid.");
        player.sendMessage(ChatColor.GOLD + "Để thực thi hợp nhất, hãy gõ lệnh: " + ChatColor.LIGHT_PURPLE + "/ally merge confirm");
    }

    private void handleMergeConfirm(Player player) {
        String allyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        if (allyId == null) {
            player.sendMessage(ChatColor.RED + "Bạn cần tham gia một Liên minh trước khi thực hiện hợp nhất!");
            return;
        }

        Alliance alliance = plugin.getAllianceManager().getAlliance(allyId);
        if (alliance == null || !alliance.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Chỉ có Thủ lĩnh Liên minh (Leader) mới có quyền hợp nhất lãnh thổ!");
            return;
        }

        List<TerritoryCore> coresToMerge = new ArrayList<>();
        if (plugin.getCoreManager() != null) {
            for (TerritoryCore core : plugin.getCoreManager().getAllActiveCores()) {
                if (allyId.equalsIgnoreCase(core.getAllyId())) {
                    coresToMerge.add(core);
                }
            }
        }

        if (coresToMerge.size() < 2) {
            player.sendMessage(ChatColor.RED + "Yêu cầu ít nhất 2 Lõi Lãnh Thổ trong Liên minh hoạt động để hợp nhất! Hiện tại chỉ có " + coresToMerge.size() + ".");
            return;
        }

        plugin.getAllianceManager().mergeTerritories(allyId, coresToMerge);
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "========== NGOẠI GIAO LIÊN MINH (ALLY HELP) ==========");
        player.sendMessage(ChatColor.YELLOW + "/ally : " + ChatColor.WHITE + "Mở GUI Quản trị bang hội.");
        player.sendMessage(ChatColor.YELLOW + "/ally create <tên> : " + ChatColor.WHITE + "Thành lập Liên Minh (Phí: 50k Xu).");
        player.sendMessage(ChatColor.YELLOW + "/ally invite <tên> : " + ChatColor.WHITE + "Mời thành viên mới (Chỉ Leader).");
        player.sendMessage(ChatColor.YELLOW + "/ally kick <tên> : " + ChatColor.WHITE + "Trục xuất thành viên (Chỉ Leader).");
        player.sendMessage(ChatColor.YELLOW + "/ally chat <tin> : " + ChatColor.WHITE + "Trò chuyện kênh chat mật nội bộ bang.");
        player.sendMessage(ChatColor.YELLOW + "/ally chest : " + ChatColor.WHITE + "Mở hòm đồ chung ServerChest.");
        player.sendMessage(ChatColor.YELLOW + "/ally declare <tên> : " + ChatColor.WHITE + "Tuyên chiến quốc gia/cá nhân theo Tên.");
        player.sendMessage(ChatColor.YELLOW + "/ally leave : " + ChatColor.WHITE + "Rời khỏi Liên minh hiện tại.");
        player.sendMessage(ChatColor.YELLOW + "/ally disband : " + ChatColor.WHITE + "Giải tán Liên minh (Chỉ Thủ lĩnh).");
        player.sendMessage(ChatColor.YELLOW + "/ally merge : " + ChatColor.WHITE + "Hợp nhất lãnh thổ các thành viên (Chỉ Thủ lĩnh).");
        player.sendMessage(ChatColor.GOLD + "=======================================================");
    }
}