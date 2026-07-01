package com.truongcm.territorydefense.feature.logistics.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import com.truongcm.territorydefense.feature.core.ui.CoreGui;
import com.truongcm.territorydefense.feature.logistics.NPCFarmer;
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

import java.util.Arrays;
import java.util.List;

public class FarmerListGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final Player player;
    private final NamespacedKey actionKey;

    public FarmerListGui(TerritoryDefense plugin, TerritoryCore core, Player player) {
        this.plugin = plugin;
        this.core = core;
        this.player = player;
        this.actionKey = PDCKeys.GUI_ACTION != null ? PDCKeys.GUI_ACTION : new NamespacedKey(plugin, "gui_action");
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 27, ChatColor.DARK_GREEN + "Nông Dân Lõi Lãnh Thổ");

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        List<NPCFarmer> farmers = plugin.getFarmerManager().getFarmersForCore(core.getCoreId());
        int slot = 10;
        for (NPCFarmer farmer : farmers) {
            if (slot > 16) break; // Chỉ hiển thị tối đa 7 nông dân hàng giữa
            if (farmer.getEntity() == null || !farmer.getEntity().isValid()) continue;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta headMeta = head.getItemMeta();
            if (headMeta != null) {
                headMeta.setDisplayName(ChatColor.YELLOW + "Nông Dân Level " + farmer.getLevel());
                headMeta.setLore(Arrays.asList(
                        ChatColor.GRAY + "ID: " + ChatColor.AQUA + farmer.getFarmerUUID().toString().substring(0, 8),
                        ChatColor.GRAY + "Tọa độ: " + ChatColor.WHITE + 
                                farmer.getEntity().getLocation().getBlockX() + ", " +
                                farmer.getEntity().getLocation().getBlockY() + ", " +
                                farmer.getEntity().getLocation().getBlockZ(),
                        ChatColor.GREEN + "👉 Click chuột để mở nâng cấp."
                ));
                headMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "FARMER_" + farmer.getFarmerUUID().toString());
                head.setItemMeta(headMeta);
            }
            inv.setItem(slot, head);
            slot++;
        }

        // Slot 22: Quay lại Lõi, Slot 26: Thoát ra
        inv.setItem(22, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Quay Lại Lõi", "BACK"));
        inv.setItem(26, createGuiItem(Material.BARRIER, ChatColor.RED + "Thoát ra", "CLOSE"));

        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.hasItemMeta()) {
            String action = clickedItem.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            if (action.equalsIgnoreCase("CLOSE")) {
                player.closeInventory();
                return;
            }

            if (action.equalsIgnoreCase("BACK")) {
                player.closeInventory();
                player.openInventory(new CoreGui(plugin, core, CoreGui.CoreTab.LOGISTICS).getInventory());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.2f);
                return;
            }

            if (action.startsWith("FARMER_")) {
                String farmerIdStr = action.substring(7);
                try {
                    java.util.UUID farmerId = java.util.UUID.fromString(farmerIdStr);
                    NPCFarmer farmer = plugin.getFarmerManager().getActiveFarmers().get(farmerId);
                    if (farmer != null && farmer.getEntity() != null && farmer.getEntity().isValid()) {
                        player.closeInventory();
                        player.openInventory(new FarmerUpgradeGui(plugin, farmer.getEntity(), core).getInventory(player));
                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 1.0f, 1.2f);
                    } else {
                        player.sendMessage(ChatColor.RED + "[Lỗi] Nông dân không khả dụng hoặc đã biến mất!");
                    }
                } catch (Exception ignored) {}
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
