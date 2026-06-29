package com.truongcm.territorydefense.base.ui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

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
}
