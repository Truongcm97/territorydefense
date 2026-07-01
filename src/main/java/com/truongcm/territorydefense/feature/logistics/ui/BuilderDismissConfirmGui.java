package com.truongcm.territorydefense.feature.logistics.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import com.truongcm.territorydefense.feature.logistics.NPCBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BuilderDismissConfirmGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final NPCBuilder builder;
    private final TerritoryCore core;
    private final NamespacedKey actionKey;
    private final double refundMoney;
    private final int refundShards;

    public BuilderDismissConfirmGui(TerritoryDefense plugin, NPCBuilder builder, TerritoryCore core) {
        this.plugin = plugin;
        this.builder = builder;
        this.core = core;
        this.actionKey = PDCKeys.GUI_ACTION;

        int level = core.getBuilderLevel();
        double totalSpentMoney = 150000.0; // Tiền gốc thuê
        int totalSpentShards = 15;

        for (int i = 1; i < level; i++) {
            totalSpentMoney += getUpgradeCostMoney(i);
            totalSpentShards += getUpgradeCostShards(i);
        }

        this.refundMoney = totalSpentMoney * 0.5;
        this.refundShards = (int) Math.round(totalSpentShards * 0.5);
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
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 27, ChatColor.RED + "Xác nhận sa thải Thợ Xây");

        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(ChatColor.GRAY + "Sa thải Thợ xây và nhận hoàn trả 50%:");
        confirmLore.add(ChatColor.GOLD + " - Hoàn trả Xu: +" + String.format("%,.0f", refundMoney) + " Xu");
        confirmLore.add(ChatColor.AQUA + " - Hoàn trả Shards: +" + refundShards + " Shards");
        confirmLore.add(" ");
        confirmLore.add(ChatColor.RED + "⚠ Lưu ý: NPC này sẽ biến mất vĩnh viễn!");
        confirmLore.add(ChatColor.GREEN + "➔ Click để đồng ý!");

        inv.setItem(11, createGuiItem(Material.GREEN_WOOL, ChatColor.GREEN + "" + ChatColor.BOLD + "ĐỒNG Ý SA THẢI", "CONFIRM", confirmLore.toArray(new String[0])));

        inv.setItem(15, createGuiItem(Material.RED_WOOL, ChatColor.RED + "" + ChatColor.BOLD + "HỦY BỎ", "CANCEL",
                ChatColor.GRAY + "Giữ lại Thợ xây NPC.",
                ChatColor.YELLOW + "➔ Click để quay lại giao diện trước."
        ));

        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.hasItemMeta()) {
            String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            if (action.equalsIgnoreCase("CONFIRM")) {
                player.closeInventory();
                
                // Hoàn tiền
                plugin.getVaultEconomy().depositPlayer(player, refundMoney);
                int currentShards = plugin.getCoreManager().getShards(core.getCoreId());
                plugin.getCoreManager().setShards(core.getCoreId(), currentShards + refundShards);

                // Sa thải khỏi manager
                plugin.getBuilderManager().getActiveBuilders().remove(core.getCoreId());

                // Xóa thực thể NPC Villager
                if (builder.getEntity() != null) {
                    builder.getEntity().remove();
                }

                // Đặt lại cấp độ thợ xây về 1 trong CSDL
                core.setBuilderLevel(1);
                plugin.getCoreManager().registerCore(core.getLocation(), core);

                player.sendMessage(ChatColor.GREEN + "[Kiến Thiết] Đã sa thải Thợ Xây NPC thành công!");
                player.sendMessage(ChatColor.GOLD + " Hoàn trả: +" + String.format("%,.0f", refundMoney) + " Xu & +" + refundShards + " Shards (cộng vào tích luỹ Lõi).");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_DEATH, 1.0f, 0.8f);
                return;
            }

            if (action.equalsIgnoreCase("CANCEL")) {
                player.closeInventory();
                player.openInventory(new BuilderUpgradeGui(plugin, builder, core).getInventory());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
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
