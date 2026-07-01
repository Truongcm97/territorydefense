package com.truongcm.territorydefense.feature.logistics.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
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

import java.util.Arrays;

public class BlueprintPriceSelectGui extends CustomHolder {
    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final int slotIndex;
    private double currentPrice;
    private final NamespacedKey actionKey;

    public BlueprintPriceSelectGui(TerritoryDefense plugin, TerritoryCore core, int slotIndex, double initialPrice) {
        this.plugin = plugin;
        this.core = core;
        this.slotIndex = slotIndex;
        this.currentPrice = initialPrice <= 0 ? 100000.0 : initialPrice;
        this.actionKey = PDCKeys.GUI_ACTION != null ? PDCKeys.GUI_ACTION : new NamespacedKey(plugin, "gui_action");
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 27, ChatColor.BLUE + "Thiết Lập Giá Bản Vẽ");

        // Fill background
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        // Adjust price buttons
        inv.setItem(10, createGuiItem(Material.RED_DYE, ChatColor.RED + "-1,000,000 Xu", "SUB_1M"));
        inv.setItem(11, createGuiItem(Material.ORANGE_DYE, ChatColor.GOLD + "-100,000 Xu", "SUB_100K"));
        inv.setItem(12, createGuiItem(Material.YELLOW_DYE, ChatColor.YELLOW + "-10,000 Xu", "SUB_10K"));

        // Current price display
        inv.setItem(13, createGuiItem(Material.GOLD_INGOT, ChatColor.GREEN + "Giá Đang Chọn: " + ChatColor.GOLD + String.format("%,.0f", currentPrice) + " Xu", "NONE",
                ChatColor.GRAY + "Bản vẽ: " + ChatColor.LIGHT_PURPLE + core.getBlueprintNames().get(slotIndex)
        ));

        inv.setItem(14, createGuiItem(Material.LIME_DYE, ChatColor.GREEN + "+10,000 Xu", "ADD_10K"));
        inv.setItem(15, createGuiItem(Material.GREEN_DYE, ChatColor.DARK_GREEN + "+100,000 Xu", "ADD_100K"));
        inv.setItem(16, createGuiItem(Material.EMERALD, ChatColor.AQUA + "+1,000,000 Xu", "ADD_1M"));

        // Confirm / Chat Input / Cancel
        inv.setItem(21, createGuiItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "XÁC NHẬN BÁN", "CONFIRM"));
        inv.setItem(22, createGuiItem(Material.OAK_SIGN, ChatColor.GOLD + "" + ChatColor.BOLD + "NHẬP GIÁ QUA CHAT", "CHAT_INPUT",
                ChatColor.GRAY + "Nhấp để tự nhập mức giá tùy chỉnh của bạn qua khung chat."
        ));
        inv.setItem(23, createGuiItem(Material.REDSTONE_BLOCK, ChatColor.RED + "" + ChatColor.BOLD + "HỦY BỎ", "CANCEL"));

        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.hasItemMeta()) {
            String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            if (action.equalsIgnoreCase("CANCEL")) {
                player.closeInventory();
                player.openInventory(new BlueprintSelectSellSlotGui(plugin, core).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("CONFIRM")) {
                core.getBlueprintSellingStatus().set(slotIndex, true);
                core.getBlueprintPrices().set(slotIndex, currentPrice);
                plugin.getCoreManager().registerCore(core.getLocation(), core);
                player.sendMessage(ChatColor.GREEN + "[Cửa Hàng] Đã bắt đầu bày bán bản vẽ \"" + core.getBlueprintNames().get(slotIndex) + "\" với giá " + String.format("%,.0f", currentPrice) + " Xu!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.closeInventory();
                player.openInventory(new BlueprintSelectSellSlotGui(plugin, core).getInventory());
                return;
            }

            if (action.equalsIgnoreCase("CHAT_INPUT")) {
                player.closeInventory();
                BlueprintInputListener.registerSell(player.getUniqueId(), slotIndex);
                player.sendMessage(ChatColor.GREEN + "[Cửa Hàng] Hãy nhập GIÁ BÁN cho bản vẽ \"" + core.getBlueprintNames().get(slotIndex) + "\" vào khung chat (hoặc gõ 'cancel'/'huy' để hủy bỏ).");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                return;
            }

            boolean changed = false;
            if (action.equalsIgnoreCase("SUB_1M")) { currentPrice = Math.max(0, currentPrice - 1000000); changed = true; }
            if (action.equalsIgnoreCase("SUB_100K")) { currentPrice = Math.max(0, currentPrice - 100000); changed = true; }
            if (action.equalsIgnoreCase("SUB_10K")) { currentPrice = Math.max(0, currentPrice - 10000); changed = true; }
            if (action.equalsIgnoreCase("ADD_10K")) { currentPrice += 10000; changed = true; }
            if (action.equalsIgnoreCase("ADD_100K")) { currentPrice += 100000; changed = true; }
            if (action.equalsIgnoreCase("ADD_1M")) { currentPrice += 1000000; changed = true; }

            if (changed) {
                player.openInventory(getInventory());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
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
