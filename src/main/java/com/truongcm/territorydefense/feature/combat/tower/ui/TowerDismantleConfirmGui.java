package com.truongcm.territorydefense.feature.combat.tower.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TowerDismantleConfirmGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final Tower tower;
    private final TerritoryCore core;
    private final NamespacedKey actionKey;
    private final double refundMoney;
    private final int refundShards;

    public TowerDismantleConfirmGui(TerritoryDefense plugin, Tower tower, TerritoryCore core) {
        this.plugin = plugin;
        this.tower = tower;
        this.core = core;
        this.actionKey = PDCKeys.GUI_ACTION;

        double buyCost = switch (tower.getType()) {
            case ARROW -> 30000.0;
            case LIGHTNING -> 45000.0;
            case FIRE -> 50000.0;
            case FROST -> 40000.0;
            case ARTILLERY -> 65000.0;
            case HEALING -> 55000.0;
            case SPELL -> 70000.0;
        };

        double totalMoney = buyCost;
        int totalShards = 0;
        for (int lvl = 2; lvl <= tower.getLevel(); lvl++) {
            totalMoney += plugin.getConfig().getDouble("tower-settings.upgrade-costs.level-" + lvl + ".money", 0.0);
            totalShards += plugin.getConfig().getInt("tower-settings.upgrade-costs.level-" + lvl + ".shards", 0);
        }

        this.refundMoney = totalMoney * 0.5;
        this.refundShards = (int) Math.round(totalShards * 0.5);
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 27, ChatColor.RED + "Xác nhận tháo dỡ tháp");

        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(ChatColor.GRAY + "Tháo dỡ tháp phòng thủ và nhận hoàn trả 50%:");
        confirmLore.add(ChatColor.GOLD + " - Hoàn trả Xu: +" + String.format("%,.0f", refundMoney) + " Xu");
        confirmLore.add(ChatColor.AQUA + " - Hoàn trả Shards: +" + refundShards + " Shards");
        confirmLore.add(" ");
        confirmLore.add(ChatColor.RED + "⚠ Lưu ý: Tháp canh này sẽ bị phá hủy vĩnh viễn!");
        confirmLore.add(ChatColor.GREEN + "➔ Click để đồng ý!");

        inv.setItem(11, createGuiItem(Material.GREEN_WOOL, ChatColor.GREEN + "" + ChatColor.BOLD + "ĐỒNG Ý THÁO DỠ", "CONFIRM", confirmLore.toArray(new String[0])));

        inv.setItem(15, createGuiItem(Material.RED_WOOL, ChatColor.RED + "" + ChatColor.BOLD + "HỦY BỎ", "CANCEL",
                ChatColor.GRAY + "Giữ lại Tháp canh này.",
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

                Location towerLoc = tower.getLocation();
                if (plugin.getTowerManager() != null) {
                    plugin.getTowerManager().getActiveTowers().remove(towerLoc);
                }

                Block block = towerLoc.getBlock();
                if (block.getState() instanceof TileState state) {
                    PersistentDataContainer pdc = state.getPersistentDataContainer();
                    pdc.remove(PDCKeys.TOWER_ID);
                    pdc.remove(PDCKeys.TOWER_TYPE);
                    pdc.remove(PDCKeys.TOWER_LEVEL);
                    pdc.remove(PDCKeys.OWNER_CORE_ID);
                    pdc.remove(PDCKeys.ALLY_ID);
                    state.update(true);
                }
                block.setType(Material.AIR);

                player.sendMessage(ChatColor.GREEN + "[Tháp] Đã tháo dỡ Tháp Canh thành công!");
                player.sendMessage(ChatColor.GOLD + " Hoàn trả: +" + String.format("%,.0f", refundMoney) + " Xu " + ChatColor.GRAY + "& " + ChatColor.AQUA + "+" + refundShards + " Shards (đã nạp vào Lõi).");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
                return;
            }

            if (action.equalsIgnoreCase("CANCEL")) {
                player.closeInventory();
                player.openInventory(new TowerUpgradeGui(plugin, tower, core).getInventory());
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
