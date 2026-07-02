package com.truongcm.territorydefense.feature.combat.tower.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

/**
 * GIAO DIỆN QUẢN LÝ THÁP PHÒNG THỦ (TOWER GUI) - PHIÊN BẢN ĐỘNG V32 (TÁI CẤU TRÚC STATEFUL)
 * Kế thừa CustomHolder giúp tự đóng gói hành vi hiển thị và click chuột.
 */
public class TowerUpgradeGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final Tower tower;
    private final TerritoryCore core;
    private final NamespacedKey actionKey;

    public TowerUpgradeGui(TerritoryDefense plugin, Tower tower, TerritoryCore core) {
        this.plugin = plugin;
        this.tower = tower;
        this.core = core;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 27, ChatColor.DARK_BLUE + "Nâng Cấp Tháp Phòng Thủ");

        // Phủ kính xám nền rương
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        // Slot 11: Thông tin chỉ số hiện tại của Tháp
        inv.setItem(11, getTowerInfoItem());

        // Slot 13: Nút Nâng Cấp tháp bằng Xu & Shard
        inv.setItem(13, getUpgradeButtonItem());

        // Slot 15: Nút tháo dỡ tháp (Hoàn trả 50% chi phí)
        inv.setItem(15, createGuiItem(Material.REDSTONE_BLOCK, ChatColor.RED + "" + ChatColor.BOLD + "Tháo Dỡ Tháp Canh", "DISMANTLE_TOWER",
                ChatColor.GRAY + "Gỡ bỏ tháp phòng thủ này khỏi thế giới thực.",
                " ",
                ChatColor.GREEN + " Hoàn tiền: +50% Xu & Shards đã chi tiêu",
                ChatColor.RED + "⚠ Nhấp để xem chi tiết và xác nhận!"
        ));

        // Slot 22: Quay lại danh sách tháp, Slot 26: Thoát ra
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
                player.openInventory(new com.truongcm.territorydefense.feature.combat.tower.ui.TowerListGui(plugin, core, player).getInventory());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("UPGRADE_TOWER")) {
                handleUpgradeTower(player);
            } else if (action.equalsIgnoreCase("DISMANTLE_TOWER")) {
                player.closeInventory();
                player.openInventory(new TowerDismantleConfirmGui(plugin, tower, core).getInventory());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
            }
        }
    }

    private void handleUpgradeTower(Player player) {
        int nextLevel = tower.getLevel() + 1;
        if (tower.getLevel() >= 5) {
            player.sendMessage(ChatColor.RED + "[Tháp] Tháp phòng thủ đã đạt cấp tối đa (Cấp 5)!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (nextLevel > core.getLevel()) {
            player.sendMessage(ChatColor.RED + "[Tháp] Cấp độ tháp không thể cao hơn cấp độ Lõi Lãnh Thổ (Lõi hiện tại Cấp " + core.getLevel() + ")!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        double moneyCost = plugin.getConfig().getDouble("tower-settings.upgrade-costs.level-" + nextLevel + ".money", 0.0);
        int shardCost = plugin.getConfig().getInt("tower-settings.upgrade-costs.level-" + nextLevel + ".shards", 0);

        if (moneyCost > 0 && !plugin.getVaultEconomy().has(player, moneyCost)) {
            player.sendMessage(ChatColor.RED + "[Tháp] Bạn không đủ Xu để nâng cấp tháp! Cần: " + String.format("%,.0f", moneyCost) + " Xu.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int coreStoredShards = plugin.getCoreManager().getShards(core.getCoreId());
        int playerInvShards = countPlayerInventoryShards(player);
        int totalAvailableShards = coreStoredShards + playerInvShards;

        if (totalAvailableShards < shardCost) {
            player.sendMessage(ChatColor.RED + "[Tháp] Bạn không đủ Shards để nâng cấp! Cần: " + shardCost + " Shards (Tích lũy ở Lõi: " + coreStoredShards + ", Túi đồ: " + playerInvShards + ").");
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

        org.bukkit.Location towerLoc = tower.getLocation();
        Block block = towerLoc.getBlock();
        if (block.getState() instanceof TileState state) {
            PersistentDataContainer pdc = state.getPersistentDataContainer();
            pdc.set(PDCKeys.TOWER_LEVEL, PersistentDataType.INTEGER, nextLevel);
            state.update(true);
        }

        tower.setLevel(nextLevel);

        com.truongcm.territorydefense.feature.core.HologramManager.updateTowerHologram(tower);

        player.sendMessage(ChatColor.GREEN + "[Tháp] Nâng cấp thành công " + ChatColor.YELLOW + tower.getDisplayName() +
                ChatColor.GREEN + " lên " + ChatColor.GOLD + "Cấp " + nextLevel + "!");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        towerLoc.getWorld().spawnParticle(org.bukkit.Particle.valueOf(plugin.getConfig().getString("particle-settings.tower-happy", "HAPPY_VILLAGER")), towerLoc.clone().add(0.5, 1.2, 0.5), 15, 0.3, 0.3, 0.3, 0.1);

        player.openInventory(getInventory());
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

    private String getTowerDisplayName(Tower.TowerType type) {
        return switch (type) {
            case ARROW -> "Tháp Cung Thủ";
            case LIGHTNING -> "Tháp Sét";
            case FIRE -> "Tháp Hỏa Cầu";
            case FROST -> "Tháp Băng Phong";
            case HEALING -> "Tháp Hồi Phục";
            case SPELL -> "Tháp Phép Thuật";
            case ARTILLERY -> "Tháp Pháo Cao Xạ";
        };
    }

    private ItemStack getTowerInfoItem() {
        Material icon = getTowerMaterialIcon(tower.getType());
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + tower.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Cấp độ hiện tại: " + ChatColor.GREEN + "Cấp " + tower.getLevel() + "/5");
            lore.add(ChatColor.GRAY + "Tầm bắn: " + ChatColor.AQUA + tower.getScanningRadius() + " blocks");
            lore.add(ChatColor.GRAY + "Tốc độ bắn: " + ChatColor.LIGHT_PURPLE + String.format("%.1fs / phát", (tower.getAttackSpeedTicks() * 0.05)));

            if (tower.getType() == Tower.TowerType.HEALING) {
                lore.add(ChatColor.GRAY + "Lượng hồi phục: " + ChatColor.GREEN + tower.getDamage() + " HP");
            } else if (tower.getType() == Tower.TowerType.SPELL) {
                lore.add(ChatColor.GRAY + "Tỉ lệ Buff: " + ChatColor.GREEN + "+" + (int)(tower.getDamage() * 100) + "% DMG");
            } else {
                lore.add(ChatColor.GRAY + "Sát thương gốc: " + ChatColor.RED + tower.getDamage() + " DMG");
            }

            lore.add(" ");
            lore.add(ChatColor.YELLOW + "Đặc tính chiến đấu:");
            lore.add(getTowerSpecialLore(tower.getType(), tower.getLevel()));

            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "NONE");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack getUpgradeButtonItem() {
        int nextLevel = tower.getLevel() + 1;
        if (tower.getLevel() >= 5) {
            return createGuiItem(Material.NETHER_STAR, ChatColor.GOLD + "" + ChatColor.BOLD + "Tháp Đạt Cấp Cực Đại (Cấp 5)", "NONE",
                    ChatColor.GRAY + "Sức mạnh phòng thủ và hiệu ứng tháp đạt mốc tối đa!"
            );
        }

        if (nextLevel > core.getLevel()) {
            return createGuiItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "YÊU CẦU CẤP ĐỘ LÕI (CẤP " + nextLevel + ")", "NONE",
                    ChatColor.GRAY + "Cấp độ tháp không thể vượt quá cấp độ của Lõi Lãnh Thổ.",
                    ChatColor.GRAY + "Cấp độ Lõi hiện tại: Cấp " + core.getLevel()
            );
        }

        double moneyCost = plugin.getConfig().getDouble("tower-settings.upgrade-costs.level-" + nextLevel + ".money", 0.0);
        int shardCost = plugin.getConfig().getInt("tower-settings.upgrade-costs.level-" + nextLevel + ".shards", 0);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Tiến trình nâng cấp: Cấp " + tower.getLevel() + " ➔ " + ChatColor.GREEN + "Cấp " + nextLevel);
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

        return createGuiItem(Material.ANVIL, ChatColor.GREEN + "" + ChatColor.BOLD + "Nâng Cấp Cấp Độ Tháp", "UPGRADE_TOWER", lore.toArray(new String[0]));
    }

    private Material getTowerMaterialIcon(Tower.TowerType type) {
        return switch (type) {
            case ARROW -> Material.SKELETON_SKULL;
            case LIGHTNING -> Material.CREEPER_HEAD;
            case FIRE -> Material.WITHER_SKELETON_SKULL;
            case FROST -> Material.ZOMBIE_HEAD;
            case HEALING -> Material.PIGLIN_HEAD;
            case SPELL -> Material.PLAYER_HEAD;
            case ARTILLERY -> Material.DRAGON_HEAD;
        };
    }

    private String getTowerSpecialLore(Tower.TowerType type, int level) {
        return switch (type) {
            case ARROW -> ChatColor.GRAY + "Bắn mũi tên xuyên thấu tối đa 3 mục tiêu thẳng hàng.";
            case LIGHTNING -> ChatColor.GRAY + "Triệu hồi sấm sét giật diện rộng lan tỏa x" + (4 + level) + " mục tiêu.";
            case FIRE -> ChatColor.GRAY + "Bắn hỏa cầu thiêu đốt và kích nổ hỏa ngục khi mục tiêu tử trận.";
            case FROST -> ChatColor.GRAY + "Tạo luồng gió tuyết làm chậm tốc độ chạy đối thủ 50%.";
            case ARTILLERY -> ChatColor.GRAY + "Xả đạn đại bác nổ cực lớn gây choáng diện rộng (AoE) 1.5s.";
            case HEALING -> ChatColor.GRAY + "Phát sóng hồi sinh lực định kỳ và phản đòn 10% sát thương.";
            case SPELL -> ChatColor.GRAY + "Duy trì vầng hào quang gia tăng sát thương các tháp xung quanh.";
        };
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

    public Tower getTower() {
        return tower;
    }

    public TerritoryCore getCore() {
        return core;
    }
}
