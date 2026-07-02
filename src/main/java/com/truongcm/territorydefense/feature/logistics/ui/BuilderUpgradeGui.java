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

public class BuilderUpgradeGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final NPCBuilder builder;
    private final NamespacedKey actionKey;

    public BuilderUpgradeGui(TerritoryDefense plugin, NPCBuilder builder, TerritoryCore core) {
        this.plugin = plugin;
        this.builder = builder;
        this.core = core;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 27, ChatColor.GOLD + "Quản Lý Thợ Xây Dựng (Mason)");

        // 1. Phủ kính xám nền rương
        ItemStack backgroundPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, backgroundPane);
        }

        // Slot 11: Thông tin chỉ số hiện tại
        inv.setItem(11, getBuilderInfoItem());

        // Slot 13: Nút Nâng Cấp Thợ xây
        inv.setItem(13, getUpgradeButtonItem());

        // Slot 15: Nút dừng sửa chữa khẩn cấp thợ xây
        inv.setItem(15, getStopRebuildButtonItem());

        // Slot 22: Quay lại menu chính
        inv.setItem(22, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Quay Lại Lõi", "BACK"));

        // Slot 26: Thoát ra
        inv.setItem(26, createGuiItem(Material.BARRIER, ChatColor.RED + "Thoát ra", "CLOSE"));

        return inv;
    }

    private ItemStack getBuilderInfoItem() {
        int level = core.getBuilderLevel();
        int speed = switch (level) {
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 15;
            case 5 -> 25;
            default -> 2;
        };

        return createGuiItem(Material.BOOK, ChatColor.GREEN + "Thông Tin Thợ Xây NPC", "INFO",
                ChatColor.GRAY + "Cấp độ hiện tại: " + ChatColor.GOLD + "Cấp " + level,
                ChatColor.GRAY + "Tốc độ xây dựng: " + ChatColor.AQUA + speed + " block/giây",
                ChatColor.GRAY + "Trạng thái hoạt động: " + (builder.isRebuilding() ? ChatColor.GREEN + "Đang xây dựng" : ChatColor.YELLOW + "Rảnh rỗi"),
                ChatColor.GRAY + "Khả năng: Bay lượn noclip xuyên vật cản để tái thiết."
        );
    }

    private ItemStack getUpgradeButtonItem() {
        int currentLevel = core.getBuilderLevel();
        if (currentLevel >= 5) {
            return createGuiItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "ĐÃ ĐẠT CẤP TỐI ĐA (CẤP 5)", "MAXED",
                    ChatColor.GRAY + "Thợ xây của bạn đã được nâng cấp lên mức giới hạn cao nhất."
            );
        }

        int nextLevel = currentLevel + 1;
        if (nextLevel > core.getLevel()) {
            return createGuiItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "YÊU CẦU CẤP ĐỘ LÕI (CẤP " + nextLevel + ")", "NONE",
                    ChatColor.GRAY + "Cấp độ Thợ xây không thể vượt quá cấp độ của Lõi Lãnh Thổ.",
                    ChatColor.GRAY + "Cấp độ Lõi hiện tại: Cấp " + core.getLevel()
            );
        }
        double moneyCost = getUpgradeCostMoney(currentLevel);
        int shardCost = getUpgradeCostShards(currentLevel);
        int nextSpeed = switch (nextLevel) {
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 15;
            case 5 -> 25;
            default -> 2;
        };

        return createGuiItem(Material.GOLD_BLOCK, ChatColor.GOLD + "" + ChatColor.BOLD + "NÂNG CẤP THỢ XÂY (LÊN CẤP " + nextLevel + ")", "UPGRADE_BUILDER",
                ChatColor.GRAY + "Nâng cấp giúp gia tăng tốc độ đặt block.",
                ChatColor.GRAY + "Tốc độ mới: " + ChatColor.GREEN + nextSpeed + " block/giây (Tăng tốc vượt bậc)",
                " ",
                ChatColor.GRAY + "Chi phí nâng cấp:",
                ChatColor.GRAY + " - Tiền xu: " + ChatColor.GOLD + String.format("%,.0f", moneyCost) + " Xu",
                ChatColor.GRAY + " - Shards: " + ChatColor.AQUA + shardCost + " Shards",
                " ",
                ChatColor.YELLOW + "➔ Click để tiến hành nâng cấp!"
        );
    }

    private ItemStack getStopRebuildButtonItem() {
        if (builder.isRebuilding()) {
            return createGuiItem(Material.REDSTONE_BLOCK, ChatColor.RED + "" + ChatColor.BOLD + "DỪNG TÁI THIẾT KHẨN CẤP", "STOP_REBUILD",
                    ChatColor.GRAY + "Dừng ngay lập tức tiến trình tái thiết.",
                    ChatColor.GRAY + "Thợ xây sẽ quay về Lõi và dừng đặt khối.",
                    " ",
                    ChatColor.YELLOW + "➔ Click để DỪNG tiến trình xây dựng!"
            );
        } else {
            return createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        }
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

            if (action.equalsIgnoreCase("CLOSE")) {
                player.closeInventory();
                return;
            }

            if (action.equalsIgnoreCase("BACK")) {
                player.closeInventory();
                player.openInventory(new CoreGui(plugin, core, CoreGui.CoreTab.LOGISTICS).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("UPGRADE_BUILDER")) {
                int currentLevel = core.getBuilderLevel();
                if (currentLevel >= 5) return;

                int nextLevel = currentLevel + 1;
                if (nextLevel > core.getLevel()) {
                    player.sendMessage(ChatColor.RED + "[Kiến Thiết] Cấp độ Thợ xây không thể cao hơn cấp độ Lõi Lãnh Thổ (Lõi hiện tại Cấp " + core.getLevel() + ")!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                double costMoney = getUpgradeCostMoney(currentLevel);
                int costShards = getUpgradeCostShards(currentLevel);

                // Kiểm tra tài chính
                if (plugin.getVaultEconomy().getBalance(player) < costMoney) {
                    player.sendMessage(ChatColor.RED + "Bạn không đủ Xu để nâng cấp Thợ Xây! Cần: " + String.format("%,.0f", costMoney) + " Xu.");
                    return;
                }

                int currentShards = plugin.getCoreManager().getShards(core.getCoreId());
                if (currentShards < costShards) {
                    player.sendMessage(ChatColor.RED + "Lõi của bạn không đủ Shards tích luỹ! Cần: " + costShards + " Shards.");
                    return;
                }

                // Trừ tiền
                plugin.getVaultEconomy().withdrawPlayer(player, costMoney);
                plugin.getCoreManager().setShards(core.getCoreId(), currentShards - costShards);

                // Tăng cấp độ
                core.setBuilderLevel(currentLevel + 1);
                plugin.getCoreManager().registerCore(core.getLocation(), core);

                // Cập nhật tên hiển thị của NPC Builder
                builder.applyAttributes();

                player.sendMessage(ChatColor.GREEN + "[Kiến Thiết] Chúc mừng! Đã nâng cấp Thợ Xây NPC lên Cấp " + core.getBuilderLevel() + " thành công!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                player.openInventory(getInventory()); // Reload GUI
                return;
            }

            if (action.equalsIgnoreCase("STOP_REBUILD")) {
                builder.cancelRebuild();
                player.sendMessage(ChatColor.RED + "[Kiến Thiết] Đã dừng khẩn cấp tiến trình tái thiết của Thợ Xây!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                player.closeInventory();
                return;
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
