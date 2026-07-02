package com.truongcm.territorydefense.feature.core;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.alliance.Alliance;
import com.truongcm.territorydefense.feature.alliance.AllianceManager;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QUẢN LÝ HOLOGRAM (HOLOGRAM MANAGER) - PHIÊN BẢN CHUYÊN BIỆT V35
 * Sử dụng thực thể TextDisplay (Paper 1.20.6+) tối ưu hiệu năng vượt trội.
 * Quản lý vòng đời hiển thị thông tin Lõi (Cores) và Tháp phòng thủ (Towers).
 */
public class HologramManager implements Listener {

    private static final Map<UUID, UUID> coreHologramMap = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> towerHologramMap = new ConcurrentHashMap<>();

    /**
     * Rebuild toàn bộ Hologram lúc khởi động hệ thống.
     */
    public static void initialize() {
        cleanupAllHologramEntities();
        
        // Rebuild Cores
        TerritoryDefense plugin = TerritoryDefense.getInstance();
        if (plugin.getCoreManager() != null) {
            for (TerritoryCore core : plugin.getCoreManager().getAllActiveCores()) {
                updateCoreHologram(core);
            }
        }

        // Rebuild Towers
        if (plugin.getTowerManager() != null) {
            for (Tower tower : plugin.getTowerManager().getActiveTowers().values()) {
                updateTowerHologram(tower);
            }
        }
    }

    /**
     * Dọn dẹp triệt để tất cả thực thể Hologram mồ côi trong các thế giới.
     */
    public static void cleanupAllHologramEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay) {
                    if (entity.getPersistentDataContainer().has(PDCKeys.IS_HOLOGRAM, PersistentDataType.BYTE)) {
                        entity.remove();
                    }
                }
            }
        }
        coreHologramMap.clear();
        towerHologramMap.clear();
    }

    /**
     * Cập nhật hoặc tạo mới Hologram cho Lõi lãnh thổ.
     */
    public static void updateCoreHologram(TerritoryCore core) {
        if (core == null || core.getLocation() == null) return;
        if (!Bukkit.isPrimaryThread()) {
            TerritoryDefense plugin = TerritoryDefense.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> updateCoreHologram(core));
            }
            return;
        }

        UUID coreId = core.getCoreId();
        Location spawnLoc = core.getLocation().clone().add(0.5, 1.5, 0.5);
        World world = spawnLoc.getWorld();
        if (world == null) return;

        // Tránh thao tác khi chunk chưa nạp để ngăn chặn lag/tạo trùng thực thể
        int chunkX = spawnLoc.getBlockX() >> 4;
        int chunkZ = spawnLoc.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        // Tạo chuỗi text thông tin
        String ownerName = getOwnerName(core.getOwnerUUID());
        String allianceName = getAllianceName(core.getOwnerUUID());
        
        StringBuilder sb = new StringBuilder();
        if (allianceName != null && !allianceName.isEmpty()) {
            sb.append(ChatColor.GOLD).append("[").append(allianceName).append("] ").append(ChatColor.WHITE).append(ownerName).append("\n");
        } else {
            sb.append(ChatColor.WHITE).append(ownerName).append("\n");
        }
        sb.append(ChatColor.YELLOW).append("Cấp Lõi: ").append(ChatColor.WHITE).append(core.getLevel()).append("\n");
        sb.append(ChatColor.AQUA).append("Năng Lượng: ").append(ChatColor.WHITE).append(String.format("%.1f", core.getFep())).append("/").append(core.getMaxFepCapacity()).append(" FEP\n");
        
        // Hiển thị đồng bộ Lá Chắn (Shield) và Máu Lõi (Temp Health khi đang có Raid)
        sb.append(ChatColor.GREEN).append("Lá Chắn: ").append(ChatColor.WHITE).append(String.format("%.0f", core.getShield())).append("/").append(String.format("%.0f", core.getMaxShieldCapacity())).append(" HP");
        if (core.isRaidActive()) {
            sb.append("\n").append(ChatColor.RED).append("Máu Lõi: ").append(ChatColor.WHITE).append(String.format("%.0f", core.getTempHealth())).append("/").append(String.format("%.0f", core.getMaxShieldCapacity())).append(" HP");
        }

        String text = sb.toString();

        // Cập nhật TextDisplay hiện tại hoặc spawn mới
        UUID displayUUID = coreHologramMap.get(coreId);
        TextDisplay textDisplay = null;
        if (displayUUID != null) {
            Entity ent = Bukkit.getEntity(displayUUID);
            if (ent instanceof TextDisplay) {
                textDisplay = (TextDisplay) ent;
            }
        }

        if (textDisplay == null || !textDisplay.isValid()) {
            textDisplay = world.spawn(spawnLoc, TextDisplay.class);
            textDisplay.setBillboard(Billboard.CENTER);
            textDisplay.setGravity(false);
            textDisplay.getPersistentDataContainer().set(PDCKeys.IS_HOLOGRAM, PersistentDataType.BYTE, (byte) 1);
            textDisplay.getPersistentDataContainer().set(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING, coreId.toString());
            coreHologramMap.put(coreId, textDisplay.getUniqueId());
        }

        // Tối ưu hóa: Chỉ thực hiện đổi Text khi có sự thay đổi thật sự, tránh spam packets
        if (!text.equals(textDisplay.getText())) {
            textDisplay.setText(text);
        }
        textDisplay.setShadowed(true);
        textDisplay.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0)); // Transparent background
    }

    /**
     * Cập nhật hoặc tạo mới Hologram cho Tháp canh phòng thủ.
     */
    public static void updateTowerHologram(Tower tower) {
        if (tower == null || tower.getLocation() == null) return;
        if (!Bukkit.isPrimaryThread()) {
            TerritoryDefense plugin = TerritoryDefense.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> updateTowerHologram(tower));
            }
            return;
        }

        if (tower == null || tower.getLocation() == null) return;
        UUID towerId = tower.getTowerId();
        Location spawnLoc = tower.getLocation().clone().add(0.5, 2.0, 0.5);
        World world = spawnLoc.getWorld();
        if (world == null) return;

        // Tránh thao tác khi chunk chưa nạp
        int chunkX = spawnLoc.getBlockX() >> 4;
        int chunkZ = spawnLoc.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        UUID ownerUUID = null;
        if (com.truongcm.territorydefense.TerritoryDefense.getInstance().getCoreManager() != null) {
            for (TerritoryCore c : com.truongcm.territorydefense.TerritoryDefense.getInstance().getCoreManager().getAllActiveCores()) {
                if (c.getCoreId().equals(tower.getOwnerCoreId())) {
                    ownerUUID = c.getOwnerUUID();
                    break;
                }
            }
        }

        // Tạo chuỗi text thông tin
        String ownerName = ownerUUID != null ? getOwnerName(ownerUUID) : "Unknown";
        String allianceName = ownerUUID != null ? getAllianceName(ownerUUID) : null;

        StringBuilder sb = new StringBuilder();
        if (allianceName != null && !allianceName.isEmpty()) {
            sb.append(ChatColor.GOLD).append("[").append(allianceName).append("] ").append(ChatColor.WHITE).append(ownerName).append("\n");
        } else {
            sb.append(ChatColor.WHITE).append(ownerName).append("\n");
        }
        sb.append(ChatColor.GREEN).append(tower.getDisplayName()).append(ChatColor.GRAY).append(" [Cấp ").append(tower.getLevel()).append("]");

        String text = sb.toString();

        // Cập nhật TextDisplay hiện tại hoặc spawn mới
        UUID displayUUID = towerHologramMap.get(towerId);
        TextDisplay textDisplay = null;
        if (displayUUID != null) {
            Entity ent = Bukkit.getEntity(displayUUID);
            if (ent instanceof TextDisplay) {
                textDisplay = (TextDisplay) ent;
            }
        }

        if (textDisplay == null || !textDisplay.isValid()) {
            textDisplay = world.spawn(spawnLoc, TextDisplay.class);
            textDisplay.setBillboard(Billboard.CENTER);
            textDisplay.setGravity(false);
            textDisplay.getPersistentDataContainer().set(PDCKeys.IS_HOLOGRAM, PersistentDataType.BYTE, (byte) 1);
            textDisplay.getPersistentDataContainer().set(PDCKeys.TOWER_ID, PersistentDataType.STRING, towerId.toString());
            towerHologramMap.put(towerId, textDisplay.getUniqueId());
        }

        // Tối ưu hóa: Tránh spam packets
        if (!text.equals(textDisplay.getText())) {
            textDisplay.setText(text);
        }
        textDisplay.setShadowed(true);
        textDisplay.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0)); // Transparent background
    }

    /**
     * Dọn dẹp Hologram mồ côi và tái nạp Hologram khi Chunk được load lại để chống nhân bản.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        TerritoryDefense plugin = TerritoryDefense.getInstance();
        if (plugin == null) return;

        for (Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof TextDisplay)) continue;
            TextDisplay display = (TextDisplay) entity;

            if (!display.getPersistentDataContainer().has(PDCKeys.IS_HOLOGRAM, PersistentDataType.BYTE)) continue;

            // Kiểm tra xem là Hologram của Lõi hay Tháp
            String ownerCoreStr = display.getPersistentDataContainer().get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING);
            String towerStr = display.getPersistentDataContainer().get(PDCKeys.TOWER_ID, PersistentDataType.STRING);

            if (ownerCoreStr != null) {
                try {
                    UUID coreId = UUID.fromString(ownerCoreStr);
                    TerritoryCore core = null;
                    if (plugin.getCoreManager() != null) {
                        for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
                            if (c.getCoreId().equals(coreId)) {
                                core = c;
                                break;
                            }
                        }
                    }

                    if (core == null) {
                        // Lõi không tồn tại nữa -> Xóa thực thể Hologram mồ côi khỏi thế giới
                        display.remove();
                    } else {
                        // Lõi còn tồn tại -> Kiểm tra xem đã có hologram hoạt động được lưu chưa
                        UUID activeUUID = coreHologramMap.get(coreId);
                        if (activeUUID != null && !activeUUID.equals(display.getUniqueId())) {
                            Entity existing = Bukkit.getEntity(activeUUID);
                            if (existing != null && existing.isValid()) {
                                // Đã có hologram hợp lệ rồi -> Xóa thực thể trùng này đi
                                display.remove();
                            } else {
                                // Đăng ký lại bằng thực thể này và cập nhật hiển thị
                                coreHologramMap.put(coreId, display.getUniqueId());
                                updateCoreHologram(core);
                            }
                        } else {
                            coreHologramMap.put(coreId, display.getUniqueId());
                            updateCoreHologram(core);
                        }
                    }
                } catch (IllegalArgumentException ignored) {}
            } else if (towerStr != null) {
                try {
                    UUID towerId = UUID.fromString(towerStr);
                    Tower tower = null;
                    if (plugin.getTowerManager() != null) {
                        tower = plugin.getTowerManager().getActiveTowers().get(towerId);
                    }

                    if (tower == null) {
                        // Tháp không tồn tại nữa -> Xóa thực thể Hologram mồ côi
                        display.remove();
                    } else {
                        // Tháp còn tồn tại -> Kiểm tra trùng lặp
                        UUID activeUUID = towerHologramMap.get(towerId);
                        if (activeUUID != null && !activeUUID.equals(display.getUniqueId())) {
                            Entity existing = Bukkit.getEntity(activeUUID);
                            if (existing != null && existing.isValid()) {
                                // Đã có hologram hợp lệ -> Xóa trùng
                                display.remove();
                            } else {
                                // Đăng ký lại và cập nhật
                                towerHologramMap.put(towerId, display.getUniqueId());
                                updateTowerHologram(tower);
                            }
                        } else {
                            towerHologramMap.put(towerId, display.getUniqueId());
                            updateTowerHologram(tower);
                        }
                    }
                } catch (IllegalArgumentException ignored) {}
            } else {
                // Không chứa ID liên kết hợp lệ -> Xóa khỏi thế giới
                display.remove();
            }
        }

        // Tự động kiểm tra và hồi sinh hologram cho Core/Tower trong chunk vừa tải
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        World world = event.getWorld();

        if (plugin.getCoreManager() != null) {
            for (TerritoryCore core : plugin.getCoreManager().getAllActiveCores()) {
                Location loc = core.getLocation();
                if (loc != null && loc.getWorld() != null && loc.getWorld().equals(world)) {
                    int cX = loc.getBlockX() >> 4;
                    int cZ = loc.getBlockZ() >> 4;
                    if (cX == chunkX && cZ == chunkZ) {
                        UUID activeUUID = coreHologramMap.get(core.getCoreId());
                        if (activeUUID == null) {
                            updateCoreHologram(core);
                        } else {
                            Entity existing = Bukkit.getEntity(activeUUID);
                            if (existing == null || !existing.isValid()) {
                                updateCoreHologram(core);
                            }
                        }
                    }
                }
            }
        }

        if (plugin.getTowerManager() != null) {
            for (Tower tower : plugin.getTowerManager().getActiveTowers().values()) {
                Location loc = tower.getLocation();
                if (loc != null && loc.getWorld() != null && loc.getWorld().equals(world)) {
                    int tX = loc.getBlockX() >> 4;
                    int tZ = loc.getBlockZ() >> 4;
                    if (tX == chunkX && tZ == chunkZ) {
                        UUID activeUUID = towerHologramMap.get(tower.getTowerId());
                        if (activeUUID == null) {
                            updateTowerHologram(tower);
                        } else {
                            Entity existing = Bukkit.getEntity(activeUUID);
                            if (existing == null || !existing.isValid()) {
                                updateTowerHologram(tower);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Xóa Hologram của Lõi khi bị dỡ bỏ.
     */
    public static void removeCoreHologram(UUID coreId) {
        removeCoreHologram(coreId, null);
    }

    /**
     * Xóa Hologram của Lõi khi bị dỡ bỏ, kết hợp kiểm tra quét sạch tại Location để tránh lỗi thực thể mồ côi (ghost hologram).
     */
    public static void removeCoreHologram(UUID coreId, Location loc) {
        if (coreId == null) return;
        UUID displayUUID = coreHologramMap.remove(coreId);
        
        // 1. Xóa bằng UUID trực tiếp nếu thực thể đang được tải (nhanh và chính xác nhất)
        if (displayUUID != null) {
            Entity ent = Bukkit.getEntity(displayUUID);
            if (ent != null) {
                ent.remove();
            }
        }
        
        // 2. Dự phòng an toàn: Quét nhanh chunk tại vị trí Lõi để loại bỏ hoàn toàn các TextDisplay rác liên quan
        if (loc != null && loc.getWorld() != null) {
            World world = loc.getWorld();
            int cx = loc.getBlockX() >> 4;
            int cz = loc.getBlockZ() >> 4;
            if (world.isChunkLoaded(cx, cz)) {
                for (Entity entity : world.getChunkAt(cx, cz).getEntities()) {
                    if (entity instanceof TextDisplay) {
                        TextDisplay td = (TextDisplay) entity;
                        if (td.getPersistentDataContainer().has(PDCKeys.IS_HOLOGRAM, PersistentDataType.BYTE)) {
                            String ownerCoreStr = td.getPersistentDataContainer().get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING);
                            if (ownerCoreStr != null && ownerCoreStr.equals(coreId.toString())) {
                                td.remove();
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Xóa Hologram của Tháp khi bị dỡ bỏ.
     */
    public static void removeTowerHologram(UUID towerId) {
        removeTowerHologram(towerId, null);
    }

    /**
     * Xóa Hologram của Tháp khi bị dỡ bỏ, kết hợp kiểm tra quét sạch tại Location để tránh lỗi thực thể mồ côi.
     */
    public static void removeTowerHologram(UUID towerId, Location loc) {
        if (towerId == null) return;
        UUID displayUUID = towerHologramMap.remove(towerId);
        
        // 1. Xóa bằng UUID trực tiếp
        if (displayUUID != null) {
            Entity ent = Bukkit.getEntity(displayUUID);
            if (ent != null) {
                ent.remove();
            }
        }
        
        // 2. Dự phòng an toàn: Quét nhanh chunk tại vị trí Tháp để xóa triệt để
        if (loc != null && loc.getWorld() != null) {
            World world = loc.getWorld();
            int cx = loc.getBlockX() >> 4;
            int cz = loc.getBlockZ() >> 4;
            if (world.isChunkLoaded(cx, cz)) {
                for (Entity entity : world.getChunkAt(cx, cz).getEntities()) {
                    if (entity instanceof TextDisplay) {
                        TextDisplay td = (TextDisplay) entity;
                        if (td.getPersistentDataContainer().has(PDCKeys.IS_HOLOGRAM, PersistentDataType.BYTE)) {
                            String towerStr = td.getPersistentDataContainer().get(PDCKeys.TOWER_ID, PersistentDataType.STRING);
                            if (towerStr != null && towerStr.equals(towerId.toString())) {
                                td.remove();
                            }
                        }
                    }
                }
            }
        }
    }

    private static String getOwnerName(UUID ownerUUID) {
        if (ownerUUID == null) return "Unknown";
        OfflinePlayer player = Bukkit.getOfflinePlayer(ownerUUID);
        return player.getName() != null ? player.getName() : "Người chơi";
    }

    private static String getAllianceName(UUID ownerUUID) {
        if (ownerUUID == null) return null;
        TerritoryDefense plugin = TerritoryDefense.getInstance();
        AllianceManager allianceManager = plugin.getAllianceManager();
        if (allianceManager == null) return null;
        
        String allyId = allianceManager.getPlayerAlliance(ownerUUID);
        if (allyId == null) return null;
        
        Alliance alliance = allianceManager.getAlliance(allyId);
        return alliance != null ? alliance.getName() : null;
    }
}
