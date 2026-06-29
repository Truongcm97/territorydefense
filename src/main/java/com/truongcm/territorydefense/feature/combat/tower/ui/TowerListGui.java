package com.truongcm.territorydefense.feature.combat.tower.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import com.truongcm.territorydefense.feature.core.ui.CoreGui;
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

public class TowerListGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final Player player;
    private final NamespacedKey actionKey;

    public TowerListGui(TerritoryDefense plugin, TerritoryCore core, Player player) {
        this.plugin = plugin;
        this.core = core;
        this.player = player;
        this.actionKey = PDCKeys.GUI_ACTION != null ? PDCKeys.GUI_ACTION : new NamespacedKey(plugin, "gui_action");
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 27, ChatColor.DARK_BLUE + "Tháp Canh Lõi Lãnh Thổ");

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        List<Tower> towers = plugin.getTowerManager().getTowersForCore(core.getCoreId());
        int slot = 10;
        for (Tower tower : towers) {
            if (slot > 16) break; // Chỉ hiển thị tối đa 7 tháp canh hàng giữa

            Material blockMat = switch (tower.getType()) {
                case ARROW -> Material.SKELETON_SKULL;
                case LIGHTNING -> Material.CREEPER_HEAD;
                case FIRE -> Material.WITHER_SKELETON_SKULL;
                case FROST -> Material.ZOMBIE_HEAD;
                case HEALING -> Material.PIGLIN_HEAD;
                case SPELL -> Material.PLAYER_HEAD;
                case ARTILLERY -> Material.DRAGON_HEAD;
            };

            ItemStack item = new ItemStack(blockMat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + tower.getDisplayName() + ChatColor.GREEN + " [Lv." + tower.getLevel() + "]");
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Tọa độ: " + ChatColor.WHITE + 
                                tower.getLocation().getBlockX() + ", " +
                                tower.getLocation().getBlockY() + ", " +
                                tower.getLocation().getBlockZ(),
                        ChatColor.GREEN + "👉 Click chuột để mở nâng cấp."
                ));
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "TOWER_" + 
                        tower.getLocation().getBlockX() + "_" + 
                        tower.getLocation().getBlockY() + "_" + 
                        tower.getLocation().getBlockZ());
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
            slot++;
        }

        inv.setItem(22, createGuiItem(Material.BARRIER, ChatColor.RED + "Quay Lại", "BACK"));

        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.hasItemMeta()) {
            String action = clickedItem.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            if (action.equalsIgnoreCase("BACK")) {
                player.closeInventory();
                player.openInventory(new CoreGui(plugin, core, CoreGui.CoreTab.COMBAT).getInventory());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.2f);
                return;
            }

            if (action.startsWith("TOWER_")) {
                String[] parts = action.split("_");
                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    
                    org.bukkit.Location loc = new org.bukkit.Location(core.getLocation().getWorld(), x, y, z);
                    Tower tower = plugin.getTowerManager().getActiveTowers().get(loc);
                    if (tower != null) {
                        player.closeInventory();
                        player.openInventory(new TowerUpgradeGui(plugin, tower, core).getInventory());
                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 1.0f, 1.2f);
                    } else {
                        player.sendMessage(ChatColor.RED + "[Lỗi] Tháp canh không tồn tại hoặc đã bị phá hủy!");
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
