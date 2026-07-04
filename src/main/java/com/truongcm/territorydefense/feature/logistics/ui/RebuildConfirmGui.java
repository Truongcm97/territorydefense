package com.truongcm.territorydefense.feature.logistics.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.base.ui.CustomHolder;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RebuildConfirmGui extends CustomHolder {

    private final TerritoryDefense plugin;
    private final TerritoryCore core;
    private final List<TerritoryCore.BlockSnapshot> design;
    private final String blueprintName;
    private final int slotIndex; // -2: List View, -1: Pre-Raid Detail View, >= 0: Custom Blueprint Detail View
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

    // Quản lý bản xem trước
    private static final Map<UUID, List<Location>> activePreviews = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> previewRotations = new ConcurrentHashMap<>();
    private static final Map<UUID, Location> previewCoreLocations = new ConcurrentHashMap<>();

    public static Location getPreviewCoreLocation(UUID uuid) {
        return previewCoreLocations.get(uuid);
    }

    public static void clearPreview(Player player) {
        previewCoreLocations.remove(player.getUniqueId());
        List<Location> locs = activePreviews.remove(player.getUniqueId());
        if (locs != null) {
            for (Location loc : locs) {
                player.sendBlockChange(loc, loc.getBlock().getBlockData());
            }
        }
    }

    @Override
    public void onClose(Player player) {
        // Không dọn dẹp preview khi đóng GUI để cho phép người chơi di chuyển ra xa quan sát bản vẽ ảo lơ lửng.
    }

    @Override
    public Inventory getInventory() {
        if (slotIndex == -2) {
            return getListViewInventory();
        } else {
            return getDetailViewInventory();
        }
    }

    private Inventory getListViewInventory() {
        List<String> names = core.getBlueprintNames();
        List<Integer> scanLevels = core.getBlueprintScanLevels();
        List<Boolean> bought = core.getBlueprintSlotsBought();

        // Lọc các bản vẽ không trống
        List<Integer> existingIndices = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            if (!core.isBlueprintSlotEmpty(i)) {
                existingIndices.add(i);
            }
        }

        int totalItems = existingIndices.size();
        int totalPages = (int) Math.ceil(totalItems / 44.0);
        if (totalPages == 0) totalPages = 1;

        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.BLUE + "Tái Thiết - Chọn Bản Vẽ");

        // Lấp đầy nền kính xám hàng dưới cùng
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, pane);
        }

        // Slot 0: Ảnh Chụp Trước Raid
        com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getOrCreateBuilder(core.getCoreId());
        List<TerritoryCore.BlockSnapshot> preRaidSnap = (builder != null && builder.getLastPreRaidSnapshot() != null) ? builder.getLastPreRaidSnapshot() : new ArrayList<>();
        
        List<String> preRaidLore = new ArrayList<>();
        preRaidLore.add(ChatColor.GRAY + "Khôi phục lại toàn bộ khối bị phá hủy");
        preRaidLore.add(ChatColor.GRAY + "dựa trên bản snapshot trước trận Raid gần nhất.");
        preRaidLore.add(" ");
        preRaidLore.add(ChatColor.GOLD + "Tổng khối block: " + ChatColor.GREEN + preRaidSnap.size() + " blocks");
        preRaidLore.add(" ");
        preRaidLore.add(ChatColor.YELLOW + "➔ Click chuột trái: So sánh & Xem chi tiết.");
        inv.setItem(0, createGuiItem(Material.FILLED_MAP, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Ảnh Chụp Trước Raid", "SELECT_PRE_RAID", preRaidLore.toArray(new String[0])));

        // Slots 1 to 44: Custom Blueprints
        int startIdx = page * 44;
        int endIdx = Math.min(startIdx + 44, totalItems);

        for (int i = startIdx; i < endIdx; i++) {
            int guiSlot = i - startIdx + 1; // slots 1 to 44
            int originalIndex = existingIndices.get(i);
            int blockCount = core.getBlueprintBlockCount(originalIndex);
            String customName = names.get(originalIndex);
            int scanLvl = scanLevels.get(originalIndex);
            boolean isBought = bought.get(originalIndex);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Nhấp chuột trái: Xem chi tiết và so sánh.");
            if (!isBought) {
                lore.add(ChatColor.AQUA + "Chuột giữa: Đổi tên bản vẽ.");
            } else {
                lore.add(ChatColor.RED + "Đổi tên: Không thể đổi tên bản vẽ đã mua.");
            }
            lore.add(ChatColor.RED + "Shift-Click / Click phải: XÓA bản vẽ này.");
            lore.add(" ");
            lore.add(ChatColor.GOLD + "Tổng khối block: " + ChatColor.GREEN + blockCount + " blocks");

            String displayName = ChatColor.GREEN + customName + " - Cấp " + scanLvl;
            inv.setItem(guiSlot, createGuiItem(Material.WRITTEN_BOOK, displayName, "SELECT_BLUEPRINT_" + originalIndex, lore.toArray(new String[0])));
        }

        // Thanh dưới điều hướng & Chức năng
        inv.setItem(45, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Quay lại Lõi", "BACK_TO_CORE"));
        
        if (page > 0) {
            inv.setItem(47, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Trang trước", "PREV_PAGE_LIST"));
        }

        int scanHeightBelow = plugin.getConfig().getInt("builder-settings.scan-height-below", 5);
        int scanHeightAbove = plugin.getConfig().getInt("builder-settings.scan-height-above", 15);
        inv.setItem(48, createGuiItem(Material.FILLED_MAP, ChatColor.GREEN + "" + ChatColor.BOLD + "Quét Lãnh Thổ Mới", "SCAN_TERRITORY",
                ChatColor.GRAY + "Quét toàn bộ lãnh thổ hiện tại.",
                ChatColor.GRAY + "Phạm vi: Độ cao từ -" + scanHeightBelow + " đến +" + scanHeightAbove + " (tính từ Lõi),",
                ChatColor.YELLOW + "➔ Click để bắt đầu quét."
        ));

        inv.setItem(49, createGuiItem(Material.PAPER, ChatColor.GOLD + "Trang " + (page + 1) + " / " + totalPages, "NONE"));

        inv.setItem(50, createGuiItem(Material.EMERALD, ChatColor.AQUA + "" + ChatColor.BOLD + "Cửa Hàng Bản Vẽ", "OPEN_SHOP",
                ChatColor.GRAY + "Mở cửa hàng bản vẽ để mua các thiết kế.",
                ChatColor.YELLOW + "➔ Click để mở."
        ));

        if (page < totalPages - 1) {
            inv.setItem(51, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Trang sau ▶", "NEXT_PAGE_LIST"));
        }

        inv.setItem(53, createGuiItem(Material.BARRIER, ChatColor.RED + "Thoát", "CLOSE_GUI"));

        return inv;
    }

    private Inventory getDetailViewInventory() {
        Inventory inv = Bukkit.createInventory(this, 54, ChatColor.BLUE + "Chi Tiết: " + blueprintName);

        // Lấp đầy nền kính xám
        ItemStack pane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "NONE");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, pane);
        }

        // 1. Tính toán danh sách nguyên liệu thực sự bị thiếu dựa trên hướng xoay hiện tại xung quanh Core
        int rotation = previewRotations.getOrDefault(core.getOwnerUUID(), 0);
        Map<Material, Integer> materialCounts = new HashMap<>();
        int totalMissingBlocks = 0;
        Map<String, Material> matchCache = new HashMap<>();
        for (TerritoryCore.BlockSnapshot snap : design) {
            Material mat = matchCache.computeIfAbsent(snap.material, Material::matchMaterial);
            if (mat != null) {
                int rotX = snap.relX;
                int rotZ = snap.relZ;
                if (rotation == 90) {
                    rotX = -snap.relZ;
                    rotZ = snap.relX;
                } else if (rotation == 180) {
                    rotX = -snap.relX;
                    rotZ = -snap.relZ;
                } else if (rotation == 270) {
                    rotX = snap.relZ;
                    rotZ = -snap.relX;
                }

                // Đối chiếu với trạng thái thực tế tại thế giới một cách an toàn (tránh Sync Chunk Load gây treo máy chủ)
                Location blockLoc = core.getLocation().clone().add(rotX, snap.relY, rotZ);
                org.bukkit.World world = blockLoc.getWorld();
                Material currentType = Material.AIR;
                if (world != null) {
                    int chunkX = blockLoc.getBlockX() >> 4;
                    int chunkZ = blockLoc.getBlockZ() >> 4;
                    if (world.isChunkLoaded(chunkX, chunkZ)) {
                        currentType = blockLoc.getBlock().getType();
                    } else {
                        currentType = Material.AIR; // Coi như chưa khớp mà không nạp chunk đồng bộ
                    }
                }
                if (currentType != mat) {
                    Material itemMat = getRepresentingItemMaterial(mat);
                    materialCounts.put(itemMat, materialCounts.getOrDefault(itemMat, 0) + 1);
                    totalMissingBlocks++;
                }
            }
        }

        // 2. Sách thông tin bản vẽ (Đặt ở Slot 4)
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Tên bản vẽ: " + ChatColor.GREEN + blueprintName);
        infoLore.add(ChatColor.GRAY + "Hướng xoay hiện tại: " + ChatColor.GOLD + rotation + "°");
        infoLore.add(ChatColor.GRAY + "Tổng số lượng khối bản vẽ: " + ChatColor.GOLD + design.size() + " blocks");
        infoLore.add(ChatColor.GRAY + "Số lượng khối thực sự thiếu: " + ChatColor.RED + totalMissingBlocks + " blocks");
        infoLore.add(" ");
        if (totalMissingBlocks == 0) {
            infoLore.add(ChatColor.GREEN + "✔ Hiện tại lãnh thổ đã khớp hoàn toàn với bản vẽ ở hướng này!");
        } else {
            infoLore.add(ChatColor.YELLOW + "➔ Hãy kiểm tra kỹ nguyên liệu bị thiếu bên dưới.");
        }
        inv.setItem(4, createGuiItem(Material.BOOK, ChatColor.AQUA + "" + ChatColor.BOLD + "Thông Tin Bản Thiết Kế", "NONE", infoLore.toArray(new String[0])));

        // Lấy thông tin số lượng block trong Rương Tái Thiết của Lõi để đối chiếu
        com.truongcm.territorydefense.feature.core.RebuildWarehouseStorage rebuildInv = core.getRebuildWarehouse();
        Map<Material, Integer> availableCounts = new HashMap<>();
        for (ItemStack item : rebuildInv.getContents()) {
            if (item != null && item.getAmount() > 0) {
                availableCounts.put(item.getType(), availableCounts.getOrDefault(item.getType(), 0) + item.getAmount());
            }
        }

        // Chỉ hiển thị các nguyên liệu có số lượng cần > số lượng sẵn có trong rương tái thiết (kho còn thiếu)
        List<Material> uniqueMaterials = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : materialCounts.entrySet()) {
            Material mat = entry.getKey();
            int needed = entry.getValue();
            int available = availableCounts.getOrDefault(mat, 0);
            if (needed > available) {
                uniqueMaterials.add(mat);
            }
        }
        uniqueMaterials.sort((a, b) -> a.name().compareTo(b.name()));

        // Phân trang: mỗi trang chứa tối đa 27 loại block (slots 18 đến 44)
        int itemsPerPage = 27;
        int totalPages = (int) Math.ceil((double) uniqueMaterials.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, uniqueMaterials.size());

        int guiSlot = 18;
        for (int i = startIndex; i < endIndex; i++) {
            Material mat = uniqueMaterials.get(i);
            int needed = materialCounts.get(mat);
            int available = availableCounts.getOrDefault(mat, 0);

            List<String> matLore = new ArrayList<>();
            matLore.add(" ");
            matLore.add(ChatColor.GRAY + "Số lượng cần thiết: " + ChatColor.GOLD + needed + " block");
            matLore.add(ChatColor.GRAY + "Trong kho tái thiết: " + ChatColor.RED + available + " / " + needed + " (Thiếu " + (needed - available) + ")");

            String cleanName = mat.name().toLowerCase().replace("_", " ");
            ItemStack matItem = createGuiItem(mat, ChatColor.YELLOW + cleanName, "NONE", matLore.toArray(new String[0]));
            inv.setItem(guiSlot, matItem);
            guiSlot++;
        }

        // 3. Các nút điều hướng phân trang (Slot 45 và 53)
        if (page > 0) {
            inv.setItem(45, createGuiItem(Material.ARROW, ChatColor.YELLOW + "◀ Trang trước (" + page + "/" + totalPages + ")", "PREV_PAGE_DETAIL"));
        }
        inv.setItem(49, createGuiItem(Material.PAPER, ChatColor.AQUA + "Trang " + (page + 1) + " / " + totalPages, "NONE"));
        if (page < totalPages - 1) {
            inv.setItem(53, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Trang sau ▶ (" + (page + 2) + "/" + totalPages + ")", "NEXT_PAGE_DETAIL"));
        }

        // 4. Các nút chức năng ở hàng dưới cùng
        // Đổi tên (Slot 46)
        if (slotIndex >= 0 && !core.getBlueprintSlotsBought().get(slotIndex)) {
            inv.setItem(46, createGuiItem(Material.NAME_TAG, ChatColor.AQUA + "" + ChatColor.BOLD + "Đổi Tên Bản Vẽ", "RENAME_BLUEPRINT",
                    ChatColor.GRAY + "Thay đổi tên của bản vẽ thiết kế này.",
                    ChatColor.YELLOW + "➔ Click để bắt đầu đổi tên."
            ));
        }

        // Xác nhận kiến thiết (Slot 47)
        inv.setItem(47, createGuiItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "✔ XÁC NHẬN KIẾN THIẾT", "CONFIRM"));

        // Bản xem trước lấy Core làm trung tâm xoay 90 độ (Slot 48)
        inv.setItem(48, createGuiItem(Material.SPYGLASS, ChatColor.YELLOW + "" + ChatColor.BOLD + "Bản Xem Trước (" + rotation + "°)", "PREVIEW_BLUEPRINT",
                ChatColor.GRAY + "Tạo một bản xem trước ảo lơ lửng lấy Core",
                ChatColor.GRAY + "làm trung tâm để hỗ trợ xây dựng chính xác.",
                ChatColor.GRAY + "Mỗi click sẽ xoay bản thiết kế 90° quanh Core.",
                ChatColor.YELLOW + "➔ Click để xoay hướng bản vẽ."
        ));

        // Quay lại danh sách (Slot 51)
        inv.setItem(51, createGuiItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "◀ TRỞ VỀ DANH SÁCH", "BACK_TO_LIST"));

        return inv;
    }

    @Override
    public void onClick(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.hasItemMeta()) {
            String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            // Xử lý các hành động chung / giao diện danh sách
            if (action.equalsIgnoreCase("BACK_TO_CORE")) {
                player.closeInventory();
                player.openInventory(new com.truongcm.territorydefense.feature.core.ui.CoreGui(plugin, core, com.truongcm.territorydefense.feature.core.ui.CoreGui.CoreTab.LOGISTICS).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("CLOSE_GUI")) {
                player.closeInventory();
                return;
            }

            if (action.equalsIgnoreCase("PREV_PAGE_LIST")) {
                player.openInventory(new RebuildConfirmGui(plugin, core, null, "Danh Sách Bản Vẽ", -2, page - 1, false).getInventory());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("NEXT_PAGE_LIST")) {
                player.openInventory(new RebuildConfirmGui(plugin, core, null, "Danh Sách Bản Vẽ", -2, page + 1, false).getInventory());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("SCAN_TERRITORY")) {
                int firstEmptySlot = -1;
                for (int i = 0; i < 54; i++) {
                    if (core.isBlueprintSlotEmpty(i)) {
                        firstEmptySlot = i;
                        break;
                    }
                }
                if (firstEmptySlot == -1) {
                    player.sendMessage(ChatColor.RED + "Danh sách bản vẽ đã đầy (tối đa 54 bản vẽ)!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getOrCreateBuilder(core.getCoreId());
                player.closeInventory();
                builder.startScanAndSave(core, firstEmptySlot, player);
                return;
            }

            if (action.equalsIgnoreCase("OPEN_SHOP")) {
                player.closeInventory();
                player.openInventory(new BlueprintShopGui(plugin, core).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("SELECT_PRE_RAID")) {
                com.truongcm.territorydefense.feature.logistics.NPCBuilder b = plugin.getBuilderManager().getOrCreateBuilder(core.getCoreId());
                List<TerritoryCore.BlockSnapshot> preRaidSnap = (b != null && b.getLastPreRaidSnapshot() != null) ? b.getLastPreRaidSnapshot() : new ArrayList<>();
                player.closeInventory();
                player.openInventory(new RebuildConfirmGui(plugin, core, preRaidSnap, "Ảnh Chụp Trước Raid", -1, 0, false).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                return;
            }

            if (action.startsWith("SELECT_BLUEPRINT_")) {
                int index = Integer.parseInt(action.substring(17));

                // Chuột giữa để đổi tên bản vẽ
                if (event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
                    if (core.getBlueprintSlotsBought().get(index)) {
                        player.sendMessage(ChatColor.RED + "[Kiến Thiết] Bạn chỉ có thể đổi tên bản vẽ do chính mình quét!");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }
                    player.closeInventory();
                    BlueprintInputListener.registerRename(player.getUniqueId(), index);
                    player.sendMessage(ChatColor.GREEN + "[Kiến Thiết] Hãy nhập tên mới cho bản vẽ vào khung chat (hoặc gõ 'cancel'/'huy' để hủy bỏ).");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    return;
                }

                // Click phải hoặc Shift-Click để xóa bản vẽ
                if (event.getClick().isShiftClick() || event.getClick().isRightClick()) {
                    player.closeInventory();
                    player.openInventory(new BlueprintDeleteConfirmGui(plugin, core, index).getInventory());
                    player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
                    return;
                }

                // Xem chi tiết bản vẽ bất đồng bộ để tránh treo luồng chính (Main Thread)
                String customName = core.getBlueprintNames().get(index);
                if (core.isBlueprintSlotLoaded(index)) {
                    // Nếu bản vẽ đã được tải sẵn trên bộ nhớ RAM, mở GUI tức thì
                    List<TerritoryCore.BlockSnapshot> d = core.getBlueprintSlot(index);
                    player.closeInventory();
                    player.openInventory(new RebuildConfirmGui(plugin, core, d, customName, index, 0, false).getInventory());
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                } else {
                    // Nếu bản vẽ chưa tải, giải nén không đồng bộ dưới nền (Async Thread)
                    player.closeInventory();
                    player.sendMessage(ChatColor.YELLOW + "[Kiến Thiết] Đang giải nén dữ liệu '" + customName + "'... Vui lòng đợi trong giây lát.");
                    player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.0f);

                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        // Việc đọc file và nén GZIP được xử lý ngầm ở Async Thread
                        List<TerritoryCore.BlockSnapshot> d = core.getBlueprintSlot(index);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                player.openInventory(new RebuildConfirmGui(plugin, core, d, customName, index, 0, false).getInventory());
                                player.sendMessage(ChatColor.GREEN + "[Kiến Thiết] Đã giải mã thành công bản vẽ '" + customName + "'!");
                                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                            }
                        });
                    });
                }
                return;
            }

            // Xử lý các hành động trong giao diện Chi Tiết Bản Vẽ
            if (action.equalsIgnoreCase("BACK_TO_LIST")) {
                clearPreview(player);
                player.closeInventory();
                if (slotIndex == -3) {
                    player.openInventory(new com.truongcm.territorydefense.feature.web.AdminCoreManagerGui(plugin, com.truongcm.territorydefense.feature.web.AdminCoreManagerGui.AdminTab.SERVER_BLUEPRINTS, core, 0).getInventory());
                } else {
                    player.openInventory(new RebuildConfirmGui(plugin, core, null, "Danh Sách Bản Vẽ", -2, 0, false).getInventory());
                }
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("PREV_PAGE_DETAIL")) {
                player.openInventory(new RebuildConfirmGui(plugin, core, design, blueprintName, slotIndex, page - 1, fromBlueprintList).getInventory());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("NEXT_PAGE_DETAIL")) {
                player.openInventory(new RebuildConfirmGui(plugin, core, design, blueprintName, slotIndex, page + 1, fromBlueprintList).getInventory());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("RENAME_BLUEPRINT")) {
                if (slotIndex < 0 || core.getBlueprintSlotsBought().get(slotIndex)) {
                    player.sendMessage(ChatColor.RED + "[Kiến Thiết] Bạn chỉ có thể đổi tên bản vẽ do chính mình quét!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                player.closeInventory();
                BlueprintInputListener.registerRename(player.getUniqueId(), slotIndex);
                player.sendMessage(ChatColor.GREEN + "[Kiến Thiết] Hãy nhập tên mới cho bản vẽ vào khung chat (hoặc gõ 'cancel'/'huy' để hủy bỏ).");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                return;
            }

            if (action.equalsIgnoreCase("CONFIRM")) {
                com.truongcm.territorydefense.feature.logistics.NPCBuilder builder = plugin.getBuilderManager().getOrCreateBuilder(core.getCoreId());
                if (builder == null) {
                    player.sendMessage(ChatColor.RED + "Bạn cần thuê 7Gao trước!");
                    return;
                }
                clearPreview(player);
                player.closeInventory();

                // Áp dụng góc xoay đã chọn khi bắt đầu xây dựng
                int rotation = previewRotations.getOrDefault(core.getOwnerUUID(), 0);
                List<TerritoryCore.BlockSnapshot> finalDesign = design;
                if (rotation != 0) {
                    finalDesign = new ArrayList<>();
                    for (TerritoryCore.BlockSnapshot snap : design) {
                        int rotX = snap.relX;
                        int rotZ = snap.relZ;
                        if (rotation == 90) {
                            rotX = -snap.relZ;
                            rotZ = snap.relX;
                        } else if (rotation == 180) {
                            rotX = -snap.relX;
                            rotZ = -snap.relZ;
                        } else if (rotation == 270) {
                            rotX = snap.relZ;
                            rotZ = -snap.relX;
                        }
                        finalDesign.add(new TerritoryCore.BlockSnapshot(rotX, snap.relY, rotZ, snap.material, snap.blockData));
                    }
                }

                builder.startRebuild(core, finalDesign, player);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                return;
            }

            if (action.equalsIgnoreCase("PREVIEW_BLUEPRINT")) {
                if (design == null || design.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Bản vẽ này đang trống!");
                    return;
                }
                int currentRot = previewRotations.getOrDefault(core.getOwnerUUID(), 0);
                int nextRot = (currentRot + 90) % 360;
                previewRotations.put(core.getOwnerUUID(), nextRot);

                // Dọn dẹp bản xem trước cũ
                clearPreview(player);

                // Lấy Core làm trung tâm
                Location anchor = core.getLocation();

                List<Location> newLocs = new ArrayList<>();
                Map<String, Material> matchCache = new HashMap<>();
                for (TerritoryCore.BlockSnapshot snap : design) {
                    Material mat = matchCache.computeIfAbsent(snap.material, Material::matchMaterial);
                    if (mat != null) {
                        int rotX = snap.relX;
                        int rotZ = snap.relZ;
                        if (nextRot == 90) {
                            rotX = -snap.relZ;
                            rotZ = snap.relX;
                        } else if (nextRot == 180) {
                            rotX = -snap.relX;
                            rotZ = -snap.relZ;
                        } else if (nextRot == 270) {
                            rotX = snap.relZ;
                            rotZ = -snap.relX;
                        }

                        Location loc = anchor.clone().add(rotX, snap.relY, rotZ);
                player.sendBlockChange(loc, Bukkit.createBlockData(mat));
                        newLocs.add(loc);
                    }
                }
                activePreviews.put(player.getUniqueId(), newLocs);
                previewCoreLocations.put(player.getUniqueId(), core.getLocation());

                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + " ");
                player.sendMessage(ChatColor.GREEN + "=================================================");
                player.sendMessage(ChatColor.GREEN + "  ⚒ ĐANG XEM TRƯỚC BẢN VẼ: " + ChatColor.GOLD + blueprintName);
                player.sendMessage(ChatColor.GREEN + "  ➔ Hướng xoay hiện tại: " + ChatColor.YELLOW + nextRot + "°");
                player.sendMessage(ChatColor.GRAY + "  Giao diện đã tạm đóng để bạn có thể tự do di chuyển");
                player.sendMessage(ChatColor.GRAY + "  và quan sát công trình ảo lơ lửng từ xa.");
                player.sendMessage(ChatColor.YELLOW + "  Mẹo: Bạn có thể đi xa tối đa 128 block để nhìn.");
                player.sendMessage(ChatColor.GREEN + "  Quay lại Lõi -> Tái thiết để Xác Nhận góc xoay & bắt đầu xây.");
                player.sendMessage(ChatColor.GREEN + "=================================================");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
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

        if (name.endsWith("_STEM")) {
            if (name.contains("PUMPKIN")) return Material.PUMPKIN_SEEDS;
            if (name.contains("MELON")) return Material.MELON_SEEDS;
        }

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

        if (name.equals("WALL_TORCH")) return Material.TORCH;
        if (name.equals("REDSTONE_WALL_TORCH")) return Material.REDSTONE_TORCH;
        if (name.equals("SOUL_WALL_TORCH")) return Material.SOUL_TORCH;

        if (name.equals("SWEET_BERRY_BUSH")) return Material.SWEET_BERRIES;
        if (name.equals("CARROTS")) return Material.CARROT;
        if (name.equals("POTATOES")) return Material.POTATO;
        if (name.equals("BEETROOTS")) return Material.BEETROOT_SEEDS;
        if (name.equals("COCOA")) return Material.COCOA_BEANS;
        if (name.equals("BAMBOO_SAPLING")) return Material.BAMBOO;
        if (name.equals("PITCHER_CROP")) return Material.PITCHER_POD;
        if (name.equals("TORCHFLOWER_CROP")) return Material.TORCHFLOWER_SEEDS;

        if (name.equals("TRIPWIRE")) return Material.STRING;

        if (name.equals("WATER") || name.equals("FLOWING_WATER")) return Material.WATER_BUCKET;
        if (name.equals("LAVA") || name.equals("FLOWING_LAVA")) return Material.LAVA_BUCKET;
        if (name.equals("FIRE")) return Material.FLINT_AND_STEEL;
        if (name.equals("SOUL_FIRE")) return Material.FLINT_AND_STEEL;

        if (name.startsWith("POTTED_")) {
            String plantName = name.substring(7);
            Material plantMat = Material.matchMaterial(plantName);
            if (plantMat != null && plantMat.isItem()) return plantMat;
            return Material.FLOWER_POT;
        }

        if (name.equals("FROSTED_ICE")) return Material.ICE;

        return Material.STONE;
    }
}
