package com.truongcm.territorydefense.base.ui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import com.truongcm.territorydefense.feature.core.PDCKeys;

import java.util.*;

/**
 * Hệ thống dựng GUI động đa năng sử dụng cơ chế Matrix Template,
 * hỗ trợ phân trang tự động và liên kết callback click trực tiếp.
 */
public class GUIBuilder {

    public interface ClickAction {
        void execute(InventoryClickEvent event, Player player);
    }

    private final String title;
    private final String[] rows;
    private final Map<Character, ItemStack> symbolItems = new HashMap<>();
    private final Map<Character, ClickAction> symbolActions = new HashMap<>();
    private final Map<Integer, ClickAction> slotActions = new HashMap<>();
    
    // Thuộc tính phân trang
    private List<ItemStack> paginatedItems = new ArrayList<>();
    private char itemSlotSymbol = 'I';
    private char prevPageSymbol = 'P';
    private char nextPageSymbol = 'N';
    private int currentPage = 0;

    public GUIBuilder(String title, String[] rows) {
        this.title = title;
        this.rows = rows;
        if (rows.length == 0 || rows.length > 6) {
            throw new IllegalArgumentException("Hàng của GUI phải từ 1 đến 6!");
        }
    }

    public GUIBuilder registerSymbol(char symbol, ItemStack item, ClickAction action) {
        symbolItems.put(symbol, item);
        if (action != null) {
            symbolActions.put(symbol, action);
        }
        return this;
    }

    public GUIBuilder registerSlotAction(int slot, ClickAction action) {
        slotActions.put(slot, action);
        return this;
    }

    public GUIBuilder enablePagination(List<ItemStack> items, char itemSlotSymbol, char prevPageSymbol, char nextPageSymbol) {
        this.paginatedItems = items;
        this.itemSlotSymbol = itemSlotSymbol;
        this.prevPageSymbol = prevPageSymbol;
        this.nextPageSymbol = nextPageSymbol;
        return this;
    }

    public GUIBuilder setCurrentPage(int page) {
        this.currentPage = page;
        return this;
    }

    public GUIHolder build(Player player) {
        int size = rows.length * 9;
        GUIHolder holder = new GUIHolder(size, title);
        Inventory inv = holder.getInventory();

        // Tìm các vị trí đặc biệt trên layout
        List<Integer> targetItemSlots = new ArrayList<>();
        int prevPageSlot = -1;
        int nextPageSlot = -1;

        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < 9 && c < row.length(); c++) {
                char symbol = row.charAt(c);
                int slot = r * 9 + c;

                if (symbol == itemSlotSymbol) {
                    targetItemSlots.add(slot);
                } else if (symbol == prevPageSymbol) {
                    prevPageSlot = slot;
                } else if (symbol == nextPageSymbol) {
                    nextPageSlot = slot;
                } else if (symbolItems.containsKey(symbol)) {
                    inv.setItem(slot, symbolItems.get(symbol).clone());
                    if (symbolActions.containsKey(symbol)) {
                        holder.registerAction(slot, symbolActions.get(symbol));
                    }
                }
            }
        }

        // Xử lý phân trang
        int itemsPerPage = targetItemSlots.size();
        if (itemsPerPage > 0 && !paginatedItems.isEmpty()) {
            int maxPage = (int) Math.ceil((double) paginatedItems.size() / itemsPerPage) - 1;
            if (maxPage < 0) maxPage = 0;
            if (currentPage > maxPage) currentPage = maxPage;

            int startIndex = currentPage * itemsPerPage;
            for (int i = 0; i < itemsPerPage; i++) {
                int slot = targetItemSlots.get(i);
                int itemIndex = startIndex + i;
                if (itemIndex < paginatedItems.size()) {
                    inv.setItem(slot, paginatedItems.get(itemIndex));
                }
            }

            // Nút Trang Trước
            if (currentPage > 0 && prevPageSlot != -1) {
                inv.setItem(prevPageSlot, createPageItem(Material.ARROW, ChatColor.YELLOW + "◀ Trang trước (Trang " + currentPage + ")"));
                final int finalPage = currentPage - 1;
                holder.registerAction(prevPageSlot, (event, p) -> {
                    this.currentPage = finalPage;
                    p.openInventory(build(p).getInventory());
                });
            }

            // Nút Trang Sau
            if ((startIndex + itemsPerPage) < paginatedItems.size() && nextPageSlot != -1) {
                inv.setItem(nextPageSlot, createPageItem(Material.ARROW, ChatColor.YELLOW + "Trang sau ▶ (Trang " + (currentPage + 2) + ")"));
                final int finalPage = currentPage + 1;
                holder.registerAction(nextPageSlot, (event, p) -> {
                    this.currentPage = finalPage;
                    p.openInventory(build(p).getInventory());
                });
            }
        }

        // Action tùy chọn gán tay theo ô
        for (Map.Entry<Integer, ClickAction> entry : slotActions.entrySet()) {
            holder.registerAction(entry.getKey(), entry.getValue());
        }

        return holder;
    }

    private ItemStack createPageItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.getPersistentDataContainer().set(PDCKeys.GUI_ACTION, PersistentDataType.STRING, "PAGE_NAV");
            item.setItemMeta(meta);
        }
        return item;
    }

    public static class GUIHolder extends CustomHolder {
        private final Inventory inventory;
        private final Map<Integer, ClickAction> actions = new HashMap<>();

        public GUIHolder(int size, String title) {
            this.inventory = Bukkit.createInventory(this, size, title);
        }

        public void registerAction(int slot, ClickAction action) {
            actions.put(slot, action);
        }

        @Override
        public void onClick(InventoryClickEvent event, Player player) {
            int slot = event.getRawSlot();
            if (actions.containsKey(slot)) {
                actions.get(slot).execute(event, player);
            }
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
