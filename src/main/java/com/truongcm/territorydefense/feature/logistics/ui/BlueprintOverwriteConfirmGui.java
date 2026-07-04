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

public class BlueprintOverwriteConfirmGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore buyerCore;
    private final TerritoryCore sellerCore;
    private final List<TerritoryCore.BlockSnapshot> designToSave;
    private final double price;
    private final String sellerName;
    private final int slotIndex;
    private final NamespacedKey actionKey;

    public BlueprintOverwriteConfirmGui(TerritoryDefense plugin, TerritoryCore buyerCore, TerritoryCore sellerCore,
                                       List<TerritoryCore.BlockSnapshot> designToSave, double price, String sellerName, int slotIndex) {
        this.plugin = plugin;
        this.buyerCore = buyerCore;
        this.sellerCore = sellerCore;
        this.designToSave = designToSave;
        this.price = price;
        this.sellerName = sellerName;
        this.slotIndex = slotIndex;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        String existingName = buyerCore.getBlueprintNames().get(slotIndex);
        Inventory inv = Bukkit.createInventory(this, 27, ChatColor.RED + "Xác nhận ghi đè Bản Vẽ");

        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        inv.setItem(11, createGuiItem(Material.GREEN_WOOL, ChatColor.GREEN + "" + ChatColor.BOLD + "ĐỒNG Ý GHI ĐÈ", "CONFIRM",
                ChatColor.GRAY + "Ghi đè thiết kế mới lên slot #" + (slotIndex + 1) + ":",
                ChatColor.RED + " - Cũ: " + existingName,
                ChatColor.GREEN + " - Mới: " + sellerCore.getBlueprintNames().get(sellerCore.getSellingSlotIndex()),
                ChatColor.GOLD + " - Giá mua: " + String.format("%,.0f", price) + " Xu",
                " ",
                ChatColor.RED + "⚠ Lưu ý: Thiết kế cũ sẽ bị xóa VĨNH VIỄN!",
                ChatColor.GREEN + "➔ Click để đồng ý mua & ghi đè!"
        ));

        inv.setItem(15, createGuiItem(Material.RED_WOOL, ChatColor.RED + "" + ChatColor.BOLD + "HỦY BỎ", "CANCEL",
                ChatColor.GRAY + "Giữ nguyên thiết kế cũ.",
                ChatColor.YELLOW + "➔ Click để quay lại chọn slot khác."
        ));

        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.hasItemMeta()) {
            String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            if (action.equalsIgnoreCase("CONFIRM")) {
                player.closeInventory();
                
                // Kiểm tra lại tiền
                if (plugin.getVaultEconomy().getBalance(player) < price) {
                    player.sendMessage(ChatColor.RED + "Bạn không đủ tiền để mua bản vẽ này!");
                    return;
                }

                // Thực hiện giao dịch tài chính
                plugin.getVaultEconomy().withdrawPlayer(player, price);
                plugin.getVaultEconomy().depositPlayer(org.bukkit.Bukkit.getOfflinePlayer(sellerCore.getOwnerUUID()), price);

                // Sao chép thiết kế
                buyerCore.setBlueprintSlot(slotIndex, designToSave);
                buyerCore.getBlueprintSlotsBought().set(slotIndex, true);

                // Sao chép tên và cấp độ quét
                int sellerSellingSlot = sellerCore.getSellingSlotIndex();
                String sellerDesignName = sellerCore.getBlueprintNames().get(sellerSellingSlot);
                int sellerScanLvl = sellerCore.getBlueprintScanLevels().get(sellerSellingSlot);

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
                return;
            }

            if (action.equalsIgnoreCase("CANCEL")) {
                player.closeInventory();
                player.openInventory(new BlueprintShopSaveSlotGui(plugin, buyerCore, sellerCore, designToSave, price, sellerName, sellerCore.getSellingSlotIndex()).getInventory());
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
