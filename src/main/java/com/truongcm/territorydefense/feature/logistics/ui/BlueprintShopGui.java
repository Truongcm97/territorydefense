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
import java.util.UUID;

public class BlueprintShopGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore buyerCore;
    private final NamespacedKey actionKey;

    public BlueprintShopGui(TerritoryDefense plugin, TerritoryCore buyerCore) {
        this.plugin = plugin;
        this.buyerCore = buyerCore;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.GOLD + "Cửa Hàng Bản Vẽ Lãnh Thổ");

        // Phủ nền kính xám hàng dưới
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, pane);
        }

        // Tải danh sách các bản vẽ đang bày bán công khai trên toàn bộ máy chủ
        int slot = 0;
        for (TerritoryCore targetCore : plugin.getCoreManager().getAllActiveCores()) {
            if (slot >= 45) break;
            if (targetCore.getCoreId().equals(buyerCore.getCoreId())) continue; // Không tự mua của mình

            // Duyệt qua toàn bộ 54 slot của lõi này để tìm các slot đang rao bán
            for (int s = 0; s < 54; s++) {
                if (slot >= 45) break;
                if (targetCore.getBlueprintSellingStatus().get(s)) {
                    List<TerritoryCore.BlockSnapshot> design = targetCore.getBlueprintSlots().get(s);
                    if (design == null || design.isEmpty()) continue;

                    String customName = targetCore.getBlueprintNames().get(s);
                    int scanLvl = targetCore.getBlueprintScanLevels().get(s);
                    double price = targetCore.getBlueprintPrices().get(s);

                    String sellerName = Bukkit.getOfflinePlayer(targetCore.getOwnerUUID()).getName();
                    if (sellerName == null) sellerName = "Người chơi ẩn danh";

                    ItemStack mapItem = createGuiItem(Material.FILLED_MAP, ChatColor.YELLOW + "Bản vẽ: " + customName, "BUY_" + targetCore.getCoreId().toString() + "_" + s,
                            ChatColor.GRAY + "Người bán: " + ChatColor.GOLD + sellerName,
                            ChatColor.GRAY + "Tên thiết kế: " + ChatColor.LIGHT_PURPLE + customName,
                            ChatColor.GRAY + "Cấp độ quét: " + ChatColor.AQUA + "Cấp " + scanLvl,
                            ChatColor.GRAY + "Tổng số khối: " + ChatColor.AQUA + design.size() + " blocks",
                            ChatColor.GRAY + "Slot nguồn: #" + (s + 1),
                            ChatColor.GRAY + "Giá bán: " + ChatColor.GREEN + String.format("%,.0f", price) + " Xu",
                            " ",
                            ChatColor.YELLOW + "➔ Click để tiến hành Mua bản vẽ này!"
                    );
                    inv.setItem(slot, mapItem);
                    slot++;
                }
            }
        }

        // Điều hướng
        inv.setItem(45, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Quay lại Lõi", "BACK"));
        inv.setItem(49, createGuiItem(Material.BOOKSHELF, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Bày Bán Bản Vẽ Của Bạn", "OPEN_BLUEPRINT_SELL_SELECTOR",
                ChatColor.GRAY + "Thiết lập xem bạn muốn bày bán bản vẽ nào",
                ChatColor.GRAY + "trong số 54 slot bản vẽ đã lưu của bạn.",
                ChatColor.GRAY + "Hỗ trợ bày bán đồng thời nhiều bản vẽ khác nhau!",
                " ",
                ChatColor.YELLOW + "➔ Click để quản lý bày bán!"
        ));
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
                player.openInventory(new com.truongcm.territorydefense.feature.core.ui.CoreGui(plugin, buyerCore, com.truongcm.territorydefense.feature.core.ui.CoreGui.CoreTab.LOGISTICS).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("OPEN_BLUEPRINT_SELL_SELECTOR")) {
                player.closeInventory();
                player.openInventory(new BlueprintSelectSellSlotGui(plugin, buyerCore).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                return;
            }

            if (action.startsWith("BUY_")) {
                String buyData = action.substring(4);
                String[] parts = buyData.split("_");
                if (parts.length < 2) return;

                try {
                    UUID targetCoreId = UUID.fromString(parts[0]);
                    int sellingSlotIdx = Integer.parseInt(parts[1]);

                    TerritoryCore targetCore = plugin.getCoreManager().getAllActiveCores().stream()
                            .filter(c -> c.getCoreId().equals(targetCoreId))
                            .findFirst().orElse(null);

                    if (targetCore == null) {
                        player.sendMessage(ChatColor.RED + "Lõi của người bán không khả dụng!");
                        return;
                    }

                    List<TerritoryCore.BlockSnapshot> design = targetCore.getBlueprintSlots().get(sellingSlotIdx);
                    if (design == null || design.isEmpty()) {
                        player.sendMessage(ChatColor.RED + "Bản vẽ thiết kế của người chơi này không còn khả dụng!");
                        return;
                    }

                    double price = targetCore.getBlueprintPrices().get(sellingSlotIdx);
                    if (plugin.getVaultEconomy().getBalance(player) < price) {
                        player.sendMessage(ChatColor.RED + "Bạn không đủ Xu để mua bản vẽ này! Cần: " + String.format("%,.0f", price) + " Xu.");
                        return;
                    }

                    String sellerName = Bukkit.getOfflinePlayer(targetCore.getOwnerUUID()).getName();
                    if (sellerName == null) sellerName = "Người chơi ẩn danh";

                    player.closeInventory();
                    // Chúng ta cần cập nhật BlueprintShopSaveSlotGui để nhận sellingSlotIdx và sao chép chính xác từ đúng slot nguồn
                    player.openInventory(new BlueprintShopSaveSlotGui(plugin, buyerCore, targetCore, design, price, sellerName, sellingSlotIdx).getInventory());
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);

                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Đã xảy ra lỗi trong quá trình xử lý mua bản vẽ!");
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
