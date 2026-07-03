package com.truongcm.territorydefense.feature.logistics.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractVillager;
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

public class FarmerDismissConfirmGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final AbstractVillager villager;
    private final TerritoryCore core;
    private final NamespacedKey actionKey;
    private final double refundMoney;
    private final int refundShards;

    public FarmerDismissConfirmGui(TerritoryDefense plugin, AbstractVillager villager, TerritoryCore core) {
        this.plugin = plugin;
        this.villager = villager;
        this.core = core;
        this.actionKey = PDCKeys.GUI_ACTION;

        int level = 1;
        if (villager.getPersistentDataContainer().has(PDCKeys.FARMER_LEVEL, PersistentDataType.INTEGER)) {
            level = villager.getPersistentDataContainer().get(PDCKeys.FARMER_LEVEL, PersistentDataType.INTEGER);
        }

        double totalMoney = plugin.getConfig().getDouble("farmer-settings.hire-costs.1", 10000.0);
        int totalShards = 0;
        for (int lvl = 2; lvl <= level; lvl++) {
            totalMoney += plugin.getConfig().getDouble("farmer-settings.levels." + lvl + ".upgrade-cost", 0.0);
            totalShards += plugin.getConfig().getInt("farmer-settings.levels." + lvl + ".upgrade-cost-shards", 0);
        }

        this.refundMoney = totalMoney * 0.5;
        this.refundShards = (int) Math.round(totalShards * 0.5);
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 27, ChatColor.RED + "Xác nhận sa thải Nông Dân");

        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(ChatColor.GRAY + "Sa thải Nông dân và nhận hoàn trả 50%:");
        confirmLore.add(ChatColor.GOLD + " - Hoàn trả Xu: +" + String.format("%,.0f", refundMoney) + " Xu");
        confirmLore.add(ChatColor.AQUA + " - Hoàn trả Shards: +" + refundShards + " Shards");
        confirmLore.add(" ");
        confirmLore.add(ChatColor.RED + "⚠ Lưu ý: NPC này sẽ biến mất vĩnh viễn!");
        confirmLore.add(ChatColor.GREEN + "➔ Click để đồng ý!");

        inv.setItem(11, createGuiItem(Material.GREEN_WOOL, ChatColor.GREEN + "" + ChatColor.BOLD + "ĐỒNG Ý SA THẢI", "CONFIRM", confirmLore.toArray(new String[0])));

        inv.setItem(15, createGuiItem(Material.RED_WOOL, ChatColor.RED + "" + ChatColor.BOLD + "HỦY BỎ", "CANCEL",
                ChatColor.GRAY + "Giữ lại Nông dân NPC.",
                ChatColor.YELLOW + "➔ Click để quay lại giao diện trước."
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
                
                if (refundMoney > 0) {
                    plugin.getVaultEconomy().depositPlayer(player, refundMoney);
                }
                if (refundShards > 0) {
                    int currentShards = plugin.getCoreManager().getShards(core.getCoreId());
                    plugin.getCoreManager().setShards(core.getCoreId(), currentShards + refundShards);
                    plugin.getCoreManager().saveAllCores();
                }

                if (villager.hasMetadata("td_farmer")) {
                    String uuidStr = villager.getMetadata("td_farmer").get(0).asString();
                    if (plugin.getFarmerManager() != null) {
                        plugin.getFarmerManager().getActiveFarmers().remove(UUID.fromString(uuidStr));
                    }
                }
                
                villager.remove();
                player.sendMessage(ChatColor.GREEN + "[Nông nghiệp] Đã sa thải Nông dân NPC thành công!");
                player.sendMessage(ChatColor.GOLD + " Hoàn trả: +" + String.format("%,.0f", refundMoney) + " Xu " + ChatColor.GRAY + "& " + ChatColor.AQUA + "+" + refundShards + " Shards (đã nạp vào Lõi).");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_DEATH, 1.0f, 0.8f);
                return;
            }

            if (action.equalsIgnoreCase("CANCEL")) {
                player.closeInventory();
                player.openInventory(new FarmerUpgradeGui(plugin, villager, core).getInventory(player));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
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
