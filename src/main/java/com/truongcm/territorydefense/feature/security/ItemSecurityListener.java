package com.truongcm.territorydefense.feature.security;

import com.truongcm.territorydefense.feature.core.PDCKeys;
import org.bukkit.ChatColor;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class ItemSecurityListener implements Listener {

    private boolean isCoreItem(ItemStack item) {
        return item != null && item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isCoreItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Lõi Lãnh Thổ không thể bị vứt bỏ!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Chặn cầm Lõi bỏ vào rương/kho
        if (isCoreItem(event.getCursor())) {
            if (event.getClickedInventory() != null && event.getClickedInventory().getType() != InventoryType.PLAYER) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(ChatColor.RED + "Lõi Lãnh Thổ không thể cất vào kho!");
            }
        }

        // Chặn lấy Lõi từ rương/kho (phòng trường hợp người chơi lách luật)
        if (isCoreItem(event.getCurrentItem())) {
            if (event.getClickedInventory() != null && event.getClickedInventory().getType() != InventoryType.PLAYER) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(ChatColor.RED + "Lõi Lãnh Thổ không thể di chuyển!");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item) {
            if (isCoreItem(item.getItemStack())) {
                event.setCancelled(true);
            }
        }
    }
}