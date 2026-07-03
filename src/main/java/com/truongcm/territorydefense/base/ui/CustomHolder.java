package com.truongcm.territorydefense.base.ui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * Lớp GUI cơ sở. Tất cả các GUI trong game sẽ kế thừa lớp này
 * để tự xử lý hành vi click chuột của mình.
 */
public abstract class CustomHolder implements InventoryHolder {
    
    /**
     * Hàm xử lý click chuột dành riêng cho từng giao diện.
     * @param event Sự kiện click chuột
     * @param player Người chơi thực hiện click
     */
    public abstract void onClick(InventoryClickEvent event, Player player);
    
    @Override
    public abstract Inventory getInventory();

    /**
     * Helper function to build custom GUI items in a DRY fashion.
     */
    protected ItemStack createGuiItem(Material material, String name, NamespacedKey actionKey, String actionTag, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines != null && loreLines.length > 0) {
                meta.setLore(Arrays.asList(loreLines));
            }
            if (actionKey != null && actionTag != null) {
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, actionTag);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
