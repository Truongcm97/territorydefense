package com.truongcm.territorydefense.feature.alliance.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.base.ui.GUIRouter;
import com.truongcm.territorydefense.feature.alliance.Alliance;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GIAO DIỆN TRỤC XUẤT THÀNH VIÊN (ALLY KICK GUI) - PHIÊN BẢN ĐỘNG V32 (TÁI CẤU TRÚC STATEFUL)
 * Kế thừa CustomHolder giúp tự đóng gói hành vi hiển thị và click chuột.
 */
public class AllyKickGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final Alliance alliance;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;

    public AllyKickGui(TerritoryDefense plugin, Alliance alliance) {
        this.plugin = plugin;
        this.alliance = alliance;
        this.actionKey = PDCKeys.GUI_ACTION;
        this.targetKey = new NamespacedKey(plugin, "td_kick_target");
    }

    @Override
    public Inventory getInventory() {
        return getInventory(null);
    }

    public Inventory getInventory(Player viewer) {
        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.RED + "Trục Xuất Thành Viên");

        setupBackground(inv);
        int membersCount = renderMembers(inv);

        if (membersCount == 0) {
            inv.setItem(22, createGuiItem(Material.BARRIER, ChatColor.YELLOW + "" + ChatColor.BOLD + "Không Có Thành Viên", actionKey, "NONE",
                    ChatColor.GRAY + "Liên minh của bạn hiện tại chưa có thành viên nào",
                    ChatColor.GRAY + "khác ngoài vị trí Thủ lĩnh của bạn."
            ));
        }

        inv.setItem(49, createGuiItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Quay Lại Bang Hội", actionKey, "CLOSE_TO_ALLY_MENU"));

        return inv;
    }

    private void setupBackground(Inventory inv) {
        ItemStack redPane = createGuiItem(Material.RED_STAINED_GLASS_PANE, " ", actionKey, "NONE");
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, redPane);
        }

        ItemStack darkPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", actionKey, "NONE");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, darkPane);
        }
    }

    private int renderMembers(Inventory inv) {
        int slot = 0;
        for (UUID memberUUID : alliance.getMembers()) {
            if (slot >= 45) break;

            if (memberUUID.equals(alliance.getLeader())) {
                continue;
            }

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(memberUUID);
            ItemStack head = createMemberHead(offlinePlayer, memberUUID);
            inv.setItem(slot, head);
            slot++;
        }
        return slot;
    }

    private ItemStack createMemberHead(OfflinePlayer offlinePlayer, UUID memberUUID) {
        String memberName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Người chơi lạ";
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(offlinePlayer);
            skullMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + memberName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Chức vụ: " + ChatColor.WHITE + "Thành Viên");
            lore.add(ChatColor.GRAY + "Trạng thái: " + (offlinePlayer.isOnline() ? ChatColor.GREEN + "● Đang hoạt động" : ChatColor.RED + "○ Ngoại tuyến"));
            lore.add(" ");
            lore.add(ChatColor.DARK_RED + "➔ Nhấp chuột trái để TRỤC XUẤT khỏi Liên Minh!");

            skullMeta.setLore(lore);
            skullMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "KICK_PLAYER_TARGET");
            skullMeta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, memberUUID.toString());
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.hasItemMeta()) {
            PersistentDataContainer pdc = clickedItem.getItemMeta().getPersistentDataContainer();
            String action = pdc.get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            if (action.equalsIgnoreCase("CLOSE_TO_ALLY_MENU")) {
                handleReturnToMenu(player);
                return;
            }

            if ("KICK_PLAYER_TARGET".equals(action)) {
                handleKickClick(pdc, player);
            }
        }
    }

    private void handleReturnToMenu(Player player) {
        player.closeInventory();
        GUIRouter.openAllianceMenu(player);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
    }

    private void handleKickClick(PersistentDataContainer pdc, Player player) {
        String targetUuidStr = pdc.get(targetKey, PersistentDataType.STRING);
        if (targetUuidStr != null) {
            try {
                UUID memberUUID = UUID.fromString(targetUuidStr);
                plugin.getAllianceManager().kickMember(player.getUniqueId(), memberUUID);
                player.sendMessage(ChatColor.RED + "[Liên minh] Đã trục xuất thành viên khỏi bang.");
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "[Lỗi] Không thể định danh thành viên này!");
            }
            player.closeInventory();
        }
    }

    public Alliance getAlliance() {
        return alliance;
    }
}
