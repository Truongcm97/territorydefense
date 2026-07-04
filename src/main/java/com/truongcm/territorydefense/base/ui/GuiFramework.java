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
            if (customHolder instanceof com.truongcm.territorydefense.feature.logistics.ui.RebuildWarehouseGui warehouseGui) {
                // Đối với kho nguyên liệu tái thiết: cho phép click bình thường ở slots 0-44 và rương cá nhân,
                // Nhưng chặn hoàn toàn click ở thanh điều hướng dưới đáy (slots 45-53)
                if (event.getClickedInventory() != null && event.getClickedInventory().equals(topInventory)) {
                    int slot = event.getSlot();
                    if (slot >= 45 && slot <= 53) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player player) {
                            warehouseGui.onClick(event, player);
                        }
                    } else {
                        // Có thay đổi ở vùng chứa, đồng bộ sau 1 tick
                        if (event.getWhoClicked() instanceof Player player) {
                            org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("TerritoryDefense");
                            if (plugin != null) {
                                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof com.truongcm.territorydefense.feature.logistics.ui.RebuildWarehouseGui gui) {
                                        gui.syncToCoreInventory(player.getOpenInventory().getTopInventory());
                                    }
                                });
                            }
                        }
                    }
                } else {
                    // Click ở hòm đồ cá nhân, đồng bộ sau 1 tick đề phòng shift-click đẩy vật phẩm lên
                    if (event.getWhoClicked() instanceof Player player) {
                        org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("TerritoryDefense");
                        if (plugin != null) {
                            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                                if (player.getOpenInventory().getTopInventory().getHolder() instanceof com.truongcm.territorydefense.feature.logistics.ui.RebuildWarehouseGui gui) {
                                    gui.syncToCoreInventory(player.getOpenInventory().getTopInventory());
                                }
                            });
                        }
                    }
                }
            } else {
                // Hành vi mặc định cho các GUI CustomHolder khác: Chặn hoàn toàn click bên ngoài hòm đồ
                event.setCancelled(true); // Ngăn lấy đồ ra khỏi GUI theo mặc định
                
                // Chỉ bắt các click diễn ra bên trong GUI (bỏ qua hòm đồ cá nhân ở dưới)
                if (event.getClickedInventory() != null && event.getClickedInventory().equals(topInventory)) {
                    if (event.getWhoClicked() instanceof Player player) {
                        customHolder.onClick(event, player);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        // Chặn đứng hoàn toàn hành vi kéo thả vật phẩm trong giao diện CustomHolder
        if (topInventory.getHolder() instanceof CustomHolder) {
            if (topInventory.getHolder() instanceof com.truongcm.territorydefense.feature.logistics.ui.RebuildWarehouseGui) {
                // Chặn kéo thả đè lên vùng nút bấm điều khiển (raw slots 45-53)
                for (int slot : event.getRawSlots()) {
                    if (slot >= 45 && slot <= 53) {
                        event.setCancelled(true);
                        break;
                    }
                }
                // Đồng bộ sau 1 tick nếu drag diễn ra thành công
                if (!event.isCancelled() && event.getWhoClicked() instanceof Player player) {
                    org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("TerritoryDefense");
                    if (plugin != null) {
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.getOpenInventory().getTopInventory().getHolder() instanceof com.truongcm.territorydefense.feature.logistics.ui.RebuildWarehouseGui gui) {
                                gui.syncToCoreInventory(player.getOpenInventory().getTopInventory());
                            }
                        });
                    }
                }
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof CustomHolder customHolder) {
            if (event.getPlayer() instanceof Player player) {
                customHolder.onClose(player);
            }
        }
    }
}

