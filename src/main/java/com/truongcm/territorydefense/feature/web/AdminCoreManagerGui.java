package com.truongcm.territorydefense.feature.web;

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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.truongcm.territorydefense.feature.core.ServerBlueprintManager;
import com.truongcm.territorydefense.feature.logistics.ui.RebuildConfirmGui;

import java.io.File;
import java.util.*;

public class AdminCoreManagerGui extends CustomHolder {

    public enum AdminTab {
        MAIN_HUB,
        CORE_LIST,
        CORE_DASHBOARD,
        MOB_BOUNTY_EDITOR,
        SERVER_BLUEPRINTS,
        TOWER_CONFIG_EDITOR,
        NPC_CONFIG_EDITOR
    }

    private final TerritoryDefense plugin;
    private final AdminTab activeTab;
    private final TerritoryCore selectedCore; // Sử dụng cho CORE_DASHBOARD
    private final int page;
    private final NamespacedKey actionKey;

    public AdminCoreManagerGui(TerritoryDefense plugin, AdminTab activeTab) {
        this(plugin, activeTab, null, 0);
    }

    public AdminCoreManagerGui(TerritoryDefense plugin, AdminTab activeTab, TerritoryCore selectedCore, int page) {
        this.plugin = plugin;
        this.activeTab = activeTab;
        this.selectedCore = selectedCore;
        this.page = page;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        String title = switch (activeTab) {
            case MAIN_HUB -> ChatColor.RED + "⭐ HỆ THỐNG QUẢN TRỊ ADMIN ⭐";
            case CORE_LIST -> ChatColor.RED + "⭐ QUẢN LÝ LÕI TOÀN SERVER ⭐";
            case CORE_DASHBOARD -> ChatColor.RED + "⭐ ĐIỀU KHIỂN LÕI: " + (selectedCore != null ? Bukkit.getOfflinePlayer(selectedCore.getOwnerUUID()).getName() : "Unknown");
            case MOB_BOUNTY_EDITOR -> ChatColor.RED + "⭐ TIỀN THƯỞNG QUÁI ⭐ (Trang " + (page + 1) + "/3)";
            case SERVER_BLUEPRINTS -> ChatColor.RED + "⭐ BẢN VẼ MÁY CHỦ ⭐ (Trang " + (page + 1) + ")";
            case TOWER_CONFIG_EDITOR -> ChatColor.RED + "⭐ CẤU HÌNH THÁP ⭐ " + (page == 0 ? "(Chọn)" : "(Tinh Chỉnh)");
            case NPC_CONFIG_EDITOR -> ChatColor.RED + "⭐ CẤU HÌNH NPC LOGISTICS ⭐";
        };

        Inventory inv = Bukkit.createInventory(this, 54, title);

        // Lấp đầy nền kính xám hàng cuối
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, pane);
        }

        switch (activeTab) {
            case MAIN_HUB -> setupMainHub(inv);
            case CORE_LIST -> setupCoreList(inv);
            case CORE_DASHBOARD -> setupCoreDashboard(inv);
            case MOB_BOUNTY_EDITOR -> setupMobBountyEditor(inv);
            case SERVER_BLUEPRINTS -> setupServerBlueprints(inv);
            case TOWER_CONFIG_EDITOR -> setupTowerConfigEditor(inv);
            case NPC_CONFIG_EDITOR -> setupNpcConfigEditor(inv);
        }

        // Tab Bar điều hướng ở hàng đáy
        if (activeTab == AdminTab.SERVER_BLUEPRINTS && selectedCore != null) {
            inv.setItem(45, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Quay Lại Core Dashboard", "VIEW_CORE_" + selectedCore.getCoreId().toString()));
        } else if (activeTab != AdminTab.MAIN_HUB) {
            inv.setItem(45, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Quay Lại Admin Hub", "GO_HUB"));
        } else {
            inv.setItem(45, createGuiItem(Material.BARRIER, ChatColor.RED + "Đóng Menu", "CLOSE"));
        }
        inv.setItem(53, createGuiItem(Material.BARRIER, ChatColor.RED + "Thoát", "CLOSE"));

        return inv;
    }

    private void setupMainHub(Inventory inv) {
        // Nền kính trang trí xung quanh
        ItemStack bg = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, bg);
        }

        // Nút 1: Book & Quill / Chest -> Quản lý Lõi người chơi
        inv.setItem(20, createGuiItem(Material.CHEST,
                ChatColor.GOLD + "" + ChatColor.BOLD + "📂 DANH SÁCH LÕI PLAYER",
                "GO_CORE_LIST",
                ChatColor.GRAY + "Quản lý toàn bộ Lõi lãnh thổ,",
                ChatColor.GRAY + "giao dịch, dịch chuyển và tặng Shards."
        ));

        // Nút 2: Spawn Eggs -> Cấu hình tiền thưởng quái vật & Mini-Boss
        inv.setItem(21, createGuiItem(Material.ZOMBIE_SPAWN_EGG,
                ChatColor.RED + "" + ChatColor.BOLD + "💰 CẤU HÌNH QUÁI & TIỀN THƯỞNG",
                "GO_MOB_BOUNTY",
                ChatColor.GRAY + "Điều chỉnh tiền thưởng trực tiếp,",
                ChatColor.GRAY + "tỷ lệ và thuộc tính của Mini-Boss."
        ));

        // Nút 3: Filled Map -> Bản vẽ Admin
        inv.setItem(22, createGuiItem(Material.FILLED_MAP,
                ChatColor.AQUA + "" + ChatColor.BOLD + "📂 QUẢN LÝ THƯ MỤC TEMPLATES",
                "GO_TEMPLATES",
                ChatColor.GRAY + "Xem và quản lý các file bản vẽ mẫu",
                ChatColor.GRAY + "trong thư mục 'templates/' của Server."
        ));

        // Nút 4: Tower Config Editor
        inv.setItem(23, createGuiItem(Material.GOLDEN_HORSE_ARMOR,
                ChatColor.GREEN + "" + ChatColor.BOLD + "🛡️ CẤU HÌNH THÁP PHÒNG THỦ",
                "GO_TOWER_CONFIG",
                ChatColor.GRAY + "Điều chỉnh bán kính, tốc độ bắn,",
                ChatColor.GRAY + "sát thương 5 cấp và giá mua 7 loại tháp."
        ));

        // Nút 5: NPC Config Editor
        inv.setItem(24, createGuiItem(Material.IRON_HOE,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "👷 CẤU HÌNH NPC LOGISTICS",
                "GO_NPC_CONFIG",
                ChatColor.GRAY + "Tinh chỉnh độ sâu/cao quét 7Gao,",
                ChatColor.GRAY + "tốc độ xây 5 cấp, giới hạn nông trại."
        ));
    }

    private void setupCoreList(Inventory inv) {
        List<TerritoryCore> cores = new ArrayList<>(plugin.getCoreManager().getAllActiveCores());
        int itemsPerPage = 36;
        int totalPages = (int) Math.ceil((double) cores.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, cores.size());

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            TerritoryCore c = cores.get(i);
            String ownerName = Bukkit.getOfflinePlayer(c.getOwnerUUID()).getName();
            if (ownerName == null) ownerName = "Người chơi ẩn danh";

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ID Lõi: " + ChatColor.AQUA + c.getCoreId().toString());
            lore.add(ChatColor.GRAY + "Chủ sở hữu: " + ChatColor.GREEN + ownerName);
            lore.add(ChatColor.GRAY + "Cấp độ Lõi: " + ChatColor.YELLOW + "Cấp " + c.getLevel());
            lore.add(ChatColor.GRAY + "Hồng tâm (FEP): " + ChatColor.GOLD + String.format("%.1f", c.getFep()));
            lore.add(ChatColor.GRAY + "Tọa độ: " + ChatColor.LIGHT_PURPLE + String.format("%s, %d, %d, %d",
                    c.getLocation().getWorld().getName(),
                    c.getLocation().getBlockX(),
                    c.getLocation().getBlockY(),
                    c.getLocation().getBlockZ()
            ));
            lore.add(" ");
            lore.add(ChatColor.YELLOW + "➔ Click để mở BẢNG ĐIỀU KHIỂN CHI TIẾT");

            inv.setItem(slot, createGuiItem(Material.BEACON, ChatColor.GREEN + "Lõi của " + ownerName, "VIEW_CORE_" + c.getCoreId().toString(), lore.toArray(new String[0])));
            slot++;
        }

        // Điền rương trống
        for (int i = slot; i < 36; i++) {
            inv.setItem(i, createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE"));
        }

        // Phân trang
        if (page > 0) {
            inv.setItem(37, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Trang trước", "CORE_LIST_PREV"));
        }
        inv.setItem(41, createGuiItem(Material.PAPER, ChatColor.GOLD + "Trang " + (page + 1) + " / " + totalPages, "NONE"));
        if (page < totalPages - 1) {
            inv.setItem(43, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Trang sau ▶", "CORE_LIST_NEXT"));
        }
    }

    private void setupCoreDashboard(Inventory inv) {
        if (selectedCore == null) return;

        // Điền nền black glass
        ItemStack bg = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, bg);
        }

        String ownerName = Bukkit.getOfflinePlayer(selectedCore.getOwnerUUID()).getName();
        if (ownerName == null) ownerName = "Người chơi ẩn danh";

        // Slot 4: Sách thông tin Lõi
        inv.setItem(4, createGuiItem(Material.BOOK, ChatColor.GREEN + "Lõi của " + ownerName, "NONE",
                ChatColor.GRAY + "Cấp độ: " + ChatColor.YELLOW + selectedCore.getLevel(),
                ChatColor.GRAY + "FEP: " + ChatColor.GOLD + String.format("%.1f", selectedCore.getFep()),
                ChatColor.GRAY + "Tọa độ: " + ChatColor.AQUA + String.format("%s %d %d %d",
                        selectedCore.getLocation().getWorld().getName(),
                        selectedCore.getLocation().getBlockX(),
                        selectedCore.getLocation().getBlockY(),
                        selectedCore.getLocation().getBlockZ()
                )
        ));

        // Slot 19: Teleport (Ender Pearl)
        inv.setItem(19, createGuiItem(Material.ENDER_PEARL, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "DỊCH CHUYỂN TỚI LÕI", "CORE_TELEPORT"));

        // Slot 20: Bản vẽ Máy Chủ (Filled Map)
        inv.setItem(20, createGuiItem(Material.FILLED_MAP, ChatColor.AQUA + "" + ChatColor.BOLD + "BẢN VẼ MÁY CHỦ", "OPEN_SERVER_BLUEPRINTS",
                ChatColor.GRAY + "Mở danh sách bản vẽ hệ thống (.dat, .yml, .schem)",
                ChatColor.GRAY + "để xem trước hoặc ép buộc kiến thiết lên Lõi này."
        ));

        // Slot 21: Force Recall Builders (Bell)
        inv.setItem(21, createGuiItem(Material.BELL, ChatColor.YELLOW + "" + ChatColor.BOLD + "GỌI 7GAO TRỞ VỀ", "CORE_RECALL_BUILDERS"));

        // Slot 22: Max Stats (Golden Apple)
        inv.setItem(22, createGuiItem(Material.ENCHANTED_GOLDEN_APPLE, ChatColor.GOLD + "" + ChatColor.BOLD + "NÂNG MAX CHỈ SỐ LÕI (Bypass)", "CORE_MAX_STATS"));

        // Slot 23: Give Secure Shards (Prismarine Shard)
        inv.setItem(23, createGuiItem(Material.PRISMARINE_SHARD, ChatColor.AQUA + "" + ChatColor.BOLD + "TRAO TẶNG SHARDS", "CORE_GIVE_SHARDS",
                ChatColor.GRAY + "Tạo Shard bảo mật tặng trực tiếp vào túi đồ",
                ChatColor.GRAY + "của người chơi đang online.",
                " ",
                ChatColor.GREEN + "➔ Click Trái: Tặng 10 Shards",
                ChatColor.YELLOW + "➔ Shift + Click Trái: Tặng 100 Shards",
                ChatColor.AQUA + "➔ Click Phải: Tặng 1 Shard"
        ));

        // Slot 24: Xuất bản vẽ của Core này (Spyglass)
        inv.setItem(24, createGuiItem(Material.SPYGLASS, ChatColor.GREEN + "" + ChatColor.BOLD + "XUẤT BẢN VẼ CORE NÀY", "EXPORT_CORE_BLUEPRINT",
                ChatColor.GRAY + "Quét toàn bộ kiến trúc hiện tại quanh Lõi",
                ChatColor.GRAY + "và lưu trữ thành file .dat trong thư mục",
                ChatColor.GRAY + "'server_blueprints/' của Server."
        ));

        // Slot 25: Reset Difficulty (Diamond Sword)
        inv.setItem(25, createGuiItem(Material.DIAMOND_SWORD, ChatColor.RED + "" + ChatColor.BOLD + "RESET ĐỘ KHÓ RAID", "CORE_RESET_DIFFICULTY",
                ChatColor.GRAY + "Reset chỉ số đợt Raid hoàn thành",
                ChatColor.GRAY + "về 0 để giảm độ khó cho người chơi."
        ));
    }

    private void setupMobBountyEditor(Inventory inv) {
        EntityType[] allMobs = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CAVE_SPIDER,
            EntityType.CREEPER, EntityType.HUSK, EntityType.DROWNED, EntityType.WITCH,
            EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.PHANTOM, EntityType.GHAST,
            EntityType.EVOKER, EntityType.RAVAGER, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
            EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.ILLUSIONER, EntityType.VEX,
            EntityType.HOGLIN, EntityType.GIANT, EntityType.WITHER, EntityType.ZOMBIFIED_PIGLIN,
            EntityType.ZOGLIN, EntityType.STRAY, EntityType.SLIME, EntityType.MAGMA_CUBE,
            EntityType.WARDEN
        };

        if (page == 0 || page == 1) {
            int itemsPerPage = 18;
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, allMobs.length);

            int slot = 0;
            for (int i = startIndex; i < endIndex; i++) {
                EntityType type = allMobs[i];
                Material egg = getSpawnEggMaterial(type);
                double reward = com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMobRegistry.getCoinReward(type);

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Tiền thưởng hiện tại: " + ChatColor.GOLD + String.format("%,.0f xu", reward));
                lore.add(" ");
                lore.add(ChatColor.GREEN + "➔ Click Trái: Tăng +10 xu (Shift: +100)");
                lore.add(ChatColor.RED + "➔ Click Phải: Giảm -10 xu (Shift: -100)");

                inv.setItem(slot, createGuiItem(egg, ChatColor.YELLOW + "Quái: " + type.name(), "BOUNTY_MOB_" + type.name(), lore.toArray(new String[0])));
                slot++;
            }

            // Điền rương trống
            for (int i = slot; i < 36; i++) {
                inv.setItem(i, createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE"));
            }

            if (page == 0) {
                inv.setItem(41, createGuiItem(Material.PAPER, ChatColor.GOLD + "Trang 1 / 3: Tiền Thưởng Quái (Phần 1)", "NONE"));
                inv.setItem(43, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Trang sau ▶", "BOUNTY_PAGE_NEXT"));
            } else {
                inv.setItem(37, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Trang trước", "BOUNTY_PAGE_PREV"));
                inv.setItem(41, createGuiItem(Material.PAPER, ChatColor.GOLD + "Trang 2 / 3: Tiền Thưởng Quái (Phần 2)", "NONE"));
                inv.setItem(43, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Trang sau ▶ (Mini-Boss)", "BOUNTY_PAGE_NEXT"));
            }
        } else {
            // Trang 3: Mini-Boss
            for (int i = 0; i < 36; i++) {
                inv.setItem(i, createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE"));
            }

            double spawnChance = plugin.getConfig().getDouble("raid-settings.elite-boss.spawn-chance", 0.05);
            double hpMult = plugin.getConfig().getDouble("raid-settings.elite-boss.hp-multiplier", 2.0);
            double dmgMult = plugin.getConfig().getDouble("raid-settings.elite-boss.damage-multiplier", 1.5);
            int minEnchants = plugin.getConfig().getInt("raid-settings.elite-boss.min-enchant-lines", 2);

            // Nút Spawn Chance (Wither Skeleton Skull)
            inv.setItem(10, createGuiItem(Material.WITHER_SKELETON_SKULL, ChatColor.YELLOW + "Tỷ Lệ Xuất Hiện Mini-Boss", "BOSS_SPAWN",
                    ChatColor.GRAY + "Giá trị: " + ChatColor.GOLD + String.format("%.0f%%", spawnChance * 100.0),
                    " ",
                    ChatColor.GREEN + "➔ Click Trái: +1%",
                    ChatColor.RED + "➔ Click Phải: -1%"
            ));

            // Nút HP Multiplier (Redstone Ore)
            inv.setItem(12, createGuiItem(Material.REDSTONE_ORE, ChatColor.RED + "Hệ Số HP Mini-Boss", "BOSS_HP",
                    ChatColor.GRAY + "Giá trị: " + ChatColor.GOLD + String.format("x%.1f", hpMult),
                    " ",
                    ChatColor.GREEN + "➔ Click Trái: +0.1",
                    ChatColor.RED + "➔ Click Phải: -0.1"
            ));

            // Nút Damage Multiplier (Netherite Sword)
            inv.setItem(14, createGuiItem(Material.NETHERITE_SWORD, ChatColor.DARK_RED + "Hệ Số Sát Thương Mini-Boss", "BOSS_DMG",
                    ChatColor.GRAY + "Giá trị: " + ChatColor.GOLD + String.format("x%.1f", dmgMult),
                    " ",
                    ChatColor.GREEN + "➔ Click Trái: +0.1",
                    ChatColor.RED + "➔ Click Phải: -0.1"
            ));

            // Nút Min Enchant Lines (Enchanted Book)
            inv.setItem(16, createGuiItem(Material.ENCHANTED_BOOK, ChatColor.AQUA + "Số Dòng Phù Phép Tối Thiểu", "BOSS_ENCH",
                    ChatColor.GRAY + "Giá trị: " + ChatColor.GOLD + minEnchants + " dòng",
                    " ",
                    ChatColor.GREEN + "➔ Click Trái: +1 dòng",
                    ChatColor.RED + "➔ Click Phải: -1 dòng"
            ));

            inv.setItem(37, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Trang trước (Quái Thường)", "BOUNTY_PAGE_PREV"));
            inv.setItem(41, createGuiItem(Material.PAPER, ChatColor.GOLD + "Trang 3 / 3: Thuộc Tính Mini-Boss", "NONE"));
        }
    }

    private void setupServerBlueprints(Inventory inv) {
        if (plugin.getServerBlueprintManager() == null) return;
        List<ServerBlueprintManager.ServerBlueprint> bps = plugin.getServerBlueprintManager().getBlueprints();
        int itemsPerPage = 36;
        int totalPages = (int) Math.ceil((double) bps.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, bps.size());

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            ServerBlueprintManager.ServerBlueprint bp = bps.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "File: " + ChatColor.YELLOW + bp.getFileName());
            lore.add(ChatColor.GRAY + "Định dạng: " + ChatColor.YELLOW + bp.getFormat());
            lore.add(ChatColor.GRAY + "Số lượng block: " + ChatColor.GOLD + bp.getBlocks().size() + " blocks");
            lore.add(" ");
            lore.add(ChatColor.YELLOW + "➔ Click để Mở Menu Thao Tác (Áp dụng/Bán/Gửi/Xoá)");

            inv.setItem(slot, createGuiItem(Material.FILLED_MAP, ChatColor.GREEN + bp.getDisplayName(), "APPLY_SERVER_BP_" + i, lore.toArray(new String[0])));
            slot++;
        }

        // Điền rương trống
        for (int i = slot; i < 36; i++) {
            inv.setItem(i, createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE"));
        }

        // Phân trang & reload
        if (page > 0) {
            inv.setItem(37, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Trang trước", "SERVER_BP_PREV"));
        }
        inv.setItem(40, createGuiItem(Material.SUNFLOWER, ChatColor.AQUA + "" + ChatColor.BOLD + "Reload Bản Vẽ Máy Chủ", "RELOAD_SERVER_BLUEPRINTS", ChatColor.GRAY + "Quét lại thư mục 'server_blueprints/'"));
        inv.setItem(41, createGuiItem(Material.PAPER, ChatColor.GOLD + "Trang " + (page + 1) + " / " + totalPages, "NONE"));
        if (page < totalPages - 1) {
            inv.setItem(43, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Trang sau ▶", "SERVER_BP_NEXT"));
        }
    }

    private void setupTowerConfigEditor(Inventory inv) {
        if (page == 0) {
            // Danh sách chọn tháp
            for (int i = 0; i < 36; i++) {
                inv.setItem(i, createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE"));
            }

            String[] keys = {"lightning", "arrow", "fire", "frost", "artillery", "healing", "spell"};
            String[] names = {"Tháp Điện (Lightning)", "Tháp Cung (Skeleton/Arrow)", "Tháp Lửa (Blaze/Fire)", "Tháp Băng (Stray/Frost)", "Tháp Pháo (Artillery)", "Tháp Hồi (Healing)", "Tháp Phép (Spell)"};
            Material[] mats = {Material.GOLDEN_HORSE_ARMOR, Material.BOW, Material.CAMPFIRE, Material.PACKED_ICE, Material.TNT, Material.POTION, Material.ENCHANTED_BOOK};

            int[] slots = {10, 11, 12, 13, 14, 15, 16};
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                String path = "tower-settings.types." + key;
                double radius = plugin.getConfig().getDouble(path + ".scanning-radius");
                int speed = plugin.getConfig().getInt(path + ".attack-speed-ticks");
                double cost = plugin.getConfig().getDouble(path + ".purchase-cost");
                List<Double> damage = plugin.getConfig().getDoubleList(path + ".damage");

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Tầm quét: " + ChatColor.AQUA + radius + " blocks");
                lore.add(ChatColor.GRAY + "Tốc độ bắn: " + ChatColor.AQUA + speed + " ticks");
                lore.add(ChatColor.GRAY + "Giá mua: " + ChatColor.GOLD + String.format("%,.0f xu", cost));
                if (damage != null && !damage.isEmpty()) {
                    lore.add(ChatColor.GRAY + "Sát thương Lvl 1-5: " + ChatColor.YELLOW + damage.toString());
                }
                lore.add(" ");
                lore.add(ChatColor.YELLOW + "➔ Click để tùy chỉnh chi tiết");

                inv.setItem(slots[i], createGuiItem(mats[i], ChatColor.GREEN + names[i], "EDIT_TOWER_PAGE_" + (i + 1), lore.toArray(new String[0])));
            }
            inv.setItem(41, createGuiItem(Material.PAPER, ChatColor.GOLD + "Chọn loại Tháp phòng thủ", "NONE"));
        } else {
            // Tinh chỉnh chi tiết tháp
            int towerIndex = page - 1;
            String[] keys = {"lightning", "arrow", "fire", "frost", "artillery", "healing", "spell"};
            String[] names = {"Tháp Điện", "Tháp Cung", "Tháp Lửa", "Tháp Băng", "Tháp Pháo", "Tháp Hồi", "Tháp Phép"};
            String key = keys[towerIndex];
            String path = "tower-settings.types." + key;

            double radius = plugin.getConfig().getDouble(path + ".scanning-radius");
            int speed = plugin.getConfig().getInt(path + ".attack-speed-ticks");
            double cost = plugin.getConfig().getDouble(path + ".purchase-cost");
            List<Double> damage = plugin.getConfig().getDoubleList(path + ".damage");
            if (damage == null || damage.isEmpty()) {
                damage = new ArrayList<>(Arrays.asList(10.0, 20.0, 30.0, 40.0, 50.0));
            }

            for (int i = 0; i < 45; i++) {
                inv.setItem(i, createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ", "NONE"));
            }

            inv.setItem(4, createGuiItem(Material.BOOK, ChatColor.GOLD + "Cấu hình: " + names[towerIndex], "NONE",
                    ChatColor.GRAY + "Đang chỉnh sửa các thông số của " + names[towerIndex]
            ));

            inv.setItem(19, createGuiItem(Material.SPYGLASS, ChatColor.YELLOW + "Tầm Quét (Bán kính)", "TWR_RADIUS_" + key,
                    ChatColor.GRAY + "Giá trị hiện tại: " + ChatColor.AQUA + radius + " blocks",
                    " ",
                    ChatColor.GREEN + "➔ Click Trái: +1.0 block",
                    ChatColor.RED + "➔ Click Phải: -1.0 block"
            ));

            inv.setItem(20, createGuiItem(Material.CLOCK, ChatColor.YELLOW + "Tốc Độ Bắn (Ticks)", "TWR_SPEED_" + key,
                    ChatColor.GRAY + "Giá trị hiện tại: " + ChatColor.AQUA + speed + " ticks",
                    ChatColor.GRAY + "Ghi chú: Càng thấp bắn càng nhanh (20 ticks = 1s)",
                    " ",
                    ChatColor.GREEN + "➔ Click Trái: +1 tick (Bắn chậm đi)",
                    ChatColor.RED + "➔ Click Phải: -1 tick (Bắn nhanh lên)"
            ));

            inv.setItem(21, createGuiItem(Material.GOLD_INGOT, ChatColor.YELLOW + "Giá Mua Trụ (xu)", "TWR_COST_" + key,
                    ChatColor.GRAY + "Giá trị hiện tại: " + ChatColor.GOLD + String.format("%,.0f xu", cost),
                    " ",
                    ChatColor.GREEN + "➔ Click Trái: +500 xu (Shift: +5000)",
                    ChatColor.RED + "➔ Click Phải: -500 xu (Shift: -5000)"
            ));

            Material[] dmgMats = {Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD, Material.NETHERITE_AXE};
            int[] slots = {28, 29, 30, 31, 32};
            for (int i = 0; i < 5; i++) {
                double dmgVal = i < damage.size() ? damage.get(i) : 10.0;
                inv.setItem(slots[i], createGuiItem(dmgMats[i], ChatColor.AQUA + "Sát Thương Cấp " + (i + 1), "TWR_DMG_" + key + "_" + i,
                        ChatColor.GRAY + "Giá trị hiện tại: " + ChatColor.RED + dmgVal,
                        " ",
                        ChatColor.GREEN + "➔ Click Trái: +1.0 Sát thương (Shift: +10.0)",
                        ChatColor.RED + "➔ Click Phải: -1.0 Sát thương (Shift: -10.0)"
                ));
            }

            inv.setItem(37, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Quay lại chọn Tháp", "GO_TOWER_CONFIG"));
        }
    }

    private void setupNpcConfigEditor(Inventory inv) {
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ", "NONE"));
        }

        inv.setItem(4, createGuiItem(Material.BOOK, ChatColor.GOLD + "Cấu hình NPC Logistics", "NONE",
                ChatColor.GRAY + "Tinh chỉnh các thông số vận hành của 7Gao & Nông Dân"
        ));

        int scanBelow = plugin.getConfig().getInt("builder-settings.scan-height-below", 5);
        int scanAbove = plugin.getConfig().getInt("builder-settings.scan-height-above", 15);

        inv.setItem(10, createGuiItem(Material.SMOOTH_STONE_SLAB, ChatColor.YELLOW + "7Gao: Độ Sâu Quét Dưới Đất", "NPC_BUILDER_BELOW",
                ChatColor.GRAY + "Giá trị hiện tại: " + ChatColor.AQUA + scanBelow + " blocks",
                ChatColor.GRAY + "Khoảng cách quét tối đa dưới chân Lõi",
                " ",
                ChatColor.GREEN + "➔ Click Trái: +1 block",
                ChatColor.RED + "➔ Click Phải: -1 block"
        ));

        inv.setItem(11, createGuiItem(Material.SMOOTH_STONE, ChatColor.YELLOW + "7Gao: Chiều Cao Quét Phía Trên", "NPC_BUILDER_ABOVE",
                ChatColor.GRAY + "Giá trị hiện tại: " + ChatColor.AQUA + scanAbove + " blocks",
                ChatColor.GRAY + "Khoảng cách quét tối đa phía trên Lõi",
                " ",
                ChatColor.GREEN + "➔ Click Trái: +1 block",
                ChatColor.RED + "➔ Click Phải: -1 block"
        ));

        int maxPasture = plugin.getConfig().getInt("farmer-settings.max-pasture-animals", 20);
        long respawnCooldown = plugin.getConfig().getLong("farmer-settings.respawn-cooldown-seconds", 180);

        inv.setItem(15, createGuiItem(Material.COW_SPAWN_EGG, ChatColor.YELLOW + "Nông Dân: Giới Hạn Chuồng Động Vật", "NPC_FARMER_PASTURE",
                ChatColor.GRAY + "Giá trị hiện tại: " + ChatColor.AQUA + maxPasture + " con",
                ChatColor.GRAY + "Số lượng động vật tối đa để NPC chăn nuôi/thu hoạch",
                " ",
                ChatColor.GREEN + "➔ Click Trái: +1 con",
                ChatColor.RED + "➔ Click Phải: -1 con"
        ));

        inv.setItem(16, createGuiItem(Material.TOTEM_OF_UNDYING, ChatColor.YELLOW + "Nông Dân: Thời Gian Hồi Sinh (Giây)", "NPC_FARMER_RESPAWN",
                ChatColor.GRAY + "Giá trị hiện tại: " + ChatColor.AQUA + respawnCooldown + " giây",
                ChatColor.GRAY + "Thời gian chờ hồi sinh nông dân sau khi bị chết",
                " ",
                ChatColor.GREEN + "➔ Click Trái: +10 giây (Shift: +60)",
                ChatColor.RED + "➔ Click Phải: -10 giây (Shift: -60)"
        ));

        // Tốc độ Builder Level 1 - 5
        int[] builderSlots = {28, 29, 30, 31, 32};
        for (int lv = 1; lv <= 5; lv++) {
            int speed = plugin.getConfig().getInt("builder-settings.levels." + lv, 2);
            inv.setItem(builderSlots[lv - 1], createGuiItem(Material.BRICK, ChatColor.YELLOW + "Tốc Độ 7Gao Cấp " + lv, "NPC_BUILDER_SPEED_" + lv,
                    ChatColor.GRAY + "Tốc độ đặt block hiện tại: " + ChatColor.GREEN + speed + " blocks/giây",
                    " ",
                    ChatColor.GREEN + "➔ Click Trái: +1 block/giây",
                    ChatColor.RED + "➔ Click Phải: -1 block/giây"
            ));
        }
    }

    private List<TerritoryCore.BlockSnapshot> scanCoreLayoutInstantly(TerritoryCore core) {
        List<TerritoryCore.BlockSnapshot> scannedBlocks = new ArrayList<>();
        org.bukkit.Location coreLoc = core.getLocation();
        int radius = plugin.getCoreManager().getCoreRadius(core);
        int rawBelow = plugin.getConfig().getInt("builder-settings.scan-height-below", 5);
        int scanHeightBelow = rawBelow < 0 ? (coreLoc.getBlockY() - coreLoc.getWorld().getMinHeight()) : Math.abs(rawBelow);
        int scanHeightAbove = Math.abs(plugin.getConfig().getInt("builder-settings.scan-height-above", 15));

        for (int dy = -scanHeightBelow; dy <= scanHeightAbove; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    org.bukkit.block.Block block = coreLoc.getWorld().getBlockAt(
                            coreLoc.getBlockX() + dx,
                            coreLoc.getBlockY() + dy,
                            coreLoc.getBlockZ() + dz
                    );
                    Material type = block.getType();
                    if (type != Material.AIR && type != Material.CAVE_AIR && type != Material.VOID_AIR && type != Material.CONDUIT) {
                        org.bukkit.Location blockLoc = block.getLocation();
                        if (plugin.getTowerManager() != null && plugin.getTowerManager().getActiveTowers().containsKey(blockLoc)) {
                            continue;
                        }
                        String blockDataStr = block.getBlockData().getAsString();
                        scannedBlocks.add(new TerritoryCore.BlockSnapshot(
                                dx, dy, dz, type.name(), blockDataStr
                        ));
                    }
                }
            }
        }
        return scannedBlocks;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        UUID playerUuid = player.getUniqueId();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.hasItemMeta()) {
            String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            // Xử lý cơ bản
            if (action.equalsIgnoreCase("CLOSE")) {
                player.closeInventory();
                return;
            }

            if (action.equalsIgnoreCase("GO_HUB")) {
                player.closeInventory();
                player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.MAIN_HUB).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("GO_CORE_LIST")) {
                player.closeInventory();
                player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.CORE_LIST).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("GO_MOB_BOUNTY")) {
                player.closeInventory();
                player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.MOB_BOUNTY_EDITOR, null, 0).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("GO_TEMPLATES")) {
                player.closeInventory();
                TerritoryCore firstCore = plugin.getCoreManager().getAllActiveCores().stream().findFirst().orElse(null);
                player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.SERVER_BLUEPRINTS, firstCore, 0).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("GO_TOWER_CONFIG")) {
                player.closeInventory();
                player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.TOWER_CONFIG_EDITOR, null, 0).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("GO_NPC_CONFIG")) {
                player.closeInventory();
                player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.NPC_CONFIG_EDITOR, null, 0).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
                return;
            }

            // Phân trang Core List
            if (action.equalsIgnoreCase("CORE_LIST_PREV")) {
                player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.CORE_LIST, null, page - 1).getInventory());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return;
            }
            if (action.equalsIgnoreCase("CORE_LIST_NEXT")) {
                player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.CORE_LIST, null, page + 1).getInventory());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return;
            }

            // Click vào Core cụ thể trong Core List
            if (action.startsWith("VIEW_CORE_")) {
                UUID coreId = UUID.fromString(action.substring(10));
                TerritoryCore core = plugin.getCoreManager().getAllActiveCores().stream()
                        .filter(c -> c.getCoreId().equals(coreId))
                        .findFirst().orElse(null);

                if (core != null) {
                    player.closeInventory();
                    player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.CORE_DASHBOARD, core, 0).getInventory());
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                }
                return;
            }

            // --- XỬ LÝ TRONG CORE DASHBOARD ---
            if (activeTab == AdminTab.CORE_DASHBOARD && selectedCore != null) {
                if (action.equalsIgnoreCase("CORE_TELEPORT")) {
                    player.teleport(selectedCore.getLocation().clone().add(0, 1, 0));
                    player.sendMessage(ChatColor.GREEN + "[Admin] Đã dịch chuyển bạn tới Lõi!");
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    player.closeInventory();
                    return;
                }

                if (action.equalsIgnoreCase("CORE_RECALL_BUILDERS")) {
                    com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getOrCreateBuilder(selectedCore.getCoreId());
                    if (builder != null && builder.isRebuilding()) {
                        builder.cancelRebuild();
                        player.sendMessage(ChatColor.GREEN + "[Admin] Đã force recall 7Gao!");
                        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                    } else {
                        player.sendMessage(ChatColor.RED + "[Admin] 7Gao của Lõi này hiện không hoạt động.");
                    }
                    return;
                }

                if (action.equalsIgnoreCase("CORE_MAX_STATS")) {
                    selectedCore.setBuilderLevel(5);
                    selectedCore.setFep(1000.0);
                    selectedCore.setBlueprintPrice(0.0);
                    selectedCore.markDirty();
                    player.sendMessage(ChatColor.GREEN + "[Admin] Đã nâng MAX các chỉ số Bypass cho Lõi này!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    player.openInventory(getInventory());
                    return;
                }

                if (action.equalsIgnoreCase("CORE_GIVE_SHARDS")) {
                    int amount = 10;
                    if (event.getClick() == ClickType.SHIFT_LEFT) {
                        amount = 100;
                    } else if (event.getClick() == ClickType.RIGHT) {
                        amount = 1;
                    }

                    Player targetPlayer = Bukkit.getPlayer(selectedCore.getOwnerUUID());
                    if (targetPlayer != null && targetPlayer.isOnline()) {
                        targetPlayer.getInventory().addItem(CoreGui.createSecureShard(amount));
                        targetPlayer.sendMessage(ChatColor.AQUA + "[Bảo vệ] Bạn đã được Admin trao tặng " + ChatColor.YELLOW + amount + " Shards" + ChatColor.AQUA + "!");
                        targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

                        player.sendMessage(ChatColor.GREEN + "[Admin] Đã gửi tặng " + amount + " Shards tới túi đồ của " + targetPlayer.getName() + ".");
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    } else {
                        player.sendMessage(ChatColor.RED + "[Admin] Người chơi (chủ lõi) hiện đang offline! Đang chuyển Shards vào túi đồ của bạn để làm quà.");
                        player.getInventory().addItem(CoreGui.createSecureShard(amount));
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }
                    return;
                }

                if (action.equalsIgnoreCase("CORE_RESET_DIFFICULTY")) {
                    selectedCore.setCompletedRaids(0);
                    selectedCore.setTotalRaidCount(0);
                    selectedCore.setRaidCallCount(0);
                    selectedCore.markDirty();
                    player.sendMessage(ChatColor.GREEN + "[Admin] Độ khó Raid của Lõi đã được khôi phục về trạng thái khởi đầu!");
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_BURN, 1.0f, 1.0f);
                    return;
                }

                if (action.equalsIgnoreCase("OPEN_SERVER_BLUEPRINTS")) {
                    player.closeInventory();
                    player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.SERVER_BLUEPRINTS, selectedCore, 0).getInventory());
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                    return;
                }

                if (action.equalsIgnoreCase("EXPORT_CORE_BLUEPRINT")) {
                    List<TerritoryCore.BlockSnapshot> snapshot = scanCoreLayoutInstantly(selectedCore);
                    if (snapshot.isEmpty()) {
                        player.sendMessage(ChatColor.RED + "[Admin] Lõi này hiện không có khối nào được xây dựng xung quanh để xuất bản vẽ!");
                        return;
                    }
                    String ownerName = Bukkit.getOfflinePlayer(selectedCore.getOwnerUUID()).getName();
                    if (ownerName == null) ownerName = "Core";
                    String safeName = ownerName.replaceAll("[\\\\/:*?\"<>|]", "_") + "_level" + selectedCore.getLevel() + "_" + System.currentTimeMillis();

                    player.sendMessage(ChatColor.YELLOW + "[Admin] Đang xuất bản vẽ asynchronously, vui lòng đợi...");
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        if (plugin.getServerBlueprintManager().exportAsDat(safeName, snapshot)) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.sendMessage(ChatColor.GREEN + "[Admin] Đã xuất bản vẽ thành công thành file .dat trong 'server_blueprints/'!");
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                            });
                        } else {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.sendMessage(ChatColor.RED + "[Admin] Đã xảy ra lỗi khi xuất bản vẽ!");
                            });
                        }
                    });
                    return;
                }
            }

            // --- XỬ LÝ TRONG MOB BOUNTY EDITOR ---
            if (activeTab == AdminTab.MOB_BOUNTY_EDITOR) {
                if (action.equalsIgnoreCase("BOUNTY_PAGE_NEXT")) {
                    player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.MOB_BOUNTY_EDITOR, null, page + 1).getInventory());
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    return;
                }
                if (action.equalsIgnoreCase("BOUNTY_PAGE_PREV")) {
                    player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.MOB_BOUNTY_EDITOR, null, page - 1).getInventory());
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    return;
                }

                if (action.startsWith("BOUNTY_MOB_")) {
                    String mobName = action.substring(11);
                    EntityType type = EntityType.valueOf(mobName);
                    double currentReward = com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMobRegistry.getCoinReward(type);

                    double change = 10.0;
                    if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                        change = 100.0;
                    }

                    if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT) {
                        currentReward += change;
                    } else if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                        currentReward = Math.max(0.0, currentReward - change);
                    }

                    plugin.getConfig().set("raid-settings.mob-rewards." + type.name(), currentReward);
                    plugin.saveConfig();

                    player.sendMessage(ChatColor.GREEN + "[Bounty] Cập nhật tiền thưởng quái " + type.name() + " -> " + ChatColor.GOLD + String.format("%,.0f xu", currentReward));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
                }

                if (action.equalsIgnoreCase("BOSS_SPAWN")) {
                    double val = plugin.getConfig().getDouble("raid-settings.elite-boss.spawn-chance", 0.05);
                    if (event.getClick() == ClickType.LEFT) {
                        val = Math.min(1.0, val + 0.01);
                    } else if (event.getClick() == ClickType.RIGHT) {
                        val = Math.max(0.0, val - 0.01);
                    }
                    plugin.getConfig().set("raid-settings.elite-boss.spawn-chance", val);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + "[Elite] Tỷ lệ xuất hiện Elite Boss: " + String.format("%.0f%%", val * 100.0));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
                }

                if (action.equalsIgnoreCase("BOSS_HP")) {
                    double val = plugin.getConfig().getDouble("raid-settings.elite-boss.hp-multiplier", 2.0);
                    if (event.getClick() == ClickType.LEFT) {
                        val += 0.1;
                    } else if (event.getClick() == ClickType.RIGHT) {
                        val = Math.max(1.0, val - 0.1);
                    }
                    plugin.getConfig().set("raid-settings.elite-boss.hp-multiplier", val);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + "[Elite] Hệ số HP Elite Boss: x" + String.format("%.1f", val));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
                }

                if (action.equalsIgnoreCase("BOSS_DMG")) {
                    double val = plugin.getConfig().getDouble("raid-settings.elite-boss.damage-multiplier", 1.5);
                    if (event.getClick() == ClickType.LEFT) {
                        val += 0.1;
                    } else if (event.getClick() == ClickType.RIGHT) {
                        val = Math.max(1.0, val - 0.1);
                    }
                    plugin.getConfig().set("raid-settings.elite-boss.damage-multiplier", val);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + "[Elite] Hệ số sát thương Elite Boss: x" + String.format("%.1f", val));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
                }

                if (action.equalsIgnoreCase("BOSS_ENCH")) {
                    int val = plugin.getConfig().getInt("raid-settings.elite-boss.min-enchant-lines", 2);
                    if (event.getClick() == ClickType.LEFT) {
                        val += 1;
                    } else if (event.getClick() == ClickType.RIGHT) {
                        val = Math.max(0, val - 1);
                    }
                    plugin.getConfig().set("raid-settings.elite-boss.min-enchant-lines", val);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + "[Elite] Số dòng phù phép mini-boss tối thiểu: " + val + " dòng.");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
                }
            }

            // --- XỬ LÝ TRONG SERVER BLUEPRINTS ---
            if (activeTab == AdminTab.SERVER_BLUEPRINTS && selectedCore != null) {
                if (action.equalsIgnoreCase("SERVER_BP_PREV")) {
                    player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.SERVER_BLUEPRINTS, selectedCore, page - 1).getInventory());
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    return;
                }
                if (action.equalsIgnoreCase("SERVER_BP_NEXT")) {
                    player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.SERVER_BLUEPRINTS, selectedCore, page + 1).getInventory());
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    return;
                }
                if (action.equalsIgnoreCase("RELOAD_SERVER_BLUEPRINTS")) {
                    player.sendMessage(ChatColor.YELLOW + "[Admin] Đang reload bản vẽ asynchronously...");
                    plugin.getServerBlueprintManager().reload(() -> {
                        player.sendMessage(ChatColor.GREEN + "[Admin] Đã reload toàn bộ bản vẽ từ thư mục 'server_blueprints/'!");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                        player.openInventory(getInventory());
                    });
                    return;
                }
                if (action.startsWith("APPLY_SERVER_BP_")) {
                    int index = Integer.parseInt(action.substring(16));
                    List<ServerBlueprintManager.ServerBlueprint> bps = plugin.getServerBlueprintManager().getBlueprints();
                    if (index >= 0 && index < bps.size()) {
                        ServerBlueprintManager.ServerBlueprint bp = bps.get(index);
                        player.closeInventory();
                        player.openInventory(new AdminBlueprintActionGui(plugin, bp, index, selectedCore, page).getInventory());
                        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                    }
                    return;
                }
            }

            // --- XỬ LÝ TRONG TOWER CONFIG EDITOR ---
            if (activeTab == AdminTab.TOWER_CONFIG_EDITOR) {
                if (action.startsWith("EDIT_TOWER_PAGE_")) {
                    int targetPage = Integer.parseInt(action.substring(16));
                    player.openInventory(new AdminCoreManagerGui(plugin, AdminTab.TOWER_CONFIG_EDITOR, null, targetPage).getInventory());
                    player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
                    return;
                }

                if (action.startsWith("TWR_RADIUS_")) {
                    String key = action.substring(11);
                    String path = "tower-settings.types." + key + ".scanning-radius";
                    double val = plugin.getConfig().getDouble(path);
                    if (event.getClick() == ClickType.LEFT) val += 1.0;
                    else if (event.getClick() == ClickType.RIGHT) val = Math.max(1.0, val - 1.0);

                    plugin.getConfig().set(path, val);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + "[Tower] Đã cập nhật Tầm Quét của " + key.toUpperCase() + " -> " + val + " blocks");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
                }

                if (action.startsWith("TWR_SPEED_")) {
                    String key = action.substring(10);
                    String path = "tower-settings.types." + key + ".attack-speed-ticks";
                    int val = plugin.getConfig().getInt(path);
                    if (event.getClick() == ClickType.LEFT) val += 1;
                    else if (event.getClick() == ClickType.RIGHT) val = Math.max(1, val - 1);

                    plugin.getConfig().set(path, val);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + "[Tower] Đã cập nhật Tốc Độ Bắn của " + key.toUpperCase() + " -> " + val + " ticks");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
                }

                if (action.startsWith("TWR_COST_")) {
                    String key = action.substring(9);
                    String path = "tower-settings.types." + key + ".purchase-cost";
                    double val = plugin.getConfig().getDouble(path);
                    double change = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT ? 5000.0 : 500.0;
                    if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT) val += change;
                    else if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) val = Math.max(0.0, val - change);

                    plugin.getConfig().set(path, val);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + "[Tower] Đã cập nhật Giá Mua của " + key.toUpperCase() + " -> " + String.format("%,.0f xu", val));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
                }

                if (action.startsWith("TWR_DMG_")) {
                    String sub = action.substring(8); // e.g., "arrow_2"
                    int lastUnderscore = sub.lastIndexOf('_');
                    String key = sub.substring(0, lastUnderscore);
                    int lvlIdx = Integer.parseInt(sub.substring(lastUnderscore + 1));

                    String path = "tower-settings.types." + key + ".damage";
                    List<Double> damage = plugin.getConfig().getDoubleList(path);
                    if (damage == null || damage.isEmpty()) {
                        damage = new ArrayList<>(Arrays.asList(10.0, 20.0, 30.0, 40.0, 50.0));
                    }
                    double val = damage.get(lvlIdx);
                    double change = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT ? 10.0 : 1.0;

                    if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT) val += change;
                    else if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) val = Math.max(1.0, val - change);

                    damage.set(lvlIdx, val);
                    plugin.getConfig().set(path, damage);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + "[Tower] Đã cập nhật Sát Thương Lvl " + (lvlIdx + 1) + " của " + key.toUpperCase() + " -> " + val);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
                }
            }

            // --- XỬ LÝ TRONG NPC CONFIG EDITOR ---
            if (activeTab == AdminTab.NPC_CONFIG_EDITOR) {
                if (action.equalsIgnoreCase("NPC_BUILDER_BELOW")) {
                    int val = plugin.getConfig().getInt("builder-settings.scan-height-below", 5);
                    if (event.getClick() == ClickType.LEFT) val += 1;
                    else if (event.getClick() == ClickType.RIGHT) val = Math.max(0, val - 1);

                    plugin.getConfig().set("builder-settings.scan-height-below", val);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + "[NPC] Đã cập nhật Độ Sâu Quét 7Gao -> " + val + " blocks");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
                }

                if (action.equalsIgnoreCase("NPC_BUILDER_ABOVE")) {
                    int val = plugin.getConfig().getInt("builder-settings.scan-height-above", 15);
                    if (event.getClick() == ClickType.LEFT) val += 1;
                    else if (event.getClick() == ClickType.RIGHT) val = Math.max(0, val - 1);

                    plugin.getConfig().set("builder-settings.scan-height-above", val);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + "[NPC] Đã cập nhật Chiều Cao Quét 7Gao -> " + val + " blocks");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
                }

                if (action.equalsIgnoreCase("NPC_FARMER_PASTURE")) {
                    int val = plugin.getConfig().getInt("farmer-settings.max-pasture-animals", 20);
                    if (event.getClick() == ClickType.LEFT) val += 1;
                    else if (event.getClick() == ClickType.RIGHT) val = Math.max(1, val - 1);

                    plugin.getConfig().set("farmer-settings.max-pasture-animals", val);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + "[NPC] Đã cập nhật Giới Hạn Chuồng Động Vật Nông Dân -> " + val + " con");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
                }

                if (action.equalsIgnoreCase("NPC_FARMER_RESPAWN")) {
                    long val = plugin.getConfig().getLong("farmer-settings.respawn-cooldown-seconds", 180);
                    long change = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT ? 60 : 10;
                    if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT) val += change;
                    else if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) val = Math.max(10, val - change);

                    plugin.getConfig().set("farmer-settings.respawn-cooldown-seconds", val);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + "[NPC] Đã cập nhật Thời Gian Hồi Sinh Nông Dân -> " + val + " giây");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
                }

                if (action.startsWith("NPC_BUILDER_SPEED_")) {
                    int lv = Integer.parseInt(action.substring(18));
                    String path = "builder-settings.levels." + lv;
                    int val = plugin.getConfig().getInt(path, 2);
                    if (event.getClick() == ClickType.LEFT) val += 1;
                    else if (event.getClick() == ClickType.RIGHT) val = Math.max(1, val - 1);

                    plugin.getConfig().set(path, val);
                    plugin.saveConfig();
                    player.sendMessage(ChatColor.GREEN + "[NPC] Đã cập nhật Tốc độ đặt block 7Gao Cấp " + lv + " -> " + val + " blocks/giây");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    player.openInventory(getInventory());
                    return;
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

    private Material getSpawnEggMaterial(EntityType type) {
        try {
            return Material.valueOf(type.name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException e) {
            return Material.ZOMBIE_SPAWN_EGG;
        }
    }
}
