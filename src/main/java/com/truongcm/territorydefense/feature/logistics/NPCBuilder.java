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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NPCBuilder {
    private final UUID builderUUID;
    private final UUID ownerCoreUUID;
    private final Villager entity;
    private boolean isRebuilding = false;
    private boolean isPausedForMaterials = false;
    private BukkitTask activeTask = null;
    private final List<TerritoryCore.BlockSnapshot> lastPreRaidSnapshot = new java.util.ArrayList<>();

    private List<TerritoryCore.BlockSnapshot> activeDesign = null;
    private TerritoryCore activeCore = null;
    private Player activeRequester = null;
    private long lastNotifyTime = 0;
    private boolean isHidden = false;

    public List<TerritoryCore.BlockSnapshot> getLastPreRaidSnapshot() {
        return lastPreRaidSnapshot;
    }

    public void hideInCore(TerritoryCore core) {
        if (isHidden) return;
        isHidden = true;
        entity.setInvisible(true);
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setCollidable(false);
        entity.teleport(core.getLocation().clone().add(0.5, 0.5, 0.5));
    }

    public void showFromCore(TerritoryCore core) {
        if (!isHidden) return;
        isHidden = false;
        entity.setInvisible(false);
        entity.setAI(true);
        entity.setInvulnerable(false);
        entity.setSilent(false);
        entity.setCollidable(true);
        entity.teleport(core.getLocation().clone().add(0.5, 1.0, 0.5));
    }

    public NPCBuilder(UUID builderUUID, UUID ownerCoreUUID, Villager entity) {
        this.builderUUID = builderUUID;
        this.ownerCoreUUID = ownerCoreUUID;
        this.entity = entity;
        applyAttributes();

        TerritoryDefense plugin = TerritoryDefense.getInstance();
        TerritoryCore core = plugin.getCoreManager().getAllActiveCores().stream()
                .filter(c -> c.getCoreId().equals(ownerCoreUUID))
                .findFirst().orElse(null);
        if (core != null) {
            hideInCore(core);
        }
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
        entity.setCustomName(ChatColor.GOLD + "Thợ Xây Liên Minh (Cấp " + lvl + ")");
        entity.setCustomNameVisible(true);
        entity.setProfession(Villager.Profession.MASON);
        entity.setBaby();
        entity.setAgeLock(true);
        entity.setMetadata("td_custom_entity", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        entity.setMetadata("td_builder", new org.bukkit.metadata.FixedMetadataValue(plugin, builderUUID.toString()));
        entity.getPersistentDataContainer().set(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING, ownerCoreUUID.toString());
        entity.setAI(true);
        entity.setGravity(false);
        entity.setCollidable(false);
    }

    public void startScanAndSave(TerritoryCore core, int slotIndex, Player requester) {
        startScanAndSave(core, slotIndex, requester, null);
    }

    public void startScanAndSave(TerritoryCore core, int slotIndex, Player requester, String customName) {
        if (isRebuilding) {
            if (requester != null) requester.sendMessage(ChatColor.RED + "Thợ Xây đang bận tái thiết, không thể quét lúc này!");
            return;
        }

        showFromCore(core);

        TerritoryDefense plugin = TerritoryDefense.getInstance();
        Location coreLoc = core.getLocation();
        int radius = plugin.getCoreManager().getCoreRadius(core);
        int scanHeightAbove = 30;

        int minY = coreLoc.getBlockY() - 2;
        int maxY = coreLoc.getBlockY() + scanHeightAbove;

        List<TerritoryCore.BlockSnapshot> scannedBlocks = new ArrayList<>();

        if (requester != null) {
            requester.sendMessage(ChatColor.YELLOW + "Thợ Xây đang bắt đầu quét thiết kế lãnh thổ...");
        }

        new BukkitRunnable() {
            int currentY = minY;

            @Override
            public void run() {
                if (currentY > maxY) {
                    if (slotIndex >= 0 && slotIndex < 54) {
                        core.getBlueprintSlots().get(slotIndex).clear();
                        core.getBlueprintSlots().get(slotIndex).addAll(scannedBlocks);
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
                    return;
                }

                int centerX = coreLoc.getBlockX();
                int centerZ = coreLoc.getBlockZ();

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dz * dz <= radius * radius) {
                            Block block = coreLoc.getWorld().getBlockAt(centerX + dx, currentY, centerZ + dz);
                            Material type = block.getType();
                            if (type != Material.AIR && type != Material.CAVE_AIR && type != Material.VOID_AIR && type != Material.CONDUIT) {
                                Location blockLoc = block.getLocation();
                                if (plugin.getTowerManager() != null && plugin.getTowerManager().getActiveTowers().containsKey(blockLoc)) {
                                    continue;
                                }
                                String blockDataStr = block.getBlockData().getAsString();
                                scannedBlocks.add(new TerritoryCore.BlockSnapshot(dx, currentY - coreLoc.getBlockY(), dz, type.name(), blockDataStr));
                            }
                        }
                    }
                }
                currentY++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void startRebuild(TerritoryCore core, List<TerritoryCore.BlockSnapshot> design, Player requester) {
        if (isRebuilding && !isPausedForMaterials) {
            if (requester != null) requester.sendMessage(ChatColor.RED + "Thợ Xây đang bận tái thiết!");
            return;
        }

        if (design == null || design.isEmpty()) {
            if (requester != null) requester.sendMessage(ChatColor.RED + "Không có bản vẽ thiết kế nào được lưu!");
            return;
        }

        TerritoryDefense plugin = TerritoryDefense.getInstance();
        int radius = plugin.getCoreManager().getCoreRadius(core);
        for (TerritoryCore.BlockSnapshot snap : design) {
            if (snap.relX * snap.relX + snap.relZ * snap.relZ > radius * radius) {
                if (requester != null) {
                    requester.sendMessage(ChatColor.RED + "[Kiến Thiết] Không thể xây dựng! Bản thiết kế này có diện tích lớn hơn diện tích lãnh thổ hiện tại của bạn.");
                }
                return;
            }
        }

        showFromCore(core);

        this.activeCore = core;
        this.activeDesign = design;
        this.activeRequester = requester;
        this.isRebuilding = true;
        this.isPausedForMaterials = false;

        reportMissingMaterials(core, design, requester);

        if (requester != null && lastNotifyTime == 0) {
            requester.sendMessage(ChatColor.GOLD + "Thợ Xây bắt đầu tái thiết lãnh thổ dựa trên bản vẽ thiết kế...");
        }

        int blocksPerSecond = switch (core.getBuilderLevel()) {
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 15;
            case 5 -> 25;
            default -> 2;
        };
        int intervalTicks = Math.max(1, 20 / blocksPerSecond);

        if (activeTask != null) {
            activeTask.cancel();
        }

        activeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isRebuilding || isPausedForMaterials) {
                    cancel();
                    return;
                }

                boolean allBuilt = true;
                for (TerritoryCore.BlockSnapshot snap : activeDesign) {
                    Location blockLoc = activeCore.getLocation().clone().add(snap.relX, snap.relY, snap.relZ);
                    Block block = blockLoc.getBlock();
                    Material targetMat = Material.matchMaterial(snap.material);
                    if (targetMat != null) {
                        if (block.getType() != targetMat || !block.getBlockData().getAsString().equals(snap.blockData)) {
                            allBuilt = false;
                            break;
                        }
                    }
                }

                if (allBuilt) {
                    isRebuilding = false;
                    if (activeRequester != null && activeRequester.isOnline()) {
                        activeRequester.sendMessage(ChatColor.GREEN + "[Kiến Thiết] Thợ Xây đã hoàn thành tiến trình tái thiết lãnh thổ!");
                        activeRequester.playSound(activeRequester.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                    hideInCore(activeCore);
                    plugin.getCoreManager().registerCore(activeCore.getLocation(), activeCore);
                    cancel();
                    return;
                }

                int batch = Math.max(1, blocksPerSecond / (20 / intervalTicks));
                int blocksPlacedThisTick = 0;

                for (int i = 0; i < activeDesign.size() && blocksPlacedThisTick < batch; i++) {
                    TerritoryCore.BlockSnapshot snap = activeDesign.get(i);
                    Location blockLoc = activeCore.getLocation().clone().add(snap.relX, snap.relY, snap.relZ);
                    Block block = blockLoc.getBlock();
                    Material targetMat = Material.matchMaterial(snap.material);

                    if (targetMat == null) continue;

                    if (block.getType() == targetMat && block.getBlockData().getAsString().equals(snap.blockData)) {
                        continue;
                    }

                    // CHECK FOR FEP/PEP REQUIREMENT
                    if (activeCore.getFep() < 1.0) {
                        isPausedForMaterials = true;
                        long now = System.currentTimeMillis();
                        if (now - lastNotifyTime > 15000) {
                            lastNotifyTime = now;
                            if (activeRequester != null && activeRequester.isOnline()) {
                                activeRequester.sendMessage(ChatColor.RED + "[Kiến Thiết] Thợ Xây đã tạm ngưng do lõi không đủ năng lượng FEP/PEP để tiếp tục sửa chữa/đặt khối!");
                                activeRequester.sendMessage(ChatColor.YELLOW + "[Kiến Thiết] Hãy nạp thêm thức ăn vào Lõi để tiếp tục.");
                            }
                        }
                        hideInCore(activeCore);
                        plugin.getCoreManager().registerCore(activeCore.getLocation(), activeCore);
                        cancel();
                        return;
                    }

                    Inventory rebuildInv = activeCore.getRebuildWarehouse();
                    boolean hasMaterial = false;

                    for (int slotIdx = 0; slotIdx < rebuildInv.getSize(); slotIdx++) {
                        ItemStack item = rebuildInv.getItem(slotIdx);
                        if (item != null && item.getType() == targetMat && item.getAmount() > 0) {
                            item.setAmount(item.getAmount() - 1);
                            rebuildInv.setItem(slotIdx, item.getAmount() > 0 ? item : null);
                            hasMaterial = true;
                            break;
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
                        
                        // Deduct 1 FEP / PEP
                        activeCore.setFep(activeCore.getFep() - 1.0);

                        blocksPlacedThisTick++;
                    }
                }

                if (blocksPlacedThisTick == 0) {
                    isPausedForMaterials = true;
                    long now = System.currentTimeMillis();
                    if (now - lastNotifyTime > 15000) {
                        lastNotifyTime = now;
                        if (activeRequester != null && activeRequester.isOnline()) {
                            activeRequester.sendMessage(ChatColor.RED + "[Kiến Thiết] Thợ Xây đã tạm ngưng làm việc do thiếu nguyên liệu để tiếp tục xây dựng!");
                            reportMissingMaterials(activeCore, activeDesign, activeRequester);
                            activeRequester.sendMessage(ChatColor.YELLOW + "[Kiến Thiết] Mẹo: Hãy nạp thêm block vào Rương Tái Thiết để Thợ Xây tự động tiếp tục.");
                        }
                    }
                    hideInCore(activeCore);
                    plugin.getCoreManager().registerCore(activeCore.getLocation(), activeCore);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, intervalTicks);
    }

    private void reportMissingMaterials(TerritoryCore core, List<TerritoryCore.BlockSnapshot> design, Player requester) {
        if (requester == null || !requester.isOnline()) return;

        Map<Material, Integer> missing = new HashMap<>();
        for (TerritoryCore.BlockSnapshot snap : design) {
            Location blockLoc = core.getLocation().clone().add(snap.relX, snap.relY, snap.relZ);
            Block block = blockLoc.getBlock();
            Material targetMat = Material.matchMaterial(snap.material);
            if (targetMat != null) {
                if (block.getType() != targetMat || !block.getBlockData().getAsString().equals(snap.blockData)) {
                    missing.put(targetMat, missing.getOrDefault(targetMat, 0) + 1);
                }
            }
        }

        Inventory rebuildInv = core.getRebuildWarehouse();
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
            requester.sendMessage(ChatColor.RED + "[Kiến Thiết] Thợ Xây đang thiếu các nguyên liệu sau:");
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
            Player p = activeRequester;
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
    }
}
