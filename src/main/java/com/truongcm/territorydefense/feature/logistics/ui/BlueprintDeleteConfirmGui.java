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

public class BlueprintDeleteConfirmGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final int slotIndex;
    private final NamespacedKey actionKey;

    public BlueprintDeleteConfirmGui(TerritoryDefense plugin, TerritoryCore core, int slotIndex) {
        this.plugin = plugin;
        this.core = core;
        this.slotIndex = slotIndex;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        String blueprintName = core.getBlueprintNames().get(slotIndex);
        Inventory inv = Bukkit.createInventory(this, 27, ChatColor.RED + "Xác nhận xóa Bản Vẽ");

        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        inv.setItem(11, createGuiItem(Material.GREEN_WOOL, ChatColor.GREEN + "" + ChatColor.BOLD + "ĐỒNG Ý XÓA", "CONFIRM",
                ChatColor.GRAY + "Xóa vĩnh viễn bản vẽ:",
                ChatColor.GOLD + " - " + blueprintName,
                " ",
                ChatColor.RED + "⚠ Lưu ý: Hành động này KHÔNG THỂ hoàn tác!",
                ChatColor.GREEN + "➔ Click để đồng ý xóa!"
        ));

        inv.setItem(15, createGuiItem(Material.RED_WOOL, ChatColor.RED + "" + ChatColor.BOLD + "HỦY BỎ", "CANCEL",
                ChatColor.GRAY + "Giữ lại bản vẽ thiết kế.",
                ChatColor.YELLOW + "➔ Click để quay lại giao diện quản lý."
        ));

        inv.setItem(26, createGuiItem(Material.BARRIER, ChatColor.RED + "Thoát Giao Diện", "CLOSE"));

        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.hasItemMeta()) {
            String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            if (action.equalsIgnoreCase("CLOSE")) {
                player.closeInventory();
                return;
            }

            if (action.equalsIgnoreCase("CONFIRM")) {
                player.closeInventory();
                core.getBlueprintSlots().get(slotIndex).clear();
                core.getBlueprintNames().set(slotIndex, "Bản thiết kế #" + (slotIndex + 1));
                core.getBlueprintScanLevels().set(slotIndex, 1);
                // Reset selling status if any
                if (core.getBlueprintPrices() != null && core.getBlueprintPrices().size() > slotIndex) {
                    core.getBlueprintPrices().set(slotIndex, 0.0);
                }
                if (core.getBlueprintSellingStatus() != null && core.getBlueprintSellingStatus().size() > slotIndex) {
                    core.getBlueprintSellingStatus().set(slotIndex, false);
                }
                plugin.getCoreManager().registerCore(core.getLocation(), core);

                player.sendMessage(ChatColor.YELLOW + "[Kiến Thiết] Đã xóa thành công bản vẽ tại Slot #" + (slotIndex + 1));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                
                // Reopen list GUI
                player.openInventory(new BlueprintListGui(plugin, core).getInventory());
                return;
            }

            if (action.equalsIgnoreCase("CANCEL")) {
                player.closeInventory();
                player.openInventory(new BlueprintListGui(plugin, core).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
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
