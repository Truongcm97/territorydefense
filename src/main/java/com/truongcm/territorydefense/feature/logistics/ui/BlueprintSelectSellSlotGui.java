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
import java.util.List;

public class BlueprintSelectSellSlotGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final NamespacedKey actionKey;
    private int page = 0; // 0: Trang 1, 1: Trang 2

    public BlueprintSelectSellSlotGui(TerritoryDefense plugin, TerritoryCore core) {
        this.plugin = plugin;
        this.core = core;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        List<List<TerritoryCore.BlockSnapshot>> slots = core.getBlueprintSlots();
        List<String> names = core.getBlueprintNames();
        List<Integer> scanLevels = core.getBlueprintScanLevels();
        List<Boolean> selling = core.getBlueprintSellingStatus();

        // Lọc ra các bản vẽ đang bày bán của người chơi
        List<Integer> sellingIndices = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (selling.get(i)) {
                sellingIndices.add(i);
            }
        }

        int totalItems = sellingIndices.size();
        int totalPages = (int) Math.ceil(totalItems / 45.0);
        if (totalPages == 0) totalPages = 1;
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.BLUE + "Bản Vẽ Đang Bán (" + (page + 1) + "/" + totalPages + ")");

        // Phủ nền kính xám hàng dưới
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, pane);
        }

        int startIdx = page * 45;
        int endIdx = Math.min(startIdx + 45, totalItems);

        for (int i = startIdx; i < endIdx; i++) {
            int guiSlot = i - startIdx;
            int originalIndex = sellingIndices.get(i);
            List<TerritoryCore.BlockSnapshot> design = slots.get(originalIndex);
            int size = (design != null) ? design.size() : 0;
            String customName = names.get(originalIndex);
            double price = core.getBlueprintPrices().get(originalIndex);

            String displayName = ChatColor.GREEN + "" + ChatColor.BOLD + customName + " (ĐANG BÁN)";
            inv.setItem(guiSlot, createGuiItem(Material.ENCHANTED_BOOK, displayName, "SELECT_SELL_" + originalIndex,
                    ChatColor.GRAY + "Số lượng khối: " + ChatColor.GOLD + size + " blocks",
                    ChatColor.GRAY + "Giá bán: " + ChatColor.GOLD + String.format("%,.0f", price) + " Xu",
                    " ",
                    ChatColor.YELLOW + "Nhấp chuột trái: Đổi giá bán",
                    ChatColor.RED + "Shift-Click / Click phải: HỦY BÀY BÁN"
            ));
        }

        // Các nút điều hướng
        inv.setItem(45, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Quay lại Cửa hàng", "BACK"));
        if (page > 0) {
            inv.setItem(47, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Trang trước", "PREV_PAGE"));
        }
        
        // Nút bày bán bản vẽ mới
        inv.setItem(48, createGuiItem(Material.BOOKSHELF, ChatColor.GREEN + "" + ChatColor.BOLD + "Bày Bán Bản Vẽ Mới", "OPEN_NEW_SELL",
                ChatColor.GRAY + "Chọn một bản vẽ trong số các bản vẽ lưu trữ",
                ChatColor.GRAY + "của bạn để tiến hành bày bán lên Cửa hàng.",
                " ",
                ChatColor.YELLOW + "➔ Click để chọn bản vẽ bán!"
        ));

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
                player.openInventory(new BlueprintShopGui(plugin, core).getInventory());
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

            if (action.equalsIgnoreCase("OPEN_NEW_SELL")) {
                player.closeInventory();
                player.openInventory(new BlueprintSelectNewSellGui(plugin, core).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                return;
            }

            if (action.startsWith("SELECT_SELL_")) {
                String slotStr = action.substring(12);
                try {
                    int slotIndex = Integer.parseInt(slotStr);
                    if (slotIndex < 0 || slotIndex >= 54) return;

                    boolean isSelling = core.getBlueprintSellingStatus().get(slotIndex);

                    // HÀNH ĐỘNG HỦY BÁN (Shift-Click hoặc click phải)
                    if (isSelling && (event.getClick().isShiftClick() || event.getClick().isRightClick())) {
                        core.getBlueprintSellingStatus().set(slotIndex, false);
                        core.getBlueprintPrices().set(slotIndex, 0.0);
                        plugin.getCoreManager().registerCore(core.getLocation(), core);

                        player.sendMessage(ChatColor.YELLOW + "[Cửa Hàng] Đã hủy bày bán bản vẽ \"" + core.getBlueprintNames().get(slotIndex) + "\" thành công!");
                        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                        player.openInventory(getInventory()); // Reload GUI
                        return;
                    }

                    // HÀNH ĐỘNG ĐỔI GIÁ BÁN
                    if (isSelling) {
                        player.closeInventory();
                        double currentPrice = core.getBlueprintPrices().get(slotIndex);
                        player.openInventory(new BlueprintPriceSelectGui(plugin, core, slotIndex, currentPrice).getInventory());
                        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                        return;
                    }
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Đã xảy ra lỗi khi chọn bản vẽ!");
                }
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
