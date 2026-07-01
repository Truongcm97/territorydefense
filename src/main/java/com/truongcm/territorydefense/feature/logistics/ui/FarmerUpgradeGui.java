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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GIAO DIỆN NÂNG CẤP NÔNG DÂN NPC (FARMER GUI) - PHIÊN BẢN ĐỘNG V32 (TÁI CẤU TRÚC STATEFUL)
 * Kế thừa CustomHolder giúp tự đóng gói hành vi hiển thị và click chuột.
 */
public class FarmerUpgradeGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final AbstractVillager farmer;
    private final TerritoryCore core;
    private final NamespacedKey actionKey;

    public FarmerUpgradeGui(TerritoryDefense plugin, AbstractVillager farmer, TerritoryCore core) {
        this.plugin = plugin;
        this.farmer = farmer;
        this.core = core;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        return getInventory(null);
    }

    public Inventory getInventory(Player viewer) {
        Inventory inv = Bukkit.createInventory(this, 27, ChatColor.DARK_GREEN + "Quản Lý Nông Dân Lãnh Thổ");

        // 1. Phủ kính xám nền rương thẩm mỹ bằng hàm helper tối giản
        ItemStack backgroundPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, backgroundPane);
        }

        // Slot 11: Thông tin chỉ số hiện tại của Nông dân (Nạp FEP)
        inv.setItem(11, getFarmerInfoItem());

        // Slot 13: Nút Nâng Cấp Nông dân bằng Xu & Shard
        inv.setItem(13, getUpgradeButtonItem());

        // Slot 15: Nút sa thải nông dân (Tiêu biến thực thể trong ranh giới)
        inv.setItem(15, createGuiItem(Material.ANVIL, ChatColor.RED + "" + ChatColor.BOLD + "Sa Thải Nông Dân", "DISMISS_FARMER",
                ChatColor.GRAY + "Trục xuất Nông dân này khỏi Lãnh thổ.",
                ChatColor.GRAY + "Giải phóng 1 vị trí nhân công trong ranh giới Lõi.",
                " ",
                ChatColor.GREEN + " Hoàn tiền: +50% Xu & Shards đã chi tiêu",
                ChatColor.RED + "⚠ Nhấp để xem chi tiết và xác nhận!"
        ));

        // Slot 22: Quay lại danh sách nông dân, Slot 26: Thoát ra
        inv.setItem(22, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Quay Lại Danh Sách", "BACK"));
        inv.setItem(26, createGuiItem(Material.BARRIER, ChatColor.RED + "Thoát ra", "CLOSE"));

        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.hasItemMeta()) {
            String action = clickedItem.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            if (action.equalsIgnoreCase("CLOSE")) {
                player.closeInventory();
                return;
            }

            if (action.equalsIgnoreCase("BACK")) {
                player.closeInventory();
                player.openInventory(new com.truongcm.territorydefense.feature.logistics.ui.FarmerListGui(plugin, core, player).getInventory());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("UPGRADE_FARMER")) {
                handleUpgradeFarmer(player);
            } else if (action.equalsIgnoreCase("DISMISS_FARMER")) {
                player.closeInventory();
                player.openInventory(new FarmerDismissConfirmGui(plugin, farmer, core).getInventory());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
            }
        }
    }

    private void handleUpgradeFarmer(Player player) {
        PersistentDataContainer pdc = farmer.getPersistentDataContainer();
        int currentLevel = 1;
        if (pdc.has(PDCKeys.FARMER_LEVEL, PersistentDataType.INTEGER)) {
            currentLevel = pdc.get(PDCKeys.FARMER_LEVEL, PersistentDataType.INTEGER);
        }

        if (currentLevel >= 5) {
            player.sendMessage(ChatColor.RED + "[Nông nghiệp] Nông dân đã đạt cấp năng lực tối đa (Cấp 5)!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int nextLevel = currentLevel + 1;
        double moneyCost = plugin.getConfig().getDouble("farmer-settings.levels." + nextLevel + ".upgrade-cost", 0.0);

        int shardCost = switch (nextLevel) {
            case 2 -> 5;
            case 3 -> 15;
            case 4 -> 30;
            case 5 -> 50;
            default -> 0;
        };

        if (moneyCost > 0 && !plugin.getVaultEconomy().has(player, moneyCost)) {
            player.sendMessage(ChatColor.RED + "[Nông nghiệp] Bạn không đủ Xu để nâng cấp Nông dân! Cần: " + String.format("%,.0f", moneyCost) + " Xu.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int coreStoredShards = plugin.getCoreManager().getShards(core.getCoreId());
        int playerInvShards = countPlayerInventoryShards(player);
        int totalAvailableShards = coreStoredShards + playerInvShards;

        if (totalAvailableShards < shardCost) {
            player.sendMessage(ChatColor.RED + "[Nông nghiệp] Bạn không đủ Shards để thực hiện nâng cấp! Cần: " + shardCost + " Shards (Hiện có trong Lõi: " + coreStoredShards + ", Túi đồ: " + playerInvShards + ").");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (moneyCost > 0) {
            plugin.getVaultEconomy().withdrawPlayer(player, moneyCost);
        }

        if (shardCost > 0) {
            if (coreStoredShards >= shardCost) {
                plugin.getCoreManager().setShards(core.getCoreId(), coreStoredShards - shardCost);
            } else {
                int remainingCost = shardCost - coreStoredShards;
                plugin.getCoreManager().setShards(core.getCoreId(), 0);
                withdrawShardsFromInventory(player, remainingCost);
            }
            plugin.getCoreManager().saveAllCores();
        }

        pdc.set(PDCKeys.FARMER_LEVEL, PersistentDataType.INTEGER, nextLevel);
        farmer.setMetadata("td_farmer_level", new org.bukkit.metadata.FixedMetadataValue(plugin, nextLevel));

        player.sendMessage(ChatColor.GREEN + "[Nông nghiệp] Nâng cấp thành công Nông dân NPC lên " + ChatColor.GOLD + "Cấp " + nextLevel + "!");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        org.bukkit.Location fLoc = farmer.getLocation();
        fLoc.getWorld().spawnParticle(org.bukkit.Particle.valueOf(plugin.getConfig().getString("particle-settings.farmer-happy", "HAPPY_VILLAGER")), fLoc.clone().add(0, 1.5, 0), 15, 0.3, 0.5, 0.3, 0.1);

        player.openInventory(getInventory(player));
    }

    private int countPlayerInventoryShards(Player player) {
        int count = 0;
        NamespacedKey shardPdcKey = PDCKeys.IS_SHARD_ITEM;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.PRISMARINE_SHARD && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(shardPdcKey, PersistentDataType.BYTE)) {
                    count += item.getAmount();
                }
            }
        }
        return count;
    }

    private void withdrawShardsFromInventory(Player player, int amountToTake) {
        NamespacedKey shardPdcKey = PDCKeys.IS_SHARD_ITEM;
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = amountToTake;

        for (int i = 0; i < contents.length; i++) {
            if (remaining <= 0) break;
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.PRISMARINE_SHARD && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(shardPdcKey, PersistentDataType.BYTE)) {
                    int amt = item.getAmount();
                    if (amt > remaining) {
                        item.setAmount(amt - remaining);
                        remaining = 0;
                    } else {
                        remaining -= amt;
                        player.getInventory().setItem(i, null);
                    }
                }
            }
        }
    }

    private ItemStack getFarmerInfoItem() {
        ItemStack item = new ItemStack(Material.WHEAT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Thông Tin Nông Dân NPC");

            PersistentDataContainer pdc = farmer.getPersistentDataContainer();
            int level = 1;
            if (pdc.has(PDCKeys.FARMER_LEVEL, PersistentDataType.INTEGER)) {
                level = pdc.get(PDCKeys.FARMER_LEVEL, PersistentDataType.INTEGER);
            }

            double speed = plugin.getConfig().getDouble("farmer-settings.levels." + level + ".speed", 0.20);
            int radius = plugin.getConfig().getInt("farmer-settings.levels." + level + ".scan-radius", 20);
            int capacity = plugin.getConfig().getInt("farmer-settings.levels." + level + ".capacity", 64);
            double multiplier = plugin.getConfig().getDouble("farmer-settings.levels." + level + ".fep-multiplier", 1.0);
            int frequency = plugin.getConfig().getInt("farmer-settings.levels." + level + ".scan-frequency-ticks", 100);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Cấp độ hiện tại: " + ChatColor.GOLD + "Cấp " + level + "/5");
            lore.add(ChatColor.GRAY + "Tốc độ di chuyển: " + ChatColor.AQUA + speed);
            lore.add(ChatColor.GRAY + "Bán kính canh tác: " + ChatColor.AQUA + radius + " blocks");
            lore.add(ChatColor.GRAY + "Sức chứa nông sản: " + ChatColor.YELLOW + capacity + " vật phẩm");
            lore.add(ChatColor.GRAY + "Hệ số sản lượng FEP: " + ChatColor.GREEN + "x" + multiplier);
            lore.add(ChatColor.GRAY + "Tần số quét ruộng: " + ChatColor.LIGHT_PURPLE + String.format("%.1fs / lần", (frequency * 0.05)));
            lore.add(" ");
            lore.add(ChatColor.GRAY + "● Duy trì năng lực trồng trọt và nạp FEP tự động.");

            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "NONE");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack getUpgradeButtonItem() {
        PersistentDataContainer pdc = farmer.getPersistentDataContainer();
        int level = 1;
        if (pdc.has(PDCKeys.FARMER_LEVEL, PersistentDataType.INTEGER)) {
            level = pdc.get(PDCKeys.FARMER_LEVEL, PersistentDataType.INTEGER);
        }

        int nextLevel = level + 1;
        if (level >= 5) {
            return createGuiItem(Material.NETHER_STAR, ChatColor.GOLD + "" + ChatColor.BOLD + "Đạt Cấp Cực Đại (Cấp 5)", "NONE",
                    ChatColor.GRAY + "Tốc độ gặt lúa và năng suất FEP đã đạt mốc tối đa!"
            );
        }

        double moneyCost = plugin.getConfig().getDouble("farmer-settings.levels." + nextLevel + ".upgrade-cost", 0.0);

        int shardCost = switch (nextLevel) {
            case 2 -> 5;
            case 3 -> 15;
            case 4 -> 30;
            case 5 -> 50;
            default -> 0;
        };

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Tiến trình nâng cấp: Cấp " + level + " ➔ " + ChatColor.GREEN + "Cấp " + nextLevel);
        lore.add(" ");
        lore.add(ChatColor.YELLOW + "Chi phí yêu cầu:");
        if (moneyCost > 0) {
            lore.add(ChatColor.GRAY + " - Xu (Vault): " + ChatColor.GOLD + String.format("%,.0f", moneyCost) + " Xu");
        }
        if (shardCost > 0) {
            lore.add(ChatColor.GRAY + " - Shards (Mảnh Không Gian): " + ChatColor.AQUA + shardCost + " Shards");
        }
        lore.add(" ");
        lore.add(ChatColor.GRAY + "Lưu ý: Hệ thống hỗ trợ khấu trừ Shards tích lũy");
        lore.add(ChatColor.GRAY + "trực tiếp trong kho chứa của Lõi Lãnh thổ.");

        return createGuiItem(Material.EMERALD, ChatColor.GREEN + "" + ChatColor.BOLD + "Nâng Cấp Năng Lực Nông Dân", "UPGRADE_FARMER", lore.toArray(new String[0]));
    }

    private ItemStack createGuiItem(Material material, String name, String actionTag, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) {
                List<String> lore = new ArrayList<>(Arrays.asList(loreLines));
                meta.setLore(lore);
            }
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, actionTag);
            item.setItemMeta(meta);
        }
        return item;
    }

    public AbstractVillager getFarmer() {
        return farmer;
    }

    public TerritoryCore getCore() {
        return core;
    }
}
