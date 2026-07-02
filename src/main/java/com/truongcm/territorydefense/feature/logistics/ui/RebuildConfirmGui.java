package com.truongcm.territorydefense.feature.logistics.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
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

public class RebuildConfirmGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final List<TerritoryCore.BlockSnapshot> design;
    private final String blueprintName;
    private final int slotIndex; // -1 đại diện cho Pre-Raid, >= 0 đại diện cho Slot thông thường
    private final int page;
    private final NamespacedKey actionKey;
    private final boolean fromBlueprintList;

    public RebuildConfirmGui(TerritoryDefense plugin, TerritoryCore core, List<TerritoryCore.BlockSnapshot> design, String blueprintName, int slotIndex, int page) {
        this(plugin, core, design, blueprintName, slotIndex, page, false);
    }

    public RebuildConfirmGui(TerritoryDefense plugin, TerritoryCore core, List<TerritoryCore.BlockSnapshot> design, String blueprintName, int slotIndex, int page, boolean fromBlueprintList) {
        this.plugin = plugin;
        this.core = core;
        this.design = design;
        this.blueprintName = blueprintName;
        this.slotIndex = slotIndex;
        this.page = page;
        this.fromBlueprintList = fromBlueprintList;
        this.actionKey = PDCKeys.GUI_ACTION;
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.BLUE + "Xác Nhận Tái Thiết Bản Vẽ");

        // Lấp đầy nền kính xám
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, pane);
        }

        // 1. Tính toán danh sách nguyên liệu thực sự bị thiếu
        Map<Material, Integer> materialCounts = new HashMap<>();
        int totalMissingBlocks = 0;
        for (TerritoryCore.BlockSnapshot snap : design) {
            Material mat = Material.matchMaterial(snap.material);
            if (mat != null) {
                // Đối chiếu với trạng thái thực tế tại thế giới
                org.bukkit.Location blockLoc = core.getLocation().clone().add(snap.relX, snap.relY, snap.relZ);
                org.bukkit.block.Block currentBlock = blockLoc.getBlock();
                if (currentBlock.getType() != mat) {
                    Material itemMat = getRepresentingItemMaterial(mat);
                    materialCounts.put(itemMat, materialCounts.getOrDefault(itemMat, 0) + 1);
                    totalMissingBlocks++;
                }
            }
        }

        // 2. Sách thông tin bản vẽ (Đặt ở Slot 4)
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Tên bản vẽ: " + ChatColor.GREEN + blueprintName);
        infoLore.add(ChatColor.GRAY + "Tổng số lượng khối bản vẽ: " + ChatColor.GOLD + design.size() + " blocks");
        infoLore.add(ChatColor.GRAY + "Số lượng khối thực sự thiếu: " + ChatColor.RED + totalMissingBlocks + " blocks");
        infoLore.add(" ");
        if (totalMissingBlocks == 0) {
            infoLore.add(ChatColor.GREEN + "✔ Hiện tại lãnh thổ đã khớp hoàn toàn với bản vẽ!");
        } else {
            infoLore.add(ChatColor.YELLOW + "➔ Hãy kiểm tra kỹ nguyên liệu bị thiếu bên dưới.");
        }
        inv.setItem(4, createGuiItem(Material.BOOK, ChatColor.AQUA + "" + ChatColor.BOLD + "Thông Tin Bản Thiết Kế", "NONE", infoLore.toArray(new String[0])));

        List<Material> uniqueMaterials = new ArrayList<>(materialCounts.keySet());
        uniqueMaterials.sort((a, b) -> a.name().compareTo(b.name()));

        // Phân trang: mỗi trang chứa tối đa 27 loại block (slots 18 đến 44)
        int itemsPerPage = 27;
        int totalPages = (int) Math.ceil((double) uniqueMaterials.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, uniqueMaterials.size());

        // Lấy thông tin số lượng block trong Rương Tái Thiết của Lõi để đối chiếu
        Inventory rebuildInv = core.getRebuildWarehouse();
        Map<Material, Integer> availableCounts = new HashMap<>();
        for (ItemStack item : rebuildInv.getContents()) {
            if (item != null && item.getAmount() > 0) {
                availableCounts.put(item.getType(), availableCounts.getOrDefault(item.getType(), 0) + item.getAmount());
            }
        }

        int guiSlot = 18;
        for (int i = startIndex; i < endIndex; i++) {
            Material mat = uniqueMaterials.get(i);
            int needed = materialCounts.get(mat);
            int available = availableCounts.getOrDefault(mat, 0);

            List<String> matLore = new ArrayList<>();
            matLore.add(" ");
            matLore.add(ChatColor.GRAY + "Số lượng cần thiết: " + ChatColor.GOLD + needed + " block");
            if (available >= needed) {
                matLore.add(ChatColor.GRAY + "Trong kho tái thiết: " + ChatColor.GREEN + available + " / " + needed + " (Đủ)");
            } else {
                matLore.add(ChatColor.GRAY + "Trong kho tái thiết: " + ChatColor.RED + available + " / " + needed + " (Thiếu " + (needed - available) + ")");
            }

            String cleanName = mat.name().toLowerCase().replace("_", " ");
            ItemStack matItem = createGuiItem(mat, ChatColor.YELLOW + cleanName, "NONE", matLore.toArray(new String[0]));
            inv.setItem(guiSlot, matItem);
            guiSlot++;
        }

        // 3. Các nút điều hướng phân trang (Slot 45 và 53)
        if (page > 0) {
            inv.setItem(45, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Trang trước (" + page + "/" + totalPages + ")", "PREV_PAGE"));
        }
        inv.setItem(49, createGuiItem(Material.PAPER, ChatColor.AQUA + "Trang " + (page + 1) + " / " + totalPages, "NONE"));
        if (page < totalPages - 1) {
            inv.setItem(53, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Trang sau ▶ (" + (page + 2) + "/" + totalPages + ")", "NEXT_PAGE"));
        }

        // 4. Nút Xác Nhận & Quay Lại chính (Slot 47 và 51)
        inv.setItem(47, createGuiItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "✔ XÁC NHẬN KIẾN THIẾT", "CONFIRM"));
        inv.setItem(51, createGuiItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "◀ TRỞ VỀ DANH SÁCH", "BACK"));

        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.hasItemMeta()) {
            String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            if (action.equalsIgnoreCase("BACK")) {
                player.closeInventory();
                if (fromBlueprintList) {
                    player.openInventory(new BlueprintListGui(plugin, core).getInventory());
                } else {
                    player.openInventory(new RebuildSelectGui(plugin, core).getInventory());
                }
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("PREV_PAGE")) {
                player.openInventory(new RebuildConfirmGui(plugin, core, design, blueprintName, slotIndex, page - 1, fromBlueprintList).getInventory());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("NEXT_PAGE")) {
                player.openInventory(new RebuildConfirmGui(plugin, core, design, blueprintName, slotIndex, page + 1, fromBlueprintList).getInventory());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("CONFIRM")) {
                com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getOrCreateBuilder(core.getCoreId());
                if (builder == null) {
                    player.sendMessage(ChatColor.RED + "Bạn cần thuê Thợ Xây NPC trước!");
                    return;
                }
                player.closeInventory();
                builder.startRebuild(core, design, player);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
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

    private Material getRepresentingItemMaterial(Material mat) {
        if (mat == null) return null;
        if (mat.isItem()) return mat;

        String name = mat.name();
        
        // Trạng thái mọc hoặc các dạng đặc biệt của cây
        if (name.endsWith("_STEM")) {
            if (name.contains("PUMPKIN")) return Material.PUMPKIN_SEEDS;
            if (name.contains("MELON")) return Material.MELON_SEEDS;
        }
        
        // Các loại bảng treo tường (WALL_SIGN, WALL_HANGING_SIGN) và bảng thường
        if (name.endsWith("_WALL_SIGN")) {
            String wood = name.substring(0, name.indexOf("_WALL_SIGN"));
            Material signMat = Material.matchMaterial(wood + "_SIGN");
            if (signMat != null) return signMat;
        }
        if (name.endsWith("_WALL_HANGING_SIGN")) {
            String wood = name.substring(0, name.indexOf("_WALL_HANGING_SIGN"));
            Material signMat = Material.matchMaterial(wood + "_HANGING_SIGN");
            if (signMat != null) return signMat;
        }
        
        // Torch và wall torch
        if (name.equals("WALL_TORCH")) return Material.TORCH;
        if (name.equals("REDSTONE_WALL_TORCH")) return Material.REDSTONE_TORCH;
        if (name.equals("SOUL_WALL_TORCH")) return Material.SOUL_TORCH;
        
        // Trồng trọt và cây bụi
        if (name.equals("SWEET_BERRY_BUSH")) return Material.SWEET_BERRIES;
        if (name.equals("CARROTS")) return Material.CARROT;
        if (name.equals("POTATOES")) return Material.POTATO;
        if (name.equals("BEETROOTS")) return Material.BEETROOT_SEEDS;
        if (name.equals("COCOA")) return Material.COCOA_BEANS;
        if (name.equals("BAMBOO_SAPLING")) return Material.BAMBOO;
        if (name.equals("PITCHER_CROP")) return Material.PITCHER_POD;
        if (name.equals("TORCHFLOWER_CROP")) return Material.TORCHFLOWER_SEEDS;
        
        // Tripwire và dây bẫy
        if (name.equals("TRIPWIRE")) return Material.STRING;
        
        // Lửa, nước, dung nham
        if (name.equals("WATER") || name.equals("FLOWING_WATER")) return Material.WATER_BUCKET;
        if (name.equals("LAVA") || name.equals("FLOWING_LAVA")) return Material.LAVA_BUCKET;
        if (name.equals("FIRE")) return Material.FLINT_AND_STEEL;
        if (name.equals("SOUL_FIRE")) return Material.FLINT_AND_STEEL;
        
        // Chậu hoa potted
        if (name.startsWith("POTTED_")) {
            String plantName = name.substring(7);
            Material plantMat = Material.matchMaterial(plantName);
            if (plantMat != null && plantMat.isItem()) return plantMat;
            return Material.FLOWER_POT;
        }

        // Đèn, cổng và block kỹ thuật khác
        if (name.equals("FROSTED_ICE")) return Material.ICE;
        
        // Mặc định fallback về STONE nếu không .isItem() và không khớp quy tắc đặc biệt
        return Material.STONE;
    }
}
