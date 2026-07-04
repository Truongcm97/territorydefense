package com.truongcm.territorydefense.feature.logistics.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import com.truongcm.territorydefense.feature.core.ui.CoreGui;
import com.truongcm.territorydefense.feature.logistics.NPCBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NPCBuilderGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final NamespacedKey actionKey;

    public NPCBuilderGui(TerritoryDefense plugin, TerritoryCore core) {
        this.plugin = plugin;
        this.core = core;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 27, ChatColor.DARK_GREEN + "⚒ Quản Lý 7Gao");

        // Điền nền kính xám
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        NPCBuilder builder = plugin.getBuilderManager().getOrCreateBuilder(core.getCoreId());
        int level = core.getBuilderLevel();
        int speed = switch (level) {
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 15;
            case 5 -> 25;
            default -> 2;
        };

        boolean isRebuilding = builder != null && builder.isRebuilding();

        // Slot 11: Thông tin chỉ số hiện tại
        inv.setItem(11, createGuiItem(Material.BOOK, ChatColor.GREEN + "Thông Tin 7Gao", "NONE",
                ChatColor.GRAY + "Cấp độ hiện tại: " + ChatColor.GOLD + "Cấp " + level + " / 5",
                ChatColor.GRAY + "Tốc độ đặt khối: " + ChatColor.AQUA + speed + " block/giây",
                ChatColor.GRAY + "Trạng thái: " + (isRebuilding ? ChatColor.GREEN + "Đang thi công tái thiết" : ChatColor.YELLOW + "Rảnh rỗi"),
                "",
                ChatColor.GRAY + "Tính năng đặc biệt: Tự động lơ lửng, noclip xuyên",
                ChatColor.GRAY + "vật cản để sửa chữa móng và phục hồi lãnh thổ."
        ));

        // Slot 13: Nút nâng cấp
        if (level >= 5) {
            inv.setItem(13, createGuiItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "ĐÃ ĐẠT CẤP TỐI ĐA (CẤP 5)", "NONE",
                    ChatColor.GRAY + "7Gao của bạn đã đạt giới hạn cao nhất."
            ));
        } else {
            int nextLevel = level + 1;
            double moneyCost = getUpgradeCostMoney(level);
            int shardCost = getUpgradeCostShards(level);
            int nextSpeed = switch (nextLevel) {
                case 2 -> 5;
                case 3 -> 10;
                case 4 -> 15;
                case 5 -> 25;
                default -> 2;
            };

            List<String> upLore = new ArrayList<>();
            upLore.add(ChatColor.GRAY + "Tốc độ mới: " + ChatColor.GREEN + nextSpeed + " block/giây");
            upLore.add("");
            upLore.add(ChatColor.GRAY + "Chi phí nâng cấp:");
            upLore.add(ChatColor.GRAY + " - Xu: " + ChatColor.GOLD + String.format("%,.0f", moneyCost) + " Xu");
            upLore.add(ChatColor.GRAY + " - Shards: " + ChatColor.AQUA + shardCost + " Shards");
            upLore.add("");
            if (core.getLevel() < nextLevel) {
                upLore.add(ChatColor.RED + "❌ Yêu cầu cấp độ Lõi phải đạt Cấp " + nextLevel + "!");
            } else {
                upLore.add(ChatColor.YELLOW + "➔ Click để tiến hành nâng cấp!");
            }

            inv.setItem(13, createGuiItem(Material.GOLD_BLOCK, ChatColor.GOLD + "NÂNG CẤP 7GAO (LÊN CẤP " + nextLevel + ")", "UPGRADE_BUILDER", upLore.toArray(new String[0])));
        }

        // Slot 15: NÚT DỪNG TÁI THIẾT KHẨN CẤP
        if (isRebuilding) {
            inv.setItem(15, createGuiItem(Material.REDSTONE_BLOCK, ChatColor.RED + "" + ChatColor.BOLD + "DỪNG TÁI THIẾT KHẨN CẤP", "STOP_REBUILD",
                    ChatColor.GRAY + "Nhấp chuột để yêu cầu 7Gao",
                    ChatColor.GRAY + "dừng ngay lập tức tiến trình thi công.",
                    ChatColor.GRAY + "7Gao sẽ quay về Lõi.",
                    "",
                    ChatColor.YELLOW + "➔ Click để DỪNG sửa chữa khẩn cấp!"
            ));
        } else {
            inv.setItem(15, createGuiItem(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + "Không trong quá trình xây dựng", "NONE"));
        }

        // Slot 22: Quay lại menu Lõi (Logistics Tab)
        inv.setItem(22, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Quay Lại Lõi", "BACK"));

        return inv;
    }

    private double getUpgradeCostMoney(int fromLevel) {
        return switch (fromLevel) {
            case 1 -> 100000.0;
            case 2 -> 200000.0;
            case 3 -> 400000.0;
            case 4 -> 800000.0;
            default -> 9999999.0;
        };
    }

    private int getUpgradeCostShards(int fromLevel) {
        return switch (fromLevel) {
            case 1 -> 10;
            case 2 -> 20;
            case 3 -> 30;
            case 4 -> 50;
            default -> 99999;
        };
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.hasItemMeta()) {
            String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            if (action.equalsIgnoreCase("BACK")) {
                player.closeInventory();
                player.openInventory(new CoreGui(plugin, core, CoreGui.CoreTab.LOGISTICS).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("STOP_REBUILD")) {
                NPCBuilder builder = plugin.getBuilderManager().getOrCreateBuilder(core.getCoreId());
                if (builder != null && builder.isRebuilding()) {
                    builder.cancelRebuild();
                    player.sendMessage(ChatColor.RED + "[Kiến Thiết] Đã dừng khẩn cấp tiến trình sửa chữa tái thiết của 7Gao!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    player.openInventory(getInventory()); // Reload GUI
                }
                return;
            }

            if (action.equalsIgnoreCase("UPGRADE_BUILDER")) {
                int currentLevel = core.getBuilderLevel();
                if (currentLevel >= 5) return;

                int nextLevel = currentLevel + 1;
                if (nextLevel > core.getLevel()) {
                    player.sendMessage(ChatColor.RED + "[Kiến Thiết] Cấp độ 7Gao không thể vượt cấp độ của Lõi Lãnh Thổ (Lõi hiện tại Cấp " + core.getLevel() + ")!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                double costMoney = getUpgradeCostMoney(currentLevel);
                int costShards = getUpgradeCostShards(currentLevel);

                if (plugin.getVaultEconomy().getBalance(player) < costMoney) {
                    player.sendMessage(ChatColor.RED + "[Kiến Thiết] Bạn không đủ Xu để nâng cấp! Cần: " + String.format("%,.0f", costMoney) + " Xu.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                int currentShards = plugin.getCoreManager().getShards(core.getCoreId());
                if (currentShards < costShards) {
                    player.sendMessage(ChatColor.RED + "[Kiến Thiết] Bạn không đủ Shards tích lũy! Cần: " + costShards + " Shards.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                // Khấu trừ tài sản
                plugin.getVaultEconomy().withdrawPlayer(player, costMoney);
                plugin.getCoreManager().setShards(core.getCoreId(), currentShards - costShards);

                // Nâng cấp
                core.setBuilderLevel(nextLevel);
                core.markDirty();
                plugin.getCoreManager().registerCore(core.getLocation(), core);

                // Áp dụng thuộc tính thợ xây
                NPCBuilder builder = plugin.getBuilderManager().getOrCreateBuilder(core.getCoreId());
                if (builder != null) {
                    builder.applyAttributes();
                }

                player.sendMessage(ChatColor.GREEN + "[Kiến Thiết] Chúc mừng! Đã nâng cấp 7Gao lên Cấp " + nextLevel + " thành công!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                player.openInventory(getInventory());
            }
        }
    }

    private ItemStack createGuiItem(Material material, String name, String actionTag, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) {
                meta.setLore(Arrays.asList(loreLines));
            }
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, actionTag);
            item.setItemMeta(meta);
        }
        return item;
    }
}
