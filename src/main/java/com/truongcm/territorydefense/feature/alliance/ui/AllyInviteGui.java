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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GIAO DIỆN MỜI THÀNH VIÊN (ALLY INVITE GUI) - PHIÊN BẢN ĐỘNG V32 (TÁI CẤU TRÚC STATEFUL)
 * Kế thừa CustomHolder giúp tự đóng gói hành vi hiển thị và click chuột.
 */
public class AllyInviteGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final Alliance alliance;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;

    public AllyInviteGui(TerritoryDefense plugin, Alliance alliance) {
        this.plugin = plugin;
        this.alliance = alliance;
        this.actionKey = PDCKeys.GUI_ACTION;
        this.targetKey = new NamespacedKey(plugin, "td_invite_target");
    }

    @Override
    public Inventory getInventory() {
        return getInventory(null);
    }

    public Inventory getInventory(Player viewer) {
        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.DARK_GREEN + "Mời Thành Viên Vào Bang");

        ItemStack greenPane = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta greenMeta = greenPane.getItemMeta();
        if (greenMeta != null) {
            greenMeta.setDisplayName(" ");
            greenPane.setItemMeta(greenMeta);
        }
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, greenPane);
        }

        ItemStack darkPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta darkMeta = darkPane.getItemMeta();
        if (darkMeta != null) {
            darkMeta.setDisplayName(" ");
            darkPane.setItemMeta(darkMeta);
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, darkPane);
        }

        int slot = 0;
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;

            if (viewer != null && onlinePlayer.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }

            String targetAllyId = plugin.getAllianceManager().getPlayerAlliance(onlinePlayer.getUniqueId());
            if (targetAllyId != null) {
                continue;
            }

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwningPlayer(onlinePlayer);
                skullMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + onlinePlayer.getName());

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Trạng thái: " + ChatColor.GREEN + "Trực tuyến (Online)");
                lore.add(ChatColor.GRAY + "Chức nghiệp: " + ChatColor.YELLOW + "Tự Do (Solo)");
                lore.add(" ");
                lore.add(ChatColor.YELLOW + "➔ Nhấp chuột trái để GỬI LỜI MỜI GIA NHẬP!");

                skullMeta.setLore(lore);
                skullMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "INVITE_PLAYER_TARGET");
                skullMeta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, onlinePlayer.getName());
                head.setItemMeta(skullMeta);
            }

            inv.setItem(slot, head);
            slot++;
        }

        if (slot == 0) {
            inv.setItem(22, createGuiItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Không Có Người Chơi Solo Online", "NONE",
                    ChatColor.GRAY + "Hiện tại toàn bộ người chơi trực tuyến trên Server",
                    ChatColor.GRAY + "đều đã tham gia các tổ chức Liên Minh khác."
            ));
        }

        inv.setItem(49, createGuiItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Quay Lại Bang Hội", "CLOSE_TO_ALLY_MENU"));

        return inv;
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
                player.closeInventory();
                player.openInventory(new AllyMainMenuGui(plugin, alliance, player).getInventory(player));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                return;
            }

            String targetName = pdc.get(targetKey, PersistentDataType.STRING);
            if ("INVITE_PLAYER_TARGET".equals(action) && targetName != null) {
                Player target = Bukkit.getPlayer(targetName);
                if (target != null) {
                    plugin.getAllianceManager().sendInvite(player, target);
                    player.sendMessage(ChatColor.GREEN + "[Liên minh] Đã gửi lời mời tới: " + target.getName());
                } else {
                    player.sendMessage(ChatColor.RED + "[Lỗi] Người chơi không còn online!");
                }
                player.closeInventory();
            }
        }
    }

    private ItemStack createGuiItem(Material material, String name, String actionTag, String... loreLines) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
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
