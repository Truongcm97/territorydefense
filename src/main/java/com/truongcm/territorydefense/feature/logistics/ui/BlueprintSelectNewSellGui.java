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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlueprintSelectNewSellGui extends CustomHolder {
    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final NamespacedKey actionKey;
    private int page = 0;

    public BlueprintSelectNewSellGui(TerritoryDefense plugin, TerritoryCore core) {
        this.plugin = plugin;
        this.core = core;
        this.actionKey = PDCKeys.GUI_ACTION != null ? PDCKeys.GUI_ACTION : new NamespacedKey(plugin, "gui_action");
    }

    @Override
    public Inventory getInventory() {
        List<List<TerritoryCore.BlockSnapshot>> slots = core.getBlueprintSlots();
        List<String> names = core.getBlueprintNames();
        List<Integer> scanLevels = core.getBlueprintScanLevels();
        List<Boolean> bought = core.getBlueprintSlotsBought();
        List<Boolean> selling = core.getBlueprintSellingStatus();

        // Lọc các bản vẽ có thể bán (không trống, không mua, chưa bán)
        List<Integer> eligibleIndices = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            List<TerritoryCore.BlockSnapshot> design = slots.get(i);
            if (design != null && !design.isEmpty() && !bought.get(i) && !selling.get(i)) {
                eligibleIndices.add(i);
            }
        }

        int totalItems = eligibleIndices.size();
        int totalPages = (int) Math.ceil(totalItems / 45.0);
        if (totalPages == 0) totalPages = 1;
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.BLUE + "Chọn Bản Vẽ Để Bán (" + (page + 1) + "/" + totalPages + ")");

        // Kính nền hàng dưới
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, pane);
        }

        int startIdx = page * 45;
        int endIdx = Math.min(startIdx + 45, totalItems);

        for (int i = startIdx; i < endIdx; i++) {
            int guiSlot = i - startIdx;
            int originalIndex = eligibleIndices.get(i);
            List<TerritoryCore.BlockSnapshot> design = slots.get(originalIndex);
            String customName = names.get(originalIndex);
            int scanLvl = scanLevels.get(originalIndex);

            ItemStack item = createGuiItem(Material.WRITTEN_BOOK, ChatColor.YELLOW + customName, "SELL_" + originalIndex,
                    ChatColor.GRAY + "Cấp độ: " + ChatColor.AQUA + "Cấp " + scanLvl,
                    ChatColor.GRAY + "Tổng số khối: " + ChatColor.AQUA + design.size() + " blocks",
                    " ",
                    ChatColor.YELLOW + "➔ Click để thiết lập giá và bày bán!"
            );
            inv.setItem(guiSlot, item);
        }

        // Điều hướng
        inv.setItem(45, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Quay lại", "BACK"));
        if (page > 0) {
            inv.setItem(47, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Trang trước", "PREV_PAGE"));
        }
        inv.setItem(49, createGuiItem(Material.PAPER, ChatColor.GOLD + "Trang " + (page + 1) + " / " + totalPages, "NONE"));
        if (page < totalPages - 1) {
            inv.setItem(51, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Trang sau ▶", "NEXT_PAGE"));
        }
        inv.setItem(53, createGuiItem(Material.BARRIER, ChatColor.RED + "Thoát ra", "CLOSE"));

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

            if (action.equalsIgnoreCase("BACK")) {
                player.closeInventory();
                player.openInventory(new BlueprintSelectSellSlotGui(plugin, core).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("PREV_PAGE")) {
                page = Math.max(0, page - 1);
                player.openInventory(getInventory());
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("NEXT_PAGE")) {
                page = page + 1;
                player.openInventory(getInventory());
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                return;
            }

            if (action.startsWith("SELL_")) {
                int slotIndex = Integer.parseInt(action.substring(5));
                player.closeInventory();
                // Mở giao diện thiết lập giá
                player.openInventory(new BlueprintPriceSelectGui(plugin, core, slotIndex, 100000.0).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
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
