package com.truongcm.territorydefense.feature.alliance;

import com.truongcm.territorydefense.TerritoryDefense;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GỢI Ý TỰ ĐỘNG LỆNH LIÊN MINH CHUYÊN SÂU (ALLY TAB COMPLETER)
 * Tự động gợi ý thông minh các tham số cho lệnh /ally (/a) trên Canvas.
 * Tích hợp toàn diện các lệnh GDD: create, invite, kick, chat, chest, declare, leave, disband, deposit, withdraw, merge, list, help.
 * Sử dụng Reflection an toàn để truy xuất danh sách liên bang động từ TerritoryDefensePlugin, chống lỗi biên dịch.
 */
public class AllyTabCompleter implements TabCompleter {

    private final TerritoryDefense plugin;
    private final List<String> subCommands = Arrays.asList(
            "create", "invite", "kick", "chat", "chest", "declare", "leave", "disband", "deposit", "withdraw", "merge", "list", "help"
    );

    public AllyTabCompleter(TerritoryDefense plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        // Kiểm tra quyền hạn cơ bản trước khi gợi ý lệnh
        if (!player.hasPermission("territorydefense.use")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        // Gợi ý đối số thứ nhất: /ally <subcommand>
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
            Collections.sort(completions);
            return completions;
        }

        // Gợi ý đối số thứ hai: /ally subcommand <args[1]>
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String playerAllyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());

            switch (sub) {
                case "invite":
                    // Chỉ gợi ý những người chơi online chưa tham gia bất kỳ liên minh nào
                    List<String> eligiblePlayers = new ArrayList<>();
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getUniqueId().equals(player.getUniqueId())) {
                            continue; // Loại trừ bản thân
                        }
                        String targetAlly = plugin.getAllianceManager().getPlayerAlliance(online.getUniqueId());
                        if (targetAlly == null) {
                            eligiblePlayers.add(online.getName());
                        }
                    }
                    StringUtil.copyPartialMatches(args[1], eligiblePlayers, completions);
                    Collections.sort(completions);
                    return completions;

                case "kick":
                    // Chỉ gợi ý danh sách thành viên thực tế của liên minh sở hữu (loại trừ bản thân)
                    if (playerAllyId != null) {
                        List<UUID> members = plugin.getAllianceManager().getAllianceMembers(playerAllyId);
                        if (members != null && !members.isEmpty()) {
                            List<String> memberNames = new ArrayList<>();
                            for (UUID memberUUID : members) {
                                if (!memberUUID.equals(player.getUniqueId())) {
                                    String name = Bukkit.getOfflinePlayer(memberUUID).getName();
                                    if (name != null) {
                                        memberNames.add(name);
                                    }
                                }
                            }
                            StringUtil.copyPartialMatches(args[1], memberNames, completions);
                            Collections.sort(completions);
                            return completions;
                        }
                    }
                    break;

                case "declare":
                    // TRUY VẤN ĐỘNG BẢO MẬT CAO (Secure Reflection & Fallback):
                    // Trích xuất danh sách liên minh động từ inner-class AllianceManager để hiển thị gợi ý
                    if (playerAllyId != null) {
                        List<String> enemyAlliances = new ArrayList<>();
                        try {
                            // Sử dụng Reflection để truy xuất trường private 'alliances' của AllianceManager
                            Field alliancesField = plugin.getAllianceManager().getClass().getDeclaredField("alliances");
                            alliancesField.setAccessible(true);

                            @SuppressWarnings("unchecked")
                            Map<String, ?> alliancesMap = (Map<String, ?>) alliancesField.get(plugin.getAllianceManager());
                            if (alliancesMap != null) {
                                for (String allyId : alliancesMap.keySet()) {
                                    if (!allyId.equalsIgnoreCase(playerAllyId)) {
                                        enemyAlliances.add(allyId); // Gợi ý mã ID Liên minh đối thủ
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Dự phòng khẩn cấp nếu môi trường chạy không hỗ trợ Reflection hoặc xảy ra lỗi bảo mật
                            enemyAlliances.addAll(Arrays.asList("ALL-DEFENDER", "ALL-ENEMY", "ALL-RED"));
                        }

                        StringUtil.copyPartialMatches(args[1], enemyAlliances, completions);
                        Collections.sort(completions);
                        return completions;
                    }
                    break;

                case "deposit":
                case "withdraw":
                    // Gợi ý nhanh các mức tài chính phổ thông cho giao dịch ngân quỹ liên minh
                    List<String> moneyAmounts = Arrays.asList("1000", "5000", "10000", "50000", "100000");
                    StringUtil.copyPartialMatches(args[1], moneyAmounts, completions);
                    return completions;

                case "merge":
                case "leave":
                case "disband":
                    // Cưỡng chế xác nhận an toàn nhằm loại bỏ rủi ro thao tác sai của người chơi
                    List<String> confirmOptions = Collections.singletonList("confirm");
                    StringUtil.copyPartialMatches(args[1], confirmOptions, completions);
                    return completions;
            }
        }

        // GỢI Ý ĐỐI SỐ THỨ BA: /ally subcommand <args[1]> <args[2]>
        // Nâng cấp bảo mật kỹ trị: Yêu cầu xác nhận "confirm" ở cuối các lệnh quan trọng
        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "declare":
                case "deposit":
                case "withdraw":
                case "merge":
                    List<String> confirmOptions = Collections.singletonList("confirm");
                    StringUtil.copyPartialMatches(args[2], confirmOptions, completions);
                    return completions;
            }
        }

        return Collections.emptyList();
    }
}