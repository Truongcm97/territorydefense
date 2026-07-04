package com.truongcm.territorydefense.feature.web;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.ServerBlueprintManager;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import com.truongcm.territorydefense.feature.logistics.ui.RebuildConfirmGui;
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

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class AdminBlueprintActionGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final ServerBlueprintManager.ServerBlueprint blueprint;
    private final int bpIndex;
    private final TerritoryCore selectedCore;
    private final int originalPage;
    private final NamespacedKey actionKey;

    public AdminBlueprintActionGui(TerritoryDefense plugin, ServerBlueprintManager.ServerBlueprint blueprint, int bpIndex, TerritoryCore selectedCore, int originalPage) {
        this.plugin = plugin;
        this.blueprint = blueprint;
        this.bpIndex = bpIndex;
        this.selectedCore = selectedCore;
        this.originalPage = originalPage;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        String title = ChatColor.RED + "⭐ THAO TÁC: " + blueprint.getDisplayName();
        Inventory inv = Bukkit.createInventory(this, 27, title);

        // Fill background with black panes
        ItemStack bg = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ", actionKey, "NONE");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }

        // Slot 10: Apply/Build Blueprint on Selected Core
        inv.setItem(10, createGuiItem(Material.EMERALD_BLOCK, 
                ChatColor.GREEN + "" + ChatColor.BOLD + "✔ ÁP DỤNG LÊN LÕI", 
                actionKey, "APPLY",
                ChatColor.GRAY + "Tiến hành tái thiết/xây dựng bản vẽ này",
                ChatColor.GRAY + "cho lõi bảo vệ hiện tại.",
                " ",
                ChatColor.YELLOW + "➔ Click để tiến hành xây dựng."
        ));

        // Slot 12: Put blueprint up for sale (Treo bán bản vẽ lên cửa hàng của Lõi)
        inv.setItem(12, createGuiItem(Material.GOLD_INGOT, 
                ChatColor.GOLD + "" + ChatColor.BOLD + "💰 TREO BÁN LÊN CỬA HÀNG", 
                actionKey, "SELL",
                ChatColor.GRAY + "Treo bán bản vẽ này công khai lên chợ,",
                ChatColor.GRAY + "giao dịch bằng Xu với người chơi khác.",
                " ",
                ChatColor.YELLOW + "➔ Click để treo bán với giá 50,000 Xu."
        ));

        // Slot 14: Send blueprint to player (Gửi bản vẽ cho chủ Lõi)
        inv.setItem(14, createGuiItem(Material.CHEST, 
                ChatColor.AQUA + "" + ChatColor.BOLD + "🎁 GỬI BẢN VẼ CHO PLAYER", 
                actionKey, "SEND",
                ChatColor.GRAY + "Thêm bản thiết kế này trực tiếp vào",
                ChatColor.GRAY + "danh sách bản vẽ cá nhân của chủ lõi.",
                " ",
                ChatColor.YELLOW + "➔ Click để gửi ngay."
        ));

        // Slot 16: Delete blueprint (Xoá bản vẽ khỏi server_blueprints)
        inv.setItem(16, createGuiItem(Material.REDSTONE_BLOCK, 
                ChatColor.RED + "" + ChatColor.BOLD + "❌ XOÁ BẢN VẼ KHỎI SERVER", 
                actionKey, "DELETE",
                ChatColor.GRAY + "Xoá vĩnh viễn tệp bản vẽ này khỏi",
                ChatColor.GRAY + "thư mục 'server_blueprints/' của máy chủ.",
                " ",
                ChatColor.DARK_RED + "⚠ Hành động này không thể hoàn tác!"
        ));

        // Slot 22: Back button (Nút quay lại)
        inv.setItem(22, createGuiItem(Material.ARROW, 
                ChatColor.YELLOW + "" + ChatColor.BOLD + "◀ QUAY LẠI DANH SÁCH", 
                actionKey, "BACK",
                ChatColor.GRAY + "Trở về danh sách bản vẽ máy chủ."
        ));

        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.hasItemMeta()) {
            String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null || action.equalsIgnoreCase("NONE")) return;

            if (action.equalsIgnoreCase("BACK")) {
                player.openInventory(new AdminCoreManagerGui(plugin, AdminCoreManagerGui.AdminTab.SERVER_BLUEPRINTS, selectedCore, originalPage).getInventory());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("APPLY")) {
                player.closeInventory();
                player.openInventory(new RebuildConfirmGui(plugin, selectedCore, blueprint.getBlocks(), blueprint.getDisplayName(), -3, 0).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("SELL")) {
                // Find a vacant blueprint slot on selectedCore to set up the sale
                int vacantSlot = -1;
                for (int s = 0; s < 54; s++) {
                    if (selectedCore.isBlueprintSlotEmpty(s)) {
                        vacantSlot = s;
                        break;
                    }
                }

                if (vacantSlot == -1) {
                    player.sendMessage(ChatColor.RED + "[Admin] Lõi của người chơi này đã đầy cả 54 slot bản vẽ, không có chỗ để treo bán!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                // Copy blocks to that slot
                selectedCore.setBlueprintSlot(vacantSlot, blueprint.getBlocks());
                selectedCore.setBlueprintSlotDirty(vacantSlot, true);
                selectedCore.getBlueprintNames().set(vacantSlot, blueprint.getDisplayName());
                selectedCore.getBlueprintSlotsBought().set(vacantSlot, true);
                selectedCore.getBlueprintPrices().set(vacantSlot, 50000.0);
                selectedCore.getBlueprintSellingStatus().set(vacantSlot, true);
                selectedCore.getBlueprintScanLevels().set(vacantSlot, 1);
                
                // Save core data asynchronously to prevent watchdog freeze
                int finalVacantSlot = vacantSlot;
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getCoreStorage().saveBlueprintBinary(selectedCore.getOwnerUUID(), selectedCore.getCoreId(), finalVacantSlot, blueprint.getBlocks());
                });
                selectedCore.markDirty();

                player.sendMessage(ChatColor.GREEN + "[Admin] Đã đăng bán bản vẽ '" + blueprint.getDisplayName() + "' lên cửa hàng Lõi của người chơi thành công (Slot #" + (vacantSlot + 1) + ", Giá: 50,000 Xu)!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                player.openInventory(new AdminCoreManagerGui(plugin, AdminCoreManagerGui.AdminTab.SERVER_BLUEPRINTS, selectedCore, originalPage).getInventory());
                return;
            }

            if (action.equalsIgnoreCase("SEND")) {
                // Find a vacant slot to send/give the blueprint directly to player core
                int vacantSlot = -1;
                for (int s = 0; s < 54; s++) {
                    if (selectedCore.isBlueprintSlotEmpty(s)) {
                        vacantSlot = s;
                        break;
                    }
                }

                if (vacantSlot == -1) {
                    player.sendMessage(ChatColor.RED + "[Admin] Lõi của người chơi này đã đầy cả 54 slot bản vẽ, không thể gửi thêm!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                // Copy blocks to that slot
                selectedCore.setBlueprintSlot(vacantSlot, blueprint.getBlocks());
                selectedCore.setBlueprintSlotDirty(vacantSlot, true);
                selectedCore.getBlueprintNames().set(vacantSlot, blueprint.getDisplayName());
                selectedCore.getBlueprintSlotsBought().set(vacantSlot, true);
                selectedCore.getBlueprintPrices().set(vacantSlot, 0.0);
                selectedCore.getBlueprintSellingStatus().set(vacantSlot, false);
                selectedCore.getBlueprintScanLevels().set(vacantSlot, 1);

                // Save core data asynchronously to prevent watchdog freeze
                int finalSendVacantSlot = vacantSlot;
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getCoreStorage().saveBlueprintBinary(selectedCore.getOwnerUUID(), selectedCore.getCoreId(), finalSendVacantSlot, blueprint.getBlocks());
                });
                selectedCore.markDirty();

                Player target = Bukkit.getPlayer(selectedCore.getOwnerUUID());
                if (target != null && target.isOnline()) {
                    target.sendMessage(ChatColor.GREEN + "[Bảo vệ] Bạn đã được Admin gửi tặng bản vẽ thiết kế '" + blueprint.getDisplayName() + "' vào danh sách bản vẽ cá nhân (Slot #" + (vacantSlot + 1) + ")!");
                    target.playSound(target.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                }

                player.sendMessage(ChatColor.GREEN + "[Admin] Đã gửi tặng bản vẽ '" + blueprint.getDisplayName() + "' trực tiếp tới danh sách bản vẽ cá nhân của người chơi (Slot #" + (vacantSlot + 1) + ")!");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                player.openInventory(new AdminCoreManagerGui(plugin, AdminCoreManagerGui.AdminTab.SERVER_BLUEPRINTS, selectedCore, originalPage).getInventory());
                return;
            }

            if (action.equalsIgnoreCase("DELETE")) {
                // Find file and delete it
                File bpsFolder = new File(plugin.getDataFolder(), "server_blueprints");
                File bpFile = new File(bpsFolder, blueprint.getFileName());
                if (bpFile.exists()) {
                    if (bpFile.delete()) {
                        player.sendMessage(ChatColor.GREEN + "[Admin] Đã xoá vĩnh viễn tệp bản vẽ '" + blueprint.getFileName() + "' khỏi máy chủ!");
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                        plugin.getServerBlueprintManager().reload();
                    } else {
                        player.sendMessage(ChatColor.RED + "[Admin] Không thể xoá tệp bản vẽ. Vui lòng kiểm tra quyền truy cập file.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "[Admin] Không tìm thấy tệp bản vẽ này trên máy chủ.");
                }
                player.openInventory(new AdminCoreManagerGui(plugin, AdminCoreManagerGui.AdminTab.SERVER_BLUEPRINTS, selectedCore, 0).getInventory());
                return;
            }
        }
    }
}
