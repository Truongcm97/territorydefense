package com.truongcm.territorydefense.feature.core;

import com.truongcm.territorydefense.TerritoryDefense;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * GỢI Ý TỰ ĐỘNG LỆNH LÃNH THỔ (TERRITORY TAB COMPLETER)
 * Tự động hiển thị danh sách các tham số gợi ý cho lệnh /territory (/t).
 * Hỗ trợ các phân cấp lệnh sâu như: /territory boundary toggle.
 */
public class TerritoryTabCompleter implements TabCompleter {

    private final TerritoryDefense plugin;
    private final List<String> subCommands = Arrays.asList(
            "boundary", "accepttax", "migrate", "help", "getcore", "getstarter", "recall",
            "copydesign", "saochep", "shareblueprint", "chiase", "sellblueprint", "banbanve"
    );

    public TerritoryTabCompleter(TerritoryDefense plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        // Gợi ý cho đối số thứ nhất
        if (args.length == 1) {
            List<String> commands = new ArrayList<>(subCommands);

            // Chỉ hiển thị lệnh Admin nếu người chơi có quyền
            if (sender.hasPermission("territorydefense.admin")) {
                commands.add("resetstarter");
                commands.add("resetdifficulty");
                commands.add("rebuildholograms");
                commands.add("rebuildhlg");
                commands.add("save");
            }

            StringUtil.copyPartialMatches(args[0], commands, completions);
            Collections.sort(completions);
            return completions;
        }

        // Gợi ý cho đối số thứ hai
        if (args.length == 2) {
            String firstArg = args[0].toLowerCase();
            if ("boundary".equals(firstArg)) {
                StringUtil.copyPartialMatches(args[1], Collections.singletonList("toggle"), completions);
            } else if ("shareblueprint".equals(firstArg) || "chiase".equals(firstArg)) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("on", "off"), completions);
            } else if ("copydesign".equals(firstArg) || "saochep".equals(firstArg)) {
                List<String> slots = new ArrayList<>();
                for (int i = 1; i <= 54; i++) {
                    slots.add(String.valueOf(i));
                }
                StringUtil.copyPartialMatches(args[1], slots, completions);
            } else if (("resetstarter".equals(firstArg) || "resetdifficulty".equals(firstArg) || "recall".equals(firstArg)) 
                    && sender.hasPermission("territorydefense.admin")) {
                // Gợi ý tên người chơi đang online
                for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        }

        return completions;
    }
}