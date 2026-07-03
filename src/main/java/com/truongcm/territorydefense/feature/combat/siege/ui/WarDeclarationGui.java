package com.truongcm.territorydefense.feature.combat.siege.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.base.ui.GUIRouter;
import com.truongcm.territorydefense.feature.alliance.Alliance;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
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

import java.util.*;

/**
 * GIAO DIỆN TUYÊN CHIẾN NGOẠI GIAO (WAR DECLARATION GUI) - PHIÊN BẢN V32 (TÁI CẤU TRÚC STATEFUL)
 * Kế thừa CustomHolder giúp tự đóng gói hành vi hiển thị và click chuột.
 */
public class WarDeclarationGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;
    private final Player viewer;

    public WarDeclarationGui(TerritoryDefense plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.actionKey = PDCKeys.GUI_ACTION;
        this.targetKey = new NamespacedKey(plugin, "td_war_target");
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.RED + "Chọn Mục Tiêu Tuyên Chiến");

        // Phủ nền kính xám tạo chiều sâu cho rương
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
        }
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, pane);
        }

        int slot = 0;
        Set<String> processedDefenders = new HashSet<>();

        for (TerritoryCore core : plugin.getCoreManager().getAllActiveCores()) {
            if (slot >= 45) break;

            String allyId = plugin.getCoreManager().getCoreAlliance(core);
            String defenderId;
            String defenderName;
            Material icon;
            List<String> lore = new ArrayList<>();
            boolean isAlliance = false;
            boolean isOnline = false;
            List<String> onlineMembers = new ArrayList<>();

            if (allyId != null && !allyId.isEmpty()) {
                defenderId = allyId;
                isAlliance = true;
                Alliance alliance = plugin.getAllianceManager().getAlliance(allyId);
                if (alliance == null) continue;

                defenderName = alliance.getName();
                icon = Material.RED_BANNER;

                for (UUID mUUID : alliance.getMembers()) {
                    Player member = Bukkit.getPlayer(mUUID);
                    if (member != null && member.isOnline()) {
                        isOnline = true;
                        onlineMembers.add(member.getName());
                    }
                }
            } else {
                UUID ownerUUID = core.getOwnerUUID();
                defenderId = ownerUUID.toString();
                isAlliance = false;

                Player targetPlayer = Bukkit.getPlayer(ownerUUID);
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    isOnline = true;
                    defenderName = targetPlayer.getName();
                } else {
                    continue;
                }
                icon = Material.PLAYER_HEAD;
            }

            if (processedDefenders.contains(defenderId)) {
                continue;
            }

            if (!isOnline) {
                continue;
            }

            processedDefenders.add(defenderId);

            lore.add(ChatColor.GRAY + "Phân loại: " + (isAlliance ? ChatColor.RED + "Liên Minh Quốc Gia" : ChatColor.YELLOW + "Người chơi Cá Nhân (Solo)"));
            lore.add(ChatColor.GRAY + "Cấp độ Lõi bảo vệ: " + ChatColor.GOLD + "Cấp " + core.getLevel());
            lore.add(ChatColor.GRAY + "Độ bền giáp ảo: " + ChatColor.AQUA + String.format("%.0f", core.getShield()) + " HP");

            int shardCost = core.getLevel() * 5;
            lore.add(ChatColor.GRAY + "Phí tuyên chiến: " + ChatColor.LIGHT_PURPLE + shardCost + " Shards");
            lore.add(" ");

            if (isAlliance) {
                lore.add(ChatColor.GREEN + "Thành viên trực tuyến (" + onlineMembers.size() + "):");
                for (String name : onlineMembers) {
                    lore.add(ChatColor.GRAY + " - " + ChatColor.WHITE + name);
                }
            } else {
                lore.add(ChatColor.GREEN + "Trạng thái: " + ChatColor.WHITE + "ĐANG TRỰC TUYẾN");
            }

            lore.add(" ");
            lore.add(ChatColor.RED + "➔ Nhấp chuột trái để PHÁT ĐỘNG TUYÊN CHIẾN!");

            ItemStack targetItem = new ItemStack(icon);
            ItemMeta meta = targetItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(isAlliance ? ChatColor.RED + "" + ChatColor.BOLD + "Liên Minh: " + defenderName : ChatColor.YELLOW + "" + ChatColor.BOLD + "Solo: " + defenderName);
                meta.setLore(lore);

                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "DECLARE_WAR_TARGET");
                meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, defenderId);
                targetItem.setItemMeta(meta);
            }

            inv.setItem(slot, targetItem);
            slot++;
        }

        if (slot == 0) {
            inv.setItem(22, createGuiItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Không Có Mục Tiêu Nào Khả Dụng", "NONE",
                    ChatColor.GRAY + "Hiện tại không có bất kỳ Liên minh hoặc người chơi",
                    ChatColor.GRAY + "Solo nào có Lõi Lãnh thổ đang trực tuyến trên thế giới."
            ));
        }

        inv.setItem(47, createGuiItem(Material.GOLD_INGOT, ChatColor.GOLD + "" + ChatColor.BOLD + "Chiêu Mộ Lính Đánh Thuê (Mercenary)", "OPEN_MERCENARY_HIRE",
                ChatColor.GRAY + "Nhấp để mở giao diện thuê lính đánh thuê",
                ChatColor.GRAY + "chuẩn bị chiến lực trước khi tuyên chiến công thành."
        ));

        inv.setItem(49, createGuiItem(Material.BARRIER, ChatColor.RED + "Quay Lại Bang Hội", "CLOSE_TO_MAIN_ALLY"));

        inv.setItem(51, createGuiItem(Material.WHITE_BANNER, ChatColor.GOLD + "" + ChatColor.BOLD + "Mua Cờ Công Thành (Siege Flag)", "BUY_SIEGE_FLAG",
                ChatColor.GRAY + "Chi phí: " + ChatColor.GOLD + "20,000 Xu",
                ChatColor.GRAY + "Bắt buộc trang bị ở tay trái (Off-hand) khi công thành",
                ChatColor.GRAY + "thì mới có thể đập block & phá khiên lõi đối phương.",
                " ",
                ChatColor.YELLOW + "➔ Click để mua!"
        ));

        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.hasItemMeta()) {
            String action = clickedItem.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            if (action.equalsIgnoreCase("OPEN_MERCENARY_HIRE")) {
                player.closeInventory();
                TerritoryCore playerCore = plugin.getCoreManager().getAllActiveCores().stream()
                        .filter(c -> c.getOwnerUUID().equals(player.getUniqueId()))
                        .findFirst().orElse(null);
                if (playerCore != null) {
                    player.openInventory(new com.truongcm.territorydefense.feature.core.ui.CoreGui(plugin, playerCore, com.truongcm.territorydefense.feature.core.ui.CoreGui.CoreTab.COMBAT).getInventory());
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 1.0f, 1.2f);
                } else {
                    player.sendMessage(ChatColor.RED + "[Lỗi] Bạn cần sở hữu một Lõi Lãnh Thổ để chiêu mộ lính đánh thuê!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return;
            }

            if (action.equalsIgnoreCase("CLOSE_TO_MAIN_ALLY")) {
                player.closeInventory();
                GUIRouter.openAllianceMenu(player);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
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

            if (action.equalsIgnoreCase("DECLARE_WAR_TARGET")) {
                String targetId = clickedItem.getItemMeta().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
                if (targetId == null) return;

                player.closeInventory();

                String targetName = null;
                Alliance alliance = plugin.getAllianceManager().getAlliance(targetId);
                if (alliance != null) {
                    targetName = alliance.getName();
                } else {
                    try {
                        UUID targetUUID = UUID.fromString(targetId);
                        Player targetPlayer = Bukkit.getPlayer(targetUUID);
                        if (targetPlayer != null) {
                            targetName = targetPlayer.getName();
                        } else {
                            targetName = Bukkit.getOfflinePlayer(targetUUID).getName();
                        }
                    } catch (Exception ignored) {}
                }

                if (targetName != null) {
                    player.performCommand("ally declare " + targetName);
                } else {
                    player.sendMessage(ChatColor.RED + "[Chiến tranh] Không thể tìm thấy thông tin đối thủ hợp lệ để tuyên chiến!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
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
}
