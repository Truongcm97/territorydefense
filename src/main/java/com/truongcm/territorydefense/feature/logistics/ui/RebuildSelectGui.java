package com.truongcm.territorydefense.feature.logistics.ui;

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
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RebuildSelectGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final NamespacedKey actionKey;

    public RebuildSelectGui(TerritoryDefense plugin, TerritoryCore core) {
        this.plugin = plugin;
        this.core = core;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.BLUE + "Lựa Chọn Bản Vẽ Tái Thiết");

        // Lấp đầy nền kính xám
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, pane);
        }

        // 1. Mục lục Ảnh chụp Trước Raid (Đặt ở Slot 11)
        com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getActiveBuilders().get(core.getCoreId());
        boolean hasPreRaid = builder != null && !builder.getLastPreRaidSnapshot().isEmpty();

        List<String> preRaidLore = new ArrayList<>();
        preRaidLore.add(ChatColor.GRAY + "Khôi phục lại trạng thái ban đầu của lãnh thổ");
        preRaidLore.add(ChatColor.GRAY + "dựa trên ảnh chụp tự động trước trận Raid gần nhất.");
        preRaidLore.add(" ");
        if (hasPreRaid) {
            preRaidLore.add(ChatColor.GOLD + "Tổng khối block: " + ChatColor.GREEN + builder.getLastPreRaidSnapshot().size() + " blocks");
            preRaidLore.add(" ");
            preRaidLore.add(ChatColor.YELLOW + "➔ Nhấp chuột để xem chi tiết nguyên liệu và xác nhận.");
            inv.setItem(11, createGuiItem(Material.ANVIL, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Tái Thiết Theo Ảnh Trước Raid", "REBUILD_PRE_RAID", preRaidLore.toArray(new String[0])));
        } else {
            preRaidLore.add(ChatColor.RED + "Không có ảnh chụp trước trận Raid nào được lưu!");
            inv.setItem(11, createGuiItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Không có ảnh chụp trước Raid", "NONE", preRaidLore.toArray(new String[0])));
        }

        // 2. Hiển thị danh sách các bản vẽ thiết kế đã lưu của Lõi (Bắt đầu hiển thị từ dòng 3 trở đi, slot 18 đến 44)
        List<List<TerritoryCore.BlockSnapshot>> slots = core.getBlueprintSlots();
        List<String> names = core.getBlueprintNames();
        List<Integer> scanLevels = core.getBlueprintScanLevels();

        int guiSlot = 18;
        for (int i = 0; i < slots.size() && guiSlot < 45; i++) {
            List<TerritoryCore.BlockSnapshot> design = slots.get(i);
            if (design != null && !design.isEmpty()) {
                String customName = names.get(i);
                int scanLvl = scanLevels.get(i);

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Khôi phục lại thiết kế dựa trên bản vẽ này.");
                lore.add(" ");
                lore.add(ChatColor.GOLD + "Tổng khối block: " + ChatColor.GREEN + design.size() + " blocks");
                lore.add(" ");
                lore.add(ChatColor.YELLOW + "➔ Nhấp chuột để xem chi tiết nguyên liệu và xác nhận.");

                String displayName = ChatColor.GREEN + customName + " (Cấp " + scanLvl + ")";
                inv.setItem(guiSlot, createGuiItem(Material.WRITTEN_BOOK, displayName, "REBUILD_SLOT_" + i, lore.toArray(new String[0])));
                guiSlot++;
            }
        }

        // 3. Nút Điều Hướng ở dưới cùng (Slot 49 và 53)
        inv.setItem(49, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Quay lại Lõi", "BACK"));
        inv.setItem(53, createGuiItem(Material.BARRIER, ChatColor.RED + "Thoát ra", "CLOSE"));

        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.hasItemMeta()) {
            String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            if (action.equalsIgnoreCase("CLOSE")) {
                player.closeInventory();
                return;
            }

            if (action.equalsIgnoreCase("BACK")) {
                player.closeInventory();
                player.openInventory(new CoreGui(plugin, core, CoreGui.CoreTab.LOGISTICS).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("REBUILD_PRE_RAID")) {
                com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getOrCreateBuilder(core.getCoreId());
                if (builder == null) {
                    player.sendMessage(ChatColor.RED + "Bạn cần thuê Thợ Xây NPC trước!");
                    return;
                }
                if (builder.getLastPreRaidSnapshot().isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Không có ảnh chụp trước trận Raid nào được lưu!");
                    return;
                }
                player.closeInventory();
                player.openInventory(new RebuildConfirmGui(plugin, core, builder.getLastPreRaidSnapshot(), "Ảnh Chụp Trước Raid", -1, 0).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                return;
            }

            if (action.startsWith("REBUILD_SLOT_")) {
                int slotIndex = Integer.parseInt(action.substring(13));
                com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getOrCreateBuilder(core.getCoreId());
                if (builder == null) {
                    player.sendMessage(ChatColor.RED + "Bạn cần thuê Thợ Xây NPC trước!");
                    return;
                }
                List<TerritoryCore.BlockSnapshot> design = core.getBlueprintSlots().get(slotIndex);
                if (design == null || design.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Bản vẽ này đang trống!");
                    return;
                }
                String customName = core.getBlueprintNames().get(slotIndex);
                player.closeInventory();
                player.openInventory(new RebuildConfirmGui(plugin, core, design, customName, slotIndex, 0).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
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
