package com.truongcm.territorydefense.feature.alliance.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.alliance.Alliance;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

/**
 * GIAO DIỆN QUẢN TRỊ LIÊN MINH (ALLY MAIN MENU GUI) - PHIÊN BẢN ĐỘNG V32 (TÁI CẤU TRÚC STATEFUL)
 * Kế thừa CustomHolder giúp tự đóng gói hành vi hiển thị và click chuột.
 */
public class AllyMainMenuGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final Alliance alliance; // Có thể null nếu người chơi chưa vào bang
    private final NamespacedKey actionKey;
    private final Player viewer;

    public AllyMainMenuGui(TerritoryDefense plugin, Alliance alliance, Player viewer) {
        this.plugin = plugin;
        this.alliance = alliance;
        this.viewer = viewer;
        this.actionKey = PDCKeys.GUI_ACTION != null ? PDCKeys.GUI_ACTION : new NamespacedKey(plugin, "gui_action");
    }

    @Override
    public Inventory getInventory() {
        return getInventory(viewer);
    }

    public Inventory getInventory(Player viewer) {
        if (alliance == null) {
            Inventory inv = Bukkit.createInventory(this, 27, ChatColor.DARK_GRAY + "Liên Minh - Chưa Vào Bang");

            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta paneMeta = pane.getItemMeta();
            if (paneMeta != null) {
                paneMeta.setDisplayName(" ");
                pane.setItemMeta(paneMeta);
            }
            for (int i = 0; i < 27; i++) {
                inv.setItem(i, pane);
            }

            inv.setItem(11, createGuiItem(Material.WRITTEN_BOOK, ChatColor.GOLD + "" + ChatColor.BOLD + "Lợi Ích Khi Vào Liên Minh", "NONE",
                    ChatColor.GRAY + "Gia nhập hoặc thành lập Liên minh để nhận bảo hộ:",
                    ChatColor.GREEN + " ✔ Tắt Friendly Fire (Chặn bắn nhầm đồng đội).",
                    ChatColor.GREEN + " ✔ Mở khóa hòm đồ chung đám mây (/ally chest).",
                    ChatColor.GREEN + " ✔ Hợp tác xây dựng và đặt tháp chung ranh giới.",
                    ChatColor.GREEN + " ✔ Phát động chiến tranh giành bá chủ thế giới."
            ));

            inv.setItem(13, createGuiItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "Thành Lập Liên Minh Mới", "CREATE_ALLY_INFO",
                    ChatColor.GRAY + "Chi phí thành lập: " + ChatColor.GOLD + "50,000 Xu",
                    ChatColor.GRAY + "Nhấp chuột vào đây để nhận hướng dẫn",
                    ChatColor.GRAY + "thành lập quốc gia của riêng bạn!"
            ));

            inv.setItem(15, createGuiItem(Material.COMPASS, ChatColor.AQUA + "" + ChatColor.BOLD + "Xin Gia Nhập Liên Minh", "NONE",
                    ChatColor.GRAY + "Để gia nhập một liên minh có sẵn,",
                    ChatColor.GRAY + "bạn cần nhận được lời mời từ Thủ lĩnh bang đó.",
                    ChatColor.YELLOW + "Nhờ Thủ lĩnh gõ: /ally invite " + ChatColor.WHITE + "<tên_bạn>"
            ));

            inv.setItem(22, createGuiItem(Material.BARRIER, ChatColor.RED + "Thoát GUI", "CLOSE"));

            return inv;
        }

        Inventory inv = Bukkit.createInventory(this, 27, ChatColor.DARK_GREEN + "Quản Trị Liên Minh: " + alliance.getName());

        ItemStack pane = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        inv.setItem(10, createGuiItem(Material.BEACON, ChatColor.GOLD + "" + ChatColor.BOLD + "Thông Tin Liên Minh", "NONE",
                ChatColor.GRAY + "Tên bang hội: " + ChatColor.YELLOW + alliance.getName(),
                ChatColor.GRAY + "Mã định danh (ID): " + ChatColor.AQUA + alliance.getAllyId(),
                ChatColor.GRAY + "Thủ Lĩnh: " + ChatColor.LIGHT_PURPLE + Bukkit.getOfflinePlayer(alliance.getLeader()).getName(),
                ChatColor.GRAY + "Tổng thành viên: " + ChatColor.GREEN + alliance.getMembers().size() + " thành viên",
                ChatColor.GRAY + "Ngân khố chung: " + ChatColor.GOLD + String.format("%,.0f", alliance.getBankBalance()) + " Xu"
        ));

        List<String> memberNames = new ArrayList<>();
        memberNames.add(ChatColor.GRAY + "Danh sách thành viên hiện có:");
        for (UUID mUUID : alliance.getMembers()) {
            String role = mUUID.equals(alliance.getLeader()) ? ChatColor.RED + " [Thủ Lĩnh]" : ChatColor.GRAY + " [Thành Viên]";
            String name = Bukkit.getOfflinePlayer(mUUID).getName();
            memberNames.add(ChatColor.GRAY + " - " + ChatColor.WHITE + (name != null ? name : "Ẩn Danh") + role);
        }
        inv.setItem(11, createGuiItem(Material.PAPER, ChatColor.YELLOW + "" + ChatColor.BOLD + "Danh Sách Thành Viên", "NONE", memberNames.toArray(new String[0])));

        inv.setItem(12, createGuiItem(Material.CHEST, ChatColor.YELLOW + "" + ChatColor.BOLD + "Hòm Đồ Liên Minh", "OPEN_CHEST",
                ChatColor.GRAY + "Mở kho bảo mật lưu trữ dùng chung.",
                ChatColor.GRAY + "Mọi thành viên cùng liên minh đều có quyền truy cập."
        ));

        inv.setItem(13, createGuiItem(Material.GOLD_INGOT, ChatColor.GOLD + "" + ChatColor.BOLD + "Quỹ Đóng Góp Liên Minh", "ALLY_BANK_INFO",
                ChatColor.GRAY + "Số dư hiện tại: " + ChatColor.GREEN + String.format("%,.0f", alliance.getBankBalance()) + " Xu",
                ChatColor.GRAY + "Để nạp tiền vào quỹ, sử dụng lệnh:",
                ChatColor.YELLOW + " -> /ally deposit <số_tiền>"
        ));

        inv.setItem(14, createGuiItem(Material.IRON_SWORD, ChatColor.RED + "" + ChatColor.BOLD + "Tuyên Chiến Quốc Gia", "OPEN_WAR_DECLARES",
                ChatColor.GRAY + "Khai chiến và công phá thành trì đối thủ.",
                ChatColor.GRAY + "Yêu cầu: Phải đứng cạnh Lõi đối thủ để tuyên chiến."
        ));

        inv.setItem(15, createGuiItem(Material.ANVIL, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Nâng Cấp Liên Minh", "OPEN_UPGRADES",
                ChatColor.GRAY + "Sử dụng ngân khố để nâng cấp giới hạn thành viên,",
                ChatColor.GRAY + "mở rộng hòm đồ chung và tăng sức mạnh tháp phòng thủ."
        ));

        if (alliance.getLeader().equals(viewer.getUniqueId())) {
            inv.setItem(15, createGuiItem(Material.OAK_SIGN, ChatColor.GREEN + "" + ChatColor.BOLD + "Mời Thành Viên", "OPEN_INVITE_MENU",
                    ChatColor.GRAY + "Gửi lời mời gia nhập Liên Minh cho người chơi khác."
            ));
            inv.setItem(16, createGuiItem(Material.SHEARS, ChatColor.RED + "" + ChatColor.BOLD + "Trục Xuất Thành Viên", "OPEN_KICK_MENU",
                    ChatColor.GRAY + "Trục xuất thành viên ra khỏi Liên Minh lập tức."
            ));
            inv.setItem(17, createGuiItem(Material.TNT, ChatColor.RED + "" + ChatColor.BOLD + "Giải Tán Liên Minh", "DISBAND_ALLY",
                    ChatColor.GRAY + "Xóa bỏ hoàn toàn Liên minh này vĩnh viễn.",
                    ChatColor.GRAY + "Mọi thành viên sẽ trở về trạng thái Solo tự do.",
                    ChatColor.RED + "Cảnh báo: Hành động này không thể hoàn tác!"
            ));
            inv.setItem(18, createGuiItem(Material.GOLDEN_HOE, ChatColor.GOLD + "" + ChatColor.BOLD + "Hợp Nhất Lãnh Thổ", "MERGE_TERRITORY",
                    ChatColor.GRAY + "Gộp diện tích lãnh thổ của các thành viên",
                    ChatColor.GRAY + "lại với nhau để tăng cường ranh giới bảo vệ",
                    ChatColor.GRAY + "và nhận thêm chỉ số gia tăng sức mạnh.",
                    " ",
                    ChatColor.YELLOW + "➔ Nhấp chuột để bắt đầu hợp nhất!"
            ));
        } else {
            inv.setItem(16, createGuiItem(Material.LEATHER_BOOTS, ChatColor.RED + "" + ChatColor.BOLD + "Rời Khỏi Liên Minh", "LEAVE_ALLY",
                    ChatColor.GRAY + "Thoát ly khỏi liên minh hiện tại.",
                    ChatColor.GRAY + "Bạn sẽ mất quyền hạn bảo vệ Friendly Fire",
                    ChatColor.GRAY + "và không thể sử dụng kho chung."
            ));
        }

        inv.setItem(22, createGuiItem(Material.BARRIER, ChatColor.RED + "Đóng Giao Diện", "CLOSE"));

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

            if (action.equalsIgnoreCase("OPEN_CHEST")) {
                if (alliance == null) return;
                player.closeInventory();
                // ĐỒNG BỘ ServerChestHook: Sử dụng lớp hỗ trợ ServerChestHook để mở kho rương
                com.truongcm.territorydefense.hook.ServerChestHook.openGuildChest(player, alliance.getAllyId(), alliance.getName());
                return;
            }

            if (action.equalsIgnoreCase("OPEN_WAR_DECLARES")) {
                player.closeInventory();
                player.openInventory(new com.truongcm.territorydefense.feature.combat.siege.ui.WarDeclarationGui(plugin, player).getInventory());
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 1.0f, 1.2f);
                return;
            }

            if (action.equalsIgnoreCase("OPEN_INVITE_MENU")) {
                player.closeInventory();
                player.openInventory(new AllyInviteGui(plugin, alliance).getInventory(player));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 1.0f, 1.1f);
                return;
            }

            if (action.equalsIgnoreCase("OPEN_KICK_MENU")) {
                player.closeInventory();
                player.openInventory(new AllyKickGui(plugin, alliance).getInventory(player));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 1.0f, 1.1f);
                return;
            }

            if (action.equalsIgnoreCase("CREATE_ALLY_INFO")) {
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + "===============================================");
                player.sendMessage(ChatColor.GREEN + "Để thành lập Liên Minh, hãy nhập lệnh sau:");
                player.sendMessage(ChatColor.YELLOW + " 👉 /ally create <Tên_Liên_Minh>");
                player.sendMessage(ChatColor.GRAY + "Chi phí yêu cầu trong ví: " + ChatColor.GOLD + "50,000 Xu");
                player.sendMessage(ChatColor.GOLD + "===============================================");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_YES, 1.0f, 1.2f);
                return;
            }

            if (action.equalsIgnoreCase("LEAVE_ALLY")) {
                if (alliance == null) return;
                player.closeInventory();
                player.performCommand("ally leave");
                return;
            }

            if (action.equalsIgnoreCase("DISBAND_ALLY")) {
                if (alliance == null) return;
                player.closeInventory();
                player.performCommand("ally disband");
                return;
            }

            if (action.equalsIgnoreCase("MERGE_TERRITORY")) {
                if (alliance == null) return;
                player.closeInventory();
                player.performCommand("ally merge");
                return;
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

    public Alliance getAlliance() {
        return alliance;
    }
}
