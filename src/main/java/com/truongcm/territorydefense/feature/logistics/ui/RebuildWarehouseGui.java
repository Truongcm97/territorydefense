package com.truongcm.territorydefense.feature.logistics.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import com.truongcm.territorydefense.feature.core.ui.CoreGui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class RebuildWarehouseGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final int page;
    private final NamespacedKey actionKey;

    public RebuildWarehouseGui(TerritoryDefense plugin, TerritoryCore core, int page) {
        this.plugin = plugin;
        this.core = core;
        this.page = page;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    public int getPage() {
        return page;
    }

    public TerritoryCore getCore() {
        return core;
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.DARK_BLUE + "📦 Kho Tái Thiết (Trang " + (page + 1) + "/2)");

        // 1. Điền nguyên liệu từ rebuildWarehouse của core tương ứng theo trang
        int startIndex = page * 45;
        for (int i = 0; i < 45; i++) {
            ItemStack item = core.getRebuildWarehouse().getItem(startIndex + i);
            if (item != null) {
                inv.setItem(i, item);
            }
        }

        // 2. Điền thanh điều hướng dưới đáy (slots 45-53)
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", actionKey, "NONE");
        for (int i = 45; i < 53; i++) {
            inv.setItem(i, pane);
        }

        // Slot 45: Quay lại Lõi
        inv.setItem(45, createGuiItem(Material.ARROW, ChatColor.YELLOW + "← Quay Lại Lõi", actionKey, "BACK",
                ChatColor.GRAY + "Quay lại giao diện Lõi Lãnh Thổ (Tab Logistics)."
        ));

        // Slot 47: Vào GUI Tái Thiết
        inv.setItem(47, createGuiItem(Material.FILLED_MAP, ChatColor.GREEN + "➔ Vào GUI Tái Thiết", actionKey, "GO_REBUILD_CONFIRM",
                ChatColor.GRAY + "Mở giao diện lựa chọn bản thiết kế",
                ChatColor.GRAY + "để tiến hành xem trước hoặc tái thiết móng."
        ));

        // Slot 49: Thông tin trang hiện tại
        inv.setItem(49, createGuiItem(Material.PAPER, ChatColor.AQUA + "Trang Hiện Tại: " + (page + 1) + " / 2", actionKey, "NONE",
                ChatColor.GRAY + "Tổng dung lượng: 90 ô chứa.",
                ChatColor.GRAY + "Người chơi có thể đặt vật phẩm ở cả 2 trang.",
                ChatColor.GRAY + "7Gao sẽ tự động quét cả 2 trang để xây dựng."
        ));

        // Slot 51: Chuyển trang
        inv.setItem(51, createGuiItem(Material.FEATHER, ChatColor.GOLD + "➔ Qua Trang " + (page == 0 ? "2" : "1"), actionKey, "SWITCH_PAGE",
                ChatColor.GRAY + "Click để chuyển sang trang " + (page == 0 ? "2" : "1") + " của kho chứa nguyên liệu."
        ));

        // Slot 53: Thoát
        inv.setItem(53, createGuiItem(Material.BARRIER, ChatColor.RED + "Đóng Cửa Sổ", actionKey, "CLOSE",
                ChatColor.GRAY + "Đóng và lưu trữ tự động."
        ));

        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        int slot = event.getSlot();
        if (slot >= 45 && slot <= 53) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.hasItemMeta()) return;

            String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            if (action.equalsIgnoreCase("BACK")) {
                syncToCoreInventory(event.getInventory());
                player.closeInventory();
                player.openInventory(new CoreGui(plugin, core, CoreGui.CoreTab.LOGISTICS).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
            } else if (action.equalsIgnoreCase("GO_REBUILD_CONFIRM")) {
                syncToCoreInventory(event.getInventory());
                player.closeInventory();
                player.openInventory(new RebuildConfirmGui(plugin, core, null, "Danh Sách Bản Vẽ", -2, 0).getInventory());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            } else if (action.equalsIgnoreCase("SWITCH_PAGE")) {
                syncToCoreInventory(event.getInventory());
                int nextPage = (page == 0) ? 1 : 0;
                player.openInventory(new RebuildWarehouseGui(plugin, core, nextPage).getInventory());
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            } else if (action.equalsIgnoreCase("CLOSE")) {
                syncToCoreInventory(event.getInventory());
                player.closeInventory();
            }
        }
    }

    public void syncToCoreInventory(Inventory guiInv) {
        int startIndex = page * 45;
        for (int i = 0; i < 45; i++) {
            ItemStack item = guiInv.getItem(i);
            core.getRebuildWarehouse().setItem(startIndex + i, item);
        }
        core.markDirty();
    }

    @Override
    public void onClose(Player player) {
        Inventory topInv = player.getOpenInventory().getTopInventory();
        if (topInv.getHolder() instanceof RebuildWarehouseGui) {
            syncToCoreInventory(topInv);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
    }
}
