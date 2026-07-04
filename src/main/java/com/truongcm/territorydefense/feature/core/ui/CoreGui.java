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
        if (core.isDisabled()) {
            Inventory inv = Bukkit.createInventory(this, 27, ChatColor.RED + "LÕI VÔ HIỆU HÓA - CỨU HỘ");
            ItemStack backgroundPane = createGuiItem(Material.RED_STAINED_GLASS_PANE, " ", "NONE");
            for (int i = 0; i < 27; i++) {
                inv.setItem(i, backgroundPane);
            }

            long remainingMs = core.getDisabledUntil() - System.currentTimeMillis();
            long mins = Math.max(0, remainingMs / (60 * 1000L));
            long secs = Math.max(0, (remainingMs % (60 * 1000L)) / 1000L);
            String remainingTimeStr = String.format("%02d:%02d", mins, secs);

            inv.setItem(11, createGuiItem(Material.OBSIDIAN, ChatColor.RED + "" + ChatColor.BOLD + "Lõi Đang Bị Sập Nguồn", "NONE",
                    ChatColor.GRAY + "Trạng thái: " + ChatColor.RED + "Đang vô hiệu hóa",
                    ChatColor.GRAY + "Tự động phục hồi sau: " + ChatColor.YELLOW + remainingTimeStr,
                    ChatColor.GRAY + " ",
                    ChatColor.GRAY + "Mọi tháp phòng thủ và nông dân NPC",
                    ChatColor.GRAY + "đều đã dừng hoạt động hoàn toàn."
            ));

            inv.setItem(13, createGuiItem(Material.NETHER_STAR, ChatColor.GREEN + "" + ChatColor.BOLD + "Kích Hoạt Lập Tức", "REACTIVATE_CORE",
                    ChatColor.GRAY + "Chi phí kích hoạt: " + ChatColor.GOLD + String.format("%,.0f", core.getReactivateCost()) + " Xu",
                    ChatColor.GRAY + " ",
                    ChatColor.YELLOW + "➔ Click để thanh toán cứu hộ Lõi ngay lập tức!"
            ));

            inv.setItem(15, createGuiItem(Material.BARRIER, ChatColor.RED + "Thoát Giao Diện", "CLOSE"));

            return inv;
        }

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

        // Nút Đóng GUI chung (Slot 53)
        inv.setItem(53, createGuiItem(Material.BARRIER, ChatColor.RED + "Đóng Giao Diện", "CLOSE"));

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

        com.truongcm.territorydefense.feature.logistics.NPCBuilder activeBuilder = plugin.getBuilderManager().getOrCreateBuilder(core.getCoreId());
        int currentLevel = core.getBuilderLevel();
        int currentSpeed = switch (currentLevel) {
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 15;
            case 5 -> 25;
            default -> 2;
        };
        inv.setItem(20, createGuiItem(Material.MUD_BRICKS, ChatColor.GOLD + "Quản Lý & Nâng Cấp 7Gao (Cấp " + currentLevel + ")", "OPEN_BUILDER_UPGRADE",
                ChatColor.GRAY + "Trạng thái: " + (activeBuilder.isRebuilding() ? ChatColor.GREEN + "Đang xây dựng" : ChatColor.YELLOW + "Rảnh rỗi"),
                ChatColor.GRAY + "Tốc độ xây dựng: " + ChatColor.AQUA + currentSpeed + " block/giây",
                " ",
                ChatColor.YELLOW + "➔ Nhấp để mở bảng Nâng cấp & Dừng sửa chữa 7Gao."
            ));

        ItemStack depositHelper = createGuiItem(Material.CHEST, ChatColor.AQUA + "" + ChatColor.BOLD + "Kho Thực Phẩm Lõi (54 Ô)", "OPEN_FOOD_WAREHOUSE",
                ChatColor.GRAY + "Bấm vào để mở kho thực phẩm trung chuyển.",
                ChatColor.GRAY + "Nông dân và người chơi có thể nạp thức ăn vào.",
                ChatColor.GRAY + "Lõi sẽ tự chuyển hóa thức ăn thành FEP dần dần."
        );
        inv.setItem(31, depositHelper);

        ItemStack rebuildHelper = createGuiItem(Material.BRICKS, ChatColor.YELLOW + "" + ChatColor.BOLD + "Kho Vật Liệu Tái Thiết (90 Ô / 2 Trang)", "OPEN_REBUILD_WAREHOUSE",
                ChatColor.GRAY + "Bấm vào để mở rương nguyên liệu tái thiết phân trang.",
                ChatColor.GRAY + "Người chơi bỏ các khối block xây dựng vào đây.",
                ChatColor.GRAY + "NPC Mason sẽ lấy nguyên liệu từ 2 trang để sửa chữa."
        );
        inv.setItem(29, rebuildHelper);

        inv.setItem(24, createGuiItem(Material.LEATHER_HELMET, ChatColor.YELLOW + "" + ChatColor.BOLD + "Quản Lý Nông Dân", "OPEN_FARMER_LIST",
                ChatColor.GRAY + "Xem danh sách và nâng cấp toàn bộ",
                ChatColor.GRAY + "Nông dân NPC hiện có của Lõi lãnh thổ."
        ));

        inv.setItem(40, createGuiItem(Material.ANVIL, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Tái Thiết Lãnh Thổ", "TRIGGER_PRE_RAID_REBUILD",
                ChatColor.GRAY + "Khôi phục lại toàn bộ các khối block bị tàn phá",
                ChatColor.GRAY + "dựa trên ảnh chụp tự động trước trận Raid gần nhất."
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

        double buyRaidCost = plugin.getConfig().getDouble("raid-settings.purchase-costs.1", 100000.0);
        String buyRaidCostStr = String.format("%,.0f", buyRaidCost);

        inv.setItem(11, createGuiItem(Material.REDSTONE_TORCH, ChatColor.RED + "" + ChatColor.BOLD + "Kích Hoạt Raid Chủ Động", "BUY_RAID",
                ChatColor.GRAY + "Chi phí kích hoạt: " + ChatColor.GOLD + buyRaidCostStr + " Xu",
                ChatColor.GRAY + "Triệu hồi cổng không gian quái Raid xâm lược.",
                ChatColor.AQUA + "Mục đích: Farm quái lấy Shards nâng cấp Lõi cực nhanh!",
                ChatColor.RED + "Lưu ý: Không thể mua khi Khiên Hòa Bình đang kích hoạt!"
        ));

        long currentPeaceRemaining = Math.max(0L, plugin.getCoreManager().getPeaceUntil(core.getCoreId()) - System.currentTimeMillis());
        long remainingMins = currentPeaceRemaining / (60 * 1000L);

        double skipDurationHours = plugin.getConfig().getDouble("raid-settings.skip-shield-duration-hours", 2.0);
        double skipCost = plugin.getConfig().getDouble("raid-settings.skip-costs.1", 250000.0);
        String skipCostStr = String.format("%,.0f", skipCost);

        inv.setItem(15, createGuiItem(Material.CLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "Bỏ Qua Raid & Khiên " + String.format("%.1f", skipDurationHours) + " Giờ", "SKIP_RAID_PROTECT",
                ChatColor.GRAY + "Chi phí cứu viện: " + ChatColor.GOLD + skipCostStr + " Xu",
                ChatColor.GRAY + "Tiêu biến toàn bộ quái Raid hiện tại lập tức,",
                ChatColor.GRAY + "đồng thời kích hoạt Khiên Hòa Bình bảo vệ trong " + String.format("%.1f", skipDurationHours) + " giờ.",
                ChatColor.YELLOW + "Khiên hiện tại còn: " + ChatColor.AQUA + remainingMins + " phút",
                ChatColor.RED + "Yêu cầu: Chỉ chủ Lõi mới có quyền mua gói cứu viện!"
        ));

        inv.setItem(17, createGuiItem(Material.COMPASS, ChatColor.YELLOW + "" + ChatColor.BOLD + "Quản Lý Tháp Canh Đang Đặt", "OPEN_TOWER_LIST",
                ChatColor.GRAY + "Xem danh sách các Tháp canh hiện có,",
                ChatColor.GRAY + "nâng cấp hoặc định vị tọa độ tháp."
        ));

        double costMelee = plugin.getConfig().getDouble("mercenary-settings.types.melee.hire-cost", 50000.0);
        String nameMelee = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("mercenary-settings.types.melee.display-name", "&6Lính Cận Chiến (Iron Golem)"));

        double costArcher = plugin.getConfig().getDouble("mercenary-settings.types.archer.hire-cost", 45000.0);
        String nameArcher = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("mercenary-settings.types.archer.display-name", "&aLính Cung Thủ (Skeleton)"));

        double costSiege = plugin.getConfig().getDouble("mercenary-settings.types.siege.hire-cost", 120000.0);
        String nameSiege = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("mercenary-settings.types.siege.display-name", "&cKỵ Binh Phá Thành (Ravager)"));

        double costSupport = plugin.getConfig().getDouble("mercenary-settings.types.support.hire-cost", 60000.0);
        String nameSupport = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("mercenary-settings.types.support.display-name", "&bLính Hỗ Trợ (Allay)"));

        inv.setItem(19, createGuiItem(Material.IRON_SWORD, nameMelee, "HIRE_MERCENARY_MELEE",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + String.format("%,.0f", costMelee) + " Xu",
                ChatColor.GRAY + "Lính cận chiến hỗ trợ cản đường và tấn công xáp lá cà.",
                ChatColor.YELLOW + "Yêu cầu: Chỉ chủ Lõi mới có quyền chiêu mộ!"
        ));

        inv.setItem(21, createGuiItem(Material.BOW, nameArcher, "HIRE_MERCENARY_ARCHER",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + String.format("%,.0f", costArcher) + " Xu",
                ChatColor.GRAY + "Lính tầm xa bắn hạ mục tiêu liên tục từ khoảng cách an toàn.",
                ChatColor.YELLOW + "Yêu cầu: Chỉ chủ Lõi mới có quyền chiêu mộ!"
        ));

        inv.setItem(23, createGuiItem(Material.IRON_GOLEM_SPAWN_EGG, nameSiege, "HIRE_MERCENARY_SIEGE",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + String.format("%,.0f", costSiege) + " Xu",
                ChatColor.GRAY + "Lượng máu cực lớn, chống chịu sát thương bảo vệ tháp canh.",
                ChatColor.YELLOW + "Yêu cầu: Chỉ chủ Lõi mới có quyền chiêu mộ!"
        ));

        inv.setItem(25, createGuiItem(Material.GOLDEN_APPLE, nameSupport, "HIRE_MERCENARY_SUPPORT",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + String.format("%,.0f", costSupport) + " Xu",
                ChatColor.GRAY + "Gia tăng chỉ số phòng thủ và hồi phục liên tục cho đồng đội.",
                ChatColor.YELLOW + "Yêu cầu: Chỉ chủ Lõi mới có quyền chiêu mộ!"
        ));

        double costArrow = getTowerCost(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.ARROW);
        String nameArrow = getTowerDisplayName(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.ARROW);
        double rArrow = plugin.getConfig().getDouble("tower-settings.types.arrow.scanning-radius", 16.0);

        double costLightning = getTowerCost(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.LIGHTNING);
        String nameLightning = getTowerDisplayName(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.LIGHTNING);
        double rLightning = plugin.getConfig().getDouble("tower-settings.types.lightning.scanning-radius", 12.0);

        double costFire = getTowerCost(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.FIRE);
        String nameFire = getTowerDisplayName(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.FIRE);
        double rFire = plugin.getConfig().getDouble("tower-settings.types.fire.scanning-radius", 10.0);

        double costFrost = getTowerCost(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.FROST);
        String nameFrost = getTowerDisplayName(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.FROST);
        double rFrost = plugin.getConfig().getDouble("tower-settings.types.frost.scanning-radius", 14.0);

        double costArtillery = getTowerCost(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.ARTILLERY);
        String nameArtillery = getTowerDisplayName(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.ARTILLERY);
        double rArtillery = plugin.getConfig().getDouble("tower-settings.types.artillery.scanning-radius", 18.0);

        double costHealing = getTowerCost(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.HEALING);
        String nameHealing = getTowerDisplayName(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.HEALING);
        double rHealing = plugin.getConfig().getDouble("tower-settings.types.healing.scanning-radius", 8.0);

        double costSpell = getTowerCost(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.SPELL);
        String nameSpell = getTowerDisplayName(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType.SPELL);
        double rSpell = plugin.getConfig().getDouble("tower-settings.types.spell.scanning-radius", 12.0);

        inv.setItem(28, createGuiItem(Material.SKELETON_SKULL, nameArrow, "TOWER_ARROW",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + String.format("%,.0f", costArrow) + " Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + String.format("%.1f", rArrow) + " blocks",
                ChatColor.GRAY + "Đặc tính: Bắn mũi tên xuyên thấu tối đa 3 kẻ địch."
        ));

        inv.setItem(29, createGuiItem(Material.CREEPER_HEAD, nameLightning, "TOWER_LIGHTNING",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + String.format("%,.0f", costLightning) + " Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + String.format("%.1f", rLightning) + " blocks",
                ChatColor.GRAY + "Đặc tính: Triệu hồi sấm sét giật diện rộng lan tỏa."
        ));

        inv.setItem(30, createGuiItem(Material.WITHER_SKELETON_SKULL, nameFire, "TOWER_FIRE",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + String.format("%,.0f", costFire) + " Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + String.format("%.1f", rFire) + " blocks",
                ChatColor.GRAY + "Đặc tính: Bắn hỏa cầu thiêu đốt gây sát thương liên tục."
        ));

        inv.setItem(31, createGuiItem(Material.ZOMBIE_HEAD, nameFrost, "TOWER_FROST",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + String.format("%,.0f", costFrost) + " Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + String.format("%.1f", rFrost) + " blocks",
                ChatColor.GRAY + "Đặc tính: Gây sát thương và làm chậm mục tiêu 50% tốc độ."
        ));

        inv.setItem(32, createGuiItem(Material.DRAGON_HEAD, nameArtillery, "TOWER_ARTILLERY",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + String.format("%,.0f", costArtillery) + " Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + String.format("%.1f", rArtillery) + " blocks",
                ChatColor.GRAY + "Đặc tính: Bắn pháo nổ gây sát thương diện rộng (AoE)."
        ));

        inv.setItem(33, createGuiItem(Material.PIGLIN_HEAD, nameHealing, "TOWER_HEALING",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + String.format("%,.0f", costHealing) + " Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + String.format("%.1f", rHealing) + " blocks",
                ChatColor.GRAY + "Đặc tính: Hồi phục sinh lực liên tục cho đồng minh lân cận."
        ));

        inv.setItem(34, createGuiItem(Material.PLAYER_HEAD, nameSpell, "TOWER_SPELL",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + String.format("%,.0f", costSpell) + " Xu",
                ChatColor.GRAY + "Tầm bắn: " + ChatColor.GREEN + String.format("%.1f", rSpell) + " blocks",
                ChatColor.GRAY + "Đặc tính: Tăng cường sát thương cho toàn bộ tháp lân cận."
        ));

        double siegeFlagCost = plugin.getConfig().getDouble("siege-settings.siege-flag-cost", 20000.0);
        inv.setItem(40, createGuiItem(Material.WHITE_BANNER, ChatColor.GOLD + "" + ChatColor.BOLD + "Mua Cờ Công Thành (Siege Flag)", "BUY_SIEGE_FLAG",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + String.format("%,.0f", siegeFlagCost) + " Xu",
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
            double moneyCost = plugin.getConfig().getDouble("core-settings.levels." + nextLevel + ".upgrade-cost-money", 500000.0);
            int shardCost = plugin.getConfig().getInt("core-settings.levels." + nextLevel + ".upgrade-cost-shards", 15);

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
                ChatColor.YELLOW + "Yêu cầu: Hòm đồ có chỗ trống.",
                ChatColor.RED + "Lưu ý: Chỉ chủ sở hữu mới có quyền rút Shard!",
                ChatColor.RED + "Không thể rút khi đang trong trạng thái Chiến Sự/Raid!"
        );
        inv.setItem(33, withdrawShardsButton);

        ItemStack depositShardsButton = createGuiItem(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Nộp Shard Vào Lõi", "DEPOSIT_SHARDS",
                ChatColor.GRAY + "Nhấp để nộp toàn bộ Shard vật lý từ hòm đồ",
                ChatColor.GRAY + "vào kho tích lũy của Lõi lãnh thổ.",
                ChatColor.YELLOW + "Yêu cầu: Có Shard hợp lệ trong người.",
                ChatColor.RED + "Lưu ý: Chỉ chủ sở hữu mới có quyền nộp Shard!"
        );
        inv.setItem(35, depositShardsButton);
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

            if (action.equalsIgnoreCase("REACTIVATE_CORE")) {
                handleReactivateCore(player);
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

            if (action.equalsIgnoreCase("OPEN_FOOD_WAREHOUSE")) {
                player.openInventory(core.getFoodWarehouse());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("OPEN_REBUILD_WAREHOUSE")) {
                player.openInventory(new com.truongcm.territorydefense.feature.logistics.ui.RebuildWarehouseGui(plugin, core, 0).getInventory());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("OPEN_BUILDER_UPGRADE")) {
                player.openInventory(new com.truongcm.territorydefense.feature.logistics.ui.NPCBuilderGui(plugin, core).getInventory());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("TRIGGER_PRE_RAID_REBUILD")) {
                player.openInventory(new com.truongcm.territorydefense.feature.logistics.ui.RebuildConfirmGui(plugin, core, null, "Danh Sách Bản Vẽ", -2, 0, false).getInventory());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
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

            if (action.equalsIgnoreCase("DEPOSIT_SHARDS")) {
                handleDepositShards(player);
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
                double flagCost = plugin.getConfig().getDouble("siege-settings.siege-flag-cost", 20000.0);
                if (!plugin.getVaultEconomy().has(player, flagCost)) {
                    player.sendMessage(ChatColor.RED + "Bạn không đủ Xu để mua Cờ Công Thành! Cần: " + String.format("%,.0f", flagCost) + " Xu.");
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
                player.sendMessage(ChatColor.GREEN + "Mua Cờ Công Thành thành công! Tiêu tốn: " + String.format("%,.0f", flagCost) + " Xu.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("OPEN_FARMER_LIST")) {
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

        double fepValue = plugin.getFepManager().getFoodFepValue(cursorItem.getType());
        double currentFep = core.getFep();
        double maxFep = core.getMaxFepCapacity();

        if (currentFep >= maxFep) {
            player.sendMessage(ChatColor.RED + "[Logistics] Bình chứa FEP của Lõi đã đầy!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        double missingFep = maxFep - currentFep;
        int requiredAmount = (int) Math.ceil(missingFep / fepValue);
        int consumedAmount = Math.min(cursorItem.getAmount(), requiredAmount);

        double fepAdded = consumedAmount * fepValue;
        double newFep = Math.min(maxFep, currentFep + fepAdded);
        core.setFep(newFep);
        plugin.getCoreManager().saveAllCores();

        int remaining = cursorItem.getAmount() - consumedAmount;
        if (remaining > 0) {
            cursorItem.setAmount(remaining);
        } else {
            event.setCursor(null);
        }

        player.sendMessage(ChatColor.GREEN + "[Logistics] Đã nạp thành công: " + ChatColor.YELLOW + consumedAmount + "x " + cursorItem.getType().name() +
                ChatColor.GREEN + " (+" + String.format("%.1f", fepAdded) + " FEP). Hiện tại: " + String.format("%.1f", newFep) + "/" + maxFep);
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

        // Cho phép player call raid chủ động bỏ qua Khiên Hòa Bình
        /*
        if (plugin.getCoreManager().isUnderPeaceProtection(core.getCoreId())) {
            player.sendMessage(ChatColor.RED + "[Raid] Lãnh thổ của bạn đang được đặt Khiên Hòa Bình, không thể kích hoạt Raid!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        */

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
            int radius = plugin.getCoreManager().getCoreRadius(core) + 150;
            Collection<Entity> entities = coreLoc.getWorld().getNearbyEntities(coreLoc, radius, 128, radius);
            int removedCount = 0;
            for (Entity entity : entities) {
                if (entity == null) continue;
                boolean isRaidMob = entity.hasMetadata("td_raid_mob") || 
                                    entity.hasMetadata("td_npc_attacker") ||
                                    (PDCKeys.RAID_MOB_TAG != null && entity.getPersistentDataContainer().has(PDCKeys.RAID_MOB_TAG, PersistentDataType.BYTE));
                if (isRaidMob) {
                    entity.remove();
                    removedCount++;
                }
            }
            if (removedCount > 0) {
                plugin.getLogger().info("[TD] Đã dọn sạch " + removedCount + " quái Raid để kích hoạt Skip.");
            }
        }

        if (plugin.getRaidSession() != null) {
            plugin.getRaidSession().endRaid(core, false);
        }

        double durationHours = plugin.getConfig().getDouble("raid-settings.skip-shield-duration-hours", 2.0);
        long peaceDuration = (long) (durationHours * 60.0 * 60.0 * 1000.0);
        long newPeaceExpiry = System.currentTimeMillis() + peaceDuration;
        plugin.getCoreManager().setPeaceUntil(core.getCoreId(), newPeaceExpiry);
        plugin.getCoreManager().saveAllCores();

        String hoursStr = (durationHours % 1 == 0) ? String.format("%.0f", durationHours) : String.format("%.1f", durationHours);
        player.sendMessage(ChatColor.GREEN + "[Khiên] Đã nạp thành công Khiên Hòa Bình " + hoursStr + " giờ! Toàn bộ quái Raid hiện tại đã bị phong ấn tiêu biến.");
        player.sendMessage(ChatColor.GRAY + "Lãnh thổ của bạn sẽ bất xâm phạm trước các đợt Raid tự động và PvP trong " + hoursStr + " tiếng.");
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

        // Kiểm tra giới hạn số lượng lính đánh thuê theo cấu hình OTR / Merge
        int currentCount = plugin.getMercenaryAI() != null ? plugin.getMercenaryAI().getActiveMercenariesCount(core) : 0;
        int limit = plugin.getConfig().getInt("mercenary-settings.limits-per-core." + core.getLevel(), 0);
        if (core.isMerged()) {
            limit = plugin.getConfig().getInt("mercenary-settings.hard-cap-after-merge", 15);
        }

        if (currentCount >= limit) {
            player.sendMessage(ChatColor.RED + "Lãnh thổ đã đạt tới giới hạn Lính Đánh Thuê tối đa (" + currentCount + "/" + limit + " lính)!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

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

    public static ItemStack createSecureShard(int amount) {
        ItemStack shard = new ItemStack(Material.PRISMARINE_SHARD, amount);
        ItemMeta meta = shard.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Mảnh Vỡ Không Gian (Shard)");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Nguyên liệu nâng cấp hệ thống lãnh thổ.",
                    ChatColor.YELLOW + "Sử dụng để nộp vào Lõi và nâng cấp Lõi Lãnh Thổ."
            ));
            meta.getPersistentDataContainer().set(PDCKeys.IS_SHARD_ITEM, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(PDCKeys.SECURE_ITEM_ID, PersistentDataType.STRING, "TD_SECURE_SHARD");
            shard.setItemMeta(meta);
        }
        return shard;
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

        // Tính toán các ô trống cần thiết để chia các stack 64
        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }

        List<ItemStack> itemsToGive = new ArrayList<>();
        int temp = shards;
        while (temp > 0) {
            int amount = Math.min(temp, 64);
            itemsToGive.add(createSecureShard(amount));
            temp -= amount;
        }

        if (emptySlots < itemsToGive.size()) {
            player.sendMessage(ChatColor.RED + "[Tài chính] Hòm đồ không đủ chỗ trống! Cần ít nhất " + itemsToGive.size() + " ô trống.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        plugin.getCoreManager().setShards(core.getCoreId(), 0);
        plugin.getCoreManager().saveAllCores();

        for (ItemStack item : itemsToGive) {
            player.getInventory().addItem(item);
        }

        player.sendMessage(ChatColor.GREEN + "[Tài chính] Rút thành công " + ChatColor.YELLOW + shards + " Shards" + ChatColor.GREEN + " tích lũy (dạng Stack 64) vào túi đồ!");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        player.openInventory(new CoreGui(plugin, core, CoreTab.FINANCE).getInventory());
    }

    private void handleDepositShards(Player player) {
        if (!core.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "[Bảo vệ] Bạn không phải là chủ sở hữu Lõi lãnh thổ này!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int totalDeposited = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.PRISMARINE_SHARD) continue;

            if (item.hasItemMeta()) {
                org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                if (pdc.has(PDCKeys.IS_SHARD_ITEM, PersistentDataType.BYTE) || pdc.has(PDCKeys.SECURE_ITEM_ID, PersistentDataType.STRING)) {
                    totalDeposited += item.getAmount();
                    player.getInventory().setItem(i, null);
                }
            }
        }

        if (totalDeposited <= 0) {
            player.sendMessage(ChatColor.RED + "[Tài chính] Bạn không có Mảnh Không Gian (Shard) hợp lệ nào trong hòm đồ để nộp!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int currentShards = plugin.getCoreManager().getShards(core.getCoreId());
        plugin.getCoreManager().setShards(core.getCoreId(), currentShards + totalDeposited);
        plugin.getCoreManager().saveAllCores();

        player.sendMessage(ChatColor.GREEN + "[Tài chính] Nộp thành công " + ChatColor.YELLOW + totalDeposited + " Shards" + ChatColor.GREEN + " vào Lõi lãnh thổ!");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

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

            com.truongcm.territorydefense.feature.core.HologramManager.updateCoreHologram(core);

            player.sendMessage(ChatColor.GREEN + "Chúc mừng! Lõi Lãnh thổ của bạn đã được nâng cấp lên Cấp " + nextLevel);
            player.sendMessage(ChatColor.GRAY + "Chi phí thanh toán: " + ChatColor.GOLD + String.format("%,.0f", moneyCost) + " Xu " + ChatColor.GRAY + "& " + ChatColor.AQUA + shardCost + " Shards");
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            player.openInventory(new CoreGui(plugin, core, CoreTab.FINANCE).getInventory());
        }
    }

    private void handleRetrieveCore(Player player) {
        // Kiểm tra xem Lõi có thực sự còn tồn tại/hoạt động hay không trước khi xử lý thu hồi (Chống exploit trùng lặp)
        if (!plugin.getCoreManager().getAllActiveCores().contains(core)) {
            player.sendMessage(ChatColor.RED + "Lõi Lãnh Thổ này không còn hoạt động hoặc đã được thu hồi trước đó!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.closeInventory();
            return;
        }

        if (isRaidActive(core)) {
            player.sendMessage(ChatColor.RED + "Không thể thu hồi Lõi khi đang có đợt Raid tấn công!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

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
        String key = type.name().toLowerCase();
        return plugin.getConfig().getDouble("tower-settings.types." + key + ".purchase-cost", switch (type) {
            case ARROW -> 30000.0;
            case LIGHTNING -> 45000.0;
            case FIRE -> 50000.0;
            case FROST -> 40000.0;
            case ARTILLERY -> 65000.0;
            case HEALING -> 55000.0;
            case SPELL -> 70000.0;
        });
    }

    private String getTowerDisplayName(com.truongcm.territorydefense.feature.combat.tower.Tower.TowerType type) {
        String key = type.name().toLowerCase();
        String name = plugin.getConfig().getString("tower-settings.types." + key + ".display-name");
        if (name != null) {
            return ChatColor.translateAlternateColorCodes('&', name);
        }
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

    private void handleReactivateCore(Player player) {
        if (!core.isDisabled()) {
            player.sendMessage(ChatColor.GREEN + "Lõi hiện tại không bị vô hiệu hóa.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (!core.getOwnerUUID().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Bạn không phải chủ sở hữu Lõi để kích hoạt lại!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        double cost = core.getReactivateCost();
        if (!plugin.getVaultEconomy().has(player, cost)) {
            player.sendMessage(ChatColor.RED + "Bạn không đủ Xu để kích hoạt lại Lõi! Cần: " + String.format("%,.0f", cost) + " Xu.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        plugin.getVaultEconomy().withdrawPlayer(player, cost);
        core.setDisabledUntil(0);
        core.revertHealth();
        plugin.getCoreManager().saveAllCores();

        com.truongcm.territorydefense.feature.core.HologramManager.updateCoreHologram(core);

        player.sendMessage(ChatColor.GREEN + "Kích hoạt lại Lõi Lãnh Thổ thành công! Hệ thống phòng thủ và sản xuất đã phục hồi.");
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        player.openInventory(new CoreGui(plugin, core, CoreTab.LOGISTICS).getInventory());
    }

    public TerritoryCore getCore() { return core; }
    public CoreTab getActiveTab() { return activeTab; }
}
