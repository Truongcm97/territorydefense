package com.truongcm.territorydefense.feature.logistics;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NPCBuilder {
    private final UUID builderUUID;
    private final UUID ownerCoreUUID;
    private Villager entity = null;
    private boolean isRebuilding = false;
    private boolean isPausedForMaterials = false;
    private BukkitTask activeTask = null;
    private final List<TerritoryCore.BlockSnapshot> lastPreRaidSnapshot = new java.util.ArrayList<>();
    private final Map<String, Material> matchCache = new HashMap<>();
    private final java.util.Map<Integer, Integer> buildAttempts = new java.util.HashMap<>();

    private List<TerritoryCore.BlockSnapshot> activeDesign = null;
    private TerritoryCore activeCore = null;
    private WeakReference<Player> activeRequesterRef = new WeakReference<>(null);
    private long lastNotifyTime = 0;
    private boolean isHidden = true;
    private int currentIndex = 0;
    private boolean placedAnyInCurrentPass = false;

    public List<TerritoryCore.BlockSnapshot> getLastPreRaidSnapshot() {
        return lastPreRaidSnapshot;
    }

    public void hideInCore(TerritoryCore core) {
        if (entity != null) {
            entity.remove();
            entity = null;
        }
        isHidden = true;
    }

    public void showFromCore(TerritoryCore core) {
        if (entity == null || !entity.isValid()) {
            Location loc = core.getLocation().clone().add(0.5, 1.0, 0.5);
            entity = loc.getWorld().spawn(loc, Villager.class);
            applyAttributes();
        }
        isHidden = false;
        if (entity != null) {
            entity.setInvisible(false);
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setCollidable(false);
            entity.setGravity(false);
        }
    }

    public NPCBuilder(UUID builderUUID, UUID ownerCoreUUID) {
        this.builderUUID = builderUUID;
        this.ownerCoreUUID = ownerCoreUUID;
        this.entity = null;
        this.isHidden = true;
    }

    public UUID getBuilderUUID() {
        return builderUUID;
    }

    public UUID getOwnerCoreUUID() {
        return ownerCoreUUID;
    }

    public Villager getEntity() {
        return entity;
    }

    public boolean isRebuilding() {
        return isRebuilding;
    }

    public boolean isPausedForMaterials() {
        return isPausedForMaterials;
    }

    public void applyAttributes() {
        if (entity == null || !entity.isValid()) return;
        TerritoryDefense plugin = TerritoryDefense.getInstance();
        TerritoryCore core = plugin.getCoreManager().getAllActiveCores().stream()
                .filter(c -> c.getCoreId().equals(ownerCoreUUID))
                .findFirst().orElse(null);
        int lvl = (core != null) ? core.getBuilderLevel() : 1;
        entity.setCustomName(ChatColor.GOLD + "7Gao (Cấp " + lvl + ")");
        entity.setCustomNameVisible(true);
        entity.setProfession(Villager.Profession.MASON);
        entity.setBaby();
        entity.setAgeLock(true);
        entity.setMetadata("td_custom_entity", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        entity.setMetadata("td_builder", new org.bukkit.metadata.FixedMetadataValue(plugin, builderUUID.toString()));
        entity.getPersistentDataContainer().set(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING, ownerCoreUUID.toString());
        entity.setAI(false);
        entity.setGravity(false);
        entity.setCollidable(false);
        entity.setInvulnerable(true);
    }

    public void startScanAndSave(TerritoryCore core, int slotIndex, Player requester) {
        startScanAndSave(core, slotIndex, requester, null);
    }

    public void startScanAndSave(TerritoryCore core, int slotIndex, Player requester, String customName) {
        if (isRebuilding) {
            if (requester != null) requester.sendMessage(ChatColor.RED + "7Gao đang bận tái thiết, không thể quét lúc này!");
            return;
        }

        showFromCore(core);

        TerritoryDefense plugin = TerritoryDefense.getInstance();
        Location coreLoc = core.getLocation();
        int radius = plugin.getCoreManager().getCoreRadius(core);
        int scanHeightBelow = plugin.getConfig().getInt("builder-settings.scan-height-below", 5);
        int scanHeightAbove = plugin.getConfig().getInt("builder-settings.scan-height-above", 15);

        class LocationDelta {
            final int dx, dy, dz;
            LocationDelta(int dx, int dy, int dz) {
                this.dx = dx;
                this.dy = dy;
                this.dz = dz;
            }
        }

        java.util.Queue<LocationDelta> scanQueue = new java.util.LinkedList<>();
        for (int dy = -scanHeightBelow; dy <= scanHeightAbove; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    scanQueue.add(new LocationDelta(dx, dy, dz));
                }
            }
        }

        List<TerritoryCore.BlockSnapshot> scannedBlocks = new ArrayList<>();

        if (requester != null) {
            requester.sendMessage(ChatColor.YELLOW + "7Gao đang bắt đầu quét thiết kế lãnh thổ...");
        }

        new BukkitRunnable() {
            private static final int BLOCKS_PER_TICK = 1500;

            @Override
            public void run() {
                int processed = 0;
                while (!scanQueue.isEmpty() && processed < BLOCKS_PER_TICK) {
                    LocationDelta delta = scanQueue.poll();
                    Block block = coreLoc.getWorld().getBlockAt(
                        coreLoc.getBlockX() + delta.dx,
                        coreLoc.getBlockY() + delta.dy,
                        coreLoc.getBlockZ() + delta.dz
                    );
                    Material type = block.getType();
                    if (type != Material.AIR && type != Material.CAVE_AIR && type != Material.VOID_AIR && type != Material.CONDUIT) {
                        Location blockLoc = block.getLocation();
                        if (plugin.getTowerManager() != null && plugin.getTowerManager().getActiveTowers().containsKey(blockLoc)) {
                            processed++;
                            continue;
                        }
                        String blockDataStr = block.getBlockData().getAsString();
                        scannedBlocks.add(new TerritoryCore.BlockSnapshot(
                            delta.dx, delta.dy, delta.dz, type.name(), blockDataStr
                        ));
                    }
                    processed++;
                }

                if (scanQueue.isEmpty()) {
                    if (slotIndex >= 0 && slotIndex < 54) {
                        core.setBlueprintSlot(slotIndex, scannedBlocks);
                        core.getBlueprintSlotsBought().set(slotIndex, false);
                        if (customName != null && !customName.trim().isEmpty()) {
                            core.getBlueprintNames().set(slotIndex, customName);
                        } else {
                            core.getBlueprintNames().set(slotIndex, "Bản thiết kế #" + (slotIndex + 1));
                        }
                        core.getBlueprintScanLevels().set(slotIndex, core.getLevel());
                        plugin.getCoreManager().registerCore(core.getLocation(), core);
                        if (requester != null) {
                            String displayName = (customName != null && !customName.trim().isEmpty()) ? customName : ("Bản thiết kế #" + (slotIndex + 1));
                            requester.sendMessage(ChatColor.GREEN + "Đã sao chép và lưu \"" + displayName + "\" vào Slot #" + (slotIndex + 1) + " thành công! Tổng số: " + scannedBlocks.size() + " block.");
                            requester.playSound(requester.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        }
                    }
                    hideInCore(core);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void startRebuild(TerritoryCore core, List<TerritoryCore.BlockSnapshot> design, Player requester) {
        if (isRebuilding && !isPausedForMaterials) {
            if (requester != null) requester.sendMessage(ChatColor.RED + "7Gao đang bận tái thiết!");
            return;
        }

        if (design == null || design.isEmpty()) {
            if (requester != null) requester.sendMessage(ChatColor.RED + "Không có bản vẽ thiết kế nào được lưu!");
            return;
        }

        TerritoryDefense plugin = TerritoryDefense.getInstance();
        boolean bypassArea = (requester != null && (requester.isOp() || requester.hasPermission("territorydefense.bypass")));
        if (!bypassArea) {
            int radius = plugin.getCoreManager().getCoreRadius(core);
            for (TerritoryCore.BlockSnapshot snap : design) {
                if (Math.abs(snap.relX) > radius || Math.abs(snap.relZ) > radius) {
                    if (requester != null) {
                        requester.sendMessage(ChatColor.RED + "[Kiến Thiết] Không thể xây dựng! Bản thiết kế này có diện tích lớn hơn diện tích lãnh thổ hiện tại của bạn.");
                    }
                    return;
                }
            }
        } else {
            if (requester != null) {
                requester.sendMessage(ChatColor.YELLOW + "[Kiến Thiết] Bạn đang sử dụng quyền Admin để xây dựng bản vẽ vượt quá diện tích lãnh thổ!");
            }
        }

        // Sắp xếp thứ tự xây dựng: Từ móng lên mái (cao độ Y tăng dần), từ trong ra ngoài (khoảng cách Euclidean tăng dần)
        sortDesignBuildOrder(design);

        showFromCore(core);

        this.activeCore = core;
        this.activeDesign = design;
        this.activeRequesterRef = new WeakReference<>(requester);
        this.isRebuilding = true;
        this.isPausedForMaterials = false;
        this.currentIndex = 0;
        this.placedAnyInCurrentPass = false;
        this.buildAttempts.clear();

        reportMissingMaterials(core, design, requester);

        if (requester != null && lastNotifyTime == 0) {
            requester.sendMessage(ChatColor.GOLD + "7Gao bắt đầu tái thiết lãnh thổ dựa trên bản vẽ thiết kế...");
        }

        int blocksPerSecond = plugin.getConfig().getInt("builder-settings.levels." + core.getBuilderLevel(), 2);
        int intervalTicks = Math.max(1, 20 / blocksPerSecond);

        if (activeTask != null) {
            activeTask.cancel();
        }

        activeTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Player req = activeRequesterRef.get();
                    boolean bypassAll = (req != null && (req.isOp() || req.hasPermission("territorydefense.bypass")));
                    if (!isRebuilding || isPausedForMaterials) {
                        cancel();
                        return;
                    }

                    // Nếu đã duyệt qua hết mảng snapshot
                    if (currentIndex >= activeDesign.size()) {
                        // Thực hiện kiểm tra toàn cục cuối cùng (O(N)) trước khi hoàn thành
                        boolean allBuilt = true;
                        int idx = 0;
                        for (TerritoryCore.BlockSnapshot snap : activeDesign) {
                            Location blockLoc = activeCore.getLocation().clone().add(snap.relX, snap.relY, snap.relZ);
                            Block block = blockLoc.getBlock();
                            Material targetMat = matchCache.computeIfAbsent(snap.material, Material::matchMaterial);
                            int att = buildAttempts.getOrDefault(idx, 0);
                            if (targetMat != null && att < 2) {
                                if (!isCompatibleBlock(block, targetMat, snap.blockData)) {
                                    allBuilt = false;
                                    break;
                                }
                            }
                            idx++;
                        }

                        if (allBuilt) {
                            isRebuilding = false;
                            if (req != null && req.isOnline()) {
                                req.sendMessage(ChatColor.GREEN + "[Kiến Thiết] 7Gao đã hoàn thành tiến trình tái thiết lãnh thổ!");
                                req.playSound(req.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                            }
                            hideInCore(activeCore);
                            plugin.getCoreManager().registerCore(activeCore.getLocation(), activeCore);
                            cleanupReferences();
                            cancel();
                            return;
                        } else {
                            if (!placedAnyInCurrentPass) {
                                // Thực sự thiếu nguyên liệu cho tất cả các block còn lại
                                isPausedForMaterials = true;
                                long now = System.currentTimeMillis();
                                if (now - lastNotifyTime > 15000) {
                                    lastNotifyTime = now;
                                    if (req != null && req.isOnline()) {
                                        req.sendMessage(ChatColor.RED + "[Kiến Thiết] 7Gao đã tạm ngưng làm việc do thiếu nguyên liệu để tiếp tục xây dựng các khối còn lại!");
                                        reportMissingMaterials(activeCore, activeDesign, req);
                                        req.sendMessage(ChatColor.YELLOW + "[Kiến Thiết] Mẹo: Hãy nạp thêm block vào Rương Tái Thiết để 7Gao tự động tiếp tục.");
                                    }
                                }
                                hideInCore(activeCore);
                                plugin.getCoreManager().registerCore(activeCore.getLocation(), activeCore);
                                cancel();
                                return;
                            }
                            // Quay lại từ đầu nếu có block bị phá hủy hoặc bỏ sót và chúng ta vẫn tiến triển
                            currentIndex = 0;
                            placedAnyInCurrentPass = false;
                        }
                    }

                    int batch = Math.max(1, blocksPerSecond / (20 / intervalTicks));
                    int blocksPlacedThisTick = 0;

                    while (currentIndex < activeDesign.size() && blocksPlacedThisTick < batch) {
                        int attempts = buildAttempts.getOrDefault(currentIndex, 0);
                        if (attempts >= 2) {
                            currentIndex++;
                            continue;
                        }

                        TerritoryCore.BlockSnapshot snap = activeDesign.get(currentIndex);
                        
                        // Bỏ qua nếu là vị trí của Lõi (0, 0, 0) để tránh đè lên/lắp khối Conduit của Lõi
                        if (snap.relX == 0 && snap.relY == 0 && snap.relZ == 0) {
                            currentIndex++;
                            continue;
                        }

                        Location blockLoc = activeCore.getLocation().clone().add(snap.relX, snap.relY, snap.relZ);
                        Block block = blockLoc.getBlock();
                        Material targetMat = matchCache.computeIfAbsent(snap.material, Material::matchMaterial);

                        if (targetMat == null) {
                            currentIndex++;
                            continue;
                        }

                        if (isCompatibleBlock(block, targetMat, snap.blockData)) {
                            currentIndex++;
                            continue;
                        }

                        // CHECK FOR FEP/PEP REQUIREMENT
                        if (!bypassAll && activeCore.getFep() < 1.0) {
                            isPausedForMaterials = true;
                            long now = System.currentTimeMillis();
                            if (now - lastNotifyTime > 15000) {
                                lastNotifyTime = now;
                                if (req != null && req.isOnline()) {
                                    req.sendMessage(ChatColor.RED + "[Kiến Thiết] 7Gao đã tạm ngưng do lõi không đủ năng lượng FEP/PEP để tiếp tục sửa chữa/đặt khối!");
                                    req.sendMessage(ChatColor.YELLOW + "[Kiến Thiết] Hãy nạp thêm thức ăn vào Lõi để tiếp tục.");
                                }
                            }
                            hideInCore(activeCore);
                            plugin.getCoreManager().registerCore(activeCore.getLocation(), activeCore);
                            cancel();
                            return;
                        }

                        com.truongcm.territorydefense.feature.core.RebuildWarehouseStorage rebuildInv = activeCore.getRebuildWarehouse();
                        boolean hasMaterial = bypassAll;

                        if (!bypassAll) {
                            for (int slotIdx = 0; slotIdx < rebuildInv.getSize(); slotIdx++) {
                                ItemStack item = rebuildInv.getItem(slotIdx);
                                if (item != null && item.getType() == targetMat && item.getAmount() > 0) {
                                    item.setAmount(item.getAmount() - 1);
                                    rebuildInv.setItem(slotIdx, item.getAmount() > 0 ? item : null);
                                    hasMaterial = true;
                                    break;
                                }
                            }
                        }

                        if (hasMaterial) {
                            if (entity != null && entity.isValid()) {
                                entity.teleport(blockLoc.clone().add(0.5, 1.0, 0.5));
                            }

                            block.setType(targetMat, false);
                            try {
                                BlockData data = Bukkit.createBlockData(snap.blockData);
                                block.setBlockData(data, true);
                            } catch (Exception ignored) {
                                block.setBlockData(targetMat.createBlockData(), true);
                            }

                            try {
                                blockLoc.getWorld().spawnParticle(Particle.valueOf("SMOKE"), blockLoc.clone().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.02);
                            } catch (Exception ignored) {}
                            blockLoc.getWorld().playSound(blockLoc, Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);
                            
                            // Khấu trừ FEP
                            if (!bypassAll) {
                                activeCore.setFep(activeCore.getFep() - 1.0);
                            }

                            blocksPlacedThisTick++;
                            buildAttempts.put(currentIndex, attempts + 1);
                            currentIndex++;
                            placedAnyInCurrentPass = true;
                        } else {
                            // Thiếu nguyên liệu cho khối này: Bỏ qua để tìm xây các khối khác trước
                            currentIndex++;
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Loi xay ra trong qua trinh tai thiet cua Tho Xay NPC!", e);
                    isRebuilding = false;
                    isPausedForMaterials = false;
                    cleanupReferences();
                    if (activeCore != null) {
                        hideInCore(activeCore);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, intervalTicks);
    }

    private void sortDesignBuildOrder(List<TerritoryCore.BlockSnapshot> design) {
        design.sort((a, b) -> {
            // Sắp xếp theo cao độ Y trước (Dưới lên trên)
            if (a.relY != b.relY) {
                return Integer.compare(a.relY, b.relY);
            }
            // Sắp xếp theo khoảng cách đến lõi (Từ trong ra ngoài)
            double distA = a.relX * a.relX + a.relZ * a.relZ;
            double distB = b.relX * b.relX + b.relZ * b.relZ;
            return Double.compare(distA, distB);
        });
    }

    private void cleanupReferences() {
        this.activeDesign = null;
        this.activeCore = null;
        this.activeRequesterRef.clear();
    }

    public void dispose() {
        cancelRebuild();
        this.lastPreRaidSnapshot.clear();
        if (entity != null) {
            entity.remove();
            entity = null;
        }
    }

    private void reportMissingMaterials(TerritoryCore core, List<TerritoryCore.BlockSnapshot> design, Player requester) {
        if (requester == null || !requester.isOnline()) return;

        Map<Material, Integer> missing = new HashMap<>();
        Map<String, Material> matchCache = new HashMap<>();

        for (TerritoryCore.BlockSnapshot snap : design) {
            // Bỏ qua nếu là vị trí của Lõi (0, 0, 0)
            if (snap.relX == 0 && snap.relY == 0 && snap.relZ == 0) {
                continue;
            }

            Location blockLoc = core.getLocation().clone().add(snap.relX, snap.relY, snap.relZ);
            Block block = blockLoc.getBlock();
            
            String matName = snap.material;
            if (matName == null) continue;

            Material targetMat = matchCache.get(matName);
            if (targetMat == null) {
                targetMat = Material.matchMaterial(matName);
                if (targetMat == null) {
                    try {
                        targetMat = Material.valueOf(matName.toUpperCase().replace("MINECRAFT:", ""));
                    } catch (Exception ignored) {}
                }
                if (targetMat == null) {
                    targetMat = Material.AIR;
                }
                matchCache.put(matName, targetMat);
            }

            if (targetMat != Material.AIR) {
                if (!isCompatibleBlock(block, targetMat, snap.blockData)) {
                    missing.put(targetMat, missing.getOrDefault(targetMat, 0) + 1);
                }
            }
        }

        com.truongcm.territorydefense.feature.core.RebuildWarehouseStorage rebuildInv = core.getRebuildWarehouse();
        for (ItemStack item : rebuildInv.getContents()) {
            if (item != null && item.getAmount() > 0) {
                Material type = item.getType();
                if (missing.containsKey(type)) {
                    int needed = missing.get(type);
                    if (item.getAmount() >= needed) {
                        missing.remove(type);
                    } else {
                        missing.put(type, needed - item.getAmount());
                    }
                }
            }
        }

        if (!missing.isEmpty()) {
            requester.sendMessage(ChatColor.RED + "[Kiến Thiết] 7Gao đang thiếu các nguyên liệu sau:");
            for (Map.Entry<Material, Integer> entry : missing.entrySet()) {
                String matName = entry.getKey().name().toLowerCase().replace("_", " ");
                requester.sendMessage(ChatColor.GRAY + " - " + ChatColor.YELLOW + matName + ": " + ChatColor.RED + entry.getValue() + " block");
            }
        } else {
            requester.sendMessage(ChatColor.GREEN + "[Kiến Thiết] Tất cả nguyên liệu cần thiết đều đã sẵn sàng!");
        }
    }

    public void tryResumeRebuilding() {
        if (isRebuilding && isPausedForMaterials && activeCore != null && activeDesign != null) {
            isPausedForMaterials = false;
            Player p = activeRequesterRef.get();
            if (p == null || !p.isOnline()) {
                p = Bukkit.getPlayer(activeCore.getOwnerUUID());
            }
            startRebuild(activeCore, activeDesign, p);
        }
    }

    public void cancelRebuild() {
        if (activeTask != null) {
            activeTask.cancel();
            activeTask = null;
        }
        isRebuilding = false;
        isPausedForMaterials = false;
        if (activeCore != null) {
            hideInCore(activeCore);
            TerritoryDefense.getInstance().getCoreManager().registerCore(activeCore.getLocation(), activeCore);
        }
        cleanupReferences();
    }

    private static java.util.Map<String, String> parseProperties(String dataStr) {
        java.util.Map<String, String> props = new java.util.HashMap<>();
        int start = dataStr.indexOf('[');
        int end = dataStr.lastIndexOf(']');
        if (start != -1 && end > start) {
            String propContent = dataStr.substring(start + 1, end);
            String[] pairs = propContent.split(",");
            for (String pair : pairs) {
                String[] parts = pair.split("=");
                if (parts.length == 2) {
                    props.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return props;
    }

    public boolean isCompatibleBlock(Block block, Material targetMat, String targetDataStr) {
        Material currentMat = block.getType();
        if (currentMat == targetMat) {
            if (isCropOrPlant(targetMat) || isLiquid(targetMat) || isSoil(targetMat)) {
                return true;
            }
            
            String currentDataStr = block.getBlockData().getAsString();
            
            // Chuẩn hóa namespace "minecraft:" để tránh lệch nhau khi so khớp
            String normCurrent = currentDataStr.startsWith("minecraft:") ? currentDataStr.substring(10) : currentDataStr;
            String normTarget = targetDataStr.startsWith("minecraft:") ? targetDataStr.substring(10) : targetDataStr;
            
            if (normCurrent.equals(normTarget)) {
                return true;
            }
            
            // Compare parsed properties, ignoring dynamic ones
            java.util.Map<String, String> currentProps = parseProperties(normCurrent);
            java.util.Map<String, String> targetProps = parseProperties(normTarget);
            
            // List of properties to ignore
            java.util.Set<String> ignoredProps = new java.util.HashSet<>(java.util.Arrays.asList(
                "north", "south", "east", "west", "up", "down",
                "waterlogged", "powered", "power", "lit", "open",
                "occupied", "signal_fire", "unstable", "triggered"
            ));
            
            for (String ignored : ignoredProps) {
                currentProps.remove(ignored);
                targetProps.remove(ignored);
            }
            
            return currentProps.equals(targetProps);
        }
        
        // Soil cross-compatibility
        if (isSoil(currentMat) && isSoil(targetMat)) {
            return true;
        }
        
        return false;
    }

    private boolean isCropOrPlant(Material mat) {
        String name = mat.name();
        return name.contains("WHEAT") || name.contains("CARROT") || name.contains("POTATO") 
            || name.contains("BEETROOT") || name.contains("STEM") || name.contains("COCOA") 
            || name.contains("BUSH") || name.contains("CROPS") || name.contains("NETHER_WART");
    }

    private boolean isLiquid(Material mat) {
        return mat == Material.WATER || mat == Material.LAVA;
    }

    private boolean isSoil(Material mat) {
        return mat == Material.FARMLAND || mat == Material.DIRT || mat == Material.GRASS_BLOCK || mat == Material.DIRT_PATH;
    }
}
