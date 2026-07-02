package com.truongcm.territorydefense.feature.core;

import com.truongcm.territorydefense.TerritoryDefense;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QUẢN LÝ LÕI LÃNH THỔ (CORE MANAGER) - PHIÊN BẢN CHUẨN MASTER GDD FINAL V30
 * BẢN RÚT GỌN (PURE MEMORY REGISTRY):
 * - Đảm nhận vai trò lưu trữ bản đồ bộ nhớ các Lõi đang hoạt động.
 * - Giải phóng hoàn toàn các logic lưu trữ tập tin sang CoreStorage.
 * - Giải phóng 11 bộ lắng nghe sự kiện sang CoreGameplayListener.
 * - Loại bỏ hoàn toàn cơ chế Reflection chậm và lỗi thời.
 */
public class CoreManager {

    private final TerritoryDefense plugin;
    
    // Package-private maps để CoreStorage và CoreGameplayListener có thể truy cập hiệu năng cao trực tiếp
    final Map<Location, TerritoryCore> activeCores = new ConcurrentHashMap<>();
    final Map<UUID, Integer> coreShards = new ConcurrentHashMap<>();
    final Map<UUID, Long> corePeaceCooldowns = new ConcurrentHashMap<>();
    final Map<UUID, Long> coreLastRaidTimes = new ConcurrentHashMap<>();
    final Map<Location, Double> blockDamageMap = new ConcurrentHashMap<>();

    public CoreManager(TerritoryDefense plugin) {
        this.plugin = plugin;
    }

    public void registerCore(Location loc, TerritoryCore core) {
        Location alignedLoc = getBlockAlignedLocation(loc);
        activeCores.put(alignedLoc, core);

        if (plugin.getCoreStorage() != null) {
            YamlConfiguration playerConfig = plugin.getCoreStorage().loadPlayerConfig(core.getOwnerUUID());
            plugin.getCoreStorage().saveCoreToConfig(playerConfig, core);
            plugin.getCoreStorage().savePlayerConfig(core.getOwnerUUID(), playerConfig);
        }
        
        HologramManager.updateCoreHologram(core);
    }

    public TerritoryCore getCoreAt(Location loc) {
        if (loc == null) return null;
        TerritoryCore core = activeCores.get(getBlockAlignedLocation(loc));
        if (core != null) {
            syncCoreAlliance(core);
        }
        return core;
    }

    public int getOwnedCoreCount(@NotNull UUID uniqueId) {
        int count = 0;
        for (TerritoryCore core : activeCores.values()) {
            if (core.getOwnerUUID() != null && core.getOwnerUUID().equals(uniqueId)) {
                count++;
            }
        }
        return count;
    }

    private void removeAssociatedNPCs(TerritoryCore core) {
        if (core == null || core.getLocation() == null || core.getLocation().getWorld() == null) return;
        World world = core.getLocation().getWorld();
        UUID coreId = core.getCoreId();
        int radius = getCoreRadius(core) + 15;

        Collection<Entity> entities = world.getNearbyEntities(core.getLocation(), radius, 64, radius);
        int removedCount = 0;
        for (Entity entity : entities) {
            if (entity == null) continue;
            org.bukkit.persistence.PersistentDataContainer pdc = entity.getPersistentDataContainer();
            boolean isOwnedByThisCore = false;

            if (pdc.has(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING)) {
                String ownerCoreIdStr = pdc.get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING);
                if (ownerCoreIdStr != null && ownerCoreIdStr.equalsIgnoreCase(coreId.toString())) {
                    isOwnedByThisCore = true;
                }
            }
            if (!isOwnedByThisCore && (entity.hasMetadata("td_farmer") || entity.hasMetadata("td_npc"))) {
                isOwnedByThisCore = true;
            }
            if (isOwnedByThisCore) {
                entity.remove();
                removedCount++;
            }
        }
    }

    public boolean forcePurgePlayerCore(UUID ownerUUID) {
        if (ownerUUID == null) return false;
        boolean removed = false;

        Iterator<Map.Entry<Location, TerritoryCore>> iterator = activeCores.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, TerritoryCore> entry = iterator.next();
            TerritoryCore core = entry.getValue();

            if (core.getOwnerUUID() != null && core.getOwnerUUID().equals(ownerUUID)) {
                removeAssociatedNPCs(core);
                if (plugin.getFarmerManager() != null) {
                    plugin.getFarmerManager().removeFarmersAssociatedWithCore(core.getCoreId(), null, false);
                }
                if (plugin.getTowerManager() != null) {
                    plugin.getTowerManager().removeTowersAssociatedWithCore(core.getCoreId(), null, false);
                }
                coreShards.remove(core.getCoreId());
                corePeaceCooldowns.remove(core.getCoreId());
                iterator.remove();

                Location coreLoc = entry.getKey();
                Block block = coreLoc.getBlock();
                if (block.getType() == Material.CONDUIT) {
                    block.setType(Material.AIR);
                }
                removed = true;
            }
        }

        if (removed && plugin.getCoreStorage() != null) {
            YamlConfiguration playerConfig = plugin.getCoreStorage().loadPlayerConfig(ownerUUID);
            playerConfig.set("cores", null);
            plugin.getCoreStorage().savePlayerConfig(ownerUUID, playerConfig);
        }
        return removed;
    }

    public TerritoryCore getCoreByLocationRange(Location loc) {
        if (loc == null) return null;
        for (TerritoryCore core : activeCores.values()) {
            Location cLoc = core.getLocation();
            if (cLoc.getWorld() == null || !cLoc.getWorld().equals(loc.getWorld())) continue;

            int radius = getCoreRadius(core);
            int locX = loc.getBlockX();
            int locZ = loc.getBlockZ();
            int coreX = cLoc.getBlockX();
            int coreZ = cLoc.getBlockZ();

            if (Math.abs(locX - coreX) <= radius && Math.abs(locZ - coreZ) <= radius) {
                syncCoreAlliance(core);
                return core;
            }
        }
        return null;
    }

    public Collection<TerritoryCore> getAllActiveCores() {
        for (TerritoryCore core : activeCores.values()) {
            syncCoreAlliance(core);
        }
        return activeCores.values();
    }

    public ItemStack createCoreItem() {
        return createCoreItem(1);
    }

    public ItemStack createCoreItem(int level) {
        return createCoreItem(level, null);
    }

    public ItemStack createCoreItem(int level, UUID coreId) {
        ItemStack coreItem = new ItemStack(Material.CONDUIT);
        ItemMeta meta = coreItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Lõi Năng Lượng Biển" + (level > 1 ? " [Cấp " + level + "]" : ""));
            meta.setLore(java.util.Arrays.asList(
                    ChatColor.GRAY + "Đặt khối này để khởi tạo mạng lưới lãnh thổ.",
                    ChatColor.GOLD + "Cấp độ Lõi: " + ChatColor.WHITE + level,
                    ChatColor.AQUA + "Nguồn năng lượng huyền bí từ đại dương."
            ));
            meta.getPersistentDataContainer().set(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(PDCKeys.CORE_LEVEL, PersistentDataType.INTEGER, level);
            if (coreId != null) {
                NamespacedKey savedCoreIdKey = new NamespacedKey(plugin, "td_saved_core_id");
                meta.getPersistentDataContainer().set(savedCoreIdKey, PersistentDataType.STRING, coreId.toString());
            }
            coreItem.setItemMeta(meta);
        }
        return coreItem;
    }

    public boolean removeCore(Location loc, Player player, boolean giveItem) {
        Location alignedLoc = getBlockAlignedLocation(loc);
        TerritoryCore core = activeCores.remove(alignedLoc);
        
        // Fallback 1: Khớp tọa độ nguyên và tên thế giới để tránh bất đồng bộ thế giới Bukkit
        if (core == null && alignedLoc != null && alignedLoc.getWorld() != null) {
            String targetWorldName = alignedLoc.getWorld().getName();
            for (java.util.Map.Entry<Location, TerritoryCore> entry : activeCores.entrySet()) {
                Location keyLoc = entry.getKey();
                if (keyLoc.getBlockX() == alignedLoc.getBlockX() &&
                    keyLoc.getBlockY() == alignedLoc.getBlockY() &&
                    keyLoc.getBlockZ() == alignedLoc.getBlockZ() &&
                    keyLoc.getWorld() != null &&
                    keyLoc.getWorld().getName().equalsIgnoreCase(targetWorldName)) {
                    core = entry.getValue();
                    activeCores.remove(keyLoc);
                    break;
                }
            }
        }

        // Fallback 2: Thu hồi theo UUID của người chơi sở hữu nếu vẫn không tìm thấy
        if (core == null && player != null) {
            for (java.util.Map.Entry<Location, TerritoryCore> entry : activeCores.entrySet()) {
                TerritoryCore c = entry.getValue();
                if (c.getOwnerUUID() != null && c.getOwnerUUID().equals(player.getUniqueId())) {
                    core = c;
                    activeCores.remove(entry.getKey());
                    break;
                }
            }
        }

        if (core == null) return false;

        // Dọn dẹp vệ tinh với try-catch độc lập từng phần
        try {
            removeAssociatedNPCs(core);
        } catch (Exception e) {
            plugin.getLogger().severe("Lỗi khi xóa NPCs vệ tinh của Lõi " + core.getCoreId() + ": " + e.getMessage());
        }

        if (plugin.getFarmerManager() != null) {
            try {
                plugin.getFarmerManager().removeFarmersAssociatedWithCore(core.getCoreId(), player, giveItem);
            } catch (Exception e) {
                plugin.getLogger().severe("Lỗi khi xóa Nông dân của Lõi " + core.getCoreId() + ": " + e.getMessage());
            }
        }

        if (plugin.getTowerManager() != null) {
            try {
                plugin.getTowerManager().removeTowersAssociatedWithCore(core.getCoreId(), player, giveItem);
            } catch (Exception e) {
                plugin.getLogger().severe("Lỗi khi xóa Tháp canh của Lõi " + core.getCoreId() + ": " + e.getMessage());
            }
        }

        // Lưu cấu hình an toàn
        UUID ownerUUID = core.getOwnerUUID();
        if (ownerUUID != null && plugin.getCoreStorage() != null) {
            try {
                YamlConfiguration playerConfig = plugin.getCoreStorage().loadPlayerConfig(ownerUUID);
                plugin.getCoreStorage().saveCoreToConfig(playerConfig, core);
                
                String path = "cores." + core.getCoreId().toString();
                playerConfig.set(path + ".world", "none");
                playerConfig.set(path + ".x", 0);
                playerConfig.set(path + ".y", 0);
                playerConfig.set(path + ".z", 0);
                plugin.getCoreStorage().savePlayerConfig(ownerUUID, playerConfig);
            } catch (Exception e) {
                plugin.getLogger().severe("Lỗi khi lưu cấu hình Lõi " + core.getCoreId() + " cho người chơi " + ownerUUID + ": " + e.getMessage());
            }
        }

        try {
            coreShards.remove(core.getCoreId());
            corePeaceCooldowns.remove(core.getCoreId());
        } catch (Exception ignored) {}
        
        try {
            HologramManager.removeCoreHologram(core.getCoreId(), core.getLocation());
        } catch (Exception e) {
            plugin.getLogger().severe("Lỗi khi xóa Hologram của Lõi " + core.getCoreId() + ": " + e.getMessage());
        }

        // Xóa khối Conduit ngoài thế giới
        try {
            Block block = alignedLoc != null ? alignedLoc.getBlock() : core.getLocation().getBlock();
            if (block != null) {
                if (!block.getChunk().isLoaded()) {
                    block.getChunk().load();
                }
                if (block.getType() == Material.CONDUIT) {
                    block.setType(Material.AIR);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Lỗi khi xóa khối Conduit của Lõi " + core.getCoreId() + ": " + e.getMessage());
        }

        // Trả lại vật phẩm Lõi cho người chơi
        if (player != null && giveItem) {
            try {
                ItemStack coreItem = createCoreItem(core.getLevel(), core.getCoreId());
                // Nếu túi đồ đầy, quăng ra đất
                if (!player.getInventory().addItem(coreItem).isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), coreItem);
                    player.sendMessage(ChatColor.YELLOW + "[Bảo vệ] Túi đồ đầy! Lõi Lãnh Thổ đã được rơi xuống đất tại vị trí của bạn.");
                } else {
                    player.sendMessage(ChatColor.GREEN + "[Bảo vệ] Lõi Lãnh Thổ (Cấp " + core.getLevel() + ") đã được đóng gói và nạp trực tiếp vào hòm đồ của bạn.");
                }
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f);
            } catch (Exception e) {
                plugin.getLogger().severe("Lỗi khi trả lại vật phẩm Lõi cho " + player.getName() + ": " + e.getMessage());
            }
        }

        return true;
    }

    public Location getBlockAlignedLocation(Location loc) {
        if (loc == null) return null;
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public int getCoreRadius(TerritoryCore core) {
        if (core == null) return 16;
        int level = core.getLevel();
        int baseRadius = switch (level) {
            case 1 -> 16;
            case 2 -> 24;
            case 3 -> 32;
            case 4 -> 40;
            case 5 -> 50;
            default -> 16;
        };

        if (core.isMerged() && core.getMergeCount() >= 2) {
            String allyId = core.getAllyId();
            if (allyId != null && !allyId.isEmpty()) {
                double sumSquares = 0;
                int count = 0;
                for (TerritoryCore c : activeCores.values()) {
                    if (allyId.equalsIgnoreCase(c.getAllyId())) {
                        int r = switch (c.getLevel()) {
                            case 1 -> 16;
                            case 2 -> 24;
                            case 3 -> 32;
                            case 4 -> 40;
                            case 5 -> 50;
                            default -> 16;
                        };
                        sumSquares += r * r;
                        count++;
                    }
                }
                if (count >= 2) {
                    double areaBoost = 1.0 + 0.05 * count;
                    return (int) Math.round(Math.sqrt(sumSquares * areaBoost));
                }
            }
        }
        return baseRadius;
    }

    public TerritoryCore getCoreById(UUID coreId) {
        if (coreId == null) return null;
        for (TerritoryCore core : activeCores.values()) {
            if (core.getCoreId().equals(coreId)) {
                syncCoreAlliance(core);
                return core;
            }
        }
        return null;
    }

    public int getShards(UUID coreId) { return coreShards.getOrDefault(coreId, 0); }
    public void setShards(UUID coreId, int amount) { coreShards.put(coreId, Math.max(0, amount)); }
    public void addShards(UUID coreId, int amount) { setShards(coreId, getShards(coreId) + amount); }

    public long getPeaceUntil(UUID coreId) { return corePeaceCooldowns.getOrDefault(coreId, 0L); }
    public void setPeaceUntil(UUID coreId, long timestamp) { corePeaceCooldowns.put(coreId, Math.max(0L, timestamp)); }
    public boolean isUnderPeaceProtection(UUID coreId) { return System.currentTimeMillis() < getPeaceUntil(coreId); }

    public long getLastRaidTime(UUID coreId) { return coreLastRaidTimes.getOrDefault(coreId, 0L); }
    public void setLastRaidTime(UUID coreId, long timestamp) { coreLastRaidTimes.put(coreId, timestamp); }

    public String getCoreAlliance(TerritoryCore core) {
        if (core == null || core.getOwnerUUID() == null) return null;
        if (plugin.getAllianceManager() == null) return null;
        return plugin.getAllianceManager().getPlayerAlliance(core.getOwnerUUID());
    }

    private void syncCoreAlliance(TerritoryCore core) {
        if (core == null) return;
        String currentAlly = getCoreAlliance(core);
        core.setAllyId(currentAlly);
    }

    // --- DELEGATED FILE STORAGE FOR BACKWARD COMPATIBILITY ---

    public void loadAllCores() {
        if (plugin.getCoreStorage() != null) {
            plugin.getCoreStorage().loadAllCores();
        }
    }

    public void saveAllCores() {
        if (plugin.getCoreStorage() != null) {
            plugin.getCoreStorage().saveAllCores();
        }
    }
}