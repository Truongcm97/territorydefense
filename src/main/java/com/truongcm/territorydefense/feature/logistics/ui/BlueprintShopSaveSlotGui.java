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
import java.util.List;

public class BlueprintShopSaveSlotGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore buyerCore;
    private final TerritoryCore sellerCore;
    private final List<TerritoryCore.BlockSnapshot> designToSave;
    private final double price;
    private final String sellerName;
    private final int sellingSlotIdx;
    private final NamespacedKey actionKey;
    private int page = 0; // 0: Trang 1, 1: Trang 2

    public BlueprintShopSaveSlotGui(TerritoryDefense plugin, TerritoryCore buyerCore, TerritoryCore sellerCore,
                                    List<TerritoryCore.BlockSnapshot> designToSave, double price, String sellerName, int sellingSlotIdx) {
        this.plugin = plugin;
        this.buyerCore = buyerCore;
        this.sellerCore = sellerCore;
        this.designToSave = designToSave;
        this.price = price;
        this.sellerName = sellerName;
        this.sellingSlotIdx = sellingSlotIdx;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.BLUE + "Lưu Vào Slot (" + (page + 1) + "/2)...");

        // Phủ nền kính xám hàng dưới
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, pane);
        }

        // Tạo các slot bản vẽ tương ứng của trang
        int startSlot = page * 45;
        int endSlot = Math.min(startSlot + 45, 54);
        List<List<TerritoryCore.BlockSnapshot>> slots = buyerCore.getBlueprintSlots();
        List<String> names = buyerCore.getBlueprintNames();
        List<Integer> scanLevels = buyerCore.getBlueprintScanLevels();

        for (int i = startSlot; i < endSlot; i++) {
            int guiSlot = i - startSlot;
            List<TerritoryCore.BlockSnapshot> design = slots.get(i);
            String customName = names.get(i);
            int scanLvl = scanLevels.get(i);

            if (design == null || design.isEmpty()) {
                inv.setItem(guiSlot, createGuiItem(Material.GRAY_DYE, ChatColor.GRAY + "Slot thiết kế #" + (i + 1) + " (Trống)", "SAVE_TO_SLOT_" + i,
                        ChatColor.GRAY + "Nhấp chuột để lưu bản vẽ đã mua vào đây."
                ));
            } else {
                String displayName = ChatColor.YELLOW + "Slot #" + (i + 1) + " - " + customName + " - Cấp " + scanLvl;
                inv.setItem(guiSlot, createGuiItem(Material.WRITTEN_BOOK, displayName, "SAVE_TO_SLOT_" + i,
                        ChatColor.GRAY + "Thiết kế hiện tại: " + ChatColor.GOLD + design.size() + " blocks",
                        " ",
                        ChatColor.RED + "⚠ Chú ý: Việc lưu đè sẽ mở Giao diện xác nhận ghi đè!"
                ));
            }
        }

        // Các nút điều hướng
        inv.setItem(45, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Quay lại Cửa hàng", "BACK"));
        if (page > 0) {
            inv.setItem(47, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Trang trước", "PREV_PAGE"));
        }
        inv.setItem(49, createGuiItem(Material.PAPER, ChatColor.GOLD + "Trang " + (page + 1) + " / 2", "NONE"));
        if (page < 1) {
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
                player.openInventory(new BlueprintShopGui(plugin, buyerCore).getInventory());
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
                page = Math.min(1, page + 1);
                player.openInventory(getInventory());
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                return;
            }

            if (action.startsWith("SAVE_TO_SLOT_")) {
                String slotStr = action.substring(13);
                try {
                    int slotIndex = Integer.parseInt(slotStr);
                    if (slotIndex < 0 || slotIndex >= 54) return;

                    // Kiểm tra tiền lại lần cuối trước giao dịch
                    if (plugin.getVaultEconomy().getBalance(player) < price) {
                        player.sendMessage(ChatColor.RED + "Bạn không đủ tiền để mua bản vẽ này!");
                        player.closeInventory();
                        return;
                    }

                    // Nếu slot đích KHÔNG TRỐNG -> mở GUI xác nhận ghi đè
                    if (!buyerCore.getBlueprintSlots().get(slotIndex).isEmpty()) {
                        player.closeInventory();
                        player.openInventory(new BlueprintOverwriteConfirmGui(plugin, buyerCore, sellerCore, designToSave, price, sellerName, slotIndex).getInventory());
                        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
                        return;
                    }

                    // Thực hiện giao dịch tài chính trực tiếp vì slot trống
                    plugin.getVaultEconomy().withdrawPlayer(player, price);
                    plugin.getVaultEconomy().depositPlayer(org.bukkit.Bukkit.getOfflinePlayer(sellerCore.getOwnerUUID()), price);

                    // Sao chép thiết kế
                    buyerCore.getBlueprintSlots().get(slotIndex).clear();
                    buyerCore.getBlueprintSlots().get(slotIndex).addAll(designToSave);
                    buyerCore.getBlueprintSlotsBought().set(slotIndex, true);

                    // Sao chép tên và cấp độ quét
                    String sellerDesignName = sellerCore.getBlueprintNames().get(sellingSlotIdx);
                    int sellerScanLvl = sellerCore.getBlueprintScanLevels().get(sellingSlotIdx);

                    buyerCore.getBlueprintNames().set(slotIndex, sellerDesignName);
                    buyerCore.getBlueprintScanLevels().set(slotIndex, sellerScanLvl);

                    plugin.getCoreManager().registerCore(buyerCore.getLocation(), buyerCore);

                    player.sendMessage(ChatColor.GREEN + "[Cửa Hàng] Giao dịch thành công!");
                    player.sendMessage(ChatColor.GOLD + "Đã trừ " + String.format("%,.0f", price) + " Xu. Đã lưu bản thiết kế vào Slot #" + (slotIndex + 1) + "!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                    // Thông báo cho người bán nếu online
                    Player seller = Bukkit.getPlayer(sellerCore.getOwnerUUID());
                    if (seller != null && seller.isOnline()) {
                        seller.sendMessage(ChatColor.GREEN + "[Cửa Hàng] " + ChatColor.GOLD + player.getName() + 
                                ChatColor.GREEN + " đã mua bản thiết kế lãnh thổ của bạn với giá " + ChatColor.GOLD + String.format("%,.0f", price) + " Xu!");
                        seller.playSound(seller.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }

                    player.closeInventory();

                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Đã xảy ra lỗi khi lưu bản vẽ!");
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
