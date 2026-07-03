package com.truongcm.territorydefense.base.ui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Bộ định tuyến sự kiện click chuột duy nhất cho toàn bộ GUI.
 * Không chứa logic nghiệp vụ, chỉ chuyển tiếp click tới CustomHolder.
 * Đã tích hợp chống trộm/dupe vật phẩm qua kéo thả (Drag Event).
 */
public class GuiFramework implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        
        // Nếu Inventory có Holder kế thừa từ CustomHolder của chúng ta
        if (topInventory.getHolder() instanceof CustomHolder customHolder) {
            event.setCancelled(true); // Ngăn lấy đồ ra khỏi GUI theo mặc định
            
            // Chỉ bắt các click diễn ra bên trong GUI (bỏ qua hòm đồ cá nhân ở dưới)
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(topInventory)) {
                if (event.getWhoClicked() instanceof Player player) {
                    customHolder.onClick(event, player);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        // Chặn đứng hoàn toàn hành vi kéo thả vật phẩm trong giao diện CustomHolder
        if (topInventory.getHolder() instanceof CustomHolder) {
            event.setCancelled(true);
        }
    }
}

