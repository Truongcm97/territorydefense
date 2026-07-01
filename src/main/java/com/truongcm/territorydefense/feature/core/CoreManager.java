package com.truongcm.territorydefense.feature.core;

import com.truongcm.territorydefense.TerritoryDefense;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Conduit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QUẢN LÝ LÕI LÃNH THỔ (CORE MANAGER) - PHIÊN BẢN CHUẨN MASTER GDD FINAL V30
 * SỬA TRIỆT ĐỂ:
 * - Lỗi đặt đè trùng Lõi đè lên ranh giới bảo vệ người khác (Lỗi 5).
 * - Chặn người lạ phá khối hoặc đặt khối trong lãnh thổ (Lỗi 2, 4) trừ khi có chiến sự.
 * - Bảo lưu cấp độ tháp phòng thủ và sọ quái vật khi người chơi đặt xuống (Lỗi 6).
 * - Đồng bộ hóa tiêm phản chiếu bán kính động để quái Raid spawn bình thường ngoài rìa (Lỗi 3).
 * ĐÃ CẬP NHẬT THEO YÊU CẦU:
 * - Lá chắn chỉ hoạt động trong thời gian Raid: Chỉ thực hiện khấu trừ Shield HP khi đang có Raid.
 * - Ngoài thời gian Raid: Lãnh thổ được bảo vệ tuyệt đối khỏi mọi vụ nổ địa hình hoàn toàn miễn phí (không tốn Shield HP).
 * - Tương tác vật phẩm: Chống người lạ click chuột phải tương tác vật phẩm, cửa, rương, nút bấm... ngoài thời gian chiến sự.
 */
public class CoreManager implements Listener {

    private final TerritoryDefense plugin;
    private final Map<Location, TerritoryCore> activeCores = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> coreShards = new ConcurrentHashMap<>();
    private final Map<UUID, Long> corePeaceCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> coreLastRaidTimes = new ConcurrentHashMap<>();

    private final File coresFile;
    private final YamlConfiguration coresConfig;

    public CoreManager(TerritoryDefense plugin) {
        this.plugin = plugin;

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.coresFile = new File(plugin.getDataFolder(), "cores.yml");
        this.coresConfig = new YamlConfiguration();
        if (coresFile.exists()) {
            try {
                this.coresConfig.load(coresFile);
            } catch (Exception ignored) {}
        }

        Bukkit.getScheduler().runTaskLater(plugin, this::wrapTowerPlaceListener, 1L);
    }

    private File getUserDataFolder() {
        File folder = new File(plugin.getDataFolder(), "userdata");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    private File getPlayerFile(UUID ownerUUID) {
        return new File(getUserDataFolder(), ownerUUID.toString() + ".yml");
    }

    private File getPlayerBackupFile(UUID ownerUUID) {
        return new File(getUserDataFolder(), ownerUUID.toString() + ".yml.bak");
    }

    private File getPlayerTempFile(UUID ownerUUID) {
        return new File(getUserDataFolder(), ownerUUID.toString() + ".yml.tmp");
    }

    private YamlConfiguration loadPlayerConfig(UUID ownerUUID) {
        File file = getPlayerFile(ownerUUID);
        File backup = getPlayerBackupFile(ownerUUID);
        YamlConfiguration config = new YamlConfiguration();

        if (file.exists() && file.length() > 0) {
            try {
                config.load(file);
                if (config.getKeys(false).isEmpty() && file.length() > 20) {
                    throw new Exception("Config has size but parsed 0 keys (corrupted)");
                }
                return config;
            } catch (Exception e) {
                plugin.getLogger().severe("[TD] Tep du lieu cua nguoi choi " + ownerUUID + " bi loi! Dang co gang khoi phuc tu file sao luu...");
                if (backup.exists() && backup.length() > 0) {
                    try {
                        config.load(backup);
                        java.nio.file.Files.copy(backup.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().info("[TD] Khoi phuc thanh cong du lieu tu file .bak cua " + ownerUUID);
                        return config;
                    } catch (Exception ex) {
                        plugin.getLogger().severe("[TD] Khoi phuc tu .bak that bai cho " + ownerUUID + ": " + ex.getMessage());
                    }
                }
            }
        }
        return config;
    }

    private void savePlayerConfig(UUID ownerUUID, YamlConfiguration config) {
        File file = getPlayerFile(ownerUUID);
        File temp = getPlayerTempFile(ownerUUID);
        File backup = getPlayerBackupFile(ownerUUID);

        try {
            config.save(temp);
            if (temp.exists() && temp.length() > 0) {
                if (file.exists() && file.length() > 0) {
                    try {
                        java.nio.file.Files.copy(file.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        plugin.getLogger().warning("[TD] Khong the tao file sao luu cho " + ownerUUID + ": " + e.getMessage());
                    }
                }
                try {
                    java.nio.file.Files.move(temp.toPath(), file.toPath(), 
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING, 
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    java.nio.file.Files.move(temp.toPath(), file.toPath(), 
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                plugin.getLogger().severe("[TD] Khong the luu file cho " + ownerUUID + " vi file tam rong hoac loi!");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[TD] Loi khi luu du lieu cho " + ownerUUID + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveCoreToConfig(YamlConfiguration config, TerritoryCore core) {
        String path = "cores." + core.getCoreId().toString();
        Location loc = core.getLocation();

        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getBlockX());
        config.set(path + ".y", loc.getBlockY());
        config.set(path + ".z", loc.getBlockZ());
        config.set(path + ".owner", core.getOwnerUUID().toString());
        config.set(path + ".level", core.getLevel());
        config.set(path + ".fep", core.getFep());
        config.set(path + ".shield", core.getShield());
        config.set(path + ".ally", getCoreAlliance(core));
        config.set(path + ".shards", getShards(core.getCoreId()));
        config.set(path + ".peace_until", getPeaceUntil(core.getCoreId()));
        config.set(path + ".last_pve_raid", getLastRaidTime(core.getCoreId()));
        config.set(path + ".permanent_raid_multiplier", core.getPermanentRaidMultiplier());
        config.set(path + ".is_merged", core.isMerged());
        config.set(path + ".merge_count", core.getMergeCount());
        config.set(path + ".completed_raids", core.getCompletedRaids());
        config.set(path + ".total_raid_count", core.getTotalRaidCount());
        config.set(path + ".raid_call_count", core.getRaidCallCount());
        config.set(path + ".disabled_until", core.getDisabledUntil());
        config.set(path + ".public_blueprint_shared", core.isPublicBlueprintShared());
        config.set(path + ".builder_level", core.getBuilderLevel());
        config.set(path + ".blueprint_price", core.getBlueprintPrice());
        config.set(path + ".selling_slot_index", core.getSellingSlotIndex());
        
        for (int s = 0; s < 54; s++) {
            java.util.List<java.util.Map<String, Object>> blueprintList = new java.util.ArrayList<>();
            java.util.List<TerritoryCore.BlockSnapshot> slotDesign = core.getBlueprintSlots().get(s);
            if (slotDesign != null) {
                for (TerritoryCore.BlockSnapshot snap : slotDesign) {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("relX", snap.relX);
                    map.put("relY", snap.relY);
                    map.put("relZ", snap.relZ);
                    map.put("material", snap.material);
                    map.put("blockData", snap.blockData);
                    blueprintList.add(map);
                }
            }
            config.set(path + ".blueprint_slot_bought_" + s, core.getBlueprintSlotsBought().get(s));
            config.set(path + ".blueprint_name_" + s, core.getBlueprintNames().get(s));
            config.set(path + ".blueprint_scan_level_" + s, core.getBlueprintScanLevels().get(s));
            config.set(path + ".blueprint_price_" + s, core.getBlueprintPrices().get(s));
            config.set(path + ".blueprint_selling_status_" + s, core.getBlueprintSellingStatus().get(s));
            config.set(path + ".blueprint_slot_" + s, blueprintList);
        }

        for (int i = 0; i < 54; i++) {
            config.set(path + ".warehouse." + i, core.getFoodWarehouse().getItem(i));
            config.set(path + ".rebuild_warehouse." + i, core.getRebuildWarehouse().getItem(i));
        }
    }

    private void loadCoresFromConfig(YamlConfiguration config, UUID ownerUUID, int[] loadedCount) {
        if (!config.contains("cores")) return;
        ConfigurationSection coresSection = config.getConfigurationSection("cores");
        if (coresSection == null) return;

        for (String key : coresSection.getKeys(false)) {
            String path = "cores." + key;
            try {
                UUID coreId = UUID.fromString(key);
                String worldName = config.getString(path + ".world");
                double x = config.getDouble(path + ".x");
                double y = config.getDouble(path + ".y");
                double z = config.getDouble(path + ".z");

                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                Location loc = new Location(world, x, y, z);
                int level = config.getInt(path + ".level", 1);
                double fep = config.getDouble(path + ".fep", 100.0);
                double shield = config.getDouble(path + ".shield", 1000.0);
                String allyId = config.getString(path + ".ally", null);
                UUID actualOwnerUUID = ownerUUID;
                if (config.contains(path + ".owner")) {
                    actualOwnerUUID = UUID.fromString(config.getString(path + ".owner"));
                }
                if (actualOwnerUUID == null) continue;

                TerritoryCore core = new TerritoryCore(coreId, loc, actualOwnerUUID, level, fep, shield, allyId);
                boolean isMerged = config.getBoolean(path + ".is_merged", false);
                int mergeCount = config.getInt(path + ".merge_count", 0);
                core.setMerged(isMerged);
                core.setMergeCount(mergeCount);
                int completedRaids = config.getInt(path + ".completed_raids", 0);
                core.setCompletedRaids(completedRaids);
                int totalRaidCount = config.getInt(path + ".total_raid_count", 0);
                core.setTotalRaidCount(totalRaidCount);
                int raidCallCount = config.getInt(path + ".raid_call_count", 0);
                core.setRaidCallCount(raidCallCount);
                long disabledUntil = config.getLong(path + ".disabled_until", 0L);
                core.setDisabledUntil(disabledUntil);
                int builderLevel = config.getInt(path + ".builder_level", 1);
                core.setBuilderLevel(builderLevel);
                double blueprintPrice = config.getDouble(path + ".blueprint_price", 0.0);
                core.setBlueprintPrice(blueprintPrice);
                int sellingSlotIndex = config.getInt(path + ".selling_slot_index", 0);
                core.setSellingSlotIndex(sellingSlotIndex);
                
                if (config.contains(path + ".warehouse")) {
                    for (int i = 0; i < 54; i++) {
                        ItemStack item = config.getItemStack(path + ".warehouse." + i);
                        if (item != null) {
                            core.getFoodWarehouse().setItem(i, item);
                        }
                    }
                }
                
                if (config.contains(path + ".rebuild_warehouse")) {
                    for (int i = 0; i < 54; i++) {
                        ItemStack item = config.getItemStack(path + ".rebuild_warehouse." + i);
                        if (item != null) {
                            core.getRebuildWarehouse().setItem(i, item);
                        }
                    }
                }

                core.setPublicBlueprintShared(config.getBoolean(path + ".public_blueprint_shared", false));
                for (int s = 0; s < 54; s++) {
                    boolean bought = config.getBoolean(path + ".blueprint_slot_bought_" + s, false);
                    core.getBlueprintSlotsBought().set(s, bought);
                    String name = config.getString(path + ".blueprint_name_" + s, "Ban thiet ke #" + (s + 1));
                    core.getBlueprintNames().set(s, name);
                    int scanLvl = config.getInt(path + ".blueprint_scan_level_" + s, 1);
                    core.getBlueprintScanLevels().set(s, scanLvl);
                    double price = config.getDouble(path + ".blueprint_price_" + s, 0.0);
                    core.getBlueprintPrices().set(s, price);
                    boolean selling = config.getBoolean(path + ".blueprint_selling_status_" + s, false);
                    core.getBlueprintSellingStatus().set(s, selling);
                    if (config.contains(path + ".blueprint_slot_" + s)) {
                        java.util.List<?> list = config.getList(path + ".blueprint_slot_" + s);
                        if (list != null) {
                            java.util.List<TerritoryCore.BlockSnapshot> slotList = core.getBlueprintSlots().get(s);
                            slotList.clear();
                            for (Object obj : list) {
                                if (obj instanceof java.util.Map<?, ?> map) {
                                    try {
                                        int relX = ((Number) map.get("relX")).intValue();
                                        int relY = ((Number) map.get("relY")).intValue();
                                        int relZ = ((Number) map.get("relZ")).intValue();
                                        String material = (String) map.get("material");
                                        String blockData = (String) map.get("blockData");
                                        slotList.add(new TerritoryCore.BlockSnapshot(relX, relY, relZ, material, blockData));
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }
                }
                
                if (blueprintPrice > 0 && sellingSlotIndex >= 0 && sellingSlotIndex < 54) {
                    if (core.getBlueprintPrices().get(sellingSlotIndex) == 0.0) {
                        core.getBlueprintPrices().set(sellingSlotIndex, blueprintPrice);
                        core.getBlueprintSellingStatus().set(sellingSlotIndex, true);
                    }
                }
                
                activeCores.put(getBlockAlignedLocation(loc), core);

                int shards = config.getInt(path + ".shards", 0);
                coreShards.put(coreId, shards);

                long peaceUntil = config.getLong(path + ".peace_until", 0L);
                corePeaceCooldowns.put(coreId, peaceUntil);

                long lastRaid = config.getLong(path + ".last_pve_raid", 0L);
                coreLastRaidTimes.put(coreId, lastRaid);

                double permMultiplier = config.getDouble(path + ".permanent_raid_multiplier", 1.0);
                core.setPermanentRaidMultiplier(permMultiplier);

                loadedCount[0]++;
            } catch (Exception e) {
                plugin.getLogger().severe("Loi khi khoi phuc Loi tu cau hinh: " + key);
            }
        }
    }


    private Location getBlockAlignedLocation(Location loc) {
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
                syncCoreRadiusField(core);
                syncCoreAlliance(core);
                return core;
            }
        }
        return null;
    }

    private void syncCoreRadiusField(TerritoryCore core) {
        if (core == null) return;
        int correctRadius = getCoreRadius(core);
        try {
            java.lang.reflect.Field radiusField = core.getClass().getDeclaredField("radius");
            radiusField.setAccessible(true);
            radiusField.setInt(core, correctRadius);
        } catch (Exception e1) {
            try {
                java.lang.reflect.Field rangeField = core.getClass().getDeclaredField("range");
                rangeField.setAccessible(true);
                rangeField.setInt(core, correctRadius);
            } catch (Exception ignored) {}
        }
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
        try {
            java.lang.reflect.Field field = core.getClass().getDeclaredField("allyId");
            field.setAccessible(true);
            field.set(core, currentAlly);
        } catch (Exception e) {
            try {
                java.lang.reflect.Method method = core.getClass().getMethod("setAllyId", String.class);
                method.invoke(core, currentAlly);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Kiểm toán xem Lõi lãnh thổ có đang trong trạng thái Raid tích cực hay không.
     */
    private boolean isRaidActive(TerritoryCore core) {
        if (plugin.getRaidSession() == null || core == null) return false;
        try {
            Object activeRaid = plugin.getRaidSession().getClass()
                    .getMethod("getActiveRaid", TerritoryCore.class)
                    .invoke(plugin.getRaidSession(), core);
            if (activeRaid != null) {
                return (boolean) activeRaid.getClass().getMethod("isRunning").invoke(activeRaid);
            }
        } catch (Exception e1) {
            try {
                Object activeRaid = plugin.getRaidSession().getClass()
                        .getMethod("getActiveRaid", java.util.UUID.class)
                        .invoke(plugin.getRaidSession(), core.getCoreId());
                if (activeRaid != null) {
                    return (boolean) activeRaid.getClass().getMethod("isRunning").invoke(activeRaid);
                }
            } catch (Exception e2) {
                try {
                    return (boolean) plugin.getRaidSession().getClass()
                            .getMethod("isRaidActive", TerritoryCore.class)
                            .invoke(plugin.getRaidSession(), core);
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    public void registerCore(Location loc, TerritoryCore core) {
        syncCoreRadiusField(core);
        Location alignedLoc = getBlockAlignedLocation(loc);
        activeCores.put(alignedLoc, core);

        YamlConfiguration playerConfig = loadPlayerConfig(core.getOwnerUUID());
        saveCoreToConfig(playerConfig, core);
        savePlayerConfig(core.getOwnerUUID(), playerConfig);
        
        HologramManager.updateCoreHologram(core);
    }

    public TerritoryCore getCoreAt(Location loc) {
        if (loc == null) return null;
        TerritoryCore core = activeCores.get(getBlockAlignedLocation(loc));
        if (core != null) {
            syncCoreRadiusField(core);
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
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
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

        ConfigurationSection coresSection = coresConfig.getConfigurationSection("cores");
        if (coresSection != null) {
            for (String key : coresSection.getKeys(false)) {
                String ownerStr = coresSection.getString(key + ".owner");
                if (ownerStr != null && ownerStr.equals(ownerUUID.toString())) {
                    coresConfig.set("cores." + key, null);
                    removed = true;
                }
            }
        }
        if (removed) saveCoresConfig();
        return removed;
    }

    public TerritoryCore getCoreByLocationRange(Location loc) {
        if (loc == null) return null;
        for (TerritoryCore core : activeCores.values()) {
            syncCoreRadiusField(core);
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
            syncCoreRadiusField(core);
            syncCoreAlliance(core);
        }
        return activeCores.values();
    }

    public ItemStack createCoreItem() {
        return createCoreItem(1);
    }

    public ItemStack createCoreItem(int level) {
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
            coreItem.setItemMeta(meta);
        }
        return coreItem;
    }

    public boolean removeCore(Location loc, Player player, boolean giveItem) {
        Location alignedLoc = getBlockAlignedLocation(loc);
        TerritoryCore core = activeCores.remove(alignedLoc);
        if (core == null) return false;

        removeAssociatedNPCs(core);
        if (plugin.getFarmerManager() != null) {
            plugin.getFarmerManager().removeFarmersAssociatedWithCore(core.getCoreId(), player, giveItem);
        }
        if (plugin.getTowerManager() != null) {
            plugin.getTowerManager().removeTowersAssociatedWithCore(core.getCoreId(), player, giveItem);
        }
        coreShards.remove(core.getCoreId());
        corePeaceCooldowns.remove(core.getCoreId());

        UUID ownerUUID = core.getOwnerUUID();
        YamlConfiguration playerConfig = loadPlayerConfig(ownerUUID);
        playerConfig.set("cores." + core.getCoreId().toString(), null);

        boolean hasCoresLeft = false;
        if (playerConfig.contains("cores")) {
            ConfigurationSection sec = playerConfig.getConfigurationSection("cores");
            if (sec != null && !sec.getKeys(false).isEmpty()) {
                hasCoresLeft = true;
            }
        }

        if (!hasCoresLeft) {
            File pFile = getPlayerFile(ownerUUID);
            File pBackup = getPlayerBackupFile(ownerUUID);
            File pTemp = getPlayerTempFile(ownerUUID);
            if (pFile.exists()) pFile.delete();
            if (pBackup.exists()) pBackup.delete();
            if (pTemp.exists()) pTemp.delete();
        } else {
            savePlayerConfig(ownerUUID, playerConfig);
        }
        
        HologramManager.removeCoreHologram(core.getCoreId(), core.getLocation());

        Block block = alignedLoc.getBlock();
        block.setType(Material.AIR);

        if (player != null && giveItem) {
            player.getInventory().addItem(createCoreItem(core.getLevel()));
            player.sendMessage(ChatColor.GREEN + "[Bảo vệ] Lõi Lãnh Thổ (Cấp " + core.getLevel() + ") đã được đóng gói và nạp trực tiếp vào hòm đồ của bạn.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f);
        }
        return true;
    }

    public void loadAllCores() {
        int[] loadedCount = new int[]{0};

        // 1. Kiểm tra di trú (Migration Check) từ file cores.yml cũ
        if (coresFile.exists() && coresFile.length() > 0) {
            YamlConfiguration oldConfig = new YamlConfiguration();
            try {
                oldConfig.load(coresFile);
                if (oldConfig.contains("cores")) {
                    ConfigurationSection oldSection = oldConfig.getConfigurationSection("cores");
                    if (oldSection != null && !oldSection.getKeys(false).isEmpty()) {
                        plugin.getLogger().warning("[TD] Phat hien du lieu cores.yml cu! Dang tien hanh di tru sang luu tru ca nhan...");
                        
                        loadCoresFromConfig(oldConfig, null, loadedCount);
                        
                        for (TerritoryCore core : activeCores.values()) {
                            YamlConfiguration playerConfig = loadPlayerConfig(core.getOwnerUUID());
                            saveCoreToConfig(playerConfig, core);
                            savePlayerConfig(core.getOwnerUUID(), playerConfig);
                        }
                        
                        plugin.getLogger().info("[TD] Da hoan thanh di tru " + loadedCount[0] + " Loi sang userdata.");
                        
                        File migratedBackup = new File(plugin.getDataFolder(), "cores_migrated_backup.yml");
                        if (coresFile.renameTo(migratedBackup)) {
                            plugin.getLogger().info("[TD] Da sao luu cores.yml thanh cores_migrated_backup.yml");
                        } else {
                            coresFile.delete();
                        }
                        return;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[TD] Loi khi nap hoac di tru file cores.yml cu: " + e.getMessage());
            }
        }

        // 2. Nap du lieu tu thu muc userdata/
        File userDataDir = getUserDataFolder();
        File[] files = userDataDir.listFiles((dir, name) -> name.endsWith(".yml") && !name.endsWith(".tmp") && !name.endsWith(".bak"));
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                String uuidStr = fileName.substring(0, fileName.length() - 4);
                try {
                    UUID ownerUUID = UUID.fromString(uuidStr);
                    YamlConfiguration playerConfig = loadPlayerConfig(ownerUUID);
                    loadCoresFromConfig(playerConfig, ownerUUID, loadedCount);
                } catch (IllegalArgumentException e) {
                    // Ignore invalid UUID filenames
                }
            }
        }

        plugin.getLogger().info("[TD] Da khoi phuc thanh cong " + loadedCount[0] + " Loi Lanh Tho dang hoat dong tu userdata.");
    }

    public void saveAllCores() {
        java.util.Map<UUID, java.util.List<TerritoryCore>> playerCores = new java.util.HashMap<>();
        for (TerritoryCore core : activeCores.values()) {
            playerCores.computeIfAbsent(core.getOwnerUUID(), k -> new java.util.ArrayList<>()).add(core);
        }

        for (Map.Entry<UUID, java.util.List<TerritoryCore>> entry : playerCores.entrySet()) {
            UUID ownerUUID = entry.getKey();
            java.util.List<TerritoryCore> cores = entry.getValue();

            YamlConfiguration playerConfig = loadPlayerConfig(ownerUUID);
            playerConfig.set("cores", null);

            for (TerritoryCore core : cores) {
                saveCoreToConfig(playerConfig, core);
                Block block = core.getLocation().getBlock();
                if (block.getType() == Material.CONDUIT) {
                    saveCoreToBlock(block, core);
                }
            }

            savePlayerConfig(ownerUUID, playerConfig);
        }
    }

    private void saveCoreToBlock(Block block, TerritoryCore core) {
        if (block.getState() instanceof Conduit conduit) {
            PersistentDataContainer pdc = conduit.getPersistentDataContainer();
            pdc.set(PDCKeys.CORE_ID, PersistentDataType.STRING, core.getCoreId().toString());
            pdc.set(PDCKeys.CORE_LEVEL, PersistentDataType.INTEGER, core.getLevel());
            pdc.set(PDCKeys.CORE_FEP, PersistentDataType.DOUBLE, core.getFep());
            pdc.set(PDCKeys.CORE_SHIELD, PersistentDataType.DOUBLE, core.getShield());
            pdc.set(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING, core.getOwnerUUID().toString());

            NamespacedKey coreShardsKey = new NamespacedKey(plugin, "td_core_shards");
            pdc.set(coreShardsKey, PersistentDataType.INTEGER, getShards(core.getCoreId()));

            NamespacedKey isMergedKey = new NamespacedKey(plugin, "td_core_is_merged");
            NamespacedKey mergeCountKey = new NamespacedKey(plugin, "td_core_merge_count");
            pdc.set(isMergedKey, PersistentDataType.BYTE, (byte) (core.isMerged() ? 1 : 0));
            pdc.set(mergeCountKey, PersistentDataType.INTEGER, core.getMergeCount());

            String currentAlly = getCoreAlliance(core);
            if (currentAlly != null) {
                pdc.set(PDCKeys.ALLY_ID, PersistentDataType.STRING, currentAlly);
            } else {
                pdc.remove(PDCKeys.ALLY_ID);
            }
            conduit.update(true);
        }
    }

    private void saveCoresConfig() {
        // De giu tuong thich cho cac cho goi cu neu co, tu dong saveAllCores
        saveAllCores();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRaidMobDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null) return;

        if (!victim.hasMetadata("td_raid_mob") && !victim.hasMetadata("td_npc_attacker")) {
            return;
        }

        Location deathLoc = victim.getLocation();
        TerritoryCore core = getCoreByLocationRange(deathLoc);
        if (core == null) return;

        int shardAmount = 1;
        if (victim.hasMetadata("td_elite_boss")) {
            shardAmount = 25;

            // Roll a premium random Netherite item
            Material[] netheriteItems = {
                Material.NETHERITE_HELMET,
                Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS,
                Material.NETHERITE_BOOTS,
                Material.NETHERITE_SWORD,
                Material.NETHERITE_AXE,
                Material.NETHERITE_PICKAXE,
                Material.NETHERITE_SHOVEL,
                Material.NETHERITE_HOE
            };

            java.util.Random random = new java.util.Random();
            Material selectedMaterial = netheriteItems[random.nextInt(netheriteItems.length)];
            ItemStack netheriteDrop = new ItemStack(selectedMaterial);

            // Fetch all available vanilla enchantments
            java.util.List<Enchantment> availableEnchants = new java.util.ArrayList<>();
            for (Enchantment ench : Enchantment.values()) {
                if (ench != null) {
                    availableEnchants.add(ench);
                }
            }

            if (!availableEnchants.isEmpty()) {
                java.util.Collections.shuffle(availableEnchants);
                int count = Math.min(10, availableEnchants.size());
                for (int i = 0; i < count; i++) {
                    Enchantment ench = availableEnchants.get(i);
                    int maxLvl = ench.getMaxLevel();
                    int minLvl = ench.getStartLevel();
                    int level = minLvl;
                    if (maxLvl > minLvl) {
                        level = minLvl + random.nextInt(maxLvl - minLvl + 1);
                    }
                    netheriteDrop.addUnsafeEnchantment(ench, level);
                }
            }

            // Set beautiful premium name
            ItemMeta meta = netheriteDrop.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "★ Cổ Vật Thượng Cổ Netherite ★");
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(ChatColor.GRAY + "Rơi ra từ việc tiêu diệt Siêu Cấp Mini-Boss!");
                lore.add(ChatColor.YELLOW + "Chứa đựng 10 dòng cổ tự ma pháp vĩnh cửu.");
                meta.setLore(lore);
                netheriteDrop.setItemMeta(meta);
            }

            // Drop at location
            if (deathLoc.getWorld() != null) {
                deathLoc.getWorld().dropItemNaturally(deathLoc, netheriteDrop);
            }
        } else if (victim.getType() == EntityType.RAVAGER || victim.getType() == EntityType.EVOKER) {
            shardAmount = 3;
        }

        addShards(core.getCoreId(), shardAmount);
        saveAllCores();

        Location coreLoc = core.getLocation();
        if (coreLoc != null && coreLoc.getWorld() != null) {
            coreLoc.getWorld().spawnParticle(Particle.PORTAL, deathLoc, 10, 0.2, 0.2, 0.2, 0.1);
            coreLoc.getWorld().playSound(deathLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.8f);
        }

        Player owner = Bukkit.getPlayer(core.getOwnerUUID());
        if (owner != null && owner.isOnline() && getCoreByLocationRange(owner.getLocation()) == core) {
            owner.sendMessage(ChatColor.AQUA + "[Tài chính] + " + shardAmount + " Shard đã tự động được thu hoạch và nạp vào Lõi từ quái Raid bị hạ gục!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCoreBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CONDUIT) return;

        TerritoryCore core = getCoreAt(block.getLocation());
        if (core == null) return;

        Player player = event.getPlayer();
        if (player == null) return;

        if (!core.getOwnerUUID().equals(player.getUniqueId()) && !player.hasPermission("territorydefense.admin")) {
            player.sendMessage(ChatColor.RED + "Bạn không phải chủ sở hữu của Lõi này! Khối Lõi có tính chất Soulbound.");
            event.setCancelled(true);
            return;
        }

        removeCore(block.getLocation(), player, true);
        event.setCancelled(true);
    }

    /**
     * SỬA LỖI 2, 4: CHỐNG PHÁ KHỐI HOẶC ĐẶT KHỐI TRÁI PHÉP TRONG RANH GIỚI NGƯỜI KHÁC
     * Cho phép tương tác xây dựng/phá hủy nếu phe công và phe thủ đang trong trạng thái Chiến Sự (War active).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreakInTerritory(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.CONDUIT) return;

        Location loc = block.getLocation();
        TerritoryCore core = getCoreByLocationRange(loc);
        if (core == null) return;

        Player player = event.getPlayer();
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());

        String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAlly = getCoreAlliance(core);
        boolean isAlly = playerAlly != null && coreAlly != null && coreAlly.equalsIgnoreCase(playerAlly);

        if (!isOwner && !isAlly) {
            boolean isAtWar = false;
            String attackerId = plugin.getSiegeSession() != null ? plugin.getSiegeSession().getCombatantId(player.getUniqueId()) : player.getUniqueId().toString();
            String defenderId = core.getAllyId() != null ? core.getAllyId() : core.getOwnerUUID().toString();
            if (plugin.getSiegeSession() != null) {
                isAtWar = plugin.getSiegeSession().isAtWar(attackerId, defenderId);
            }

            if (!isAtWar) {
                player.sendMessage(ChatColor.RED + "Bạn không thể phá khối trong vùng bảo vệ ranh giới của người khác!");
                event.setCancelled(true);
            } else {
                // Đang có chiến tranh, kiểm tra cờ công thành ở tay trái
                ItemStack offHand = player.getInventory().getItemInOffHand();
                boolean holdsFlag = offHand != null && offHand.hasItemMeta() &&
                        offHand.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_SIEGE_FLAG, PersistentDataType.BYTE);
                if (!holdsFlag) {
                    player.sendMessage(ChatColor.RED + "[Chiến sự] Bạn phải trang bị Cờ Công Thành ở tay trái (Off-hand) mới có thể công phá block của đối thủ!");
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlaceInTerritory(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.CONDUIT) return;

        Location loc = block.getLocation();
        TerritoryCore core = getCoreByLocationRange(loc);
        if (core == null) return;

        Player player = event.getPlayer();
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());

        String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAlly = getCoreAlliance(core);
        boolean isAlly = playerAlly != null && coreAlly != null && coreAlly.equalsIgnoreCase(playerAlly);

        if (!isOwner && !isAlly) {
            boolean isAtWar = false;
            String attackerId = plugin.getSiegeSession() != null ? plugin.getSiegeSession().getCombatantId(player.getUniqueId()) : player.getUniqueId().toString();
            String defenderId = core.getAllyId() != null ? core.getAllyId() : core.getOwnerUUID().toString();
            if (plugin.getSiegeSession() != null) {
                isAtWar = plugin.getSiegeSession().isAtWar(attackerId, defenderId);
            }

            if (!isAtWar) {
                player.sendMessage(ChatColor.RED + "Bạn không thể đặt khối trong vùng bảo vệ ranh giới của người khác!");
                event.setCancelled(true);
            }
        }
    }

    /**
     * CHỐNG TƯƠNG TÁC VẬT PHẨM/BLOCKS TRÁI PHÉP TRONG LÃNH THỔ (LỖI 2 & TƯƠNG TÁC)
     * Ngăn chặn người lạ tương tác với rương, cửa, cần gạt, nút bấm... trong ranh giới bảo vệ ngoài thời gian chiến sự.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractInTerritory(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        // Bỏ qua khối Lõi Conduit vì đã có CoreProtectionListener và FEPManager xử lý riêng biệt
        if (block.getType() == Material.CONDUIT) return;

        Location loc = block.getLocation();
        TerritoryCore core = getCoreByLocationRange(loc);
        if (core == null) return;

        Player player = event.getPlayer();
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());

        String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAlly = getCoreAlliance(core);
        boolean isAlly = playerAlly != null && coreAlly != null && coreAlly.equalsIgnoreCase(playerAlly);

        if (!isOwner && !isAlly) {
            boolean isAtWar = false;
            String attackerId = plugin.getSiegeSession() != null ? plugin.getSiegeSession().getCombatantId(player.getUniqueId()) : player.getUniqueId().toString();
            String defenderId = core.getAllyId() != null ? core.getAllyId() : core.getOwnerUUID().toString();
            if (plugin.getSiegeSession() != null) {
                isAtWar = plugin.getSiegeSession().isAtWar(attackerId, defenderId);
            }

            if (!isAtWar) {
                player.sendMessage(ChatColor.RED + "[Bảo vệ] Bạn không thể tương tác với các khối hoặc vật phẩm trong vùng lãnh thổ của người khác!");
                event.setCancelled(true);
            }
        }
    }

    /**
     * SỬA LỖI 5: CHỐNG ĐẶT CORE TRÙNG HOẶC GIAO THOA ĐỒNG THỜI VỚI RANH GIỚI NGƯỜI KHÁC
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCorePlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.CONDUIT) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        if (item == null || !item.hasItemMeta()) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE)) {
            return;
        }

        // 1. Kiểm toán giới hạn sở hữu 1 Lõi
        if (getOwnedCoreCount(player.getUniqueId()) >= 1) {
            player.sendMessage(ChatColor.RED + "Bạn đã sở hữu một lãnh thổ rồi! Hãy thu hồi lõi cũ trước khi lập đất mới.");
            event.setCancelled(true);
            return;
        }

        Location alignedLoc = getBlockAlignedLocation(block.getLocation());

        int placedLevel = 1;
        if (item.hasItemMeta()) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            if (pdc.has(PDCKeys.CORE_LEVEL, PersistentDataType.INTEGER)) {
                placedLevel = pdc.get(PDCKeys.CORE_LEVEL, PersistentDataType.INTEGER);
            }
        }

        // 2. Thuật toán quét và chống giao thoa ranh giới (AABB Boundary Overlap Checker)
        int newCoreRadius = switch (placedLevel) {
            case 1 -> 16;
            case 2 -> 24;
            case 3 -> 32;
            case 4 -> 40;
            case 5 -> 50;
            default -> 16;
        };

        String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;

        for (TerritoryCore existingCore : activeCores.values()) {
            Location existingLoc = existingCore.getLocation();
            if (existingLoc.getWorld() == null || !existingLoc.getWorld().equals(alignedLoc.getWorld())) continue;

            int existingRadius = getCoreRadius(existingCore);
            int distanceX = Math.abs(alignedLoc.getBlockX() - existingLoc.getBlockX());
            int distanceZ = Math.abs(alignedLoc.getBlockZ() - existingLoc.getBlockZ());

            // Ranh giới bảo vệ không được phép giao nhau hoặc gối chồng lên nhau (trừ khi cùng liên minh)
            if (distanceX <= (newCoreRadius + existingRadius) && distanceZ <= (newCoreRadius + existingRadius)) {
                String existingAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(existingCore.getOwnerUUID()) : null;
                if (playerAlly != null && playerAlly.equals(existingAlly)) {
                    // Cùng liên minh: Cho phép đặt chồng ranh giới lên nhau để chuẩn bị gộp đất
                    continue;
                }
                player.sendMessage(ChatColor.RED + "Vị trí này quá gần với một Lãnh thổ khác đang tồn tại! Bán kính ranh giới dự kiến bị giao thoa đè lên nhau.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                event.setCancelled(true);
                return;
            }
        }

        UUID newCoreId = UUID.randomUUID();

        TerritoryCore newCore = new TerritoryCore(
                newCoreId, alignedLoc, player.getUniqueId(), placedLevel, 100.0, 1000.0, playerAlly
        );
        newCore.setFep(newCore.getMaxFepCapacity());
        newCore.setShield(newCore.getMaxShieldCapacity());

        registerCore(alignedLoc, newCore);
        saveCoreToBlock(block, newCore);

        player.sendMessage(ChatColor.GREEN + "Chúc mừng! Lãnh thổ của bạn đã được thiết lập thành công.");
        player.playSound(block.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.0f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeathKeepCore(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player == null) return;

        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (item != null && item.hasItemMeta()) {
                PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                if (pdc.has(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE)) {
                    event.getItemsToKeep().add(item);
                    iterator.remove();
                }
            }
        }
    }

    /**
     * SỬA LỖI 2: CHỐNG CHÁY NỔ (EXPLOSION SHIELD PROTECT) & KHẤU TRỪ ĐỘ BỀN LÁ CHẮN
     * Kích hoạt giáp bảo hộ của Lõi lãnh thổ triệt tiêu hoàn toàn mọi vụ nổ vật lý địa hình
     * (bao gồm Creeper, TNT, Fireball của Ghast, v.v.) bên trong lãnh thổ được bảo vệ khi có Raid hoặc trạng thái thường.
     * ĐÃ CẬP NHẬT CHUẨN XÁC:
     * - LÁ CHẮN CHỈ HOẠT ĐỘNG TRONG RAID: Chỉ thực hiện trừ điểm khiên ảo Shield HP khi đang có Raid PvE diễn ra.
     * - NGOÀI RAID BẢO VỆ TUYỆT ĐỐI MIỄN PHÍ: Khi không có Raid, mọi vụ nổ địa hình đều bị vô hiệu hoá 100% (không phá block, không tốn khiên ảo).
     * - MIỄN NHIỄM TNT TRONG RAID: TNT của đồng minh/chủ sở hữu kích nổ không gây tổn hại Shield HP của Lõi trong suốt thời gian Raid.
     * Chỉ loại bỏ sát thương từ TNT, vẫn nhận sát thương bình thường từ Creeper, Fireball, Wither, End Crystal, v.v.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTNTExplodeShieldProtect(EntityExplodeEvent event) {
        Location explodeLoc = event.getLocation();
        TerritoryCore core = getCoreByLocationRange(explodeLoc);

        if (core != null) {
            boolean raidActive = isRaidActive(core);

            if (raidActive) {
                // Trong thời gian Raid: Lá chắn hoạt động dựa trên chỉ số Shield HP
                if (core.getShield() <= 0) {
                    return; // Điểm khiên đã cạn, cho phép nổ phá block thông thường
                }

                Entity entity = event.getEntity();
                // Bao gồm cả thực thể TNT thường và Xe mỏ TNT để triệt tiêu lỗ hổng
                boolean isTNT = entity != null && (entity.getType() == EntityType.TNT || entity.getType() == EntityType.TNT_MINECART);

                double damage = 0;
                if (isTNT) {
                    // Trong thời gian Raid, TNT của người chơi không gây damage lên Shield HP (loại bỏ hoàn toàn sát thương từ TNT)
                    damage = 0;
                } else {
                    // Sát thương vụ nổ từ quái vật lên khiên ảo (vẫn nhận sát thương từ các nguồn khác)
                    if (entity != null) {
                        damage = switch (entity.getType()) {
                            case CREEPER -> 150.0;
                            case FIREBALL, SMALL_FIREBALL -> 80.0;
                            case WITHER_SKULL -> 200.0;
                            case WITHER -> 350.0;
                            case END_CRYSTAL -> 250.0;
                            default -> 100.0;
                        };
                    } else {
                        damage = 100.0; // Vụ nổ từ block/bed/anchor
                    }
                }

                // Khấu trừ điểm khiên ảo khi có Raid
                if (damage > 0) {
                    double newShield = Math.max(0.0, core.getShield() - damage);
                    core.setShield(newShield);
                    saveAllCores();
                }

                // Triệt tiêu vụ nổ
                event.blockList().clear();
                explodeLoc.getWorld().playSound(explodeLoc, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                explodeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, explodeLoc, 15, 0.5, 0.5, 0.5, 0.1);

                Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                if (owner != null && owner.isOnline()) {
                    if (damage > 0) {
                        owner.sendMessage(ChatColor.YELLOW + "[Khiên Bảo Vệ] Lá chắn hấp thụ vụ nổ Raid! -" + damage + " Shield HP. Còn lại: " + String.format("%.0f", core.getShield()) + "/" + core.getMaxShieldCapacity() + " HP.");
                    } else {
                        owner.sendMessage(ChatColor.GREEN + "[Khiên Bảo Vệ] Đã chặn đứng vụ nổ TNT đồng minh trong thời gian Raid (Không tổn hại Shield HP)!");
                    }
                }
            } else {
                // Ngoài thời gian Raid: Lãnh thổ được bảo vệ tuyệt đối không thể bị phá huỷ (block list cleared miễn phí, không tốn khiên)
                event.blockList().clear();
                explodeLoc.getWorld().playSound(explodeLoc, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                explodeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, explodeLoc, 15, 0.5, 0.5, 0.5, 0.1);

                Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage(ChatColor.GREEN + "[Lá Chắn Tuyệt Đối] Đã triệt tiêu hoàn toàn vụ nổ ngoài thời gian Raid!");
                }
            }
        }
    }

    private void wrapTowerPlaceListener() {
        try {
            for (org.bukkit.plugin.RegisteredListener rl : org.bukkit.event.block.BlockPlaceEvent.getHandlerList().getRegisteredListeners()) {
                if (rl.getPlugin().equals(plugin) && rl.getListener().getClass().getSimpleName().equals("TowerManager")) {
                    org.bukkit.event.block.BlockPlaceEvent.getHandlerList().unregister(rl);
                    org.bukkit.event.block.BlockPlaceEvent.getHandlerList().register(new org.bukkit.plugin.RegisteredListener(
                            rl.getListener(),
                            (listener, event) -> {
                                if (event instanceof org.bukkit.event.block.BlockPlaceEvent) {
                                    org.bukkit.event.block.BlockPlaceEvent pe = (org.bukkit.event.block.BlockPlaceEvent) event;
                                    ItemStack item = pe.getItemInHand();
                                    Material mat = pe.getBlockPlaced().getType();
                                    if (isTowerMaterial(mat)) {
                                        boolean isTowerItem = item != null && item.hasItemMeta() && (
                                                item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "td_tower_type"), PersistentDataType.STRING)
                                                        || item.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE)
                                                        || (item.getItemMeta().hasDisplayName() && ChatColor.stripColor(item.getItemMeta().getDisplayName()).startsWith("Tháp"))
                                        );
                                        if (!isTowerItem) return;
                                    }
                                }
                                rl.getExecutor().execute(listener, event);
                            },
                            rl.getPriority(),
                            rl.getPlugin(),
                            rl.isIgnoringCancelled()
                    ));
                    plugin.getLogger().info("[TD] Đã kích hoạt màng lọc thông minh cho việc đặt Tháp Canh.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[TD] Không thể tự động bọc bộ đặt tháp: " + e.getMessage());
        }
    }

    private boolean isTowerMaterial(Material mat) {
        return mat == Material.SKELETON_SKULL || mat == Material.SKELETON_WALL_SKULL
                || mat == Material.CREEPER_HEAD || mat == Material.CREEPER_WALL_HEAD
                || mat == Material.WITHER_SKELETON_SKULL || mat == Material.WITHER_SKELETON_WALL_SKULL
                || mat == Material.ZOMBIE_HEAD || mat == Material.ZOMBIE_WALL_HEAD
                || mat == Material.PIGLIN_HEAD || mat == Material.PIGLIN_WALL_HEAD
                || mat == Material.PLAYER_HEAD || mat == Material.PLAYER_WALL_HEAD
                || mat == Material.DRAGON_HEAD || mat == Material.DRAGON_WALL_HEAD;
    }
}