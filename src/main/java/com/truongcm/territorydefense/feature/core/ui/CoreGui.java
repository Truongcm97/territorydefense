package com.truongcm.territorydefense.feature.core.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * GIAO DIỆN ĐIỀU HÀNH LÕI CHÍNH (CORE GUI) - PHIÊN BẢN ĐỘNG V32 (TÁI CẤU TRÚC STATEFUL)
 * Kế thừa CustomHolder giúp tự đóng gói hành vi hiển thị và click chuột.
 */
public class CoreGui extends CustomHolder {

    public enum CoreTab {
        LOGISTICS,
        COMBAT,
        FINANCE
    }

    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final CoreTab activeTab;
    private final NamespacedKey actionKey;

    public CoreGui(TerritoryDefense plugin, TerritoryCore core, CoreTab activeTab) {
        this.plugin = plugin;
        this.core = core;
        this.activeTab = activeTab;
        this.actionKey = new NamespacedKey(plugin, "td_gui_action");
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.DARK_BLUE + "Lõi Lãnh Thổ - " + activeTab.name());

        // Phủ kính xám nền rương thẩm mỹ
        ItemStack backgroundPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, backgroundPane);
        }

        // Vẽ 3 Tab điều hướng cố định tại hàng dưới cùng (Slot 45, 46, 47)
        inv.setItem(45, createTabButton(Material.HOPPER, ChatColor.GREEN + "Phân khu Logistics", CoreTab.LOGISTICS));
        inv.setItem(46, createTabButton(Material.IRON_SWORD, ChatColor.RED + "Phân khu Chiến Tranh", CoreTab.COMBAT));
        inv.setItem(47, createTabButton(Material.GOLD_INGOT, ChatColor.GOLD + "Phân khu Tài Chính", CoreTab.FINANCE));

        // Nút Đóng GUI chung
        inv.setItem(49, createGuiItem(Material.BARRIER, ChatColor.RED + "Đóng Giao Diện", "CLOSE"));

        switch (activeTab) {
            case LOGISTICS -> renderLogisticsTab(inv);
            case COMBAT -> renderCombatTab(inv);
            case FINANCE -> renderFinanceTab(inv);
        }

        return inv;
    }

    private void renderLogisticsTab(Inventory inv) {
        ItemStack fepGauge = new ItemStack(Material.WHEAT);
        ItemMeta fepMeta = fepGauge.getItemMeta();
        if (fepMeta != null) {
            fepMeta.setDisplayName(ChatColor.YELLOW + "Bình Chứa Năng Lượng FEP");
            fepMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Năng lượng hiện có: " + ChatColor.GREEN + String.format("%.1f", core.getFep()) + "/" + core.getMaxFepCapacity() + " FEP",
                    ChatColor.GRAY + "Tốc độ tiêu hao cơ bản: " + ChatColor.RED + "2.0 FEP/giờ",
                    ChatColor.GRAY + "Trạng thái: " + (core.getFep() > 0 ? ChatColor.GREEN + "ĐANG HOẠT ĐỘNG" : ChatColor.RED + "SẬP NGUỒN")
            ));
            fepGauge.setItemMeta(fepMeta);
        }
        inv.setItem(13, fepGauge);

        double hireCost = plugin.getConfig().getDouble("farmer-settings.hire-costs.1", 10000.0);
        inv.setItem(22, createGuiItem(Material.VILLAGER_SPAWN_EGG, ChatColor.GREEN + "Thuê Nông Dân NPC (Farmer)", "HIRE_FARMER",
                ChatColor.GRAY + "Giá thuê: " + ChatColor.GOLD + hireCost + " Xu",
                ChatColor.GRAY + " Farmer sẽ tự động làm ruộng & chăn nuôi nạp FEP."
        ));

        ItemStack depositHelper = createGuiItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, ChatColor.AQUA + "Khu tiếp tế: Thả thức ăn vào đây để nạp FEP", "DEPOSIT_ZONE",
                ChatColor.GRAY + "Cầm nông sản/thức ăn trên tay,",
                ChatColor.GRAY + "sau đó click chuột trái vào đây để nạp FEP!"
        );
        inv.setItem(31, depositHelper);

        inv.setItem(24, createGuiItem(Material.LEATHER_HELMET, ChatColor.YELLOW + "" + ChatColor.BOLD + "Quản Lý Nông Dân", "OPEN_FARMER_LIST",
                ChatColor.GRAY + "Xem danh sách và nâng cấp toàn bộ",
                ChatColor.GRAY + "Nông dân NPC hiện có của Lõi lãnh thổ."
        ));
    }

    private void renderCombatTab(Inventory inv) {
        ItemStack shieldInfo = new ItemStack(Material.SHIELD);
        ItemMeta shieldMeta = shieldInfo.getItemMeta();
        if (shieldMeta != null) {
            shieldMeta.setDisplayName(ChatColor.AQUA + "Lá Chắn Giáp Ảo (Shield)");
            shieldMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Độ bền khiên bảo vệ: " + ChatColor.GREEN + String.format("%.0f", core.getShield()) + "/" + core.getMaxShieldCapacity() + " HP",
                    ChatColor.GRAY + "Tốc độ nạp tối đa: " + ChatColor.YELLOW + "100 HP/giây",
                    ChatColor.GRAY + "Giới hạn tháp canh đặt: " + ChatColor.LIGHT_PURPLE + core.getMaxTowers() + " tháp"
            ));
            shieldInfo.setItemMeta(shieldMeta);
        }
        inv.setItem(13, shieldInfo);

        inv.setItem(11, createGuiItem(Material.REDSTONE_TORCH, ChatColor.RED + "" + ChatColor.BOLD + "Kích Hoạt Raid Chủ Động", "BUY_RAID",
                ChatColor.GRAY + "Chi phí kích hoạt: " + ChatColor.GOLD + "100,000 Xu",
                ChatColor.GRAY + "Triệu hồi cổng không gian quái Raid xâm lược.",
                ChatColor.AQUA + "Mục đích: Farm quái lấy Shards nâng cấp Lõi cực nhanh!",
                ChatColor.RED + "Lưu ý: Không thể mua khi Khiên Hòa Bình đang kích hoạt!"
        ));

        long currentPeaceRemaining = Math.max(0L, plugin.getCoreManager().getPeaceUntil(core.getCoreId()) - System.currentTimeMillis());
        long remainingMins = currentPeaceRemaining / (60 * 1000L);

        inv.setItem(15, createGuiItem(Material.CLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "Bỏ Qua Raid & Khiên 2 Giờ", "SKIP_RAID_PROTECT",
                ChatColor.GRAY + "Chi phí cứu viện: " + ChatColor.GOLD + "250,000 Xu",
                ChatColor.GRAY + "Tiêu biến toàn bộ quái Raid hiện tại lập tức,",
                ChatColor.GRAY + "đồng thời kích hoạt Khiên Hòa Bình bảo vệ trong 2 giờ.",
                ChatColor.YELLOW + "Khiên hiện tại còn: " + ChatColor.AQUA + remainingMins + " phút",
                ChatColor.RED + "Yêu cầu: Chỉ chủ Lõi mới có quyền mua gói cứu viện!"
        ));

        inv.setItem(17, createGuiItem(Material.COMPASS, ChatColor.YELLOW + "" + ChatColor.BOLD + "Quản Lý Tháp Canh Đang Đặt", "OPEN_TOWER_LIST",
                ChatColor.GRAY + "Xem danh sách các Tháp canh hiện có,",
                ChatColor.GRAY + "nâng cấp hoặc định vị tọa độ tháp."
        ));

        inv.setItem(19, createGuiItem(Material.IRON_SWORD, ChatColor.RED + "Chiêu Mộ Lính Cận Chiến (Melee)", "HIRE_MERCENARY_MELEE",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + "50,000 Xu",
                ChatColor.GRAY + "Lính cận chiến hỗ trợ cản đường và tấn công xáp lá cà.",
                ChatColor.YELLOW + "Yêu cầu: Chỉ chủ Lõi mới có quyền chiêu mộ!"
        ));

        inv.setItem(21, createGuiItem(Material.BOW, ChatColor.YELLOW + "Chiêu Mộ Lính Cung Thủ (Archer)", "HIRE_MERCENARY_ARCHER",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + "75,000 Xu",
                ChatColor.GRAY + "Lính tầm xa bắn hạ mục tiêu liên tục từ khoảng cách an toàn.",
                ChatColor.YELLOW + "Yêu cầu: Chỉ chủ Lõi mới có quyền chiêu mộ!"
        ));

        inv.setItem(23, createGuiItem(Material.IRON_GOLEM_SPAWN_EGG, ChatColor.GOLD + "Chiêu Mộ Lính Công Thành (Siege)", "HIRE_MERCENARY_SIEGE",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + "90,000 Xu",
                ChatColor.GRAY + "Lượng máu cực lớn, chống chịu sát thương bảo vệ tháp canh.",
                ChatColor.YELLOW + "Yêu cầu: Chỉ chủ Lõi mới có quyền chiêu mộ!"
        ));

        inv.setItem(25, createGuiItem(Material.GOLDEN_APPLE, ChatColor.LIGHT_PURPLE + "Chiêu Mộ Lính Hỗ Trợ (Support)", "HIRE_MERCENARY_SUPPORT",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + "120,000 Xu",
                ChatColor.GRAY + "Gia tăng chỉ số phòng thủ và hồi phục liên tục cho đồng đội.",
                ChatColor.YELLOW + "Yêu cầu: Chỉ chủ Lõi mới có quyền chiêu mộ!"
        ));

        inv.setItem(28, createGuiItem(Material.SKELETON_SKULL, ChatColor.YELLOW + "Tháp Cung (Skeleton)", "TOWER_ARROW",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + "30,000 Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + "16.0 blocks",
                ChatColor.GRAY + "Đặc tính: Bắn mũi tên xuyên thấu tối đa 3 kẻ địch."
        ));

        inv.setItem(29, createGuiItem(Material.CREEPER_HEAD, ChatColor.GOLD + "Tháp Sét (Creeper)", "TOWER_LIGHTNING",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + "45,000 Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + "12.0 blocks",
                ChatColor.GRAY + "Đặc tính: Triệu hồi sấm sét giật diện rộng lan tỏa."
        ));

        inv.setItem(30, createGuiItem(Material.WITHER_SKELETON_SKULL, ChatColor.RED + "Tháp Hỏa (Blaze)", "TOWER_FIRE",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + "50,000 Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + "10.0 blocks",
                ChatColor.GRAY + "Đặc tính: Bắn hỏa cầu thiêu đốt gây sát thương liên tục."
        ));

        inv.setItem(31, createGuiItem(Material.ZOMBIE_HEAD, ChatColor.AQUA + "Tháp Băng (Stray)", "TOWER_FROST",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + "40,000 Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + "14.0 blocks",
                ChatColor.GRAY + "Đặc tính: Gây sát thương và làm chậm mục tiêu 50% tốc độ."
        ));

        inv.setItem(32, createGuiItem(Material.DRAGON_HEAD, ChatColor.DARK_PURPLE + "Tháp Pháo (Ghast)", "TOWER_ARTILLERY",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + "65,000 Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + "18.0 blocks",
                ChatColor.GRAY + "Đặc tính: Bắn pháo nổ gây sát thương diện rộng (AoE)."
        ));

        inv.setItem(33, createGuiItem(Material.PIGLIN_HEAD, ChatColor.GREEN + "Tháp Hồi Phục (Evoker)", "TOWER_HEALING",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + "55,000 Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + "8.0 blocks",
                ChatColor.GRAY + "Đặc tính: Hồi phục sinh lực liên tục cho đồng minh lân cận."
        ));

        inv.setItem(34, createGuiItem(Material.PLAYER_HEAD, ChatColor.LIGHT_PURPLE + "Tháp Ma Pháp (Witch)", "TOWER_SPELL",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + "70,000 Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + "12.0 blocks",
                ChatColor.GRAY + "Đặc tính: Tăng cường sát thương cho toàn bộ tháp lân cận."
        ));

        inv.setItem(40, createGuiItem(Material.WHITE_BANNER, ChatColor.GOLD + "" + ChatColor.BOLD + "Mua Cờ Công Thành (Siege Flag)", "BUY_SIEGE_FLAG",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + "20,000 Xu",
                ChatColor.GRAY + "Bắt buộc cầm ở tay trái (Off-hand) khi công thành",
                ChatColor.GRAY + "thì mới có thể đập block & phá khiên lõi đối phương.",
                " ",
                ChatColor.YELLOW + "Bùa lợi chiến sự PvP:",
                ChatColor.GREEN + " - Tăng 10% sát thương gây ra",
                ChatColor.GREEN + " - Tăng 20% phòng thủ (giảm 20% sát thương nhận)",
                ChatColor.GRAY + "(Áp dụng cho đồng minh và lính triệu hồi)",
                " ",
                ChatColor.YELLOW + "➔ Click để mua cờ công thành!"
        ));
    }

    private void renderFinanceTab(Inventory inv) {
        int nextLevel = core.getLevel() + 1;
        boolean maxed = core.getLevel() >= 5;

        ItemStack upgradeButton;
        if (maxed) {
            upgradeButton = createGuiItem(Material.NETHER_STAR, ChatColor.GOLD + "Lõi Đạt Cấp Cực Đại (Cấp 5)", "NONE",
                    ChatColor.GRAY + "Toàn bộ ranh giới, tháp và FEP đã kịch trần."
            );
        } else {
            double moneyCost = plugin.getConfig().getDouble("core-upgrades.money-cost-level-" + nextLevel, 500000.0);
            int shardCost = plugin.getConfig().getInt("core-upgrades.shard-cost-level-" + nextLevel, 15);

            upgradeButton = createGuiItem(Material.ANVIL, ChatColor.GREEN + "Nâng Cấp Tiến Trình Lõi", "UPGRADE_CORE",
                    ChatColor.GRAY + "Nâng lên cấp độ: " + ChatColor.YELLOW + nextLevel,
                    ChatColor.GRAY + "Chi phí Xu: " + ChatColor.GOLD + moneyCost + " Xu",
                    ChatColor.GRAY + "Chi phí Shards: " + ChatColor.AQUA + shardCost + " Shards",
                    ChatColor.GRAY + " Nâng cấp để mở rộng ranh giới và giới hạn tháp canh."
            );
        }
        inv.setItem(13, upgradeButton);

        ItemStack retrieveButton = createGuiItem(Material.REDSTONE_BLOCK, ChatColor.RED + "" + ChatColor.BOLD + "Thu Hồi & Di Dời Lõi Lãnh Thổ", "RETRIEVE_CORE",
                ChatColor.GRAY + "Thu hồi Lõi về dạng vật phẩm gốc.",
                ChatColor.GRAY + "Toàn bộ tháp canh và Farmer liên quan sẽ được đóng gói.",
                ChatColor.YELLOW + "Yêu cầu: Hòm đồ phải có ít nhất 1 ô trống.",
                ChatColor.RED + "Lưu ý: Chỉ chủ sở hữu mới có quyền thu hồi!"
        );
        inv.setItem(31, retrieveButton);

        int currentShards = plugin.getCoreManager().getShards(core.getCoreId());
        ItemStack withdrawShardsButton = createGuiItem(Material.PRISMARINE_SHARD, ChatColor.AQUA + "" + ChatColor.BOLD + "Rút Shard Tích Lũy", "WITHDRAW_SHARDS",
                ChatColor.GRAY + "Số lượng tích lũy hiện tại: " + ChatColor.GREEN + currentShards + " Shards",
                ChatColor.GRAY + "Nhấp để rút toàn bộ Shard vật lý ra hòm đồ.",
                ChatColor.YELLOW + "Yêu cầu: Hòm đồ phải có ít nhất 1 ô trống.",
                ChatColor.RED + "Lưu ý: Chỉ chủ sở hữu mới có quyền rút Shard!",
                ChatColor.RED + "Không thể rút khi đang trong trạng thái Chiến Sự/Raid!"
        );
        inv.setItem(33, withdrawShardsButton);
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

            if (action.startsWith("TAB_")) {
                String tabStr = action.substring(4);
                try {
                    CoreTab targetTab = CoreTab.valueOf(tabStr);
                    player.openInventory(new CoreGui(plugin, core, targetTab).getInventory());
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 0.8f, 1.2f);
                } catch (Exception ignored) {}
                return;
            }

            if (action.equalsIgnoreCase("DEPOSIT_ZONE")) {
                handleDepositZone(event, player);
                return;
            }

            if (action.equalsIgnoreCase("HIRE_FARMER")) {
                if (plugin.getFarmerManager().hireNewFarmer(player, core)) {
                    player.closeInventory();
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                }
                return;
            }

            if (action.equalsIgnoreCase("BUY_RAID")) {
                handleBuyRaid(player);
                return;
            }

            if (action.equalsIgnoreCase("SKIP_RAID_PROTECT")) {
                handleSkipRaid(player);
                return;
            }

            if (action.startsWith("HIRE_MERCENARY_")) {
                String mercType = action.substring(15);
                handleHireMercenary(player, mercType);
                return;
            }

            if (action.startsWith("TOWER_")) {
                String towerTypeStr = action.substring(6);
                handleBuyTower(player, towerTypeStr);
                return;
            }

            if (action.equalsIgnoreCase("WITHDRAW_SHARDS")) {
                handleWithdrawShards(player);
                return;
            }

            if (action.equalsIgnoreCase("UPGRADE_CORE")) {
                handleUpgradeCore(player);
                return;
            }

            if (action.equalsIgnoreCase("RETRIEVE_CORE")) {
                handleRetrieveCore(player);
                return;
            }

            if (action.equalsIgnoreCase("BUY_SIEGE_FLAG")) {
                double flagCost = 20000.0;
                if (!plugin.getVaultEconomy().has(player, flagCost)) {
                    player.sendMessage(ChatColor.RED + "Bạn không đủ Xu để mua Cờ Công Thành! Cần: 20,000 Xu.");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                
                if (player.getInventory().firstEmpty() == -1) {
                    player.sendMessage(ChatColor.RED + "Hòm đồ của bạn đã đầy!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                
                plugin.getVaultEconomy().withdrawPlayer(player, flagCost);
                ItemStack flag = plugin.getSiegeSession().createSiegeFlagItem();
                player.getInventory().addItem(flag);
                player.sendMessage(ChatColor.GREEN + "Mua Cờ Công Thành thành công! Tiêu tốn: 20,000 Xu.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("OPEN_FARMER_LIST")) {
                // Kiểm tra xem người click có quyền sở hữu hoặc đồng minh không
                String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
                String coreAlly = core.getAllyId();
                boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());
                boolean isAlly = coreAlly != null && playerAlly != null && coreAlly.equalsIgnoreCase(playerAlly);

                if (!isOwner && !isAlly) {
                    player.sendMessage(ChatColor.RED + "[Bảo vệ] Bạn không có quyền quản lý hay xem Nông dân của Lõi này!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                player.closeInventory();
                player.openInventory(new com.truongcm.territorydefense.feature.logistics.ui.FarmerListGui(plugin, core, player).getInventory());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 1.0f, 1.2f);
                return;
            }

            if (action.equalsIgnoreCase("OPEN_TOWER_LIST")) {
                player.closeInventory();
                player.openInventory(new com.truongcm.territorydefense.feature.combat.tower.ui.TowerListGui(plugin, core, player).getInventory());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 1.0f, 1.2f);
                return;
            }
        }
    }

    private void handleDepositZone(InventoryClickEvent event, Player player) {
        ItemStack cursorItem = event.getCursor();
        if (cursorItem == null || cursorItem.getType() == Material.AIR) {
            player.sendMessage(ChatColor.YELLOW + "[Logistics] Vui lòng cầm thực phẩm ở con trỏ chuột và nhấp chuột trái vào ô này để nạp FEP!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (!isFoodItem(cursorItem.getType())) {
            player.sendMessage(ChatColor.RED + "[Logistics] Vật phẩm này không thể dùng làm thức ăn nạp FEP!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        double fepValue = getFoodFepValue(cursorItem.getType());
        double currentFep = core.getFep();
        double maxFep = core.getMaxFepCapacity();

        if (currentFep >= maxFep) {
            player.sendMessage(ChatColor.RED + "[Logistics] Bình chứa FEP của Lõi đã đầy!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        double newFep = Math.min(maxFep, currentFep + fepValue);
        core.setFep(newFep);
        plugin.getCoreManager().saveAllCores();

        int amount = cursorItem.getAmount();
        if (amount > 1) {
            cursorItem.setAmount(amount - 1);
        } else {
            event.setCursor(null);
        }

        player.sendMessage(ChatColor.GREEN + "[Logistics] Đã nạp thành công: " + ChatColor.YELLOW + cursorItem.getType().name() +
                ChatColor.GREEN + " (+" + fepValue + " FEP). Hiện tại: " + String.format("%.1f", newFep) + "/" + maxFep);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);

        player.openInventory(new CoreGui(plugin, core, CoreTab.LOGISTICS).getInventory());
    }

    private void handleBuyRaid(Player player) {
        double cost = plugin.getConfig().getDouble("raid-settings.purchase-costs.1", 100000.0);

        if (!plugin.getVaultEconomy().has(player, cost)) {
            player.sendMessage(ChatColor.RED + "[Raid] Bạn không đủ Xu để mua đợt quái công thành! Cần: " + String.format("%,.0f", cost) + " Xu.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (isRaidActive(core)) {
            player.sendMessage(ChatColor.RED + "[Raid] Lãnh thổ hiện tại đang có quái vật công thành tấn công!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (plugin.getCoreManager().isUnderPeaceProtection(core.getCoreId())) {
            player.sendMessage(ChatColor.RED + "[Raid] Lãnh thổ của bạn đang được đặt Khiên Hòa Bình, không thể kích hoạt Raid!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        boolean activated = false;
        if (plugin.getRaidSession() != null) {
            plugin.getRaidSession().startRaid(core, true, 1);
            activated = true;
        }

        if (activated) {
            plugin.getVaultEconomy().withdrawPlayer(player, cost);
            player.sendMessage(ChatColor.GREEN + "[Raid] Đã nạp " + String.format("%,.0f", cost) + " Xu! Cổng không gian rạn nứt, quái Raid đang kéo đến...");
            player.playSound(player.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 1.0f, 0.8f);
            player.closeInventory();
        } else {
            player.sendMessage(ChatColor.RED + "[Hệ thống] Không thể kích hoạt đợt tấn công Raid lúc này!");
        }
    }

    private void handleSkipRaid(Player player) {
        double cost = plugin.getConfig().getDouble("raid-settings.skip-costs.1", 250000.0);

        if (!plugin.getVaultEconomy().has(player, cost)) {
            player.sendMessage(ChatColor.RED + "[Khiên] Bạn không đủ Xu để kích hoạt Khiên Bão và Bỏ Qua Raid! Cần: " + String.format("%,.0f", cost) + " Xu.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (!core.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "[Khiên] Bạn không phải chủ sở hữu Lõi để mua Gói Cứu Hộ này!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        plugin.getVaultEconomy().withdrawPlayer(player, cost);

        Location coreLoc = core.getLocation();
        if (coreLoc != null && coreLoc.getWorld() != null) {
            int radius = plugin.getCoreManager().getCoreRadius(core) + 15;
            Collection<Entity> entities = coreLoc.getWorld().getNearbyEntities(coreLoc, radius, 64, radius);
            int removedCount = 0;
            for (Entity entity : entities) {
                if (entity == null) continue;
                if (entity.hasMetadata("td_raid_mob") || entity.hasMetadata("td_npc_attacker")) {
                    entity.remove();
                    removedCount++;
                }
            }
            if (removedCount > 0) {
                plugin.getLogger().info("[TD] Đã dọn sạch " + removedCount + " quái Raid để kích hoạt Skip.");
            }
        }

        if (plugin.getRaidSession() != null) {
            try {
                java.lang.reflect.Method endMethod = plugin.getRaidSession().getClass()
                        .getMethod("stopActiveRaid", TerritoryCore.class);
                endMethod.invoke(plugin.getRaidSession(), core);
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Method endMethod = plugin.getRaidSession().getClass()
                            .getMethod("stopRaid", TerritoryCore.class);
                    endMethod.invoke(plugin.getRaidSession(), core);
                } catch (Exception e2) {
                    try {
                        java.lang.reflect.Method endMethod = plugin.getRaidSession().getClass()
                                .getMethod("endRaid", UUID.class);
                        endMethod.invoke(plugin.getRaidSession(), core.getCoreId());
                    } catch (Exception ignored) {}
                }
            }
        }

        long peaceDuration = 2 * 60 * 60 * 1000L;
        long newPeaceExpiry = System.currentTimeMillis() + peaceDuration;
        plugin.getCoreManager().setPeaceUntil(core.getCoreId(), newPeaceExpiry);
        plugin.getCoreManager().saveAllCores();

        player.sendMessage(ChatColor.GREEN + "[Khiên] Đã nạp thành công Khiên Hòa Bình 2 giờ! Toàn bộ quái Raid hiện tại đã bị phong ấn tiêu biến.");
        player.sendMessage(ChatColor.GRAY + "Lãnh thổ của bạn sẽ bất xâm phạm trước các đợt Raid tự động và PvP trong 2 tiếng.");
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);

        if (coreLoc != null && coreLoc.getWorld() != null) {
            coreLoc.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, coreLoc.add(0.5, 1.0, 0.5), 50, 1.0, 1.0, 1.0, 0.1);
        }

        player.closeInventory();
    }

    private void handleHireMercenary(Player player, String type) {
        String configKey = type.toLowerCase();
        double cost = switch (configKey) {
            case "melee" -> plugin.getConfig().getDouble("mercenary-settings.types.melee.hire-cost", 50000.0);
            case "archer" -> plugin.getConfig().getDouble("mercenary-settings.types.archer.hire-cost", 45000.0);
            case "siege" -> plugin.getConfig().getDouble("mercenary-settings.types.siege.hire-cost", 120000.0);
            case "support" -> plugin.getConfig().getDouble("mercenary-settings.types.support.hire-cost", 60000.0);
            default -> 50000.0;
        };

        if (!plugin.getVaultEconomy().has(player, cost)) {
            player.sendMessage(ChatColor.RED + "Bạn không đủ Xu để thuê lính " + type + "! Cần: " + String.format("%,.0f", cost) + " Xu.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (spawnMercenaryHelper(player, core, type)) {
            plugin.getVaultEconomy().withdrawPlayer(player, cost);
            player.sendMessage(ChatColor.GREEN + "Đã chiêu mộ thành công Lính " + type + " phòng vệ lãnh thổ!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            player.closeInventory();
        }
    }

    private void handleBuyTower(Player player, String towerTypeStr) {
        com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType towerType = null;
        try {
            towerType = com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.valueOf(towerTypeStr.toUpperCase());
        } catch (Exception ignored) {}

        if (towerType == null) return;

        double towerCost = getTowerCost(towerType);
        if (!plugin.getVaultEconomy().has(player, towerCost)) {
            player.sendMessage(ChatColor.RED + "Bạn không đủ Xu để mua Tháp này! Cần: " + String.format("%,.0f", towerCost) + " Xu.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int currentTowers = getPlacedTowersCount(core);
        if (currentTowers >= core.getMaxTowers()) {
            player.sendMessage(ChatColor.RED + "Lãnh thổ đã đạt tới giới hạn Tháp Canh tối đa (" + core.getMaxTowers() + " tháp)!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        boolean success = false;
        if (plugin.getTowerManager() != null) {
            try {
                java.lang.reflect.Method method = plugin.getTowerManager().getClass()
                        .getMethod("giveTowerItem", Player.class, com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.class);
                method.invoke(plugin.getTowerManager(), player, towerType);
                success = true;
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Method method = plugin.getTowerManager().getClass()
                            .getMethod("giveTower", Player.class, com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.class);
                    method.invoke(plugin.getTowerManager(), player, towerType);
                    success = true;
                } catch (Exception e2) {
                    try {
                        java.lang.reflect.Method method = plugin.getTowerManager().getClass()
                                .getMethod("buildTower", Player.class, com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.class, Location.class);
                        method.invoke(plugin.getTowerManager(), player, towerType, player.getLocation());
                        success = true;
                    } catch (Exception e3) {
                        success = giveFallbackTowerItem(player, towerType);
                    }
                }
            }
        }

        if (success) {
            plugin.getVaultEconomy().withdrawPlayer(player, towerCost);
            player.sendMessage(ChatColor.GREEN + "Bạn đã chiêu mộ thành công tháp: " + ChatColor.YELLOW + getTowerDisplayName(towerType));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            player.closeInventory();
        }
    }

    private void handleWithdrawShards(Player player) {
        String playerAllyId = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAllyId = plugin.getCoreManager().getCoreAlliance(core);

        if (isRaidActive(core) || (plugin.getSiegeSession() != null && plugin.getSiegeSession().isAtWar(playerAllyId, coreAllyId))) {
            player.sendMessage(ChatColor.RED + "[Bảo vệ] Quốc gia đang trong thời khắc chiến sự hoặc có đợt Raid! Khóa chặt bộ chứa Shards chống tẩu tán tài sản!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (!core.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "[Bảo vệ] Bạn không phải là chủ sở hữu Lõi lãnh thổ này!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int shards = plugin.getCoreManager().getShards(core.getCoreId());
        if (shards <= 0) {
            player.sendMessage(ChatColor.RED + "[Tài chính] Kho tích trữ Shards hiện tại đang trống rỗng (0 Shards)!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "[Tài chính] Hòm đồ của bạn đã đầy! Vui lòng dọn trống ít nhất 1 ô.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ItemStack shardItem = new ItemStack(Material.PRISMARINE_SHARD, shards);
        ItemMeta meta = shardItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Mảnh Không Gian (Shard)");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Nguyên liệu quý hiếm thu hoạch từ các đợt Raid.",
                    ChatColor.YELLOW + "Sử dụng để nâng cấp Lõi Lãnh Thổ lên cấp độ cao hơn."
            ));
            NamespacedKey shardPdcKey = PDCKeys.IS_SHARD_ITEM;
            meta.getPersistentDataContainer().set(shardPdcKey, PersistentDataType.BYTE, (byte) 1);
            shardItem.setItemMeta(meta);
        }

        plugin.getCoreManager().setShards(core.getCoreId(), 0);
        plugin.getCoreManager().saveAllCores();

        player.getInventory().addItem(shardItem);
        player.sendMessage(ChatColor.GREEN + "[Tài chính] Rút thành công " + ChatColor.YELLOW + shards + " Shards" + ChatColor.GREEN + " tích lũy vào túi đồ cá nhân!");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        player.openInventory(new CoreGui(plugin, core, CoreTab.FINANCE).getInventory());
    }

    private void handleUpgradeCore(Player player) {
        int nextLevel = core.getLevel() + 1;

        double moneyCost = plugin.getConfig().getDouble("core-settings.levels." + nextLevel + ".upgrade-cost-money", 500000.0);
        int shardCost = plugin.getConfig().getInt("core-settings.levels." + nextLevel + ".upgrade-cost-shards", 15);

        if (!plugin.getVaultEconomy().has(player, moneyCost)) {
            player.sendMessage(ChatColor.RED + "[Nâng cấp] Bạn không đủ Xu để thực hiện nâng cấp Lõi! Cần: " + String.format("%,.0f", moneyCost) + " Xu.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int playerShards = plugin.getCoreManager().getShards(core.getCoreId());
        if (playerShards < shardCost) {
            player.sendMessage(ChatColor.RED + "[Nâng cấp] Lõi không tích lũy đủ Shards để nâng cấp! Cần: " + shardCost + " Shards (Hiện có trong Lõi: " + playerShards + ").");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (core.getLevel() < 5) {
            plugin.getVaultEconomy().withdrawPlayer(player, moneyCost);
            plugin.getCoreManager().setShards(core.getCoreId(), playerShards - shardCost);

            core.setLevel(nextLevel);
            plugin.getCoreManager().saveAllCores();

            player.sendMessage(ChatColor.GREEN + "Chúc mừng! Lõi Lãnh thổ của bạn đã được nâng cấp lên Cấp " + nextLevel);
            player.sendMessage(ChatColor.GRAY + "Chi phí thanh toán: " + ChatColor.GOLD + String.format("%,.0f", moneyCost) + " Xu " + ChatColor.GRAY + "& " + ChatColor.AQUA + shardCost + " Shards");
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            player.openInventory(new CoreGui(plugin, core, CoreTab.FINANCE).getInventory());
        }
    }

    private void handleRetrieveCore(Player player) {
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "Hòm đồ của bạn đã đầy! Vui lòng dọn trống ít nhất 1 ô để thu hồi Lõi.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (plugin.getCoreManager().removeCore(core.getLocation(), player, true)) {
            player.closeInventory();
        }
    }

    private boolean isFoodItem(Material material) {
        return material != null && (material.isEdible() || material == Material.WHEAT || material == Material.PUMPKIN);
    }

    private double getFoodFepValue(Material material) {
        return switch (material) {
            case WHEAT -> 5.0;
            case PUMPKIN -> 8.0;
            case BREAD -> 15.0;
            case CARROT, POTATO -> 4.0;
            case BAKED_POTATO -> 10.0;
            case COOKED_BEEF, COOKED_PORKCHOP -> 25.0;
            case COOKED_CHICKEN -> 18.0;
            case APPLE -> 10.0;
            case GOLDEN_APPLE -> 50.0;
            default -> 5.0;
        };
    }

    private boolean isRaidActive(TerritoryCore core) {
        if (plugin.getRaidSession() == null || core == null) return false;
        try {
            Object activeRaid = plugin.getRaidSession().getClass()
                    .getMethod("getActiveRaid", TerritoryCore.class)
                    .invoke(plugin.getRaidSession(), core);
            if (activeRaid != null) {
                return (boolean) activeRaid.getClass().getMethod("isRunning").invoke(activeRaid);
            }
        } catch (Exception e1) {
            try {
                Object activeRaid = plugin.getRaidSession().getClass()
                        .getMethod("getActiveRaid", java.util.UUID.class)
                        .invoke(plugin.getRaidSession(), core.getCoreId());
                if (activeRaid != null) {
                    return (boolean) activeRaid.getClass().getMethod("isRunning").invoke(activeRaid);
                }
            } catch (Exception e2) {
                try {
                    return (boolean) plugin.getRaidSession().getClass()
                            .getMethod("isRaidActive", TerritoryCore.class)
                            .invoke(plugin.getRaidSession(), core);
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean spawnMercenaryHelper(Player player, TerritoryCore core, String type) {
        if (plugin.getMercenaryAI() == null) return false;
        try {
            return (boolean) plugin.getMercenaryAI().getClass()
                    .getMethod("spawnDefender", Player.class, core.getClass(), String.class)
                    .invoke(plugin.getMercenaryAI(), player, core, type);
        } catch (Exception e1) {
            try {
                for (java.lang.reflect.Method method : plugin.getMercenaryAI().getClass().getMethods()) {
                    if (method.getName().equals("spawnDefender") && method.getParameterCount() == 3) {
                        Class<?>[] params = method.getParameterTypes();
                        if (params[0].equals(Player.class) && params[1].isAssignableFrom(core.getClass()) && params[2].isEnum()) {
                            try {
                                Class<? extends Enum> enumClass = (Class<? extends Enum>) params[2];
                                Object enumVal = Enum.valueOf(enumClass, type);
                                return (boolean) method.invoke(plugin.getMercenaryAI(), player, core, enumVal);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}

            try {
                return (boolean) plugin.getMercenaryAI().getClass()
                        .getMethod("spawnDefender", Player.class, core.getClass())
                        .invoke(plugin.getMercenaryAI(), player, core);
            } catch (Exception ignored) {}
        }
        return false;
    }

    private double getTowerCost(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType type) {
        return switch (type) {
            case ARROW -> 30000.0;
            case LIGHTNING -> 45000.0;
            case FIRE -> 50000.0;
            case FROST -> 40000.0;
            case ARTILLERY -> 65000.0;
            case HEALING -> 55000.0;
            case SPELL -> 70000.0;
        };
    }

    private String getTowerDisplayName(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType type) {
        return switch (type) {
            case ARROW -> "Tháp Cung (Skeleton)";
            case LIGHTNING -> "Tháp Sét (Zeus)";
            case FIRE -> "Tháp Hỏa (Hellfire)";
            case FROST -> "Tháp Băng (Blizzard)";
            case ARTILLERY -> "Tháp Pháo (Artillery)";
            case HEALING -> "Tháp Hồi Phục (Sanctuary)";
            case SPELL -> "Tháp Ma Pháp (Archmage)";
        };
    }

    private int getPlacedTowersCount(TerritoryCore core) {
        if (plugin.getTowerManager() == null) return 0;
        try {
            return (int) plugin.getTowerManager().getClass()
                    .getMethod("getTowersCount", TerritoryCore.class)
                    .invoke(plugin.getTowerManager(), core);
        } catch (Exception e) {
            try {
                Collection<?> activeTowers = (Collection<?>) plugin.getTowerManager().getClass()
                        .getMethod("getActiveTowers", TerritoryCore.class)
                        .invoke(plugin.getTowerManager(), core);
                return activeTowers != null ? activeTowers.size() : 0;
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private boolean giveFallbackTowerItem(Player player, com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType type) {
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "Hòm đồ của bạn đã đầy!");
            return false;
        }

        ItemStack item = null;
        if (plugin.getTowerManager() != null) {
            try {
                java.lang.reflect.Method createItemMethod = plugin.getTowerManager().getClass()
                        .getMethod("createTowerItem", com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.class);
                item = (ItemStack) createItemMethod.invoke(plugin.getTowerManager(), type);
            } catch (Exception ignored) {}
        }

        if (item == null) {
            Material blockMat = switch (type) {
                case ARROW -> Material.SKELETON_SKULL;
                case LIGHTNING -> Material.CREEPER_HEAD;
                case FIRE -> Material.WITHER_SKELETON_SKULL;
                case FROST -> Material.ZOMBIE_HEAD;
                case HEALING -> Material.PIGLIN_HEAD;
                case SPELL -> Material.PLAYER_HEAD;
                case ARTILLERY -> Material.DRAGON_HEAD;
            };
            item = new ItemStack(blockMat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + getTowerDisplayName(type));
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Đặt khối này trong lãnh thổ để kích hoạt Tháp Canh.",
                        ChatColor.AQUA + "Tháp tự động bắn hạ quái vật và kẻ địch.",
                        ChatColor.RED + "Lưu ý: Chỉ chủ lãnh thổ mới có quyền đặt tháp!"
                ));
                meta.getPersistentDataContainer().set(PDCKeys.TOWER_TYPE, PersistentDataType.STRING, type.name());
                item.setItemMeta(meta);
            }
        }

        player.getInventory().addItem(item);
        return true;
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

    private ItemStack createTabButton(Material material, String name, CoreTab tab) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            if (tab == activeTab) {
                lore.add(ChatColor.GREEN + "● ĐANG ĐƯỢC CHỌN");
            } else {
                lore.add(ChatColor.GRAY + "Click để chuyển sang Tab này");
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "TAB_" + tab.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    public TerritoryCore getCore() { return core; }
    public CoreTab getActiveTab() { return activeTab; }
}
