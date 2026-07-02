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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlueprintListGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final NamespacedKey actionKey;
    private int page = 0; // 0: Trang 1, 1: Trang 2

    public BlueprintListGui(TerritoryDefense plugin, TerritoryCore core) {
        this.plugin = plugin;
        this.core = core;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        List<List<TerritoryCore.BlockSnapshot>> slots = core.getBlueprintSlots();
        List<String> names = core.getBlueprintNames();
        List<Integer> scanLevels = core.getBlueprintScanLevels();
        List<Boolean> bought = core.getBlueprintSlotsBought();

        // Lọc các bản vẽ không trống
        List<Integer> existingIndices = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            List<TerritoryCore.BlockSnapshot> design = slots.get(i);
            if (design != null && !design.isEmpty()) {
                existingIndices.add(i);
            }
        }

        int totalItems = existingIndices.size();
        int totalPages = (int) Math.ceil(totalItems / 45.0);
        if (totalPages == 0) totalPages = 1;
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.BLUE + "Quản Lý Bản Vẽ Thiết Kế (" + (page + 1) + "/" + totalPages + ")");

        // Lấp đầy kính màu xám làm nền hàng dưới cùng (45-53)
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, pane);
        }

        int startIdx = page * 45;
        int endIdx = Math.min(startIdx + 45, totalItems);

        for (int i = startIdx; i < endIdx; i++) {
            int guiSlot = i - startIdx;
            int originalIndex = existingIndices.get(i);
            List<TerritoryCore.BlockSnapshot> design = slots.get(originalIndex);
            String customName = names.get(originalIndex);
            int scanLvl = scanLevels.get(originalIndex);
            boolean isBought = bought.get(originalIndex);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Nhấp chuột trái: Xem chi tiết và tiến hành xây dựng.");
            
            if (!isBought) {
                lore.add(ChatColor.AQUA + "Chuột giữa: Đổi tên bản vẽ.");
            } else {
                lore.add(ChatColor.RED + "Đổi tên: Không thể đổi tên bản vẽ đã mua.");
            }
            
            lore.add(ChatColor.RED + "Shift-Click / Click phải: XÓA bản vẽ này.");
            lore.add(" ");
            lore.add(ChatColor.GOLD + "Tổng khối block: " + ChatColor.GREEN + design.size() + " blocks");

            String displayName = ChatColor.GREEN + customName + " - Cấp " + scanLvl;
            inv.setItem(guiSlot, createGuiItem(Material.WRITTEN_BOOK, displayName, "USE_SLOT_" + originalIndex, lore.toArray(new String[0])));
        }

        // Điều hướng & Trạng thái phân trang
        inv.setItem(45, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Quay lại Lõi", "BACK"));
        if (page > 0) {
            inv.setItem(47, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Trang trước", "PREV_PAGE"));
        }
        
        // Nút Tạo Bản Vẽ Mới
        int scanHeightBelow = plugin.getConfig().getInt("builder-settings.scan-height-below", 5);
        int scanHeightAbove = plugin.getConfig().getInt("builder-settings.scan-height-above", 15);
        inv.setItem(48, createGuiItem(Material.FILLED_MAP, ChatColor.GREEN + "" + ChatColor.BOLD + "Tạo Bản Vẽ Mới", "CREATE_BLUEPRINT",
                ChatColor.GRAY + "Quét toàn bộ lãnh thổ hiện tại.",
                ChatColor.GRAY + "Phạm vi: Độ cao từ -" + scanHeightBelow + " đến +" + scanHeightAbove + " (tính từ Lõi),",
                ChatColor.GRAY + "Diện tích: Toàn bộ vùng đất hình vuông lãnh thổ.",
                ChatColor.YELLOW + "➔ Nhấp chuột để bắt đầu quét."
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
                player.openInventory(new CoreGui(plugin, core, CoreGui.CoreTab.LOGISTICS).getInventory());
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

            if (action.equalsIgnoreCase("CREATE_BLUEPRINT")) {
                int firstEmptySlot = -1;
                List<List<TerritoryCore.BlockSnapshot>> slots = core.getBlueprintSlots();
                for (int i = 0; i < slots.size(); i++) {
                    if (slots.get(i) == null || slots.get(i).isEmpty()) {
                        firstEmptySlot = i;
                        break;
                    }
                }
                if (firstEmptySlot == -1) {
                    player.sendMessage(ChatColor.RED + "Danh sách bản vẽ đã đầy (tối đa 54 bản vẽ)!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getOrCreateBuilder(core.getCoreId());
                player.closeInventory();
                builder.startScanAndSave(core, firstEmptySlot, player);
                return;
            }

            if (action.startsWith("USE_SLOT_")) {
                int slotIndex = Integer.parseInt(action.substring(9));
                
                // Đổi tên bản vẽ với nút cuộn chuột giữa (Middle click)
                if (event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
                    if (core.getBlueprintSlotsBought().get(slotIndex)) {
                        player.sendMessage(ChatColor.RED + "[Kiến Thiết] Bạn chỉ có thể đổi tên bản vẽ do chính mình quét!");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }
                    player.closeInventory();
                    BlueprintInputListener.registerRename(player.getUniqueId(), slotIndex);
                    player.sendMessage(ChatColor.GREEN + "[Kiến Thiết] Hãy nhập tên mới cho bản vẽ vào khung chat (hoặc gõ 'cancel'/'huy' để hủy bỏ).");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    return;
                }

                // Kiểm tra hành động Xóa -> Mở GUI xác nhận xóa
                if (event.getClick().isShiftClick() || event.getClick().isRightClick()) {
                    player.closeInventory();
                    player.openInventory(new BlueprintDeleteConfirmGui(plugin, core, slotIndex).getInventory());
                    player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
                    return;
                }

                // Nhấp chuột trái để xem chi tiết nguyên liệu và xác nhận
                com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getOrCreateBuilder(core.getCoreId());
                if (builder == null) {
                    player.sendMessage(ChatColor.RED + "Bạn cần thuê Thợ Xây NPC trước!");
                    return;
                }
                List<TerritoryCore.BlockSnapshot> design = core.getBlueprintSlots().get(slotIndex);
                if (design == null || design.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Bản vẽ này đang trống!");
                    return;
                }
                String customName = core.getBlueprintNames().get(slotIndex);
                player.closeInventory();
                player.openInventory(new RebuildConfirmGui(plugin, core, design, customName, slotIndex, 0, true).getInventory());
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
